package net.runelite.client.plugins.microbot.drocolosseumprayers;

/**
 * What a finished Colosseum run still owes before AIO takes over. After Sol: Search the Rewards Chest, Bank-all, Search
 * again until the game says it is empty, then leave the arena (01:38 recording). Nothing is taken to the inventory.
 * After a death: loot the grave in the lobby.
 * <p>
 * AIO only rerolls once the items are secured, so there is no deadline: a watchdog retries a stalled step and raises
 * one alert, and only a manual stop hands ownership back early. The arena is never left before the chest reports
 * itself empty. Nothing here clicks.
 */
final class ColosseumAftermath {
    enum Kind { CHEST, GRAVE }
    enum Step { WAIT, SEARCH_CHEST, BANK_REWARDS, LEAVE_ARENA, CONFIRM_LEAVE, LOOT_GRAVE, BANK_FOR_GRAVE, DONE }
    static final int REWARDS_CHEST=50741;
    static final String CHEST_EMPTY="The chest appears to be empty.";
    /** The game's line once the grave is emptied (1.0.58 death recording). */
    static final String GRAVE_EMPTIED="You successfully retrieved everything from your gravestone.";
    static final long SETTLE_MS=1_800L,RETRY_MS=3_000L;
    /** No progress for this long retries the current step; this many stalls raises the alert once. */
    static final long WATCHDOG_MS=30_000L;
    static final int ALERT_STALLS=3;
    /** After the respawn, no grave varbit for this long means there is nothing left to secure. */
    static final long NO_GRAVE_MS=20_000L;

    /** One read of the arena after Sol, on the client thread. */
    static final class ChestView {
        private final boolean inside,open,bankable,near,leaveConfirmation;
        /**
         * @param bankable the Bank-all button was found; Discard is never chosen
         * @param leaveConfirmation Minimus's "Are you sure you wish to leave?" is on screen
         */
        ChestView(boolean inside,boolean open,boolean bankable,boolean near,boolean leaveConfirmation){
            this.inside=inside;this.open=open;this.bankable=bankable;
            this.near=near;this.leaveConfirmation=leaveConfirmation;
        }
        boolean inside(){return inside;}
        boolean open(){return open;}
        boolean bankable(){return bankable;}
        boolean near(){return near;}
        boolean leaveConfirmation(){return leaveConfirmation;}
        @Override public boolean equals(Object o){
            if(!(o instanceof ChestView))return false;
            ChestView v=(ChestView)o;
            return inside==v.inside&&open==v.open&&bankable==v.bankable
                &&near==v.near&&leaveConfirmation==v.leaveConfirmation;
        }
        @Override public int hashCode(){return java.util.Objects.hash(inside,open,bankable,near,leaveConfirmation);}
    }

    private Kind kind;
    private long startedAt,nextAt,outsideAt=-1,progressAt;
    private boolean bankDispatched,empty,looted,finished,released,alert,progressed;
    private int stalls;
    private Object lastView;
    private String status;

    synchronized boolean begin(Kind kind,long now){
        if(this.kind!=null)return false;
        this.kind=kind;startedAt=now;nextAt=now+SETTLE_MS;progressAt=now;
        status=kind==Kind.CHEST?"Claiming the Rewards Chest":"Waiting to respawn, then looting the grave";
        return true;
    }
    synchronized boolean pending(){return kind!=null&&!finished;}
    synchronized Kind kind(){return kind;}
    synchronized String status(){return status;}
    synchronized void message(String text){
        if(kind==Kind.CHEST&&CHEST_EMPTY.equals(text)&&!empty){empty=true;progressed=true;}
        if(kind==Kind.GRAVE&&GRAVE_EMPTIED.equals(text))looted=true;
    }
    synchronized void bankDispatched(){bankDispatched=true;}
    synchronized void looted(){looted=true;}
    /** A manual stop: the only release before the items are secured. */
    synchronized void cancel(){finished=true;}
    /** True once, after the claim finished: the moment AIO ownership goes back. */
    synchronized boolean release(){if(kind==null||!finished||released)return false;released=true;return true;}
    /** True once, when the watchdog has retried {@link #ALERT_STALLS} times without progress. */
    synchronized boolean takeAlert(){boolean a=alert;alert=false;return a;}
    synchronized int stalls(){return stalls;}

    synchronized Step chest(long now,ChestView view){
        if(kind!=Kind.CHEST||finished)return Step.DONE;
        observe(now,view);
        if(!view.inside()&&empty)return finish("Rewards secured; left the Colosseum");
        watchdog(now);
        if(!view.inside()){status="Out of the arena before the Rewards Chest was claimed; claim it by hand";return Step.WAIT;}
        if(now<nextAt)return Step.WAIT;
        if(empty){
            status="Rewards secured; leaving the Colosseum";nextAt=now+RETRY_MS;
            return view.leaveConfirmation()?Step.CONFIRM_LEAVE:Step.LEAVE_ARENA;
        }
        // One Bank-all per opening of the chest; one Search per retry window, never a click every poll.
        if(view.open()&&!bankDispatched){
            if(!view.bankable()){status="Rewards Chest open without a Bank-all button; holding until it is claimed";return Step.WAIT;}
            status="Banking all rewards";nextAt=now+RETRY_MS;return Step.BANK_REWARDS;
        }
        if(!view.near()){status="Looking for the Rewards Chest; holding until it is claimed";return Step.WAIT;}
        // Searched again after Bank-all: only "The chest appears to be empty." proves the claim.
        bankDispatched=false;
        status="Claiming the Rewards Chest";nextAt=now+RETRY_MS;return Step.SEARCH_CHEST;
    }

    /**
     * @param outside respawned: logged in, alive and out of the arena instance
     * @param hasGrave the game reports a standing grave
     * @param graveNear that grave is in the loaded scene
     */
    /**
     * 1.0.72 (user, 2026-11-05: "after failing a certain number of times, bank and try again; if still nothing then
     * logout anyways"; live 23:44:22-44 retried "Grave not emptied: inventory full" six times): a loot that leaves
     * items in the grave with no free slot is a failure. After {@link #GRAVE_FAILS} of them the inventory is banked and
     * the grave tried again; {@link #GRAVE_FAILS} more and the claim gives up and logs out whatever the setting.
     */
    static final int GRAVE_FAILS=3;
    private int graveFails,bankTrips;private boolean bankDue,banked,forceLogout;
    synchronized void lootBlocked(){
        if(kind!=Kind.GRAVE||finished)return;
        graveFails++;
        if(graveFails<GRAVE_FAILS)return;
        if(!banked){bankDue=true;graveFails=0;status="Grave blocked by a full inventory: banking, then trying again";return;}
        forceLogout=true;finish("Grave not emptied after banking (inventory full); logging out");
    }
    synchronized void graveBanked(){banked=true;bankDue=false;graveFails=0;nextAt=0;}
    /** The claim gave up with items left: log out even with Logout after run off. */
    synchronized boolean forceLogout(){return forceLogout;}
    synchronized Step grave(long now,boolean outside,boolean hasGrave,boolean graveNear){
        if(kind!=Kind.GRAVE||finished)return Step.DONE;
        if(looted)return finish("Grave looted");
        if(!outside)return Step.WAIT;
        if(outsideAt<0){outsideAt=now;progressAt=now;}
        // No grave, or none any more (emptied by hand too): nothing is left to secure.
        if(!hasGrave)return now-outsideAt>NO_GRAVE_MS?finish("No grave left to loot"):Step.WAIT;
        if(bankDue)
        {
            if(now<nextAt)return Step.WAIT;
            bankDue=false;progressAt=now;nextAt=now+RETRY_MS;
            // Two bank trips that never reported back (no bank reached): carry on as if banked, so the claim still ends.
            if(++bankTrips>2){banked=true;}else return Step.BANK_FOR_GRAVE;
        }
        observe(now,graveNear);
        watchdog(now);
        if(!graveNear){status="Grave not in view; holding until it is looted";return Step.WAIT;}
        if(now<nextAt)return Step.WAIT;
        status="Looting the grave";nextAt=now+RETRY_MS;return Step.LOOT_GRAVE;
    }

    /** Any change in what the game shows counts as progress. */
    private void observe(long now,Object view){
        if(progressed||!view.equals(lastView)){lastView=view;progressed=false;progressAt=now;}
    }
    /** A stalled step is retried from scratch; the hold itself never lapses. */
    private void watchdog(long now){
        if(now-progressAt<=WATCHDOG_MS)return;
        stalls++;progressAt=now;nextAt=now;bankDispatched=false;
        if(stalls==ALERT_STALLS)alert=true;
    }

    private Step finish(String status){this.status=status;finished=true;return Step.DONE;}
}
