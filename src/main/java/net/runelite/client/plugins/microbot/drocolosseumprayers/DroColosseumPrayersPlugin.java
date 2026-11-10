package net.runelite.client.plugins.microbot.drocolosseumprayers;

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

@PluginDescriptor(name = PluginConstants.DRO + "Colosseum Prayers",
    description = "Switches protection and offensive prayers in the Fortis Colosseum",
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
	private DroColosseumPrayersConfig config;

	private volatile LocalColosseumSession localSession;

    private volatile ColosseumDecisionEngine decisionEngine;
    private volatile Commands commands;
    private NpcSpawnDiagnostics npcDiagnostics;
    @Inject private net.runelite.client.ui.overlay.OverlayManager overlayManager;
    @Inject private ColosseumOverlay overlay;
    @Inject private ColosseumTilePackOverlay tilePackOverlay;
    private Map<String,Object> previousStatus;
    private volatile String terminalStatus;
    @Inject private ConfigManager configManager;
    private ArenaInputLease inputLease;
    private ArenaHudTracker hudTracker;
    private volatile ArenaHud hud;
    private final ArenaAntibanPolicy antibanPolicy=new ArenaAntibanPolicy();
    private ArenaRunGuard runGuard = new ArenaRunGuard();

    @Override public boolean arenaRunProtected(){return runGuard.protectedRun();}
    @Override public ArenaHud arenaHud(){return active||arenaRunProtected()?hud:null;}

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
    private volatile boolean active;
    private volatile long session;

    public boolean canStartAtCurrentLocation(){
        return Microbot.getClientThread().runOnClientThreadOptional(()->
            client.getGameState()==net.runelite.api.GameState.LOGGED_IN&&client.getLocalPlayer()!=null
            &&client.getBoostedSkillLevel(net.runelite.api.Skill.HITPOINTS)>0&&client.getTopLevelWorldView()!=null
            &&(ArenaProduct.COLOSSEUM.inside(client)||!client.getTopLevelWorldView().isInstance()
                &&(client.getLocalPlayer().getWorldLocation().distanceTo(new WorldPoint(1808,9506,0))<=20
                ||client.getLocalPlayer().getWorldLocation().distanceTo(new WorldPoint(1793,3106,0))<=15))).orElse(false);
    }
    @Override public boolean arenaRunning() {
        LocalColosseumSession current=localSession;
        return active&&current!=null&&current.lastEnd()==null;
    }
    @Override public String arenaStatus(){return status();}
    @Override public String arenaFailure(){
        LocalColosseumSession current=localSession;
        if(current!=null&&current.lastEnd()!=null)return current.lastEnd();
        return null;
    }
    public String status() {
        if(terminalStatus!=null)return terminalStatus;
        LocalColosseumSession current=localSession;
        if(current!=null) return current.lastEnd()!=null ? current.lastEnd() : current.status();
        return !active ? "Stopped; re-arm and start the activity to restart" : "Enter the Colosseum to start";
    }
    private boolean insideSnapshot() {
        return Microbot.getClientThread()
            .runOnClientThreadOptional(() -> ArenaProduct.COLOSSEUM.inside(client)).orElse(false);
    }

    private long arenaHeldAt;

    private boolean arenaHeld(){
        long now=System.nanoTime();
        if(ArenaProduct.COLOSSEUM.instanceHeld(client)){arenaHeldAt=now;return true;}
        return client.getGameState()==net.runelite.api.GameState.LOADING&&arenaHeldAt!=0
            &&now-arenaHeldAt<java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
    }
    private boolean localSessionAllowed(){return active&&arenaHeld();}
    private final ArenaInputState inputState=new ArenaInputState();

    private static final ArenaRunLogout runLogout=new ArenaRunLogout();

    private boolean logoutAfterRun(){DroColosseumPrayersConfig c=config;return c!=null&&c.logoutAfterRun();}
    private boolean inputAllowed() {
        if (!active || Microbot.pauseAllScripts.get()
            || net.runelite.client.plugins.microbot.util.input.InputArbiter.isHuman()) return false;
        return inputState.allowed();
    }

	@Provides
	DroColosseumPrayersConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(configType());
	}

    protected Class<DroColosseumPrayersConfig> configType(){return DroColosseumPrayersConfig.class;}
    protected String configGroup(){return DroColosseumPrayersConfig.PRAYERS_GROUP;}
    protected DroColosseumPrayersConfig config(){return config;}

    protected boolean prayersOnly(){return true;}
    protected boolean manualMovement(){return false;}

    protected ArenaFightInput fightInput(){return ArenaFightInput.INVOKES;}



	@Override
	protected void startUp()
	{
        terminalStatus=null;
        if(ArenaInputLease.active()||infernoRunning()){
            active=false;terminalStatus="Stop local Inferno before starting Colosseum";return;
        }
        inputState.clear();active=true;session++;
        npcDiagnostics=new NpcSpawnDiagnostics(client, ArenaProduct.COLOSSEUM, values -> {
            try { log.info("[Dro Colosseum] [npc-visibility] {}",values); }
            catch (RuntimeException ignored) {  }
            var bus=Microbot.getEventBus();
            if(bus!=null)bus.post(new net.runelite.client.plugins.microbot.drocolosseumprayers.ArenaDiagnostic(values));
        });
        previousStatus=null;
        runGuard=new ArenaRunGuard();antibanPolicy.start();
        hudTracker=null;hud=null;
        enforceInputLease();

        if(overlayManager!=null&&overlay!=null){overlayManager.add(overlay);overlay.start();}
        if(overlayManager!=null&&tilePackOverlay!=null)overlayManager.add(tilePackOverlay);
        actions=new ArenaInputDispatcher(this::inputAllowed);

	}

	@Override
	protected void shutDown()
	{
        inputState.clear();active=false;session++;antibanPolicy.close();

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

    private Map<String,Object> runSettings(){
        Map<String,Object> options=new java.util.LinkedHashMap<>();
        options.put("prayersOnly",prayersOnly());options.put("manualMovement",manualMovement());
        return options;
    }

    private boolean infernoRunning(){return false;}
    private boolean stopForConflict(){
        if(!active||!infernoRunning())return false;
        stopSession();

        if(inputLease!=null){inputLease.close();inputLease=null;}
        hud=null;terminalStatus="Colosseum stopped: local Inferno started";return true;
    }

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
        antibanPolicy.apply();
        enforceInputLease();
        updateHud(ArenaProduct.COLOSSEUM.inside(client));
        publishStatus(ArenaProduct.COLOSSEUM.inside(client));
        if (!active) return;
        if (localSession!=null && !localSessionAllowed()) { stopSession();return; }
        if (!ArenaProduct.COLOSSEUM.inside(client)) return;
        if (!inputAllowed()&&localSession==null) return;

        if (localSession==null) connect();
        if (localSession!=null) {
            localSession.spellState(net.runelite.client.plugins.microbot.util.magic.Rs2Magic.isSpellbook(
                net.runelite.client.plugins.microbot.util.magic.Rs2Spellbook.ANCIENT),localBloodAutocastLevel());
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

    static final String SOL="Sol Heredit";
    private long nextSolContinue;



    @Subscribe public void onActorDeath(net.runelite.api.events.ActorDeath event) {
        if(!arenaRunProtected())return;
        if(event.getActor()==client.getLocalPlayer()){localDeath();return;}
        if(!(event.getActor() instanceof NPC)||!ArenaProduct.COLOSSEUM.inside(client))return;
        int id=((NPC)event.getActor()).getId();
        if(id==net.runelite.api.gameval.NpcID.COLOSSEUM_SOL_P1) {
            runGuard.completed();
            stopSession();terminalStatus="Colosseum complete: observed Sol death";

        }
    }

    static final String DEATH_MESSAGE="Oh dear, you are dead!";
    private void localDeath(){
        inputState.clear();runGuard.observe(true,false,true);stopSession();
        terminalStatus="Colosseum ended: player died";

    }








    @Subscribe public void onVarbitChanged(net.runelite.api.events.VarbitChanged event){
        LocalColosseumSession current=localSession;if(current!=null)current.observePrayers(client);
    }
    @Subscribe public void onMenuOptionClicked(net.runelite.api.events.MenuOptionClicked event){
        ArenaInputDispatcher.pressRead();
        LocalColosseumSession current=localSession;if(current!=null)current.observePrayerMenu(event.getParam1(),event.isConsumed());
    }

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
        if(e.getType()==ChatMessageType.GAMEMESSAGE&&arenaRunProtected()&&DEATH_MESSAGE.equals(Text.removeTags(e.getMessage()).trim()))localDeath();
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
    }

    private void publishStatus(boolean inside){
        LocalColosseumSession current=localSession;
        boolean paused=Microbot.pauseAllScripts.get(),human=net.runelite.client.plugins.microbot.util.input.InputArbiter.isHuman();
        String reason=!active?"ACTIVITY_STOPPED":!inside?"OUTSIDE_ARENA":paused?"PAUSED":human?"HUMAN_INPUT":current==null||!current.running()?"LOCAL_NOT_READY":"LIVE";
        Map<String,Object> values=new java.util.LinkedHashMap<>(Map.of("event","arena_input_status","product","colosseum","session",current==null?"NONE":current.running()?"LOCAL_READY":"LOCAL_STOPPED","live",reason.equals("LIVE"),"reason",reason,"paused",paused,"humanInput",human,"inside",inside,"fightInput","INVOKES"));

        values.put("manualMovement",manualMovement());values.put("prayersOnly",prayersOnly());
        values.put("version",engineVersion());
        if(values.equals(previousStatus))return;previousStatus=values;
        try{var bus=Microbot.getEventBus();if(bus!=null)bus.post(new net.runelite.client.plugins.microbot.drocolosseumprayers.ArenaDiagnostic(values));}catch(RuntimeException ignored){}
    }

    private void stopSession() {
        inputState.clear();active=false;session++;arenaHeldAt=0;antibanPolicy.close();
        npcDiagnostics=null;
        publishStatus(false);
        if(!arenaRunProtected()&&inputLease!=null){inputLease.close();inputLease=null;}
        if(!arenaRunProtected())hud=null;
        LocalColosseumSession old=localSession;localSession=null;if(old!=null) old.close();
        closeInput();
    }
    @Subscribe public void onGameStateChanged(net.runelite.api.events.GameStateChanged e) {
        if(e.getGameState()!=net.runelite.api.GameState.LOGGED_IN)inputState.clear();
        if (localSession!=null && !localSessionAllowed()) stopSession();
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
        return "DroColosseum "+(descriptor==null?"unknown":descriptor.version());
    }
}
