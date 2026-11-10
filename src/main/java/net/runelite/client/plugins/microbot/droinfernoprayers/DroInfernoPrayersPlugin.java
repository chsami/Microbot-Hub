package net.runelite.client.plugins.microbot.droinfernoprayers;

import com.google.inject.Provides;
import java.util.Map;
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
import net.runelite.client.plugins.microbot.droinfernoprayers.PluginConstants;
import net.runelite.client.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.runelite.client.plugins.microbot.droinfernoprayers.plugins.common.Commands;
import net.runelite.client.plugins.microbot.droinfernoprayers.execution.LocalInfernoState;
import net.runelite.client.plugins.microbot.droinfernoprayers.plugins.common.NpcSpawnDiagnostics;
import net.runelite.client.plugins.microbot.droinfernoprayers.plugins.inferno.InfernoCommands;
import net.runelite.client.plugins.microbot.droinfernoprayers.plugins.inferno.InfernoState;
import net.runelite.client.plugins.microbot.droinfernoprayers.execution.LocalInfernoSession;
import net.runelite.client.plugins.microbot.droinfernoprayers.execution.Events;
import net.runelite.client.plugins.microbot.droinfernoprayers.execution.Messages;
import net.runelite.client.plugins.microbot.droinfernoprayers.local.InfernoDecisionEngine;

/**
 * [Dro] Inferno Prayers: Prayers only or Auto no movement, played by the in-process Inferno engine off the
 * client thread. There is no full-automatic mode and no automatic startup; the player gears up and enters.
 */
@PluginDescriptor(name = PluginConstants.DRO + "Inferno Prayers",
    description = "Inferno assist: Prayers only or Auto no movement; no full-automatic mode",
    tags = {"dro","inferno","prayers","pvm"},
    authors = {"droplugins"},
    version = DroInfernoPrayersPlugin.version,
    minClientVersion = "2.6.55",
    iconUrl = "https://chsami.github.io/Microbot-Hub/DroInfernoPrayersPlugin/assets/icon.png",
    cardUrl = "https://chsami.github.io/Microbot-Hub/DroInfernoPrayersPlugin/assets/card.png",
    enabledByDefault = PluginConstants.DEFAULT_ENABLED,
    isExternal = PluginConstants.IS_EXTERNAL)
public class DroInfernoPrayersPlugin extends Plugin implements ArenaActivity
{
    static final String version = "1.0.10";
	private static final Logger log = LoggerFactory.getLogger(DroInfernoPrayersPlugin.class);

	@Inject
	private Client client;

	@Inject
	private DroInfernoConfig config;

	private volatile LocalInfernoSession localSession;
    private NpcSpawnDiagnostics npcDiagnostics;
    @Inject private net.runelite.client.ui.overlay.OverlayManager overlayManager;
    private ArenaMouseOverlay mouseOverlay;
    @Inject private InfernoOverlay overlay;
    @Inject private InfernoTilePackOverlay tilePackOverlay;
    private volatile String terminalStatus;
    private Map<String,Object> previousRecordingStatus;
    @Inject private ConfigManager configManager;
    private ArenaInputLease inputLease;
    private ArenaHudTracker hudTracker;
    private volatile ArenaHud hud;
    private final ArenaAntibanPolicy antibanPolicy=new ArenaAntibanPolicy();
    private ArenaRunGuard runGuard = new ArenaRunGuard();
    @Override public boolean arenaRunProtected(){return runGuard.protectedRun();}
    @Override public ArenaHud arenaHud(){return active||arenaRunProtected()?hud:null;}
    /** Client-thread boundary receipt controls visibility and the run clock. */
    void updateHud(boolean inside){
        if(!inside){hud=null;hudTracker=null;return;}
        if(!active&&!arenaRunProtected())return;
        if(hudTracker==null)hudTracker=new ArenaHudTracker(ArenaProduct.INFERNO,System.currentTimeMillis());
        hud=hudTracker.snapshot(client,status());
    }
    private void enforceInputLease(){
        if((!active||arenaFailure()!=null)&&!arenaRunProtected()){if(inputLease!=null){inputLease.close();inputLease=null;}return;}
        if(inputLease==null)inputLease=new ArenaInputLease(configManager);else inputLease.enforce();
    }

    private ArenaInputDispatcher actions;
    private DroInfernoPreparation preparation;
    private volatile boolean active;
    private volatile long session;
    private boolean normalizingSupplySettings;
    private static final java.util.Set<String> RESTART_SETTINGS = java.util.Set.of(
        "automateStartup", "sacrificeFirecape", "setup", "zukHealersAtOnce", "logoutAfterRun",
        "vitalsPercent", "rangingFromWave", "rangeOnly", "purchaseBudget", "coinReserve",
        "maxUnitPrice", "priceMarkupPercent", "offerWaitSeconds", "buyRunePouchNote");

    public boolean canStartAtCurrentLocation(){
        if(insideSnapshot())return true;
        if(config.automateStartup()&&selectedSetup()==null)return false;
        return net.runelite.client.plugins.microbot.Microbot.getClientThread().runOnClientThreadOptional(()->
            client.getGameState()==net.runelite.api.GameState.LOGGED_IN&&client.getLocalPlayer()!=null
            &&client.getBoostedSkillLevel(net.runelite.api.Skill.HITPOINTS)>0&&client.getTopLevelWorldView()!=null
            &&(InfernoBoundary.inside(client)||!config.automateStartup()&&!client.getTopLevelWorldView().isInstance()&&client.getLocalPlayer().getWorldLocation().distanceTo(InfernoApproach.KET_KEH)<=15)).orElse(false);
    }
    @Override public boolean arenaRunning() {
        LocalInfernoSession current=localSession;
        return active&&(current==null ? preparation!=null&&!preparation.stopped() : current.lastEnd()==null);
    }
    @Override public String arenaStatus(){return status();}
    @Override public String arenaFailure(){
        LocalInfernoSession current=localSession;
        if(current!=null&&current.lastEnd()!=null)return current.lastEnd();
        return preparation!=null&&preparation.stopped()?preparation.status():null;
    }
    public String status() {
        if(terminalStatus!=null)return terminalStatus;
        LocalInfernoSession current=localSession;
        if(current!=null) return current.status();
        DroInfernoPreparation local=preparation;
        return !active ? "Stopped; re-arm and start the activity to restart" : local==null ? "Enter the Inferno to start" : local.status();
    }
    private net.runelite.client.plugins.microbot.inventorysetups.InventorySetup selectedSetup(){return null;}
    private boolean insideSnapshot() {
        return net.runelite.client.plugins.microbot.Microbot.getClientThread()
            .runOnClientThreadOptional(() -> InfernoBoundary.inside(client)).orElse(false);
    }
    private final ArenaInputState inputState=new ArenaInputState();
    /** Survives plugin shutdown so a stop right after death cannot cancel the logout. */
    private static final ArenaRunLogout runLogout=new ArenaRunLogout();
    /** Never throws from death/completion handling. */
    private boolean logoutAfterRun(){DroInfernoConfig c=config;return c!=null&&c.logoutAfterRun();}
    private boolean inputAllowed() {
        if (!active || net.runelite.client.plugins.microbot.Microbot.pauseAllScripts.get()
            || net.runelite.client.plugins.microbot.util.input.InputArbiter.isHuman()) return false;
        return inputState.allowed();
    }
    /** Tiles from the entrance at which the session may open before Jump-in. */
    static final int PRE_ENTRY_SESSION_TILES = 15;
    /**
     * Session lifetime (not command) permission: inside the Inferno, or alive beside the entrance before
     * Jump-in so the local controller can initialize before arrival. Commands still require inputAllowed (inside only).
     */
    private boolean sessionAllowed() {
        if (!active || net.runelite.client.plugins.microbot.Microbot.pauseAllScripts.get()
            || net.runelite.client.plugins.microbot.util.input.InputArbiter.isHuman()) return false;
        return net.runelite.client.plugins.microbot.Microbot.getClientThread().runOnClientThreadOptional(this::sessionLocation).orElse(false);
    }
    /** Client thread only. */
    private boolean sessionLocation() {
        return client.getGameState()==net.runelite.api.GameState.LOGGED_IN&&client.getLocalPlayer()!=null
            &&client.getTopLevelWorldView()!=null&&client.getBoostedSkillLevel(net.runelite.api.Skill.HITPOINTS)>0
            &&(InfernoBoundary.inside(client)||preEntry());
    }
    /** Client thread only: outside any instance and within PRE_ENTRY_SESSION_TILES of the entrance. */
    private boolean preEntry() {
        return !client.getTopLevelWorldView().isInstance()
            &&client.getLocalPlayer().getWorldLocation().distanceTo(InfernoApproach.KET_KEH)<=PRE_ENTRY_SESSION_TILES;
    }

	@Provides
	DroInfernoConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(configType());
	}
    /** The settings page of this plugin; the Prayers variant supplies its own group. */
    protected Class<? extends DroInfernoConfig> configType(){return DroInfernoPrayersConfig.class;}
    protected String configGroup(){return DroInfernoPrayersConfig.PRAYERS_GROUP;}
    protected DroInfernoConfig config(){return config;}
    /** The two assist switches; the Prayers variant derives them from its Mode setting. */
    /** Unset or unreadable settings fall back to Prayers only, never to full automatic play. */
    ArenaAssistMode mode()
    {
        DroInfernoConfig current = config();
        ArenaAssistMode mode = current instanceof DroInfernoPrayersConfig ? ((DroInfernoPrayersConfig) current).mode() : null;
        return mode == null ? ArenaAssistMode.PRAYERS_ONLY : mode;
    }
    protected boolean prayersOnly(){return mode() != ArenaAssistMode.AUTO_NO_MOVEMENT;}
    protected boolean manualMovement(){return mode() == ArenaAssistMode.AUTO_NO_MOVEMENT;}
    /** Input for fight commands; Manual movement always uses direct invokes. */
    /** Both modes send their actions as direct invokes, so the Fight input choice is not offered. */
    protected ArenaFightInput fightInput(){return ArenaFightInput.INVOKES;}

    /** Saved values outside the UI range retain the recorded one-to-four healer contract. */
    static int zukHealersAtOnce(DroInfernoConfig config){return Math.max(1,Math.min(4,config.zukHealersAtOnce()));}
    /** Explicit local controls; preparation settings never alter another plugin's configuration. */
    static Map<String,Object> localSettings(DroInfernoConfig config){
        var options=new java.util.LinkedHashMap<String,Object>();
        options.put("zukHealersAtOnce",zukHealersAtOnce(config));
        options.put("vitalsPercent",localSupplySetting(config.vitalsPercent(),55,99));
        options.put("rangingFromWave",localSupplySetting(config.rangingFromWave(),35,69));
        options.put("rangeOnly",config.rangeOnly());
        options.put("prayersOnly",config.prayersOnly());
        options.put("manualMovement",config.manualMovement());
        return options;
    }
    private static int localSupplySetting(int value,int fallback,int maximum){return value<=0?fallback:Math.min(maximum,value);}
    /** Normalize only invalid saved Inferno local controls; defaults and valid preferences stay untouched. */
    static void migrateSupplySettings(ConfigManager configs){migrateSupplySettings(configs,DroInfernoConfig.GROUP);}
    static void migrateSupplySettings(ConfigManager configs,String group){
        migrateSupplySetting(configs,group,"vitalsPercent",55,99);
        migrateSupplySetting(configs,group,"rangingFromWave",35,69);
    }
    private static void migrateSupplySetting(ConfigManager configs,String group,String key,int fallback,int maximum){
        String raw=configs.getConfiguration(group,key);
        if(raw==null)return;
        long value;
        try{value=Long.parseLong(raw);}
        catch(NumberFormatException invalid){configs.setConfiguration(group,key,fallback);return;}
        int normalized=value<=0?fallback:(int)Math.min(maximum,value);
        if(value!=normalized)configs.setConfiguration(group,key,normalized);
    }
    void normalizeSupplySettings(){
        normalizingSupplySettings=true;
        try{migrateSupplySettings(configManager,configGroup());}finally{normalizingSupplySettings=false;}
    }

	@Override
	protected void startUp()
	{
        terminalStatus=null;
        // PluginManager serializes startup on the EDT; both arenas share this input lease.
        if(ArenaInputLease.active()){
            active=false;terminalStatus="Stop the active arena before starting Inferno";return;
        }
        normalizeSupplySettings();
        ArenaFightInput.migrate(configManager,configGroup());
        inputState.clear();active=true;session++;
        npcDiagnostics=new NpcSpawnDiagnostics(client, ArenaProduct.INFERNO, values -> {
            try { log.info("[Inferno local] [npc-visibility] {}",values); }
            catch (RuntimeException ignored) { /* Logging cannot affect the run. */ }
            var bus=Microbot.getEventBus();
            if(bus!=null)bus.post(new net.runelite.client.plugins.microbot.droinfernoprayers.DroRecorderDiagnostic(values));
        });
        previousRecordingStatus = null;
        runGuard=new ArenaRunGuard();antibanPolicy.start();
        hudTracker=null;hud=null;
        enforceInputLease();
        log.info("[Inferno local] stage=START controller=local setupSelected={} inside={}",config.automateStartup()&&selectedSetup()!=null,insideSnapshot());

        if(overlayManager!=null&&overlay!=null){overlayManager.add(overlay);overlay.start();}
        if(overlayManager!=null&&tilePackOverlay!=null)overlayManager.add(tilePackOverlay);
        actions=new ArenaInputDispatcher(this::inputAllowed);
        // Draws only while the private arena mouse moves; idle with native mouse or invokes.
        mouseOverlay=new ArenaMouseOverlay(actions.mouse());
        if(overlayManager!=null)overlayManager.add(mouseOverlay);
        if (!insideSnapshot()) {
            preparation=new DroInfernoPreparation(config, () -> active
                && !net.runelite.client.plugins.microbot.Microbot.pauseAllScripts.get()
                && !net.runelite.client.plugins.microbot.util.input.InputArbiter.isHuman()
                && net.runelite.client.plugins.microbot.Microbot.getClientThread().runOnClientThreadOptional(() ->
                    client.getGameState()==net.runelite.api.GameState.LOGGED_IN && client.getTopLevelWorldView()!=null && !client.getTopLevelWorldView().isInstance()
                    && client.getBoostedSkillLevel(net.runelite.api.Skill.HITPOINTS)>0).orElse(false),this::selectedSetup);
            preparation.start();
        }
	}

	@Override
	protected void shutDown()
	{
        log.info("[Inferno local] stage=STOP localPreparation={} sessionPresent={}",preparation!=null,localSession!=null);
        inputState.clear();active=false;session++;antibanPolicy.close();runGuard.departed();
        npcDiagnostics=null;
        if(overlayManager!=null&&overlay!=null){overlay.stop();overlayManager.remove(overlay);}
        if(overlayManager!=null&&tilePackOverlay!=null)overlayManager.remove(tilePackOverlay);
        recordStatus(false);
        if(inputLease!=null){inputLease.close();inputLease=null;}
        hud=null;hudTracker=null;
        if (localSession!=null) { localSession.close();localSession=null; }
        if (preparation!=null) { preparation.shutdown();preparation=null; }
        closeInput();
        closeRecording();
	}

	/** Apply changed preparation/settings when no run is protected. */
	@Subscribe
	public void onConfigChanged(ConfigChanged e)
    {
        if(normalizingSupplySettings)return;
        if(net.runelite.client.plugins.microbot.MicrobotConfig.configGroup.equals(e.getGroup())
            &&net.runelite.client.plugins.microbot.MicrobotConfig.keyDisableInputYielding.equals(e.getKey())
            &&active&&inputLease!=null){inputLease.enforce();return;}
		if (configGroup().equals(e.getGroup())&&e.getKey()!=null&&RESTART_SETTINGS.contains(e.getKey())&&!arenaRunProtected())
		{
			shutDown();
			startUp();
		}
	}

	private void startLocalSession()
	{
        boolean inside=InfernoBoundary.inside(client);
        log.info("[Inferno local] stage=LOCAL_START confirmedInside={} preEntry={}",inside,!inside);
		long expected=session;
        localSession = new LocalInfernoSession(task -> { if(expected==session)task.run(); },
            line -> log.info("[Inferno local] {}",line),this::inputAllowed,new InfernoDecisionEngine())
            .input(actions)
            .fightInput(this::fightInput).prayersOnly(this::prayersOnly).manualMovement(this::manualMovement)
			.common(new LocalInfernoState(client, npcDiagnostics), new Commands(client, this::inputAllowed).prayersOnly(this::prayersOnly))
			.inferno(new InfernoState(client), new InfernoCommands(client, this::inputAllowed))
			.settings(runSettings());
		localSession.start();
	}
    /** Engine options for the next run, with the assist switches of this plugin. */
    private Map<String,Object> runSettings(){
        Map<String,Object> options=localSettings(config);
        options.put("prayersOnly",prayersOnly());options.put("manualMovement",manualMovement());
        return options;
    }
    private void closeRecording(){}

    @Subscribe public void onClientTick(net.runelite.api.events.ClientTick event){
        if(overlay!=null)overlay.capture(client);
        if(active)inputState.observe(client,ArenaProduct.INFERNO);
        beginSessionIfReady();
    }
    /**
     * Prepares the local controller beside the entrance before Jump-in, or on the first
     * loaded frame inside. Local startup keeps owning Jump-in until the player is inside.
     */
    private void beginSessionIfReady(){
        if(!active)return;
        boolean inside=InfernoBoundary.inside(client);
        if(inside&&preparation!=null&&inputAllowed()){preparation.shutdown();preparation=null;}
        if(localSession!=null||client.getGameState()!=net.runelite.api.GameState.LOGGED_IN)return;
        if(!inside&&preparation!=null&&!preparation.readyForSession())return;
        if(inside?!inputAllowed():!sessionAllowed())return;
        if(inside)runGuard.observe(true,true,false);
        startLocalSession();
    }

	@Subscribe
	public void onGameTick(GameTick e)
    {
        if(active)inputState.observe(client,ArenaProduct.INFERNO);
        boolean reliable=client.getGameState()==net.runelite.api.GameState.LOGGED_IN
            &&client.getLocalPlayer()!=null&&client.getLocalPlayer().getLocalLocation()!=null&&client.getTopLevelWorldView()!=null;
        boolean inside=InfernoBoundary.inside(client);
        boolean departed=arenaRunProtected()&&reliable&&!inside;
        runGuard.observe(reliable,inside,reliable&&client.getBoostedSkillLevel(net.runelite.api.Skill.HITPOINTS)<=0);
        antibanPolicy.apply();
        enforceInputLease();
        updateHud(InfernoBoundary.inside(client));
        recordStatus(InfernoBoundary.inside(client));
        if (!active) return;
        if(departed){stopSession();return;}
        // A local controller keeps observing NPC attack clocks while input is held. Real logout, death
        // or departure still closes the session. The worker and runner both recheck action permission.
        boolean localLocation=client.getGameState()==net.runelite.api.GameState.LOADING
            || (!reliable&&client.getGameState()==net.runelite.api.GameState.LOGGED_IN) || sessionLocation();
        if (localSession!=null && !localLocation) { stopSession();return; }
        if (!InfernoBoundary.inside(client)) return;
        beginSessionIfReady();
        if (localSession!=null) {
            localSession.spellState(
                net.runelite.client.plugins.microbot.util.magic.Rs2Magic.isSpellbook(
                    net.runelite.client.plugins.microbot.util.magic.Rs2Spellbook.ANCIENT),localBloodAutocastLevel());
            localSession.iceRunes(net.runelite.client.plugins.microbot.droinfernoprayers.plugins.inferno.InfernoCommands.iceRuneTiers());
            localSession.observePrayers(client);localSession.onGameTick();
        }
	}

    /** Same Blood and non-defensive autocast receipt as the supplied preparation controller. */
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

    @Subscribe public void onActorDeath(net.runelite.api.events.ActorDeath event) {
        if(!arenaRunProtected())return;
        if(event.getActor()==client.getLocalPlayer()){
            inputState.clear();
            runGuard.observe(true,false,true);
            stopSession();
            terminalStatus="Inferno ended: player died";
            Microbot.status=terminalStatus;
            if(logoutAfterRun())runLogout.start("Inferno: died; logging out with auto-login held.");
            return;
        }
        if(!(event.getActor() instanceof NPC)||!InfernoBoundary.inside(client))return;
        int id=((NPC)event.getActor()).getId();
        if(id==net.runelite.api.gameval.NpcID.INFERNO_TZKALZUK_PLACEHOLDER) {
            runGuard.completed();
            stopSession();
            terminalStatus="Inferno complete: observed Zuk death";
            Microbot.status=terminalStatus;
            if(logoutAfterRun())runLogout.start("Inferno complete; logging out with auto-login held.");
        }
    }

    @Subscribe public void onVarbitChanged(net.runelite.api.events.VarbitChanged event){
        LocalInfernoSession current=localSession;if(current!=null)current.observePrayers(client);
    }
    @Subscribe public void onMenuOptionClicked(net.runelite.api.events.MenuOptionClicked event){
        LocalInfernoSession current=localSession;if(current!=null)current.observePrayerMenu(event.getParam1(),event.isConsumed());
    }

	// ---- events: what happened between ticks ----

	@Subscribe
	public void onNpcSpawned(NpcSpawned e)
	{
		if (active && npcDiagnostics != null && InfernoBoundary.inside(client)) npcDiagnostics.spawned(e.getNpc());
		Events events = npcEvents();
		if (events != null)
		{
			events.npcSpawned(e.getNpc().getIndex());
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned e)
	{
		if (npcDiagnostics != null) npcDiagnostics.despawned(e.getNpc());
		Events events = npcEvents();
		if (events != null)
		{
			events.npcDespawned(e.getNpc().getIndex());
		}
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
        if(active&&InfernoBoundary.inside(client)&&(e.getType()==ChatMessageType.GAMEMESSAGE||e.getType()==ChatMessageType.SPAM))
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

    /** Spawn membership can arrive during Jump-in before the player's template tile is available. */
    private Events npcEvents()
    {
        LocalInfernoSession current=localSession;
        return current==null||!active||!current.running()?null:current.events();
    }
	private Events events()
	{
		LocalInfernoSession current=localSession;
        return current == null || !active || !InfernoBoundary.inside(client)
            || !current.running() ? null : current.events();
	}

    private void closeInput() {
        if(actions!=null){actions.close();actions=null;}
        if(mouseOverlay!=null){if(overlayManager!=null)overlayManager.remove(mouseOverlay);mouseOverlay=null;}
    }

    private void recordStatus(boolean inside) {
        LocalInfernoSession current = localSession;
        boolean paused=Microbot.pauseAllScripts.get();
        boolean human=net.runelite.client.plugins.microbot.util.input.InputArbiter.isHuman();
        String reason=!active?"ACTIVITY_STOPPED":!inside?"OUTSIDE_ARENA":paused?"PAUSED":human?"HUMAN_INPUT"
            :current==null||!current.running()?"LOCAL_NOT_READY":"LIVE";
        ArenaFightInput input=config.fightInput();
        Map<String,Object> values=Map.of("record","arena_input_status","product",ArenaProduct.INFERNO.protocolName,
            "connection",current==null?"NONE":current.running()?"LOCAL_READY":"LOCAL_STOPPED","live",reason.equals("LIVE"),
            "reason",reason,"paused",paused,"humanInput",human,"inside",inside,"fightInput",input==null?"MICROBOT_MOUSE":input.name());
        if(values.equals(previousRecordingStatus))return;
        previousRecordingStatus=values;
        try{
            var bus=Microbot.getEventBus();
            if(bus!=null)bus.post(new net.runelite.client.plugins.microbot.droinfernoprayers.DroRecorderDiagnostic(values));
        }catch(RuntimeException unavailable){ /* Evidence must never interrupt the activity. */ }
    }

    private void stopSession() {
        log.info("[Inferno local] stage=STOP localPreparation={} sessionPresent={}",preparation!=null,localSession!=null);
        inputState.clear();active=false;session++;antibanPolicy.close();
        recordStatus(false);
        if(!arenaRunProtected()&&inputLease!=null){inputLease.close();inputLease=null;}
        if(!arenaRunProtected())hud=null;
        LocalInfernoSession old=localSession;localSession=null;if(old!=null) old.close();
        if(preparation!=null){preparation.shutdown();preparation=null;}
        closeInput();
        closeRecording();
    }
    @Subscribe public void onGameStateChanged(net.runelite.api.events.GameStateChanged e) {
        if(e.getGameState()!=net.runelite.api.GameState.LOGGED_IN)inputState.clear();
        // Jump-in passes through LOADING; keep the pre-entry session. Logout and other states still end it.
        if (e.getGameState()!=net.runelite.api.GameState.LOGGED_IN
            && e.getGameState()!=net.runelite.api.GameState.LOADING) {
            runGuard.departed();
            if(active||localSession!=null||preparation!=null)stopSession();
        }
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
        return "DroInferno "+(descriptor==null?"unknown":descriptor.version());
    }
}
