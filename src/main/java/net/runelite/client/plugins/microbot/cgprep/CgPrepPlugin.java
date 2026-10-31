package net.runelite.client.plugins.microbot.cgprep;

import com.google.inject.Provides;
import javax.inject.Inject;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.runelite.api.Actor;
import net.runelite.api.NPC;
import net.runelite.api.Projectile;
import net.runelite.api.TileObject;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GroundObjectDespawned;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.api.events.NpcChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.client.plugins.microbot.cgprep.data.CgIds;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.cgprep.data.CgMode;
import net.runelite.client.plugins.microbot.cgprep.data.Monster;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.HotkeyListener;

/**
 * CG Prep: automates Corrupted Gauntlet prep (gather, kill, craft, brew, cook) and hands over to
 * the player in the start room, next to the boss barrier, with time left on the clock.
 *
 * Event handlers here only record facts (client thread, no clicks, no sleeps).
 */
@PluginDescriptor(
	name = PluginConstants.SYD + "Auto Gauntlet",
	description = "Corrupted Gauntlet: preps (gathers, kills demi-bosses, crafts armour + T3 bow/staff, brews, cooks) and fights the Hunllef. Prep & Boss, Prep Only or Boss Only",
	tags = {"gauntlet", "corrupted", "hunllef", "cg", "prep", "pvm", "boss"},
	authors = {"Syd"},
	version = CgPrepPlugin.version,
	minClientVersion = "2.6.43",
	enabledByDefault = PluginConstants.DEFAULT_ENABLED,
	isExternal = PluginConstants.IS_EXTERNAL
)
public class CgPrepPlugin extends Plugin
{
	static final String version = "1.7.3";

	@Inject
	private CgPrepConfig config;
	@Inject
	private CgPrepScript script;
	@Inject
	private CgPrepOverlay overlay;
	@Inject
	private OverlayManager overlayManager;
	@Inject
	private KeyManager keyManager;
	@Inject
	private ConfigManager configManager;
	@Inject
	private net.runelite.api.Client client;
	@Inject
	private net.runelite.client.game.ItemManager itemManager;
	private net.runelite.client.plugins.microbot.cgprep.boss.BossFightOverlay bossOverlay;

	@Provides
	CgPrepConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CgPrepConfig.class);
	}

	private final HotkeyListener pauseListener = new HotkeyListener(() -> config.pauseKey())
	{
		@Override
		public void hotkeyPressed()
		{
			script.togglePause();
			boolean paused = script.paused;
			boolean dry = config.dryRun();
			Microbot.log("[CG Prep] " + (paused ? "paused" : "resumed") + (dry ? " (DRY RUN: no actions)" : ""));
			String msg = paused ? "<col=ff9040>[CG Prep]</col> Paused."
				: dry ? "<col=ff0000>[CG Prep] Resumed in DRY RUN: it will plan but NOT click. Turn off 'Dry run (plan only)' under Development.</col>"
				: "<col=ff9040>[CG Prep]</col> Resumed (live).";
			Microbot.getClientThread().invoke(() ->
				Microbot.getClient().addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "", msg, null));
		}
	};

	private final HotkeyListener continueListener = new HotkeyListener(() -> config.continueKey())
	{
		@Override
		public void hotkeyPressed()
		{
			script.requestContinue();
			Microbot.log("[CG Prep] continue requested");
		}
	};

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
		bossOverlay = new net.runelite.client.plugins.microbot.cgprep.boss.BossFightOverlay(client, () -> script.boss, config::bossOverlay);
		overlayManager.add(bossOverlay);
		keyManager.registerKeyListener(pauseListener);
		keyManager.registerKeyListener(continueListener);
		// Decision log (setting "Decision log"): the "Log folder" setting, else .runelite/cg-prep.
		if (config.decisionLog())
		{
			String folder = config.logFolder();
			if (folder == null || folder.trim().isEmpty())
			{
				// Carry over the folder the development builds used (the old CG Recon "Output folder").
				String old = configManager.getConfiguration("cgrecon", "outputDir");
				if (old != null && !old.trim().isEmpty())
				{
					folder = old.trim();
					configManager.setConfiguration(CgPrepConfig.GROUP, "logFolder", folder);
				}
			}
			java.io.File dir = folder == null || folder.trim().isEmpty()
				? new java.io.File(net.runelite.client.RuneLite.RUNELITE_DIR, "cg-prep")
				: new java.io.File(folder.trim());
			script.openLog(dir);
		}
		script.saveSetting = (key, value) -> configManager.setConfiguration(CgPrepConfig.GROUP, key, value);
		script.stats = new net.runelite.client.plugins.microbot.cgprep.stats.RunStats(configManager, itemManager, script::note);
		script.run();
		Microbot.log("[CG Prep] started" + (config.dryRun() ? " in DRY RUN (no actions)" : ""));
	}

	@Override
	protected void shutDown()
	{
		keyManager.unregisterKeyListener(pauseListener);
		keyManager.unregisterKeyListener(continueListener);
		overlayManager.remove(overlay);
		if (bossOverlay != null)
		{
			overlayManager.remove(bossOverlay);
		}
		script.shutdown();
		script.closeLog();
	}

	CgPrepScript script()
	{
		return script;
	}

	// ------------------------------------------------------------------ fight tracker feeds
	// All record-only: these run on the client thread and must stay cheap and input-free.

	private final Set<Projectile> seenProjectiles = Collections.newSetFromMap(new WeakHashMap<>());

	@Subscribe
	public void onGameTick(GameTick e)
	{
		script.fight.onTick(Microbot.getClient().getTickCount());
		net.runelite.client.plugins.microbot.cgprep.actions.Weaver wv = script.weaver;
		if (wv != null)
		{
			wv.onTick(Microbot.getClient().getTickCount());
		}
		net.runelite.client.plugins.microbot.cgprep.actions.BrewChain bc = script.brewChain;
		if (bc != null)
		{
			bc.onTick(Microbot.getClient().getTickCount());
		}
		// One read of NPCs / HP / prayer per tick, here on the client thread (see WorldScan).
		net.runelite.client.plugins.microbot.cgprep.fight.WorldScan.capture(Microbot.getClient());
		script.onGameTick();
		// Boss fight: started (varbit) and not switched off. Reads the client here, decides and
		// clicks on its own thread.
		if (script.boss.mode != net.runelite.client.plugins.microbot.cgprep.boss.BossFight.Mode.OFF
			&& client.getVarbitValue(CgIds.VB_BOSS_STARTED) != 0)
		{
			script.boss.onGameTick(client, config.mode());
		}
		else
		{
			script.boss.active = false;
		}
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged e)
	{
		Actor a = e.getActor();
		if (a instanceof NPC && CgIds.HUNLLEF_IDS.contains(((NPC) a).getId()))
		{
			script.fight.onBossAnimation(a.getAnimation());
			script.boss.onBossAnimation(a.getAnimation());
		}
		else if (a != null && a == client.getLocalPlayer())
		{
			script.boss.onOurAnimation(a.getAnimation());
		}
	}

	@Subscribe
	public void onProjectileMoved(ProjectileMoved e)
	{
		Projectile p = e.getProjectile();
		int id = p.getId();
		boolean bossProj = CgIds.PROJ_HUNLLEF_MAGIC.contains(id) || CgIds.PROJ_HUNLLEF_RANGED.contains(id)
			|| CgIds.PROJ_HUNLLEF_PRAYER_DISABLE.contains(id);
		if (bossProj && seenProjectiles.add(p))
		{
			script.fight.onBossProjectile(id);
			script.boss.onProjectile(id);
		}
	}

	@Subscribe
	public void onStatChanged(net.runelite.api.events.StatChanged e)
	{
		net.runelite.client.plugins.microbot.cgprep.actions.Weaver wv = script.weaver;
		if (wv != null)
		{
			wv.onStat(e.getSkill(), e.getXp(), Microbot.getClient().getTickCount());
		}
	}

	@Subscribe
	public void onItemContainerChanged(net.runelite.api.events.ItemContainerChanged e)
	{
		if (e.getContainerId() == net.runelite.api.gameval.InventoryID.INV)
		{
			script.wake(); // a yield / a drop picked up: react this tick, not at the next poll
			net.runelite.client.plugins.microbot.cgprep.stats.RunStats st = script.stats;
			if (st != null && e.getItemContainer() != null)
			{
				st.onInventory(client, e.getItemContainer().getItems(), client.getTickCount());
			}
		}
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned e)
	{
		if (CgIds.HUNLLEF_IDS.contains(e.getNpc().getId()))
		{
			script.fight.onBossIdChanged(e.getNpc().getId());
		}
	}

	@Subscribe
	public void onNpcChanged(NpcChanged e)
	{
		if (CgIds.HUNLLEF_IDS.contains(e.getNpc().getId()))
		{
			script.fight.onBossIdChanged(e.getNpc().getId());
		}
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned e)
	{
		floor(e.getGameObject(), true);
		if (net.runelite.client.plugins.microbot.cgprep.map.GauntletMaze.isNodeObject(config.mode(), e.getGameObject().getId()))
		{
			script.maze.onNodeSpawned(config.mode(), e.getGameObject().getId(), e.getGameObject().getWorldLocation());
			script.wake(); // a node ran out (its depleted version spawned): the queued next step goes now
		}
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned e)
	{
		floor(e.getGameObject(), false);
		if (net.runelite.client.plugins.microbot.cgprep.map.GauntletMaze.isNodeObject(config.mode(), e.getGameObject().getId()))
		{
			script.maze.markDirty();
		}
	}

	@Subscribe
	public void onGroundObjectSpawned(GroundObjectSpawned e)
	{
		floor(e.getGroundObject(), true);
	}

	@Subscribe
	public void onGroundObjectDespawned(GroundObjectDespawned e)
	{
		floor(e.getGroundObject(), false);
	}

	private void floor(TileObject o, boolean spawned)
	{
		if (o == null)
		{
			return;
		}
		int id = o.getId();
		if (CgIds.FLOOR_WARNING.contains(id) || CgIds.FLOOR_HIT.contains(id))
		{
			script.fight.onFloorObject(id, o.getWorldLocation(), spawned);
			script.boss.onFloor(id, o.getWorldLocation(), spawned);
		}
	}

	@Subscribe
	public void onChatMessage(net.runelite.api.events.ChatMessage e)
	{
		if (e.getType() == net.runelite.api.ChatMessageType.GAMEMESSAGE)
		{
			script.boss.onChat(e.getMessage());
			net.runelite.client.plugins.microbot.cgprep.stats.RunStats st = script.stats;
			if (st != null)
			{
				st.onChat(e.getMessage(), client.getVarbitValue(net.runelite.api.gameval.VarbitID.PLAYER_IN_GAUNTLET) != 0,
					client.getVarbitValue(CgIds.VB_BOSS_STARTED) != 0);
			}
		}
	}

	/** Reward Chest opened by hand: its loot lands in the inventory over the next ticks. */
	@Subscribe
	public void onMenuOptionClicked(net.runelite.api.events.MenuOptionClicked e)
	{
		net.runelite.client.plugins.microbot.cgprep.stats.RunStats st = script.stats;
		if (st != null && "Open".equals(e.getMenuOption()) && e.getMenuTarget() != null && e.getMenuTarget().contains("Reward Chest"))
		{
			st.onChestOpened(client, client.getTickCount());
		}
	}

	/** The reward-available flag clearing also marks the chest as opened. */
	@Subscribe
	public void onVarbitChanged(net.runelite.api.events.VarbitChanged e)
	{
		if (e.getVarbitId() == net.runelite.api.gameval.VarbitID.PLAYER_IN_GAUNTLET && e.getValue() == 0)
		{
			net.runelite.client.plugins.microbot.cgprep.stats.RunStats st0 = script.stats;
			if (st0 != null)
			{
				st0.onLeftGauntlet(client.getTickCount());
			}
		}
		if (e.getVarbitId() == net.runelite.api.gameval.VarbitID.GAUNTLET_REWARD_AVAILABLE && e.getValue() == 0)
		{
			net.runelite.client.plugins.microbot.cgprep.stats.RunStats st = script.stats;
			if (st != null)
			{
				st.onChestOpened(client, client.getTickCount());
			}
		}
	}

	/** Demi-boss despawn after death = killed. Light: just a flag on the maze. */
	@Subscribe
	public void onNpcDespawned(NpcDespawned e)
	{
		script.wake(); // a kill: loot / next step right away
		NPC n = e.getNpc();
		if (n == null || !n.isDead())
		{
			return;
		}
		CgMode m = config.mode();
		int id = n.getId();
		if (id == m.bear)
		{
			script.maze.markKilled(Monster.BEAR);
		}
		else if (id == m.darkBeast)
		{
			script.maze.markKilled(Monster.DARK_BEAST);
		}
		else if (id == m.dragon)
		{
			script.maze.markKilled(Monster.DRAGON);
		}
	}
}
