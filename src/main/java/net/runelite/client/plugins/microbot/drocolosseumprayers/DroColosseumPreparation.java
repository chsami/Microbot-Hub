package net.runelite.client.plugins.microbot.drocolosseumprayers;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import net.runelite.client.plugins.microbot.statemachine.StateMachineScript;
import net.runelite.client.plugins.microbot.statemachine.Transition;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;

/** Local setup and recorded approach. Colosseum instance arrival, not a click, confirms entry. */
final class DroColosseumPreparation extends StateMachineScript<DroColosseumPreparation.State> {
    enum State { STARTUP, PREFLIGHT, EQUIPMENT, INVENTORY, CLOSE_BANK, COMBAT_SETUP, APPROACH, ENTER, ARRIVED, STOPPED }
    private final DroColosseumConfig config;
    private final BooleanSupplier allowed;
    private final java.util.function.Supplier<net.runelite.client.plugins.microbot.inventorysetups.InventorySetup> selection;
    private final ArenaPreparationProfile profile=new ArenaPreparationProfile();
    private ArenaPreflight preflight;
    void message(String text){if(preflight!=null)preflight.message(text);}
    private Rs2InventorySetup setup;
    private ArenaCombatSetup combatSetup;
    private volatile String status="Open your bank to prepare the selected setup";
    private volatile String failure;
    private long startedAt;
    private final ColosseumStartup startup;
    DroColosseumPreparation(DroColosseumConfig config, BooleanSupplier allowed) {
        this(config,allowed,config::setup);
    }
    DroColosseumPreparation(DroColosseumConfig config, BooleanSupplier allowed,java.util.function.Supplier<net.runelite.client.plugins.microbot.inventorysetups.InventorySetup> selection) {
        this.config=config;this.allowed=allowed;this.selection=selection;this.startup=new ColosseumStartup(allowed,profile::close);
    }
    @Override protected State initialState() { return config.automateStartup()?State.STARTUP:State.ENTER; }
    @Override protected List<Transition<State>> defineTransitions() { return List.of(); }
    String status() { return status; }
    boolean stopped(){return getCurrentState()==State.STOPPED;}
    void start() {
        if(config.automateStartup())profile.activate();
        mainScheduledFuture=scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (!allowed.getAsBoolean()) { return; }
            try { if(config.automateStartup()&&getCurrentState()!=State.ENTER){profile.perform(()->{step();return true;},()->{});}else step(); } catch (RuntimeException error) { failure="Local preparation failed; restart manually"; }
        },0,600,TimeUnit.MILLISECONDS);
    }
    @Override public void shutdown() {
        // Cancel our own loop before the shared base cleanup, preserving global pause/input state.
        if(mainScheduledFuture!=null) mainScheduledFuture.cancel(true);
        profile.close();
        startup.shutdown();
        super.shutdown();
        scheduledExecutorService.shutdownNow();
    }
    @Override protected State onError(State state,Exception error) {
        status="Local setup failed; inspect supplies and restart"; return State.STOPPED;
    }
    @Override protected void onState(State state) {
        if (failure!=null) { status=failure;forceState(State.STOPPED,"Local preparation stopped");return; }
        if (state==State.STOPPED || state==State.ARRIVED) return;
        if(config.automateStartup()&&selection.get()==null){failure="Select an Inventory Setup before local preparation";return;}
        if(state==State.STARTUP){
            if(startedAt==0)startedAt=System.currentTimeMillis();
            if(System.currentTimeMillis()-startedAt>180_000L){failure="Colosseum bank approach timed out; restart near the outside bank";return;}
            if(startup.openBank()){startedAt=0;forceState(State.PREFLIGHT,"Outside bank open");}
            status=startup.status();return;
        }
        if(state==State.ENTER){startup.enter();status=startup.status();if(startup.stopped())forceState(State.STOPPED,"Local entry timed out");return;}
        if(state==State.COMBAT_SETUP) {
            if(combatSetup==null) combatSetup=new ArenaCombatSetup(allowed);
            ArenaCombatSetup.Result result=combatSetup.tick();status=combatSetup.status();
            if(result==ArenaCombatSetup.Result.READY) forceState(State.ENTER,"Local combat settings verified");
            else if(result==ArenaCombatSetup.Result.STOPPED) forceState(State.STOPPED,"Combat settings not confirmed");
            return;
        }
        if(state==State.PREFLIGHT){
            if(preflight==null)preflight=new ArenaPreflight(allowed,false,true,selection.get());
            ArenaPreflight.Result result=preflight.tick();status=preflight.status();
            if(result==ArenaPreflight.Result.READY)forceState(State.EQUIPMENT,"Pre-run potion sips confirmed");
            else if(result==ArenaPreflight.Result.STOPPED)failure=status;
            return;
        }
        if (selection.get()==null) { failure="Select an Inventory Setup before local preparation";return; }
        if (!Rs2Bank.isOpen() && state!=State.CLOSE_BANK) { status="Open your bank to prepare the selected setup";return; }
        if (startedAt==0) startedAt=System.currentTimeMillis();
        if (System.currentTimeMillis()-startedAt>120_000L) { failure="Local setup timed out; check supplies and restart";return; }
        if (setup==null) setup=new Rs2InventorySetup(selection.get(),mainScheduledFuture)
            .withMissingSupplyHandler(message -> failure="Selected setup has missing supplies; restock locally and restart");
        switch (state) {
            case EQUIPMENT:
                status="Preparing selected equipment locally";
                if (setup.doesEquipmentMatch() || (setup.loadEquipment() && setup.doesEquipmentMatch()))
                    forceState(State.INVENTORY,"Equipment match confirmed");
                break;
            case INVENTORY:
                status="Preparing selected inventory locally";
                if (setup.doesInventoryMatch() || (setup.loadInventory() && setup.doesInventoryMatch()))
                    forceState(State.CLOSE_BANK,"Inventory match confirmed");
                break;
            case CLOSE_BANK:
                if (Rs2Bank.isOpen()) { Rs2Bank.closeBank();return; }
                status="Setup ready; entering the Colosseum lobby";
                forceState(State.COMBAT_SETUP,"Bank closure confirmed");
                break;
            default: break;
        }
    }
}
