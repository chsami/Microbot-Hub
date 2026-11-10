package net.runelite.client.plugins.microbot.droinfernoprayers;

import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;

/** Walker owns both grouping and Karamja/glory routes. Cape access is explicitly opt-in and local. */
public final class InfernoStartup {
    public enum Phase { START, CAPE_BANK, CAPE_GATE, SACRIFICE, RETURN_ROUTE, BANK, READY, STOPPED }
    private static final WorldPoint GATE=new WorldPoint(2474,5137,0);
    // Recorded Fight Pit landing, outer-city approach, then the eastern cape-showing gate.
    private static final WorldPoint[] CAPE_ROUTE={
        new WorldPoint(2404,5179,0),new WorldPoint(2438,5172,0),
        new WorldPoint(2448,5165,0),new WorldPoint(2454,5154,0),
        new WorldPoint(2453,5141,0),new WorldPoint(2457,5140,0),
        new WorldPoint(2468,5140,0),new WorldPoint(2474,5138,0)};
    private final BooleanSupplier allowed,sacrifice;
    private final LocalArenaActions local;
    private final LongSupplier clock;
    private final boolean accessOnly;
    private Phase phase=Phase.START;
    private long phaseAt,nextAction;
    private int gateAttempts,offerAttempts,capeRouteIndex;
    private boolean shownCape,dialogueOwned;
    private String status="Preparing local Inferno access";
    InfernoStartup(BooleanSupplier allowed){this(allowed,()->false);}
    InfernoStartup(BooleanSupplier allowed,BooleanSupplier sacrifice){this(allowed,sacrifice,new LocalArenaActions(allowed),System::currentTimeMillis);}
    InfernoStartup(BooleanSupplier allowed,BooleanSupplier sacrifice,LocalArenaActions local,LongSupplier clock){
        this(allowed,sacrifice,local,clock,false);
    }
    InfernoStartup(BooleanSupplier allowed,BooleanSupplier sacrifice,LocalArenaActions local,LongSupplier clock,boolean accessOnly){
        this.allowed=allowed;this.sacrifice=sacrifice;this.local=local;this.clock=clock;phaseAt=clock.getAsLong();
        this.accessOnly=accessOnly;
    }
    public static InfernoStartup sacrificeOnly(BooleanSupplier allowed){return new InfernoStartup(allowed,()->true,new LocalArenaActions(allowed),System::currentTimeMillis,true);}
    public String status(){return status;}
    public void shutdown(){local.cancel();}
    public void resumeAfter(long millis){if(millis>0){phaseAt+=millis;nextAction+=millis;}}
    public Phase tick(){
        if(!allowed.getAsBoolean()||phase==Phase.READY||phase==Phase.STOPPED)return phase;
        if(accessOnly&&local.infernoUnlocked()){
            local.cancel();status="Inferno access confirmed; returning to the GE before supply preparation";set(Phase.READY);return phase;
        }
        String routeFailure=local.routeFailure();
        if(routeFailure!=null){stop(routeFailure);return phase;}
        boolean pending=sacrifice.getAsBoolean()&&!local.infernoUnlocked();
        if(!pending&&(phase==Phase.CAPE_BANK||phase==Phase.CAPE_GATE||phase==Phase.SACRIFICE)){local.cancel();set(Phase.RETURN_ROUTE);}
        if(!pending&&local.near(InfernoApproach.BANK,6)&&Rs2Bank.isOpen()){set(Phase.READY);return phase;}
        long now=clock.getAsLong();
        if(now-phaseAt>180_000L){stop("Local Inferno route timed out; inspect access, transport supplies and route");return phase;}
        if(now<nextAction)return phase;nextAction=now+1800;
        switch(phase){
            case START:
                if(pending)set(Phase.CAPE_BANK);
                else set(local.near(InfernoApproach.BANK,16)?Phase.BANK:Phase.RETURN_ROUTE);
                break;
            case CAPE_BANK:
                status="Retrieving one Fire cape for the enabled sacrifice";
                LocalArenaActions.CapeSupply supply=local.supplyCape();
                if(supply==LocalArenaActions.CapeSupply.MISSING)stop("Sacrifice firecape is enabled but no Fire cape is available in bank");
                else if(supply==LocalArenaActions.CapeSupply.READY)set(local.near(InfernoApproach.BANK,16)||local.near(InfernoApproach.KET_KEH,16)?Phase.SACRIFICE:Phase.CAPE_GATE);
                break;
            case CAPE_GATE:
                status="Following the recorded cape-showing route into Mor Ul Rek";
                if(Rs2Bank.isOpen()){Rs2Bank.closeBank();break;}
                WorldPoint p=local.position();
                if(p!=null&&p.getPlane()==0&&p.getX()>=2470&&p.getX()<=2480&&p.getY()>=5130&&p.getY()<=5136){set(Phase.SACRIFICE);break;}
                if(!local.near(GATE,4)){
                    while(capeRouteIndex<CAPE_ROUTE.length&&local.near(CAPE_ROUTE[capeRouteIndex],3))capeRouteIndex++;
                    if(capeRouteIndex==0)local.walkWithSupplies(CAPE_ROUTE[0]);
                    else local.walk(capeRouteIndex<CAPE_ROUTE.length?CAPE_ROUTE[capeRouteIndex]:GATE);
                    break;
                }
                local.cancel();
                if(local.inDialogue()){local.continueDialogue();break;}
                if(!shownCape){
                    LocalArenaActions.CapeUse use=local.useCape(2186);
                    if(use==LocalArenaActions.CapeUse.SUBMITTED)shownCape=true;
                    else if(use==LocalArenaActions.CapeUse.UNAVAILABLE)stop("Unable to show Fire cape to the hot vent guard; skipping Inferno");
                    break;
                }
                if(gateAttempts>=3){stop("Hot vent door failed after showing the cape and three passes; skipping Inferno");break;}
                gateAttempts++;local.object(30266,"Pass");break;
            case SACRIFICE:
                status="Sacrificing the Fire cape; waiting for confirmed Inferno unlock";
                if(Rs2Bank.isOpen()){Rs2Bank.closeBank();break;}
                if(!local.near(InfernoApproach.KET_KEH,8)){local.walkWithSupplies(InfernoApproach.KET_KEH);break;}
                local.cancel();
                // The recorded NPC owns this dialogue; keep accepting Yes through both confirmations.
                if(dialogueOwned&&local.inDialogue()){
                    if(!local.confirmCape())local.continueDialogue();break;
                }
                if(!local.capeCarried())break; // Consumption is not proof of the unlock; never fetch a second cape.
                if(offerAttempts>=3){stop("Cape offer was not confirmed; inspect the sacrifice dialogue and restart");break;}
                LocalArenaActions.CapeUse use=local.useCape(11247);
                if(use==LocalArenaActions.CapeUse.SUBMITTED){offerAttempts++;dialogueOwned=true;}
                else if(use==LocalArenaActions.CapeUse.UNAVAILABLE)offerAttempts++;
                break;
            case RETURN_ROUTE:
                status="Efficient Walker to Mor Ul Rek: grouping when faster/available, Karamja glory fallback";
                if(local.near(InfernoApproach.BANK,3)){set(Phase.BANK);break;}
                local.walkWithSupplies(InfernoApproach.BANK);break;
            case BANK:
                status="Opening Mor Ul Rek bank for the selected Inventory Setup";
                if(local.near(InfernoApproach.BANK,3)){
                    if(Rs2Bank.isOpen())set(Phase.READY);else local.object(30267,"Use");
                }else set(Phase.RETURN_ROUTE);
                break;
            default:break;
        }
        return phase;
    }
    private void stop(String reason){status=reason;local.cancel();set(Phase.STOPPED);}
    private void set(Phase phase){this.phase=phase;phaseAt=clock.getAsLong();nextAction=0;}
}
