package net.runelite.client.plugins.microbot.droinfernoprayers;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.statemachine.StateMachineScript;
import net.runelite.client.plugins.microbot.statemachine.Transition;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2MiniMap;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

/** Standalone local setup, deficits-only supplies and the recorded approach to Inferno. */
final class DroInfernoPreparation extends StateMachineScript<DroInfernoPreparation.State> {
    enum State { ACCESS, SUPPLIES, STARTUP, PREFLIGHT, EQUIPMENT, INVENTORY, CONTAINERS, VERIFY_SETUP, CLOSE_BANK, COMBAT_SETUP, APPROACH, ENTER, ARRIVED, STOPPED }
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(DroInfernoPreparation.class);
    private final DroInfernoConfig config;
    private final BooleanSupplier permission;
    private final Supplier<InventorySetup> selection;
    private volatile ArenaPreparationProfile profile;
    private final InfernoStartup startup,access;
    private final InfernoApproach approach;
    private final InfernoEntry entry;
    private ArenaSupplyPreparation supplies;
    private ArenaPreflight preflight;
    private Rs2InventorySetup setup;
    private InventorySetup selectedSetup;
    private ArenaCombatSetup combatSetup;
    private ArenaContainerPreparation containers;
    private volatile String status="Preparing local Inferno startup";
    private volatile boolean closed;
    private volatile String failure;
    private long setupStarted,heldAt;

    DroInfernoPreparation(DroInfernoConfig config,BooleanSupplier allowed){this(config,allowed,config::setup);}
    DroInfernoPreparation(DroInfernoConfig config,BooleanSupplier allowed,Supplier<InventorySetup> selection){
        this(config,allowed,new InfernoStartup(allowed,()->false),selection);
    }
    DroInfernoPreparation(DroInfernoConfig config,BooleanSupplier allowed,InfernoStartup startup){this(config,allowed,startup,config::setup);}
    DroInfernoPreparation(DroInfernoConfig config,BooleanSupplier allowed,InfernoStartup startup,Supplier<InventorySetup> selection){
        this.config=config;this.permission=allowed;this.startup=startup;this.selection=selection;
        this.profile=new ArenaPreparationProfile(this::allowed);
        this.access=config.sacrificeFirecape()?InfernoStartup.sacrificeOnly(this::allowed):null;
        approach=new InfernoApproach(new InfernoApproach.Port(){
            public WorldPoint location(){return Microbot.getClientThread().runOnClientThreadOptional(Rs2Player::getWorldLocation).orElse(null);}
            public boolean moving(){return Microbot.getClientThread().runOnClientThreadOptional(Rs2Player::isMoving).orElse(false);}
            public boolean allowed(){return DroInfernoPreparation.this.allowed();}
            public boolean canClick(WorldPoint target){return Microbot.getClientThread().runOnClientThreadOptional(()->{
                var point=Rs2MiniMap.worldToMinimap(target);return point!=null&&Rs2MiniMap.isPointInsideMinimap(point);
            }).orElse(false);}
            public boolean walk(WorldPoint target){return allowed()&&Rs2Walker.walkMiniMap(target);}
        },System::currentTimeMillis);
        entry=new InfernoEntry(new InfernoEntry.Port(){
            public boolean inside(){return Microbot.getClientThread().runOnClientThreadOptional(()->InfernoBoundary.inside(Microbot.getClient())).orElse(false);}
            public boolean allowed(){return DroInfernoPreparation.this.allowed();}
            public boolean jump(){return InfernoEntrance.jump(DroInfernoPreparation.this::allowed);}
        },System::currentTimeMillis);
    }
    private boolean allowed(){return !closed&&!Thread.currentThread().isInterrupted()&&permission.getAsBoolean();}
    @Override protected State initialState(){return config.automateStartup()?(access==null?State.SUPPLIES:State.ACCESS):State.ENTER;}
    @Override protected List<Transition<State>> defineTransitions(){return List.of();}
    String status(){return status;}
    boolean stopped(){var snapshot=getSnapshot();return snapshot!=null&&snapshot.currentState()==State.STOPPED;}
    /** Client-thread lifecycle gate: sacrifice visits near Ket-Keh must never start a fight session. */
    boolean readyForSession(){
        var snapshot=getSnapshot();
        if(closed||failure!=null||snapshot==null)return false;
        State state=snapshot.currentState();
        return state==State.APPROACH||state==State.ENTER||state==State.ARRIVED;
    }
    void message(String text){if(supplies!=null)supplies.message(text);if(preflight!=null)preflight.message(text);}
    void start(){
        if(closed||mainScheduledFuture!=null)return;
        if(config.automateStartup())profile.activate();
        mainScheduledFuture=scheduledExecutorService.scheduleWithFixedDelay(()->{
            try{
                if(!allowed()){
                    if(heldAt==0){heldAt=System.currentTimeMillis();pauseRoutes();profile.close();}
                    return;
                }
                if(heldAt!=0){
                    resumeAfter(System.currentTimeMillis()-heldAt);heldAt=0;
                    if(config.automateStartup()&&getCurrentState()!=State.ENTER&&getCurrentState()!=State.ARRIVED&&getCurrentState()!=State.STOPPED){
                        profile=new ArenaPreparationProfile(this::allowed);profile.activate();
                    }
                }
                if(config.automateStartup()&&getCurrentState()!=State.ENTER)
                    profile.perform(()->{step();return true;},()->{if(!allowed())throw new IllegalStateException("Preparation input held");});
                else step();
            }catch(RuntimeException error){
                if(!allowed())return;
                LOG.warn("[DRO Inferno] Local preparation failed ({})",error.getClass().getSimpleName());
                failure="Local preparation failed ("+error.getClass().getSimpleName()+"); inspect supplies and restart";
            }
        },0,600,TimeUnit.MILLISECONDS);
    }
    private void pauseRoutes(){startup.shutdown();if(access!=null)access.shutdown();if(supplies!=null)supplies.pause();}
    private void resumeAfter(long millis){
        startup.resumeAfter(millis);if(access!=null)access.resumeAfter(millis);
        approach.resumeAfter(millis);entry.resumeAfter(millis);
        if(supplies!=null)supplies.resumeAfter(millis);if(preflight!=null)preflight.resumeAfter(millis);
        if(combatSetup!=null)combatSetup.resumeAfter(millis);if(setupStarted>0)setupStarted+=millis;
        if(containers!=null)containers.resumeAfter(millis);
    }
    @Override public void shutdown(){
        if(closed)return;closed=true;
        if(mainScheduledFuture!=null)mainScheduledFuture.cancel(true);
        pauseRoutes();if(supplies!=null)supplies.close();profile.close();
        // The cancelled future is already done, so Script's global-cleanup branch is not entered.
        super.shutdown();scheduledExecutorService.shutdownNow();
    }
    @Override protected State onError(State state,Exception error){status="Local setup failed; inspect supplies and restart";pauseRoutes();profile.close();return State.STOPPED;}
    private void stop(String text){status=text;pauseRoutes();profile.close();forceState(State.STOPPED,"Local preparation stopped with an observed failure");}
    @Override protected void onState(State state){
        if(failure!=null){stop(failure);return;}
        if(state==State.STOPPED||state==State.ARRIVED)return;
        if(config.automateStartup()&&selectedSetup==null){
            selectedSetup=selection.get();
            if(selectedSetup==null){stop("Select a saved Inventory Setup before automatic startup");return;}
            // worn-item canonicalisation is private-client only
        }
        if(!allowed())return;
        switch(state){
            case ACCESS:
                InfernoStartup.Phase unlocked=access.tick();status=access.status();
                if(unlocked==InfernoStartup.Phase.READY)forceState(State.SUPPLIES,"Inferno access confirmed; return to GE before shopping");
                else if(unlocked==InfernoStartup.Phase.STOPPED)stop(status);
                return;
            case SUPPLIES:
                if(supplies==null)supplies=new ArenaSupplyPreparation(config,selectedSetup,this::allowed);
                supplies.tick(System.currentTimeMillis());status=supplies.status();
                if(supplies.ready())forceState(State.STARTUP,"Deficits-only purchases, pouch and blowpipe receipts confirmed");
                else if(supplies.stopped())stop(status);
                return;
            case STARTUP:
                InfernoStartup.Phase travelled=startup.tick();status=startup.status();
                if(travelled==InfernoStartup.Phase.READY)forceState(State.PREFLIGHT,"Mor Ul Rek bank arrival confirmed");
                else if(travelled==InfernoStartup.Phase.STOPPED)stop(status);
                return;
            case PREFLIGHT:
                if(preflight==null)preflight=new ArenaPreflight(this::allowed,false,true,selectedSetup);
                ArenaPreflight.Result pre=preflight.tick();status=preflight.status();
                if(pre==ArenaPreflight.Result.READY)forceState(State.EQUIPMENT,"Destination pre-sips observed; final quantities reserved");
                else if(pre==ArenaPreflight.Result.STOPPED)stop(status);
                return;
            case COMBAT_SETUP:
                if(combatSetup==null)combatSetup=new ArenaCombatSetup(this::allowed);
                ArenaCombatSetup.Result combat=combatSetup.tick();status=combatSetup.status();
                if(combat==ArenaCombatSetup.Result.READY)forceState(State.APPROACH,"Rapid and Blood autocast observed, equipment and potion restored");
                else if(combat==ArenaCombatSetup.Result.STOPPED)stop(status);
                return;
            case CONTAINERS:
                if(containers==null)containers=new ArenaContainerPreparation(selectedSetup,this::allowed);
                ArenaContainerPreparation.Result content=containers.tick();status=containers.status();
                if(content==ArenaContainerPreparation.Result.READY)forceState(State.VERIFY_SETUP,"Full configured rune and ammunition container receipts observed");
                else if(content==ArenaContainerPreparation.Result.STOPPED)stop(status);
                return;
            case VERIFY_SETUP:
                if(System.currentTimeMillis()-setupStarted>360_000){stop("Final setup and container restoration timed out; inspect the selected quantities");return;}
                if(!setup.doesEquipmentMatch()){
                    status="Restoring selected equipment after container preparation";
                    if(!Microbot.getClientThread().runOnClientThreadOptional(Rs2Bank::isOpen).orElse(false)){if(allowed())Rs2Bank.openBank();return;}
                    setup.loadEquipment();return;
                }
                if(!setup.doesInventoryMatch()){
                    status="Restoring selected inventory after container preparation";
                    if(!Microbot.getClientThread().runOnClientThreadOptional(Rs2Bank::isOpen).orElse(false)){if(allowed())Rs2Bank.openBank();return;}
                    setup.loadInventory();return;
                }
                ArenaContainerPreparation.Result verified=containers.tick();status=containers.status();
                if(verified==ArenaContainerPreparation.Result.READY)forceState(State.CLOSE_BANK,"Native gear/inventory and full container quantities rechecked together");
                else if(verified==ArenaContainerPreparation.Result.STOPPED)stop(status);
                else forceState(State.CONTAINERS,"Container contents changed during final setup restoration; reloading and verifying");
                return;
            case APPROACH:
                InfernoApproach.Result route=approach.tick();status=approach.status();
                if(route==InfernoApproach.Result.ENTRY_REQUIRED){profile.close();forceState(State.ENTER,"Recorded approach confirmed");}
                else if(route==InfernoApproach.Result.STOPPED)stop(status);
                return;
            case ENTER:
                InfernoEntry.Result entered=entry.tick();status=entry.status();
                if(entered==InfernoEntry.Result.ARRIVED)forceState(State.ARRIVED,"Inferno instance arrival observed");
                else if(entered==InfernoEntry.Result.STOPPED)stop(status);
                return;
            default:break;
        }
        boolean bank=Microbot.getClientThread().runOnClientThreadOptional(Rs2Bank::isOpen).orElse(false);
        if(!bank&&state!=State.CLOSE_BANK){status="Open your bank to prepare the selected setup";return;}
        if(setupStarted==0)setupStarted=System.currentTimeMillis();
        if(System.currentTimeMillis()-setupStarted>360_000){stop("Final setup timed out; inspect equipment, inventory and pouch supplies");return;}
        if(setup==null)setup=new Rs2InventorySetup(selectedSetup,mainScheduledFuture)
            .withMissingSupplyHandler(message->failure="Selected setup still has missing supplies after preparation; inspect bank and restart");
        switch(state){
            case EQUIPMENT:
                status="Loading the selected equipment";
                if(setup.doesEquipmentMatch()||(setup.loadEquipment()&&setup.doesEquipmentMatch()))forceState(State.INVENTORY,"Selected equipment match observed");
                break;
            case INVENTORY:
                status="Loading the selected inventory and pouch supplies";
                if(setup.doesInventoryMatch()||(setup.loadInventory()&&setup.doesInventoryMatch()))forceState(State.CONTAINERS,"Native selected inventory match observed; full container quantities still require verification");
                break;
            case CLOSE_BANK:
                if(bank){if(allowed())Rs2Bank.closeBank();return;}
                status="Setup ready; checking local combat settings";forceState(State.COMBAT_SETUP,"Bank closure observed");break;
            default:break;
        }
    }
}
