package net.runelite.client.plugins.microbot.drozulrah;

import net.runelite.api.Skill;
import java.util.function.IntBinaryOperator;
import java.util.function.IntUnaryOperator;
import net.runelite.api.coords.WorldPoint;

/** One return, one route/AFK/XP selection. Only travel/setup actions are exposed here. */
final class ZulrahFeroxReturn {
    enum Route { CAMERA, KBD }
    enum Stage { PREPARE, ARRIVAL_PAUSE, CAMERA_POOL, POOL_APPROACH, KBD_APPROACH, DRINK, PARK_DELAY, PARK, AFK,
        RESTORE, BANK_SETTLE, BANK_APPROACH, OPEN_BANK, CLOSE_BANK, WAIT_CLOSED,
        SKILLS, HOVER, XP_WAIT, INVENTORY, REOPEN_BANK, DONE, FAILED }
    interface Actions {
        default boolean initialize(Route route) { return true; }
        boolean turnPool();
        boolean walkKbd(WorldPoint target);
        boolean walkPool(WorldPoint target);
        void drink();
        boolean park();
        boolean walkBank(WorldPoint target);
        boolean openBank();
        boolean closeBank();
        boolean skills();
        boolean hoverSkill(Skill skill);
        boolean inventory();
        default long completedAt(long suppliedNow) { return suppliedNow; }
    }
    private static final net.runelite.api.coords.WorldArea FEROX = new net.runelite.api.coords.WorldArea(3123,3617,34,29,0);
    static final WorldPoint POOL = new WorldPoint(3128,3637,0);
    static final WorldPoint BANK = new WorldPoint(3130,3631,0);
    final Route route;
    final boolean poolAfk, xpCheck;
    final Skill xpSkill;
    final long arrivalPause, parkPause, afkDuration, xpDuration, kbdPoolPause;
    private final IntBinaryOperator random;
    private Stage stage;
    private long enteredAt=-1, nextInputAt, poolClickedAt, restoreStartedAt=-1, pausedAt=-1;
    private String status="Preparing Ferox return";
    static ZulrahFeroxReturn select(IntUnaryOperator roll,IntBinaryOperator random) {
        return new ZulrahFeroxReturn(Route.CAMERA,
            roll.applyAsInt(10)==0,roll.applyAsInt(35)==0,random);
    }
    ZulrahFeroxReturn(Route route,boolean afk,boolean xp,IntBinaryOperator random) {
        this.route=route;poolAfk=afk;xpCheck=xp;this.random=random;
        xpSkill=new Skill[]{Skill.RANGED,Skill.MAGIC,Skill.HITPOINTS}[random.applyAsInt(0,2)];
        arrivalPause=random.applyAsInt(700,1200);parkPause=random.applyAsInt(250,300);
        afkDuration=random.applyAsInt(2800,4800);xpDuration=3000L+random.applyAsInt(700,1200);
        kbdPoolPause=random.applyAsInt(2600,3799)+random.applyAsInt(75,249);
        stage=Stage.PREPARE;
    }
    Stage stage() { return stage; }
    String status() { return status; }
    boolean ready() { return stage==Stage.DONE; }
    boolean failed() { return stage==Stage.FAILED; }
    private void move(Stage next,long now,String message) {stage=next;enteredAt=now;status=message;}
    private void fail(long now,String message) {move(Stage.FAILED,now,message+"; stop/restart Zulrah");}
    void pause(long now) { if(pausedAt<0 && !ready() && !failed())pausedAt=now; }
    private void resume(long now) {
        if(pausedAt<0)return;
        long gap=Math.max(0L,now-pausedAt);
        if(enteredAt>=0)enteredAt+=gap;
        if(nextInputAt>0)nextInputAt+=gap;
        if(poolClickedAt>0)poolClickedAt+=gap;
        if(restoreStartedAt>=0)restoreStartedAt+=gap;
        pausedAt=-1;
    }
    void tick(WorldPoint here,boolean moving,boolean bankOpen,long now,Actions a) {
        if(here==null || ready() || failed())return;
        resume(now);
        if(enteredAt<0)enteredAt=now;
        if(!FEROX.contains(here)) {
            fail(now,"Left Ferox during return");return;
        }
        if(now-enteredAt>120000L) {
            // Keep the original ability to retry preparation. Recover the pool leg through
            // Zulrah's original short-step walk, without rerolling AFK/XP or using KBD.
            nextInputAt=0L;
            if(stage.ordinal()<=Stage.RESTORE.ordinal()) {
                restoreStartedAt=-1L;
                move(Stage.POOL_APPROACH,now,"Pool return stalled; retrying original Zulrah pool approach");
            } else {
                move(stage==Stage.WAIT_CLOSED?Stage.CLOSE_BANK:stage,now,"Retrying Ferox step " + stage);
            }
            return;
        }
        if(now<nextInputAt)return;
        switch(stage) {
            case PREPARE:
                if(a.initialize(route)) move(route==Route.CAMERA?Stage.ARRIVAL_PAUSE:Stage.KBD_APPROACH,
                    a.completedAt(now),"Return route camera setup complete");
                break;
            case ARRIVAL_PAUSE:
                status="Ferox arrival pause before pool camera";
                if(!moving && now-enteredAt>=arrivalPause)move(Stage.CAMERA_POOL,now,"Turning camera to pool");
                break;
            case CAMERA_POOL:
                if(a.turnPool())move(Stage.DRINK,a.completedAt(now),"Pool visible; direct Drink click");
                else move(Stage.POOL_APPROACH,a.completedAt(now),"Pool camera unavailable; original Zulrah walking fallback");
                break;
            case POOL_APPROACH:
                if(here.distanceTo(POOL)<=3 && !moving)move(Stage.DRINK,now,"Original Zulrah approach reached pool; Drink");
                else {
                    status="Walking original Zulrah pool fallback";
                    if(!moving) {
                        a.walkPool(POOL);nextInputAt=a.completedAt(now)+700L+random.applyAsInt(50,300);
                    }
                }
                break;
            case KBD_APPROACH:
                if(here.distanceTo(POOL)>8) {
                    status="KBD route: walking to Ferox restoration pool";
                    a.walkKbd(POOL);
                    nextInputAt=a.completedAt(now)+700L+random.applyAsInt(50,300);
                } else move(Stage.DRINK,now,"KBD route: pool Drink");
                break;
            case DRINK:
                // Keep Zulrah's independent one-click latch even if object helpers report true incorrectly.
                move(poolAfk?Stage.PARK_DELAY:Stage.RESTORE,now,"Pool Drink issued once");
                a.drink();poolClickedAt=a.completedAt(now);enteredAt=poolClickedAt;
                break;
            case PARK_DELAY:
                status="Pool clicked; pausing before offscreen AFK";
                if(now-enteredAt>=parkPause)move(Stage.PARK,now,"Moving mouse completely off screen");
                break;
            case PARK:
                if(a.park())move(Stage.AFK,a.completedAt(now),"Pool AFK: mouse off screen");
                else fail(now,"Could not park mouse off screen");
                break;
            case AFK:
                if(now-enteredAt>=afkDuration)move(Stage.RESTORE,now,"Pool AFK finished; verifying restoration wait");
                break;
            case RESTORE:
                if(route==Route.CAMERA) {
                    if(moving || here.distanceTo(POOL)>3) {status="Waiting to reach pool after direct Drink";break;}
                    if(restoreStartedAt<0)restoreStartedAt=now;
                    if(now-restoreStartedAt<3400L) {status="Waiting for camera-route pool restore";break;}
                } else if(now-poolClickedAt<kbdPoolPause) {status="Waiting after KBD-route pool";break;}
                move(Stage.BANK_SETTLE,now,"Pool restore wait complete");
                break;
            case BANK_SETTLE:
                if(now-enteredAt>=(route==Route.CAMERA?250L:0L))move(Stage.BANK_APPROACH,now,"Approaching Ferox bank");
                break;
            case BANK_APPROACH:
                if(bankOpen || here.distanceTo(BANK)<=4)move(Stage.OPEN_BANK,now,"Opening Ferox bank");
                else {
                    if(!moving) {
                        a.walkBank(BANK);nextInputAt=a.completedAt(now)+700L+random.applyAsInt(50,300);
                    }
                }
                break;
            case OPEN_BANK:
                if(bankOpen)move(xpCheck?Stage.CLOSE_BANK:Stage.DONE,now,xpCheck?"Selected " + xpSkill + " XP detour":"Ferox bank ready for regear");
                else if(!moving) {a.openBank();nextInputAt=a.completedAt(now)+random.applyAsInt(150,300);}
                break;
            case CLOSE_BANK:
                if(a.closeBank())move(Stage.WAIT_CLOSED,a.completedAt(now),"Waiting for bank closure");break;
            case WAIT_CLOSED:
                if(!bankOpen)move(Stage.SKILLS,now,"Opening Skills for " + xpSkill + " XP");break;
            case SKILLS:
                if(a.skills())move(Stage.HOVER,a.completedAt(now),"Hovering " + xpSkill + " XP");break;
            case HOVER:
                if(a.hoverSkill(xpSkill))move(Stage.XP_WAIT,a.completedAt(now),"Holding " + xpSkill + " XP hover");break;
            case XP_WAIT:
                if(now-enteredAt>=xpDuration)move(Stage.INVENTORY,now,"Returning to Inventory");break;
            case INVENTORY:
                if(a.inventory())move(Stage.REOPEN_BANK,a.completedAt(now),"Reopening bank before regear");break;
            case REOPEN_BANK:
                if(bankOpen)move(Stage.DONE,now,"XP detour complete; Ferox bank ready for regear");
                else {a.openBank();nextInputAt=a.completedAt(now)+random.applyAsInt(150,300);}
                break;
            default:break;
        }
    }
}
