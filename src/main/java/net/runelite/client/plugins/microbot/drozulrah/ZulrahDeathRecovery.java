package net.runelite.client.plugins.microbot.drozulrah;

import net.runelite.api.coords.WorldPoint;

/** Recorded respawn-bank-priestess-Ferox route; owns inputs until observed completion. */
final class ZulrahDeathRecovery {
    enum Stage { SPAWN, BANK, SUPPLIES, CLOSE_BANK, TELEPORT, WAIT_SHORE, PRIESTESS,
        WAIT_COLLECTION, RECLAIM, WAIT_RECLAIM, CLEAR_INTERFACE, FEROX, WAIT_FEROX, DONE, STOPPED }
    enum Spawn { LUMBRIDGE, EDGEVILLE }
    static Spawn spawnAt(WorldPoint p) {
        if(p==null || p.getPlane()!=0)return null;
        if(p.getX()>=3217 && p.getX()<=3227 && p.getY()>=3212 && p.getY()<=3223)return Spawn.LUMBRIDGE;
        if(p.getX()>=3088 && p.getX()<=3101 && p.getY()>=3463 && p.getY()<=3478)return Spawn.EDGEVILLE;
        return null;
    }
    static final class Frame {
        WorldPoint here;
        boolean arena, bankOpen, bankReady, ring, teleport, ringStock, teleportStock, inventoryFull;
        boolean retrievalOpen, nothingToCollect, hasContinue;
        int retrievalItems, carriedSlots;
    }
    interface Actions {
        void bank(Spawn spawn);
        void withdrawRing();
        void withdrawTeleport();
        void closeBank();
        boolean teleport();
        boolean collect();
        boolean reclaim();
        void clearInterface();
        boolean ferox();
    }
    final ZulrahRecoveryBankRoute bankRoute = new ZulrahRecoveryBankRoute();
    private Stage stage=Stage.SPAWN;
    private Spawn spawn;
    private long enteredAt=-1L, nextInputAt, pausedAt=-1L;
    private int beforeClaim;
    private String status="Waiting for death respawn";
    Stage stage(){return stage;}
    String status(){return status;}
    boolean done(){return stage==Stage.DONE;}
    boolean stopped(){return stage==Stage.STOPPED;}
    void pause(long now){if(pausedAt<0)pausedAt=now;}
    private void move(Stage next,long now,String text){stage=next;enteredAt=now;status=text;}
    private void stop(long now,String text){move(Stage.STOPPED,now,"Death recovery stopped: " + text);}
    private boolean inputReady(long now) {
        if(now<nextInputAt)return false;
        nextInputAt=now+700L;
        return true;
    }
    void tick(Frame f,long now,Actions a) {
        if(f.here==null || done() || stopped())return;
        if(pausedAt>=0){long gap=Math.max(0L,now-pausedAt);if(enteredAt>=0)enteredAt+=gap;nextInputAt+=gap;pausedAt=-1L;}
        if(enteredAt<0)enteredAt=now;
        if(now-enteredAt>120000L){stop(now,"no progress in " + stage);return;}
        switch(stage) {
            case SPAWN:
                if(f.arena)return;
                spawn=spawnAt(f.here);
                if(spawn==null){stop(now,"unsupported respawn; only Lumbridge and Edgeville are supported");return;}
                move(Stage.BANK,now,"Respawn " + spawn + "; heading to local bank");break;
            case BANK:
                if(f.bankOpen)move(Stage.SUPPLIES,now,"Bank open; checking death recovery teleports");
                else if(inputReady(now))a.bank(spawn);break;
            case SUPPLIES:
                if(!f.bankOpen){move(Stage.BANK,now,"Bank closed; reopening recovery bank");break;}
                if(!f.bankReady)break;
                if(!f.ring && !f.ringStock){stop(now,"no charged Ring of dueling in bank");break;}
                if(!f.teleport && !f.teleportStock){stop(now,"no Zul-andra teleports in bank");break;}
                if(f.ring && f.teleport){move(Stage.CLOSE_BANK,now,"Recovery teleports observed in inventory/equipment");break;}
                if(f.inventoryFull){stop(now,"no inventory space for recovery teleports");break;}
                if(inputReady(now)){if(!f.ring)a.withdrawRing();else a.withdrawTeleport();}break;
            case CLOSE_BANK:
                if(!f.bankOpen)move(Stage.TELEPORT,now,"Teleporting to priestess before regear");else if(inputReady(now))a.closeBank();break;
            case TELEPORT:
                if(inputReady(now) && a.teleport())move(Stage.WAIT_SHORE,now,"Zul-andra teleport clicked; waiting for shore");break;
            case WAIT_SHORE:
                if(shore(f.here))move(Stage.PRIESTESS,now,"Zul-andra arrival observed; collect from priestess");
                else if(now-enteredAt>20000L)stop(now,"Zul-andra teleport did not arrive");break;
            case PRIESTESS:
                if(!shore(f.here)){stop(now,"left Zul-andra before reclaim");break;}
                if(inputReady(now) && a.collect())move(Stage.WAIT_COLLECTION,now,"Collect clicked; waiting for priestess response");break;
            case WAIT_COLLECTION:
                if(f.nothingToCollect)move(Stage.CLEAR_INTERFACE,now,"Priestess reports no items to collect");
                else if(f.retrievalOpen)move(Stage.RECLAIM,now,"Priestess retrieval interface observed");
                else if(now-enteredAt>20000L)stop(now,"no collection response; check priestess dialogue");break;
            case RECLAIM:
                if(now-enteredAt<1200L)break;
                if(!f.retrievalOpen){stop(now,"retrieval interface closed before reclaim");break;}
                if(f.retrievalItems==0)move(Stage.CLEAR_INTERFACE,now,"Retrieval interface has no items remaining");
                else if(f.inventoryFull)stop(now,"inventory full; reclaim remains with priestess");
                else {
                    beforeClaim=f.carriedSlots;
                    if(inputReady(now) && a.reclaim())move(Stage.WAIT_RECLAIM,now,"Reclaim clicked; waiting for observed collection");
                }
                break;
            case WAIT_RECLAIM:
                if((f.retrievalOpen && f.retrievalItems==0) || (!f.retrievalOpen && f.carriedSlots>beforeClaim))
                    move(Stage.CLEAR_INTERFACE,now,"Reclaim result observed");
                else if(now-enteredAt>20000L)stop(now,"reclaim not completed; check fee or inventory space");break;
            case CLEAR_INTERFACE:
                if(!f.retrievalOpen && !f.hasContinue)move(Stage.FEROX,now,"Returning to Ferox after collection");
                else if(inputReady(now))a.clearInterface();break;
            case FEROX:
                if(!f.ring){stop(now,"charged Ring of dueling missing for Ferox return");break;}
                if(inputReady(now) && a.ferox())move(Stage.WAIT_FEROX,now,"Ferox teleport clicked; waiting for arrival");break;
            case WAIT_FEROX:
                if(f.here.getPlane()==0 && f.here.getX()>=3123 && f.here.getX()<=3156
                        && f.here.getY()>=3617 && f.here.getY()<=3645)
                    move(Stage.DONE,now,"Death recovery complete; resuming normal Ferox pool/regear");
                else if(now-enteredAt>20000L)stop(now,"Ferox teleport did not arrive");break;
            default:break;
        }
    }
    private static boolean shore(WorldPoint p){return p.getPlane()==0 && p.getX()>=2180 && p.getX()<2220 && p.getY()>=3040 && p.getY()<3080;}
}
