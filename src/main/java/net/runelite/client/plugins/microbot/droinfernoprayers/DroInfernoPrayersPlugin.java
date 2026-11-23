package net.runelite.client.plugins.microbot.droinfernoprayers;

import com.google.inject.Provides;
import java.util.List;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcDespawned;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.droinfernoprayers.prayer.Guard;
import net.runelite.client.plugins.microbot.droinfernoprayers.prayer.GuardPlanner;
import net.runelite.client.plugins.microbot.droinfernoprayers.prayer.PrayerWriter;
import net.runelite.client.plugins.microbot.droinfernoprayers.prayer.Threat;
import net.runelite.client.plugins.microbot.droinfernoprayers.prayer.ThreatReader;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
	name = "[Dro] Inferno Prayers",
	description = "1-tick protection prayers in the Inferno. You play everything else.",
	tags = {"dro", "inferno", "prayers", "pvm"},
	authors = {"droplugins"},
	version = DroInfernoPrayersPlugin.version,
	minClientVersion = "2.6.55",
	iconUrl = "https://chsami.github.io/Microbot-Hub/DroInfernoPrayersPlugin/assets/icon.png",
	cardUrl = "https://chsami.github.io/Microbot-Hub/DroInfernoPrayersPlugin/assets/card.png",
	enabledByDefault = false,
	isExternal = true
)
public class DroInfernoPrayersPlugin extends Plugin
{
	static final String version = "2.0.1";
	static final int INFERNO_REGION = 9043;

	@Inject
	private Client client;

	@Inject
	private DroInfernoPrayersConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private InfernoPrayerOverlay overlay;

	private final ThreatReader reader = new ThreatReader(InfernoMobs::of);
	private PrayerWriter writer;
	private volatile boolean inside;
	private volatile Threat answering;
	private volatile Threat upcoming;
	private volatile Guard wanted = Guard.NONE;

	@Provides
	DroInfernoPrayersConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(DroInfernoPrayersConfig.class);
	}

	@Override
	protected void startUp()
	{
		writer = new PrayerWriter(client);
		reader.clear();
		inside = false;
		overlayManager.add(overlay);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		reader.clear();
		writer = null;
		inside = false;
		answering = upcoming = null;
		wanted = Guard.NONE;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() != GameState.LOGGED_IN && event.getGameState() != GameState.LOADING)
		{
			leave();
		}
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		if (inside && event.getActor() instanceof NPC)
		{
			reader.animation((NPC) event.getActor());
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		reader.despawned(event.getNpc().getIndex());
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		PrayerWriter w = writer;
		if (w == null)
		{
			return;
		}
		if (!inInferno())
		{
			leave();
			return;
		}
		inside = true;
		int tick = client.getTickCount();
		w.gameTick(tick);
		List<Threat> threats = reader.read(client, tick);
		Guard current = w.observed();
		GuardPlanner.Decision decision = GuardPlanner.decide(threats, tick + 1, current);
		Player me = client.getLocalPlayer();
		boolean moving = me != null && me.getPoseAnimation() != me.getIdlePoseAnimation();
		w.want(decision.guard(), config.holdWithPairs() && !moving && !decision.conflict());
		wanted = decision.guard();
		answering = decision.answered();
		upcoming = GuardPlanner.upcoming(threats, tick + 1);
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		PrayerWriter w = writer;
		if (w != null && inside)
		{
			w.clientTick(!Microbot.pauseAllScripts.get());
		}
	}

	private void leave()
	{
		if (!inside)
		{
			return;
		}
		inside = false;
		reader.clear();
		answering = upcoming = null;
		wanted = Guard.NONE;
		if (writer != null)
		{
			writer.reset();
		}
	}

	private boolean inInferno()
	{
		Player me = client.getLocalPlayer();
		if (client.getGameState() != GameState.LOGGED_IN || me == null || me.getLocalLocation() == null)
		{
			return false;
		}
		return WorldPoint.fromLocalInstance(client, me.getLocalLocation()).getRegionID() == INFERNO_REGION;
	}

	boolean inside()
	{
		return inside;
	}

	String wantedLabel()
	{
		return wanted.label();
	}

	String answeringLabel()
	{
		Threat t = answering;
		return t == null ? "-" : t.source();
	}

	String upcomingLabel()
	{
		Threat t = upcoming;
		PrayerWriter w = writer;
		if (t == null || w == null)
		{
			return "-";
		}
		return t.source() + " " + t.guard().label() + " +" + Math.max(0, t.tick() - client.getTickCount());
	}

	String status()
	{
		PrayerWriter w = writer;
		return w == null ? "Off" : w.status();
	}
}
