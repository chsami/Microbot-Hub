package net.runelite.client.plugins.microbot.drocolosseumprayers;

import com.google.inject.Provides;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Inject;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.api.GraphicsObject;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GraphicChanged;
import net.runelite.api.events.GraphicsObjectCreated;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.drocolosseumprayers.PluginConstants;
import net.runelite.client.plugins.microbot.drocolosseumprayers.local.ColosseumDecisionEngine;
import net.runelite.client.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.runelite.client.plugins.microbot.drocolosseumprayers.plugins.common.Commands;
import net.runelite.client.plugins.microbot.drocolosseumprayers.plugins.common.State;
import net.runelite.client.plugins.microbot.drocolosseumprayers.plugins.common.NpcSpawnDiagnostics;
import net.runelite.client.plugins.microbot.drocolosseumprayers.plugins.inferno.InfernoCommands;
import net.runelite.client.plugins.microbot.drocolosseumprayers.plugins.inferno.InfernoState;
import net.runelite.client.plugins.microbot.drocolosseumprayers.execution.LocalColosseumSession;
import net.runelite.client.plugins.microbot.drocolosseumprayers.execution.Events;
import net.runelite.client.plugins.microbot.drocolosseumprayers.execution.Messages;

/**
 * [Dro] Colosseum Prayers: Prayers only or Auto no movement, played by the in-process Colosseum engine.
 * There is no full-automatic mode and no automatic startup; the player gears up and enters.
 */
@PluginDescriptor(name = PluginConstants.DRO + "Colosseum Prayers",
    description = "Colosseum assist: Prayers only or Auto no movement; no full-automatic mode",
    tags = {"dro","colosseum","prayers","pvm"},
    authors = {"droplugins"},
    version = DroColosseumPrayersPlugin.version,
    minClientVersion = "2.6.55",
    iconUrl = "https://chsami.github.io/Microbot-Hub/DroColosseumPrayersPlugin/assets/icon.png",
    cardUrl = "https://chsami.github.io/Microbot-Hub/DroColosseumPrayersPlugin/assets/card.png",
    enabledByDefault = PluginConstants.DEFAULT_ENABLED,
    isExternal = PluginConstants.IS_EXTERNAL)
public class DroColosseumPrayersPlugin extends Plugin implements ArenaActivity
{
    static final String version = "1.0.99";
	private static final Logger log = LoggerFactory.getLogger(DroColosseumPrayersPlugin.class);

	@Inject
	private Client client;

	@Inject
	private DroColosseumConfig config;

	private volatile LocalColosseumSession localSession;
    /** The running session's engine, read by the overlay for its live state and modifiers. */
    private volatile ColosseumDecisionEngine decisionEngine;
    private volatile Commands commands;
    private final ColosseumFirstWave firstWave=new ColosseumFirstWave();
    private NpcSpawnDiagnostics npcDiagnostics;
    @Inject private net.runelite.client.ui.overlay.OverlayManager overlayManager;
    private ArenaMouseOverlay mouseOverlay;
    @Inject private ColosseumOverlay overlay;
    @Inject private ColosseumTilePackOverlay tilePackOverlay;
    private Map<String,Object> previousRecordingStatus;
    private volatile String terminalStatus;
    @Inject private ConfigManager configManager;
    private ArenaInputLease inputLease;
    private ArenaHudTracker hudTracker;
    private volatile ArenaHud hud;
    private final ArenaAntibanPolicy antibanPolicy=new ArenaAntibanPolicy();
    private ArenaRunGuard runGuard = new ArenaRunGuard();
    private volatile ColosseumAftermath aftermath = new ColosseumAftermath();
    /** Chest and grave clicks; the grave walk waits inside, so never on the client thread. */
    private ExecutorService aftermathWorker;
    private final java.util.concurrent.atomic.AtomicBoolean aftermathBusy=new java.util.concurrent.atomic.AtomicBoolean();
    private volatile boolean aftermathInside;
    @Override public boolean arenaRunProtected(){return runGuard.protectedRun();}
    @Override public ArenaHud arenaHud(){return active||arenaRunProtected()?hud:null;}
    /** Client-thread boundary receipt controls visibility and the run clock. */
    void updateHud(boolean inside){
        if(!inside){hud=null;hudTracker=null;picked=java.util.List.of();modifierOffers=java.util.List.of();return;}
        if(!active&&!arenaRunProtected())return;
        if(hudTracker==null)hudTracker=new ArenaHudTracker(ArenaProduct.COLOSSEUM,System.currentTimeMillis());
        ColosseumDecisionEngine engine=localSession!=null?decisionEngine:null;
        java.util.Map<String,Integer> taken=new java.util.HashMap<>(engine==null?java.util.Map.of():engine.activeModifiers());
        for(String key:picked)taken.merge(key,1,Math::max);
        hud=hudTracker.snapshot(client,status(),engine==null?null:engine.liveState(),taken);
    }
    private void enforceInputLease(){
        if((!active||arenaFailure()!=null)&&!arenaRunProtected()){if(inputLease!=null){inputLease.close();inputLease=null;}return;}
        if(inputLease==null)inputLease=new ArenaInputLease(configManager);else inputLease.enforce();
    }

    private ArenaInputDispatcher actions;
    private DroColosseumPreparation preparation;
    private volatile boolean active;
    private volatile long session;

    public boolean canStartAtCurrentLocation(){
        if(insideSnapshot())return true;
        if(config.automateStartup()&&selectedSetup()==null)return false;
        return Microbot.getClientThread().runOnClientThreadOptional(()->
            client.getGameState()==net.runelite.api.GameState.LOGGED_IN&&client.getLocalPlayer()!=null
            &&client.getBoostedSkillLevel(net.runelite.api.Skill.HITPOINTS)>0&&client.getTopLevelWorldView()!=null
            &&(ArenaProduct.COLOSSEUM.inside(client)||!config.automateStartup()&&!client.getTopLevelWorldView().isInstance()&&(client.getLocalPlayer().getWorldLocation().distanceTo(new WorldPoint(1808,9506,0))<=20||client.getLocalPlayer().getWorldLocation().distanceTo(new WorldPoint(1793,3106,0))<=15))).orElse(false);
    }
    @Override public boolean arenaRunning() {
        LocalColosseumSession current=localSession;
        return active&&(current==null ? preparation!=null&&!preparation.stopped() : current.lastEnd()==null);
    }
    @Override public String arenaStatus(){return status();}
    @Override public String arenaFailure(){
        LocalColosseumSession current=localSession;
        if(current!=null&&current.lastEnd()!=null)return current.lastEnd();
        return preparation!=null&&preparation.stopped()?preparation.status():null;
    }
    public String status() {
        ColosseumAftermath after=aftermath;if(after.pending())return after.status();
        if(terminalStatus!=null)return terminalStatus;
        LocalColosseumSession current=localSession;
        if(current!=null) return current.lastEnd()!=null ? current.lastEnd() : current.status();
        DroColosseumPreparation local=preparation;
        return !active ? "Stopped; re-arm and start the activity to restart" : local==null ? "Enter the Colosseum to start" : local.status();
    }
    private net.runelite.client.plugins.microbot.inventorysetups.InventorySetup selectedSetup(){return null;}
    private boolean insideSnapshot() {
        return Microbot.getClientThread()
            .runOnClientThreadOptional(() -> ArenaProduct.COLOSSEUM.inside(client)).orElse(false);
    }
    /** Client thread. Last time the arena instance was observed on any plane. */
    private long arenaHeldAt;
    /** Keeps the localSession through cutscene plane changes and a brief scene LOADING; input stays blocked. */
    private boolean arenaHeld(){
        long now=System.nanoTime();
        if(ArenaProduct.COLOSSEUM.instanceHeld(client)){arenaHeldAt=now;return true;}
        return client.getGameState()==net.runelite.api.GameState.LOADING&&arenaHeldAt!=0
            &&now-arenaHeldAt<java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
    }
    private boolean localSessionAllowed(){return active&&arenaHeld();}
    private final ArenaInputState inputState=new ArenaInputState();
    /** Survives plugin shutdown so a stop right after death cannot cancel the logout. */
    private static final ArenaRunLogout runLogout=new ArenaRunLogout();
    /** Never throws from death/completion handling. */
    private boolean logoutAfterRun(){DroColosseumConfig c=config;return c!=null&&c.logoutAfterRun();}
    private boolean inputAllowed() {
        if (!active || Microbot.pauseAllScripts.get()
            || net.runelite.client.plugins.microbot.util.input.InputArbiter.isHuman()) return false;
        return inputState.allowed();
    }

	@Provides
	DroColosseumConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(configType());
	}
    /** The settings page of this plugin; the Prayers variant supplies its own group. */
    protected Class<? extends DroColosseumConfig> configType(){return DroColosseumPrayersConfig.class;}
    protected String configGroup(){return DroColosseumPrayersConfig.PRAYERS_GROUP;}
    protected DroColosseumConfig config(){return config;}
    /** The two assist switches; the Prayers variant derives them from its Mode setting. */
    /** Unset or unreadable settings fall back to Prayers only, never to full automatic play. */
    ArenaAssistMode mode()
    {
        DroColosseumConfig current = config();
        ArenaAssistMode mode = current instanceof DroColosseumPrayersConfig ? ((DroColosseumPrayersConfig) current).mode() : null;
        return mode == null ? ArenaAssistMode.PRAYERS_ONLY : mode;
    }
    protected boolean prayersOnly(){return mode() != ArenaAssistMode.AUTO_NO_MOVEMENT;}
    protected boolean manualMovement(){return mode() == ArenaAssistMode.AUTO_NO_MOVEMENT;}
    /** Input for fight commands; Manual movement always uses direct invokes. */
    /** Both modes send their actions as direct invokes, so the Fight input choice is not offered. */
    protected ArenaFightInput fightInput(){return ArenaFightInput.INVOKES;}

    /** A saved 0 meant "local default"; the setting is now 1-99 with 55 as that default. */
    static void migrateVitals(ConfigManager configs){migrateVitals(configs,DroColosseumConfig.GROUP);}
    static void migrateVitals(ConfigManager configs,String group){
        if(configs==null)return;
        String raw=configs.getConfiguration(group,"vitalsPercent");
        if(raw==null)return;
        long value;
        try{value=Long.parseLong(raw.trim());}catch(NumberFormatException invalid){value=0;}
        int normalized=value<=0?55:(int)Math.min(99,value);
        if(value!=normalized)configs.setConfiguration(group,"vitalsPercent",normalized);
    }
    /** Source Colosseum gameplay options; Inferno-only settings are not imported. */
    static Map<String,Object> settings(DroColosseumConfig config){
        var options=new java.util.LinkedHashMap<String,Object>();
        ArenaPreparationConfig.putSharedOptions(config,options);
        // Unset values preserve controller defaults.
        if(config.killWith()!=null)options.put("killWith",config.killWith().killWith());
        if(config.warbandFirst()!=null)options.put("warbandFirst",config.warbandFirst().warbandFirst());
        options.put("prayersOnly",config.prayersOnly());
        options.put("manualMovement",config.manualMovement());
        options.put("bloodHealLast",config.bloodHealLast());
        options.put("potionsFromWave",config.potionsFromWave());
        return options;
    }

	@Override
	protected void startUp()
	{
        terminalStatus=null;
        if(ArenaInputLease.active()||infernoRunning()){
            active=false;terminalStatus="Stop local Inferno before starting Colosseum";return;
        }
        migrateVitals(configManager,configGroup());
        inputState.clear();active=true;session++;firstWave.reset();
        npcDiagnostics=new NpcSpawnDiagnostics(client, ArenaProduct.COLOSSEUM, values -> {
            try { log.info("[Dro Colosseum] [npc-visibility] {}",values); }
            catch (RuntimeException ignored) { /* Logging cannot affect the run. */ }
            var bus=Microbot.getEventBus();
            if(bus!=null)bus.post(new net.runelite.client.plugins.microbot.drocolosseumprayers.DroRecorderDiagnostic(values));
        });
        previousRecordingStatus=null;
        runGuard=new ArenaRunGuard();aftermath=new ColosseumAftermath();antibanPolicy.start();
        hudTracker=null;hud=null;
        enforceInputLease();

        if(overlayManager!=null&&overlay!=null){overlayManager.add(overlay);overlay.start();}
        if(overlayManager!=null&&tilePackOverlay!=null)overlayManager.add(tilePackOverlay);
        actions=new ArenaInputDispatcher(this::inputAllowed);
        // Draws only while the arena mouse moves; idle with Dro mouse or invokes.
        mouseOverlay=new ArenaMouseOverlay(actions.mouse());
        if(overlayManager!=null)overlayManager.add(mouseOverlay);
        if (!insideSnapshot()) {
            long expected=session;
            preparation=new DroColosseumPreparation(config, () -> active&&expected==session
                && !Microbot.pauseAllScripts.get()
                && !net.runelite.client.plugins.microbot.util.input.InputArbiter.isHuman()
                && Microbot.getClientThread().runOnClientThreadOptional(() ->
                    client.getGameState()==net.runelite.api.GameState.LOGGED_IN && client.getTopLevelWorldView()!=null && !client.getTopLevelWorldView().isInstance()
                    && client.getBoostedSkillLevel(net.runelite.api.Skill.HITPOINTS)>0).orElse(false),this::selectedSetup);
            // The local controller starts only after the live instance is observed.
            preparation.start();
        }
	}

	@Override
	protected void shutDown()
	{
        inputState.clear();active=false;session++;antibanPolicy.close();
        // A manual stop ends a chest or grave claim.
        aftermath.cancel();runGuard.hold(false);
        if(aftermathWorker!=null){aftermathWorker.shutdownNow();aftermathWorker=null;}
        npcDiagnostics=null;
        if(overlayManager!=null&&overlay!=null){overlay.stop();overlayManager.remove(overlay);}
        if(overlayManager!=null&&tilePackOverlay!=null)overlayManager.remove(tilePackOverlay);
        recordStatus(false);
        if(inputLease!=null){inputLease.close();inputLease=null;}
        hud=null;hudTracker=null;
        if (localSession!=null) { localSession.close();localSession=null; }
        closeRecording();
        if (preparation!=null) { preparation.shutdown();preparation=null; }
        closeInput();
	}

	/** Apply settings on a new run when no entered run is protected. */
	@Subscribe
	public void onConfigChanged(ConfigChanged e)
    {
        if(net.runelite.client.plugins.microbot.MicrobotConfig.configGroup.equals(e.getGroup())
            &&net.runelite.client.plugins.microbot.MicrobotConfig.keyDisableInputYielding.equals(e.getKey())
            &&active&&inputLease!=null){inputLease.enforce();return;}
		if (configGroup().equals(e.getGroup())&&!"run".equals(e.getKey())&&!"fightInput".equals(e.getKey())&&!arenaRunProtected())
		{
			shutDown();
			startUp();
		}
	}

    private void connect(){
        long expected=session;
        Commands common=new Commands(client,this::inputAllowed,ArenaProduct.COLOSSEUM).prayersOnly(this::prayersOnly);commands=common;
        localSession=new LocalColosseumSession(task->{if(expected==session)task.run();},
            line->log.info("[Colosseum local] {}",line),this::inputAllowed,decisionEngine=new ColosseumDecisionEngine())
            .input(actions).fightInput(this::fightInput).prayersOnly(this::prayersOnly).manualMovement(this::manualMovement)
            .common(new State(client,npcDiagnostics),common)
            .inferno(new InfernoState(client),new InfernoCommands(client,this::inputAllowed))
            .settings(runSettings());
        localSession.start();
    }
    /** Engine options for the next run, with the assist switches of this plugin. */
    private Map<String,Object> runSettings(){
        Map<String,Object> options=settings(config);
        options.put("prayersOnly",prayersOnly());options.put("manualMovement",manualMovement());
        return options;
    }
    private void closeRecording(){}

    /** Each Hub plugin ships in its own jar; the Inferno plugin cannot be seen from here. */
    private boolean infernoRunning(){return false;}
    private boolean stopForConflict(){
        if(!active||!infernoRunning())return false;
        stopSession();
        // Explicit conflicting start releases this product, including a previously protected run.
        if(inputLease!=null){inputLease.close();inputLease=null;}
        hud=null;terminalStatus="Colosseum stopped: local Inferno started";return true;
    }
    /** Modifiers taken this run and the offers on screen (Fortis Colosseum reads the same script and varbit). */
    private volatile java.util.List<String> picked=java.util.List.of(),modifierOffers=java.util.List.of();
    @Subscribe public void onScriptPreFired(net.runelite.api.events.ScriptPreFired event){
        if(event.getScriptId()!=ColosseumModifierIcons.SCRIPT_MODIFIER_SELECT_INIT||event.getScriptEvent()==null)return;
        Object[] args=event.getScriptEvent().getArguments();
        if(args!=null)modifierOffers=ColosseumModifierIcons.offers(args);
    }
    @Subscribe public void onClientTick(net.runelite.api.events.ClientTick event){
        if(overlay!=null)overlay.capture(client);
        if(stopForConflict())return;
        if(active)inputState.observe(client,ArenaProduct.COLOSSEUM);
        if(localSession!=null&&!localSessionAllowed())stopSession();
    }

	@Subscribe
	public void onGameTick(GameTick e)
    {
        if(stopForConflict())return;
        if(active)inputState.observe(client,ArenaProduct.COLOSSEUM);
        boolean reliable=client.getGameState()==net.runelite.api.GameState.LOGGED_IN
            &&client.getLocalPlayer()!=null&&client.getTopLevelWorldView()!=null;
        runGuard.observe(reliable,ArenaProduct.COLOSSEUM.inside(client),reliable&&client.getBoostedSkillLevel(net.runelite.api.Skill.HITPOINTS)<=0);
        pollAftermath(reliable);
        antibanPolicy.apply();
        enforceInputLease();
        updateHud(ArenaProduct.COLOSSEUM.inside(client));
        recordStatus(ArenaProduct.COLOSSEUM.inside(client));
        if (!active) return;
        if (localSession!=null && !localSessionAllowed()) { stopSession();return; }
        if (!ArenaProduct.COLOSSEUM.inside(client)) { firstWave.reset();return; }
        if (!inputAllowed()&&localSession==null) return;
        
        if (preparation!=null) { preparation.shutdown();preparation=null; }
        if (localSession==null) connect();
        if (localSession!=null) {
            localSession.spellState(net.runelite.client.plugins.microbot.util.magic.Rs2Magic.isSpellbook(
                net.runelite.client.plugins.microbot.util.magic.Rs2Spellbook.ANCIENT),localBloodAutocastLevel());
            localSession.observePrayers(client);localSession.onGameTick();
        }
        // "Prayers only": the player starts waves and talks through Sol themselves.
        if(inputAllowed()&&!prayersOnly()){startFirstWave();continueSolDialogue();}
	}

    /** Actual selected Blood spell and defensive-casting receipt; manual startup cannot assume preparation ran. */
    private int localBloodAutocastLevel(){
        if(!net.runelite.client.plugins.microbot.util.magic.Rs2Magic.isSpellbook(
            net.runelite.client.plugins.microbot.util.magic.Rs2Spellbook.ANCIENT))return 0;
        var spell=net.runelite.client.plugins.microbot.util.magic.Rs2Magic.getCurrentAutoCastSpell();
        if(spell==null||client.getVarbitValue(net.runelite.api.Varbits.DEFENSIVE_CASTING_MODE)!=0)return 0;
        switch(spell){
            case BLOOD_RUSH:case BLOOD_BURST:case BLOOD_BLITZ:case BLOOD_BARRAGE:return spell.getRequiredLevel();
            default:return 0;
        }
    }

    static final String SOL="Sol Heredit";
    private long nextSolContinue;
    /** Client thread. Only Sol's plain Continue dialogue is advanced during his pre-fight introduction. */
    private void continueSolDialogue(){
        Commands common=commands;ArenaInputDispatcher input=actions;
        if(common==null||input==null)return;
        var name=client.getWidget(net.runelite.api.gameval.InterfaceID.ChatLeft.NAME);
        var next=client.getWidget(net.runelite.api.gameval.InterfaceID.ChatLeft.CONTINUE);
        if(name==null||name.isHidden()||next==null||next.isHidden()
            ||!SOL.equals(Text.removeTags(name.getText())))return;
        long now=System.currentTimeMillis();
        if(now<nextSolContinue)return;
        nextSolContinue=now+java.util.concurrent.ThreadLocalRandom.current().nextLong(900,1500);
        long expected=session;
        input.execute("dialogue.next",0,()->expected==session&&inputAllowed(),()->{
            if(expected==session&&inputAllowed())common.dialogueNext();
        });
    }

    /** Client thread. Wave 1 has no Jump-in equivalent; dispatch Minimus Start-wave on the command lane. */
    private void startFirstWave(){
        if(firstWave.done())return;
        LocalColosseumSession current=localSession;Commands common=commands;ArenaInputDispatcher input=actions;
        boolean ready=current!=null&&common!=null&&input!=null&&current.readyForFirstWave();
        int minimus=-1;boolean waveActive=false;
        for(var model:Microbot.getRs2NpcCache().query().toListOnClientThread()){
            NPC npc=model.getNpc();
            if(npc==null||npc.getWorldView()!=client.getTopLevelWorldView())continue;
            var composition=npc.getTransformedComposition();
            if(composition==null||composition.getActions()==null)continue;
            for(String action:composition.getActions()){
                if(action==null)continue;
                if(npc.getId()==ColosseumFirstWave.MINIMUS&&"Start-wave".equalsIgnoreCase(action))minimus=npc.getIndex();
                else if("Attack".equalsIgnoreCase(action))waveActive=true;
            }
        }
        // An open modifier screen means Start-wave landed; clicking it again resets the chosen modifier.
        boolean started=waveActive||Commands.colosseumModifierScreen(client);
        int index=firstWave.poll(System.currentTimeMillis(),ready,minimus,started);
        if(index<0)return;
        long expected=session;
        input.execute("npc.interact",0,()->expected==session&&inputAllowed(),()->{
            if(expected==session&&inputAllowed())common.npcInteract(index,"Start-wave");
        });
    }

    @Subscribe public void onActorDeath(net.runelite.api.events.ActorDeath event) {
        if(!arenaRunProtected())return;
        if(event.getActor()==client.getLocalPlayer()){localDeath();return;}
        if(!(event.getActor() instanceof NPC)||!ArenaProduct.COLOSSEUM.inside(client))return;
        int id=((NPC)event.getActor()).getId();
        if(id==net.runelite.api.gameval.NpcID.COLOSSEUM_SOL_P1) {
            runGuard.completed();
            stopSession();terminalStatus="Colosseum complete: observed Sol death";
            // The logout follows the chest claim.
            if(aftermath.begin(ColosseumAftermath.Kind.CHEST,System.currentTimeMillis()))runGuard.hold(true);
        }
    }

    /** Doom kills above 0 HP without a local ActorDeath; the game's death line is then the only receipt. */
    static final String DEATH_MESSAGE="Oh dear, you are dead!";
    private void localDeath(){
        if(aftermath.kind()!=null)return;
        inputState.clear();runGuard.observe(true,false,true);stopSession();
        terminalStatus="Colosseum ended: player died";
        // The items wait in a grave in the lobby once you respawn (Dro 1.0.58 death recording).
        if(aftermath.begin(ColosseumAftermath.Kind.GRAVE,System.currentTimeMillis()))runGuard.hold(true);
    }

    /** Client thread. Advances the chest or grave claim, then releases the run exactly once. */
    private void pollAftermath(boolean reliable){
        ColosseumAftermath after=aftermath;
        if(after.kind()==null)return;
        if(after.pending()&&reliable&&!Microbot.pauseAllScripts.get()
            &&!net.runelite.client.plugins.microbot.util.input.InputArbiter.isHuman()){
            long now=System.currentTimeMillis();
            aftermathInside=ArenaProduct.COLOSSEUM.inside(client);
            if(after.kind()==ColosseumAftermath.Kind.CHEST){
                boolean open=visible(REWARD_CHEST_UNIVERSE)||visible(REWARD_CHEST_BANK_ALL);
                boolean bankable=open&&buttonAction(REWARD_CHEST_BANK_ALL,"bank-all")!=null;
                var step=after.chest(now,new ColosseumAftermath.ChestView(aftermathInside,open,bankable,
                    aftermathInside&&rewardChestNear(),leaveConfirmationVisible()));
                if(step==ColosseumAftermath.Step.SEARCH_CHEST)aftermathTask(()->aftermathCommands().objectInteract(
                    new int[]{ColosseumAftermath.REWARDS_CHEST},null,"Search",30,false));
                else if(step==ColosseumAftermath.Step.BANK_REWARDS)aftermathTask(()->{
                    if(aftermathCommands().widgetClick(REWARD_CHEST_BANK_ALL,null,"Bank-all"))after.bankDispatched();
                });
                // Leave runs only once the chest reports itself empty.
                else if(step==ColosseumAftermath.Step.LEAVE_ARENA)aftermathTask(()->{
                    var minimus=Microbot.getRs2NpcCache().query().where(n->"Minimus".equals(Text.removeTags(n.getName()))).nearest(30);
                    if(minimus!=null&&aftermathAllowed()&&aftermathInside)minimus.click("Leave");
                });
                else if(step==ColosseumAftermath.Step.CONFIRM_LEAVE)aftermathTask(()->{
                    if(aftermathAllowed()&&aftermathInside)net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue.keyPressForDialogueOption("Yes");
                });
            }else{
                var me=client.getLocalPlayer();
                boolean outside=me!=null&&!client.getTopLevelWorldView().isInstance()
                    &&client.getBoostedSkillLevel(net.runelite.api.Skill.HITPOINTS)>0;
                boolean hasGrave=client.getVarbitValue(net.runelite.api.gameval.VarbitID.GRAVESTONE_VISIBLE)!=0;
                boolean graveNear=outside&&Microbot.getRs2NpcCache().query().where(n->n.getId()>=net.runelite.api.gameval.NpcID.GRAVESTONE_DEFAULT
                    &&n.getId()<=net.runelite.api.gameval.NpcID.GRAVESTONE_ANGEL_255).nearest(me.getWorldLocation(),104)!=null;
                ColosseumAftermath.Step graveStep=after.grave(now,outside,hasGrave,graveNear);
                if(graveStep==ColosseumAftermath.Step.LOOT_GRAVE)aftermathTask(()->{
                    if(lootLobbyGrave())after.looted();
                    // Items left in the grave with no free slot: a failed attempt (1.0.72: bank after 3, log out after 3 more).
                    else if(net.runelite.client.plugins.microbot.util.death.Rs2Death.hasGrave()
                        &&net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory.isFull())after.lootBlocked();
                });
                else if(graveStep==ColosseumAftermath.Step.BANK_FOR_GRAVE)aftermathTask(()->{
                    if(bankForGrave())after.graveBanked();
                });
            }
            if(after.takeAlert()){
                log.info("[Dro Colosseum] Watchdog: {} stalls; {}",after.stalls(),after.status());
                Microbot.showMessage("Colosseum: "+after.status()+". The run is held until the items are secured.");
            }
        }
        if(!after.release())return;
        runGuard.hold(false);
        log.info("[Dro Colosseum] {}",after.status());
        if(after.kind()==ColosseumAftermath.Kind.CHEST){
            terminalStatus="Colosseum complete: "+after.status();
            if(logoutAfterRun())runLogout.start("Colosseum complete; logging out with auto-login held.");
        }else{
            terminalStatus="Colosseum ended: player died; "+after.status();
            // 1.0.72: a grave that could not be emptied even after banking logs out whatever the setting (user).
            if(logoutAfterRun()||after.forceLogout())runLogout.start("Colosseum: died; logging out with auto-login held.");
        }
    }
    /**
     * Worker thread. Dro 1.0.58 death recording: you respawn in the lobby (1803,9508) with the grave 4 tiles away;
     * its first option, Check, opens it. The paid half is unlocked and taken first, then the free Take-All repeats
     * while the grave still stands (Dro 1.0.64 recording).
     */
    private boolean lootLobbyGrave(){
        // The recorded death tile is inside the closed instance; find the grave from where you stand.
        net.runelite.client.plugins.microbot.util.death.Rs2Death.clearDeathState();
        if(!net.runelite.client.plugins.microbot.util.death.Rs2Death.isGraveOpen()){
            var grave=net.runelite.client.plugins.microbot.util.death.Rs2Death.getGrave();
            if(grave==null||!aftermathAllowed()||!(grave.click("Check")||grave.click("Loot")))return false;
            if(!net.runelite.client.plugins.microbot.util.Global.sleepUntil(
                net.runelite.client.plugins.microbot.util.death.Rs2Death::isGraveOpen,8_000))return false;
        }
        if(aftermathAllowed()){net.runelite.client.plugins.microbot.util.death.Rs2Death.lootGraveFreeItems();net.runelite.client.plugins.microbot.util.death.Rs2Death.lootGravePaidItems(Integer.MAX_VALUE);}
        net.runelite.client.plugins.microbot.util.death.Rs2Death.closeInterfaces();
        return !net.runelite.client.plugins.microbot.util.death.Rs2Death.hasGrave();
    }
    /**
     * Worker thread (1.0.72). The grave's items did not fit: note where the grave stands, walk to the nearest bank,
     * deposit the inventory and walk back to the grave so the next attempt can take them.
     */
    private boolean bankForGrave(){
        var grave=net.runelite.client.plugins.microbot.util.death.Rs2Death.getGrave();
        var graveTile=grave!=null?grave.getWorldLocation():null;
        net.runelite.client.plugins.microbot.util.death.Rs2Death.closeInterfaces();
        if(!aftermathAllowed()||!net.runelite.client.plugins.microbot.util.bank.Rs2Bank.walkToBankAndUseBank())return false;
        net.runelite.client.plugins.microbot.util.bank.Rs2Bank.depositAll();
        net.runelite.client.plugins.microbot.util.Global.sleepUntil(net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory::isEmpty,5_000);
        net.runelite.client.plugins.microbot.util.bank.Rs2Bank.closeBank();
        if(!aftermathAllowed())return false;
        boolean back=graveTile!=null?net.runelite.client.plugins.microbot.util.walker.Rs2Walker.walkTo(graveTile,2)
            :net.runelite.client.plugins.microbot.util.death.Rs2Death.walkToGrave();
        log.info("[Dro Colosseum] Grave blocked by a full inventory: banked{}",back?" and walked back":"; walk back failed");
        return true;
    }
    /** The Rewards Chest interface (864) and its Bank-all button, 864:15 op 6 (Dro 01:38 recording). */
    static final int REWARD_CHEST_UNIVERSE=net.runelite.api.gameval.InterfaceID.ColosseumRewardChest2.UNIVERSE;
    static final int REWARD_CHEST_BANK_ALL=net.runelite.api.gameval.InterfaceID.ColosseumRewardChest2.TAB_CONTENT_2_BUTTON_3;
    private boolean visible(int id){var w=client.getWidget(id);return w!=null&&!w.isHidden();}
    private String buttonAction(int id,String word){
        var w=client.getWidget(id);
        if(w==null||w.isHidden()||w.getActions()==null)return null;
        for(String action:w.getActions()){
            if(action==null||action.isBlank())continue;
            String text=Text.removeTags(action);String lower=text.toLowerCase(java.util.Locale.ROOT);
            if(!lower.contains("discard")&&(word==null||lower.contains(word)))return text;
        }
        return null;
    }
    /** Client thread. Minimus's "Are you sure you wish to leave?" (Dro 23:53 recording: Leave, then "Yes."). */
    private boolean leaveConfirmationVisible(){
        var options=client.getWidget(net.runelite.api.gameval.InterfaceID.Chatmenu.OPTIONS);
        var lines=options==null||options.isHidden()?null:options.getDynamicChildren();
        return lines!=null&&lines.length>0&&lines[0]!=null&&lines[0].getText()!=null
            &&"Are you sure you wish to leave?".equalsIgnoreCase(Text.removeTags(lines[0].getText()).trim());
    }
    /** Client thread. */
    private boolean rewardChestNear(){
        return Microbot.getRs2TileObjectCache().query().withIds(ColosseumAftermath.REWARDS_CHEST)
            .where(o->o.getWorldView()==client.getTopLevelWorldView()).nearest(30)!=null;
    }
    private boolean aftermathAllowed(){
        return aftermath.pending()&&!Microbot.pauseAllScripts.get()&&!net.runelite.client.plugins.microbot.util.input.InputArbiter.isHuman();
    }
    /** Chest clicks stay inside the arena; the combat session is already closed. */
    private Commands aftermathCommands(){return new Commands(client,()->aftermathAllowed()&&aftermathInside,ArenaProduct.COLOSSEUM);}
    private void aftermathTask(Runnable task){
        if(!aftermathBusy.compareAndSet(false,true))return;
        if(aftermathWorker==null)aftermathWorker=Executors.newSingleThreadExecutor(r->{
            Thread t=new Thread(r,"dro-colosseum-aftermath");t.setDaemon(true);return t;});
        try{aftermathWorker.execute(()->{
            try{if(aftermathAllowed())task.run();}
            catch(RuntimeException failure){log.info("[Dro Colosseum] Claim step failed: {}",failure.getClass().getSimpleName());}
            finally{aftermathBusy.set(false);}
        });}catch(java.util.concurrent.RejectedExecutionException stopped){aftermathBusy.set(false);}
    }

    @Subscribe public void onVarbitChanged(net.runelite.api.events.VarbitChanged event){
        LocalColosseumSession current=localSession;if(current!=null)current.observePrayers(client);
    }
    @Subscribe public void onMenuOptionClicked(net.runelite.api.events.MenuOptionClicked event){
        ArenaInputDispatcher.pressRead();
        LocalColosseumSession current=localSession;if(current!=null)current.observePrayerMenu(event.getParam1(),event.isConsumed());
    }

	// ---- events: what happened between ticks ----

	@Subscribe
	public void onNpcSpawned(NpcSpawned e)
	{
		if (active && npcDiagnostics != null && ArenaProduct.COLOSSEUM.inside(client)) npcDiagnostics.spawned(e.getNpc());
		Events events = events();
		if (events != null)
		{
			events.npcSpawned(e.getNpc().getIndex());
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned e)
	{
        if(e.getNpc().getId()==ColosseumModifierIcons.MINIMUS&&!modifierOffers.isEmpty()){
            String key=ColosseumModifierIcons.picked(modifierOffers,client.getVarbitValue(ColosseumModifierIcons.VARBIT_MODIFIER_SELECTED));
            if(key!=null&&!picked.contains(key)){var merged=new java.util.ArrayList<>(picked);merged.add(key);picked=java.util.List.copyOf(merged);}
            modifierOffers=java.util.List.of();
        }
		if (npcDiagnostics != null) npcDiagnostics.despawned(e.getNpc());
		Events events = events();
		if (events != null)
		{
			events.npcDespawned(e.getNpc().getIndex());
		}
	}

    @Subscribe public void onNpcChanged(net.runelite.api.events.NpcChanged event){
        if(active && npcDiagnostics != null && ArenaProduct.COLOSSEUM.inside(client)) npcDiagnostics.changed(event.getNpc());
    }

	@Subscribe
	public void onAnimationChanged(AnimationChanged e)
	{
		Events events = events();
		Object actor = actor(e.getActor());
		if (events != null && actor != null)
		{
			events.animation(actor, e.getActor().getAnimation());
		}
	}

	@Subscribe
	public void onGraphicChanged(GraphicChanged e)
	{
		Events events = events();
		Object actor = actor(e.getActor());
		if (events != null && actor != null)
		{
			int[] ids = java.util.stream.StreamSupport.stream(e.getActor().getSpotAnims().spliterator(), false)
				.mapToInt(a -> a.getId()).toArray();
			events.graphic(actor, ids);
		}
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied e)
	{
		Events events = events();
		Object actor = actor(e.getActor());
		if (events != null && actor != null)
		{
			events.hitsplat(actor, e.getHitsplat().getHitsplatType(), e.getHitsplat().getAmount());
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage e)
    {
        if(preparation!=null&&(e.getType()==ChatMessageType.GAMEMESSAGE||e.getType()==ChatMessageType.SPAM))preparation.message(Text.removeTags(e.getMessage()));
        if(e.getType()==ChatMessageType.GAMEMESSAGE&&arenaRunProtected()&&DEATH_MESSAGE.equals(Text.removeTags(e.getMessage()).trim()))localDeath();
        if(e.getType()==ChatMessageType.GAMEMESSAGE)aftermath.message(Text.removeTags(e.getMessage()).trim());
        if(active&&ArenaProduct.COLOSSEUM.inside(client)&&(e.getType()==ChatMessageType.GAMEMESSAGE||e.getType()==ChatMessageType.SPAM))
        {
            if(hudTracker==null)updateHud(true);
            hudTracker.message(Text.removeTags(e.getMessage()),System.currentTimeMillis());
        }
		Events events = events();
		if (events != null && (e.getType() == ChatMessageType.GAMEMESSAGE || e.getType() == ChatMessageType.SPAM))
		{
			String text=Text.removeTags(e.getMessage());
            if (client.getLocalPlayer()!=null && client.getLocalPlayer().getName()!=null)
                text=text.replace(client.getLocalPlayer().getName(),"[player]");
            events.chat(e.getType().name(),text);
		}
	}

	@Subscribe
	public void onGraphicsObjectCreated(GraphicsObjectCreated e)
	{
		Events events = events();
		if (events != null)
		{
			GraphicsObject g = e.getGraphicsObject();
			WorldPoint tile = WorldPoint.fromLocalInstance(client, g.getLocation());
			events.graphicsObject(g.getId(), tile.getX(), tile.getY(), Math.max(1, (g.getStartCycle() - client.getGameCycle() + 29) / 30));
		}
	}

	private Events events()
	{
		return localSession == null || !active || !ArenaProduct.COLOSSEUM.inside(client)
            || !localSession.running() ? null : localSession.events();
	}

    private void closeInput() {
        if(actions!=null){actions.close();actions=null;}
        if(mouseOverlay!=null){if(overlayManager!=null)overlayManager.remove(mouseOverlay);mouseOverlay=null;}
    }

    private void recordStatus(boolean inside){
        LocalColosseumSession current=localSession;
        boolean paused=Microbot.pauseAllScripts.get(),human=net.runelite.client.plugins.microbot.util.input.InputArbiter.isHuman();
        String reason=!active?"ACTIVITY_STOPPED":!inside?"OUTSIDE_ARENA":paused?"PAUSED":human?"HUMAN_INPUT":current==null||!current.running()?"LOCAL_NOT_READY":"LIVE";
        Map<String,Object> values=new java.util.LinkedHashMap<>(Map.of("record","arena_input_status","product","colosseum","connection",current==null?"NONE":current.running()?"LOCAL_READY":"LOCAL_STOPPED","live",reason.equals("LIVE"),"reason",reason,"paused",paused,"humanInput",human,"inside",inside,"fightInput",config.fightInput()==null?"MICROBOT_MOUSE":config.fightInput().name()));
        // The run's mode and settings, so a recording shows whether the user or the script moved (user, 2026-11-05).
        values.put("manualMovement",manualMovement());values.put("prayersOnly",prayersOnly());
        values.put("potionsFromWave",config.potionsFromWave());
        values.put("killWith",String.valueOf(config.killWith()));values.put("bloodHealLast",config.bloodHealLast());
        values.put("version",engineVersion());
        if(values.equals(previousRecordingStatus))return;previousRecordingStatus=values;
        try{var bus=Microbot.getEventBus();if(bus!=null)bus.post(new net.runelite.client.plugins.microbot.drocolosseumprayers.DroRecorderDiagnostic(values));}catch(RuntimeException ignored){}
    }

    private void stopSession() {
        inputState.clear();active=false;session++;arenaHeldAt=0;antibanPolicy.close();
        npcDiagnostics=null;
        recordStatus(false);
        if(!arenaRunProtected()&&inputLease!=null){inputLease.close();inputLease=null;}
        if(!arenaRunProtected())hud=null;
        LocalColosseumSession old=localSession;localSession=null;if(old!=null) old.close();
        closeRecording();
        if(preparation!=null){preparation.shutdown();preparation=null;}
        closeInput();
    }
    @Subscribe public void onGameStateChanged(net.runelite.api.events.GameStateChanged e) {
        if(e.getGameState()!=net.runelite.api.GameState.LOGGED_IN)inputState.clear();
        if (localSession!=null && !localSessionAllowed()) stopSession();
    }

	/** NPCs and you count; other players don't. */
	private Object actor(Actor a)
	{
		if (a == client.getLocalPlayer())
		{
			return Messages.PLAYER;
		}
		return a instanceof NPC ? ((NPC) a).getIndex() : null;
	}

    /** Written into each automatic recording, so a run can be matched to the engine that played it. */
    private String engineVersion()
    {
        PluginDescriptor descriptor=getClass().getAnnotation(PluginDescriptor.class);
        return "DroColosseum "+(descriptor==null?"unknown":descriptor.version());
    }
}
