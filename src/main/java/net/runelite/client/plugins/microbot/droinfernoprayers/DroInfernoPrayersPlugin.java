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

@PluginDescriptor(name = PluginConstants.DRO + "Inferno Prayers",
    description = "Switches protection and offensive prayers in the Inferno",
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
	private DroInfernoPrayersConfig config;

	private volatile LocalInfernoSession localSession;
    private NpcSpawnDiagnostics npcDiagnostics;
    @Inject private net.runelite.client.ui.overlay.OverlayManager overlayManager;
    @Inject private InfernoOverlay overlay;
    @Inject private InfernoTilePackOverlay tilePackOverlay;
    private volatile String terminalStatus;
    private Map<String,Object> previousStatus;
    @Inject private ConfigManager configManager;
    private ArenaInputLease inputLease;
    private ArenaHudTracker hudTracker;
    private volatile ArenaHud hud;
    private final ArenaAntibanPolicy antibanPolicy=new ArenaAntibanPolicy();
    private ArenaRunGuard runGuard = new ArenaRunGuard();
    @Override public boolean arenaRunProtected(){return runGuard.protectedRun();}
    @Override public ArenaHud arenaHud(){return active||arenaRunProtected()?hud:null;}

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
    private volatile boolean active;
    private volatile long session;
    private static final java.util.Set<String> RESTART_SETTINGS = java.util.Set.of("logoutAfterRun");

    public boolean canStartAtCurrentLocation(){
        if(insideSnapshot())return true;
                return net.runelite.client.plugins.microbot.Microbot.getClientThread().runOnClientThreadOptional(()->
            client.getGameState()==net.runelite.api.GameState.LOGGED_IN&&client.getLocalPlayer()!=null
            &&client.getBoostedSkillLevel(net.runelite.api.Skill.HITPOINTS)>0&&client.getTopLevelWorldView()!=null
            &&(InfernoBoundary.inside(client)||!client.getTopLevelWorldView().isInstance()&&client.getLocalPlayer().getWorldLocation().distanceTo(KET_KEH)<=15)).orElse(false);
    }
    @Override public boolean arenaRunning() {
        LocalInfernoSession current=localSession;
        return active&&current!=null&&current.lastEnd()==null;
    }
    @Override public String arenaStatus(){return status();}
    @Override public String arenaFailure(){
        LocalInfernoSession current=localSession;
        if(current!=null&&current.lastEnd()!=null)return current.lastEnd();
        return null;
    }
    public String status() {
        if(terminalStatus!=null)return terminalStatus;
        LocalInfernoSession current=localSession;
        if(current!=null) return current.status();
        return !active ? "Stopped; re-arm and start the activity to restart" : "Enter the Inferno to start";
    }
    private boolean insideSnapshot() {
        return net.runelite.client.plugins.microbot.Microbot.getClientThread()
            .runOnClientThreadOptional(() -> InfernoBoundary.inside(client)).orElse(false);
    }
    private final ArenaInputState inputState=new ArenaInputState();

    private static final ArenaRunLogout runLogout=new ArenaRunLogout();

    private boolean logoutAfterRun(){DroInfernoPrayersConfig c=config;return c!=null&&c.logoutAfterRun();}
    private boolean inputAllowed() {
        if (!active || net.runelite.client.plugins.microbot.Microbot.pauseAllScripts.get()
            || net.runelite.client.plugins.microbot.util.input.InputArbiter.isHuman()) return false;
        return inputState.allowed();
    }

    static final WorldPoint KET_KEH = new WorldPoint(2494, 5112, 0);


    private boolean sessionLocation() {
        return client.getGameState()==net.runelite.api.GameState.LOGGED_IN&&client.getLocalPlayer()!=null
            &&client.getTopLevelWorldView()!=null&&client.getBoostedSkillLevel(net.runelite.api.Skill.HITPOINTS)>0
            &&InfernoBoundary.inside(client);
    }


	@Provides
	DroInfernoPrayersConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(configType());
	}

    protected Class<DroInfernoPrayersConfig> configType(){return DroInfernoPrayersConfig.class;}
    protected String configGroup(){return DroInfernoPrayersConfig.PRAYERS_GROUP;}
    protected DroInfernoPrayersConfig config(){return config;}

    protected boolean prayersOnly(){return true;}
    protected boolean manualMovement(){return false;}

    protected ArenaFightInput fightInput(){return ArenaFightInput.INVOKES;}


    private static int localSupplySetting(int value,int fallback,int maximum){return value<=0?fallback:Math.min(maximum,value);}

    void normalizeSupplySettings(){
    }

	@Override
	protected void startUp()
	{
        terminalStatus=null;

        if(ArenaInputLease.active()){
            active=false;terminalStatus="Stop the active arena before starting Inferno";return;
        }
        normalizeSupplySettings();
        ArenaFightInput.migrate(configManager,configGroup());
        inputState.clear();active=true;session++;
        npcDiagnostics=new NpcSpawnDiagnostics(client, ArenaProduct.INFERNO, values -> {
            try { log.info("[Inferno local] [npc-visibility] {}",values); }
            catch (RuntimeException ignored) {  }
            var bus=Microbot.getEventBus();
            if(bus!=null)bus.post(new net.runelite.client.plugins.microbot.droinfernoprayers.ArenaDiagnostic(values));
        });
        previousStatus = null;
        runGuard=new ArenaRunGuard();antibanPolicy.start();
        hudTracker=null;hud=null;
        enforceInputLease();
        log.info("[Inferno local] stage=START inside={}",insideSnapshot());

        if(overlayManager!=null&&overlay!=null){overlayManager.add(overlay);overlay.start();}
        if(overlayManager!=null&&tilePackOverlay!=null)overlayManager.add(tilePackOverlay);
        actions=new ArenaInputDispatcher(this::inputAllowed);

	}

	@Override
	protected void shutDown()
	{
        log.info("[Inferno local] stage=STOP sessionPresent={}",localSession!=null);
        inputState.clear();active=false;session++;antibanPolicy.close();runGuard.departed();
        npcDiagnostics=null;
        if(overlayManager!=null&&overlay!=null){overlay.stop();overlayManager.remove(overlay);}
        if(overlayManager!=null&&tilePackOverlay!=null)overlayManager.remove(tilePackOverlay);
        publishStatus(false);
        if(inputLease!=null){inputLease.close();inputLease=null;}
        hud=null;hudTracker=null;
        if (localSession!=null) { localSession.close();localSession=null; }
        closeInput();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged e)
    {
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

    private Map<String,Object> runSettings(){
        Map<String,Object> options=new java.util.LinkedHashMap<>();
        options.put("prayersOnly",prayersOnly());options.put("manualMovement",manualMovement());
        return options;
    }

    @Subscribe public void onClientTick(net.runelite.api.events.ClientTick event){
        if(overlay!=null)overlay.capture(client);
        if(active)inputState.observe(client,ArenaProduct.INFERNO);
        beginSessionIfReady();
    }

    private void beginSessionIfReady(){
        if(!active)return;
        boolean inside=InfernoBoundary.inside(client);
        if(localSession!=null||client.getGameState()!=net.runelite.api.GameState.LOGGED_IN)return;
        if(!inside||!inputAllowed())return;
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
        publishStatus(InfernoBoundary.inside(client));
        if (!active) return;
        if(departed){stopSession();return;}

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
    }

    private void publishStatus(boolean inside) {
        LocalInfernoSession current = localSession;
        boolean paused=Microbot.pauseAllScripts.get();
        boolean human=net.runelite.client.plugins.microbot.util.input.InputArbiter.isHuman();
        String reason=!active?"ACTIVITY_STOPPED":!inside?"OUTSIDE_ARENA":paused?"PAUSED":human?"HUMAN_INPUT"
            :current==null||!current.running()?"LOCAL_NOT_READY":"LIVE";
        ArenaFightInput input=ArenaFightInput.INVOKES;
        Map<String,Object> values=Map.of("event","arena_input_status","product",ArenaProduct.INFERNO.productName,
            "session",current==null?"NONE":current.running()?"LOCAL_READY":"LOCAL_STOPPED","live",reason.equals("LIVE"),
            "reason",reason,"paused",paused,"humanInput",human,"inside",inside,"fightInput",input==null?"MICROBOT_MOUSE":input.name());
        if(values.equals(previousStatus))return;
        previousStatus=values;
        try{
            var bus=Microbot.getEventBus();
            if(bus!=null)bus.post(new net.runelite.client.plugins.microbot.droinfernoprayers.ArenaDiagnostic(values));
        }catch(RuntimeException unavailable){  }
    }

    private void stopSession() {
        log.info("[Inferno local] stage=STOP sessionPresent={}",localSession!=null);
        inputState.clear();active=false;session++;antibanPolicy.close();
        publishStatus(false);
        if(!arenaRunProtected()&&inputLease!=null){inputLease.close();inputLease=null;}
        if(!arenaRunProtected())hud=null;
        LocalInfernoSession old=localSession;localSession=null;if(old!=null) old.close();
        closeInput();
    }
    @Subscribe public void onGameStateChanged(net.runelite.api.events.GameStateChanged e) {
        if(e.getGameState()!=net.runelite.api.GameState.LOGGED_IN)inputState.clear();

        if (e.getGameState()!=net.runelite.api.GameState.LOGGED_IN
            && e.getGameState()!=net.runelite.api.GameState.LOADING) {
            runGuard.departed();
            if(active||localSession!=null)stopSession();
        }
    }

	private Object actor(Actor a)
	{
		if (a == client.getLocalPlayer())
		{
			return Messages.PLAYER;
		}
		return a instanceof NPC ? ((NPC) a).getIndex() : null;
	}

    private String engineVersion()
    {
        PluginDescriptor descriptor=getClass().getAnnotation(PluginDescriptor.class);
        return "DroInferno "+(descriptor==null?"unknown":descriptor.version());
    }
}
