package net.runelite.client.plugins.microbot.cgprep;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.cgprep.actions.ActionResult;
import net.runelite.client.plugins.microbot.cgprep.actions.CombatAction;
import net.runelite.client.plugins.microbot.cgprep.actions.GatherAction;
import net.runelite.client.plugins.microbot.cgprep.actions.LootAction;
import net.runelite.client.plugins.microbot.cgprep.actions.StationActions;
import net.runelite.client.plugins.microbot.cgprep.antiban.CgAntiban;
import net.runelite.client.plugins.microbot.cgprep.antiban.Pace;
import net.runelite.client.plugins.microbot.cgprep.data.CgIds;
import net.runelite.client.plugins.microbot.cgprep.data.CgMode;
import net.runelite.client.plugins.microbot.cgprep.data.Monster;
import net.runelite.client.plugins.microbot.cgprep.fight.HunllefTracker;
import net.runelite.client.plugins.microbot.cgprep.handover.Handover;
import net.runelite.client.plugins.microbot.cgprep.log.PrepLog;
import net.runelite.client.plugins.microbot.cgprep.map.GauntletMaze;
import net.runelite.client.plugins.microbot.cgprep.nav.InstanceNav;
import net.runelite.client.plugins.microbot.cgprep.plan.Ledger;
import net.runelite.client.plugins.microbot.cgprep.plan.Needs;
import net.runelite.client.plugins.microbot.cgprep.plan.PrepGoal;
import net.runelite.client.plugins.microbot.cgprep.plan.PrepPlanner;
import net.runelite.client.plugins.microbot.cgprep.plan.PrepTimer;
import net.runelite.client.plugins.microbot.statemachine.StateMachineScript;
import net.runelite.client.plugins.microbot.statemachine.Transition;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.depositbox.Rs2DepositBox;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CG prep bot: lobby -> prep -> handover -> (player fights) -> post-run -> repeat on Continue.
 *
 * Loop shape (per the repo rules): observe() refreshes every fact the guards need, then step()
 * evaluates transitions (pure guards reading those facts) and runs the current state's action.
 * Each onState() sends at most one game action, so every decision is re-derived from fresh state.
 */
public class CgPrepScript extends StateMachineScript<CgState>
{
	private static final Logger log = LoggerFactory.getLogger(CgPrepScript.class);

	private final CgPrepConfig config;

	// Components
	final GauntletMaze maze = new GauntletMaze();
	final PrepTimer timer = new PrepTimer();
	final Handover handover = new Handover();
	/** Fed by plugin events; passive (overlay only) until the fight module is enabled. */
	final HunllefTracker fight = new HunllefTracker();
	/** The automated boss fight (BOSS state); plugin events feed it, it runs on its own thread. */
	public final net.runelite.client.plugins.microbot.cgprep.boss.BossFight boss = new net.runelite.client.plugins.microbot.cgprep.boss.BossFight();
	private final PrepPlanner planner = new PrepPlanner();
	private final InstanceNav nav = new InstanceNav();
	private final LootAction loot = new LootAction();
	private GatherAction gather;
	private CombatAction combat;
	private StationActions station;

	// Observed facts (written by observe(), read by guards and the overlay)
	volatile boolean loggedIn;
	volatile boolean inGauntlet;
	volatile boolean bossStarted;
	volatile int secondsLeft = -1;
	volatile Ledger ledger;
	volatile Needs needs;
	volatile PrepGoal goal;
	volatile String lastAction = "-";
	volatile int runsCompleted;

	// Control flags (hotkeys)
	volatile boolean paused;
	volatile boolean continueRequested;
	/** Outside the Gauntlet and its lobby objects (entrance) are in the scene. */
	volatile boolean inLobby;
	/** When the lobby wait ends and we click Enter again (epoch ms; -1 = no wait running). */
	volatile long reenterAtMs = -1;
	private long lastLobbyClickMs;
	private int chestFails;
	/** Entrance clicks in a row that didn't get us in (review: a locked Corrupted Gauntlet looped forever). */
	private int enterFails;
	/** Set after {@link #ENTER_GIVE_UP} failed entries: no more Enter clicks until Continue / the Gauntlet setting changes / restart. */
	volatile String enterBlocked;
	private CgMode enterBlockedMode;
	private static final int ENTER_GIVE_UP = 3;

	private boolean wasInGauntlet;
	private volatile PrepLog plog;
	private String lastGoalStr = "";
	private long lastStatusMs;
	private String lastActKey = "";
	private long lastActMs;
	private boolean pendingLoot;
	private volatile WorldPoint lootTile;
	private final java.util.Map<net.runelite.client.plugins.microbot.cgprep.data.Resource, Long> dropSentMs = new java.util.concurrent.ConcurrentHashMap<>();
	private long lootUntilMs;
	private int failStreak;
	private long lastPlanLogMs;
	private int runSeed;
	/** Set by the plugin: applies "CG Prep Settings.bat" changes (polled from this loop). */
	/** Saves one of CG Prep's settings (set by the plugin: ConfigManager). */
	volatile java.util.function.BiConsumer<String, String> saveSetting;
	/** Runs, deaths and loot value for this session (reset every time the plugin is switched on; set by the plugin). */
	public volatile net.runelite.client.plugins.microbot.cgprep.stats.RunStats stats;

	void note(String type, String body)
	{
		pl(type, body);
	}
	private boolean toolStorageSeen;
	private long lastToolCheckMs;
	private long lastEatMs;
	private long lastDrinkMs;
	/** Egniol doses drunk this run (the ledger can't see them; the decant method plans 2). */
	private volatile int dosesDrunk;
	private int lastDoseTotal = -1;
	private int brewedDoses;
	private int lastUnf;
	/** Escaping (low HP, no food, under attack) until this time or until home. */
	private volatile long fleeUntilMs;
	/** Needs as the loot filter sees them: fish limited by the slot budget (0.3.11 looted fish into a full bag). */
	private volatile Needs lootNeeds;
	/** Nodes this close to a live demi-boss we aren't fighting are left alone (0.4.7). */
	static final int DEMI_AVOID_TILES = 10;

	/**
	 * Around each live demi-boss seen in the last 2 minutes that isn't the one we're fighting: a
	 * Dragon / Dark Beast hits from range for up to ~48 and gathering next to one is how 0.4.7 lost
	 * 80% HP. Null when there's nothing to avoid.
	 */
	private java.util.function.Predicate<WorldPoint> demiHazard()
	{
		long now = System.currentTimeMillis();
		PrepGoal g = goal;
		Monster fighting = g != null && g.type == PrepGoal.Type.FIGHT ? g.monster : null;
		java.util.List<WorldPoint> around = new java.util.ArrayList<>();
		for (java.util.Map.Entry<Monster, net.runelite.client.plugins.microbot.cgprep.map.GauntletMaze.Sighting> e : maze.sightings().entrySet())
		{
			if (e.getKey() != fighting && !maze.isKilled(e.getKey()) && now - e.getValue().atMs < 120_000)
			{
				around.add(e.getValue().raw);
			}
		}
		if (around.isEmpty())
		{
			return null;
		}
		return p ->
		{
			for (WorldPoint d : around)
			{
				if (d.getPlane() == p.getPlane() && Math.max(Math.abs(d.getX() - p.getX()), Math.abs(d.getY() - p.getY())) <= DEMI_AVOID_TILES)
				{
					return true;
				}
			}
			return false;
		};
	}

	/** A teleport home was activated: hold world clicks until we land (or this passes). */
	private volatile long teleportingUntilMs;
	/** Field brewing failed (e.g. nothing that frees a slot): don't pick it again for a while. */
	private long fieldBrewBlockedUntilMs;
	private final Pace pace = new Pace();
	/** Set by a quick station action so the loop runs the next step now, not after 300 ms. */
	private boolean fastFollow;
	/** Released by game events (plugin thread) to run the loop now instead of at the next 300 ms. */
	private final java.util.concurrent.Semaphore wake = new java.util.concurrent.Semaphore(0);

	/** Run the loop now (a node ran out, the inventory changed, an NPC despawned). Any thread. */
	public void wake()
	{
		if (wake.availablePermits() == 0)
		{
			wake.release();
		}
	}
	/** Inside a station click chain (standing still): skip the path search in observe(). */
	private volatile boolean chaining;
	private PrepGoal.Type lastStationType;
	final net.runelite.client.plugins.microbot.cgprep.fight.PrayerGuard prayer = new net.runelite.client.plugins.microbot.cgprep.fight.PrayerGuard();
	final CgAntiban antiban = new CgAntiban(s -> pl("ANTIBAN", s));

	@Inject
	public CgPrepScript(CgPrepConfig config)
	{
		this.config = config;
	}

	public boolean run()
	{
		breakGate.arm();
		gather = new GatherAction(nav);
		weaver = new net.runelite.client.plugins.microbot.cgprep.actions.Weaver();
		brewChain = new net.runelite.client.plugins.microbot.cgprep.actions.BrewChain();
		gather.weaver = weaver;
		combat = new CombatAction(nav, config.eatBelowPercent(), config.bailBelowHp());
		station = new StationActions(nav, pace);
		// Prayers are never needed in the start room: monsters can't come in (user, 0.4.7).
		prayer.safeRoom = wp ->
		{
			if (!maze.grid().isCalibrated() || maze.startRoomX() < 0)
			{
				return false;
			}
			int[] c = maze.grid().roomOf(wp);
			return c != null && c[0] == maze.startRoomX() && c[1] == maze.startRoomZ();
		};
		// Walk only through lit rooms, never the boss room (unlit rooms read as open floor).
		nav.setFilter((x, y) ->
		{
			if (!maze.grid().isCalibrated())
			{
				return true;
			}
			int[] c = maze.grid().roomOf(new WorldPoint(x, y, 0));
			return c != null && maze.found(c[0], c[1]) && !(c[0] == CgIds.BOSS_ROOM_X && c[1] == CgIds.BOSS_ROOM_Z);
		});
		if (config.useAntiban())
		{
			antiban.start();
		}
		zoomedOut = false;
		zoomTries = 0;
		zoomRetryAtMs = 0;
		net.runelite.client.plugins.microbot.cgprep.actions.HumanCamera.get().log = s -> pl("ACT", s);
		mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
		{
			try
			{
				// Event-driven (0.3.24): a node running out, an item landing or an NPC going away
				// wakes the loop at once; otherwise it runs every ~300 ms as before.
				try
				{
					wake.tryAcquire(285, TimeUnit.MILLISECONDS);
					wake.drainPermits();
				}
				catch (InterruptedException ie)
				{
					Thread.currentThread().interrupt();
					return;
				}
				if (!Microbot.isLoggedIn())
				{
					loggedIn = false;
					return;
				}
				loggedIn = true;
				observe();
				antiban.releaseStuckCooldown(false);
				// Respect Microbot's global pause (antiban cooldowns, the pause-all button) and
				// blocking events. Observation above keeps the overlay live meanwhile.
				if (!mayAct())
				{
					return;
				}
				// On start: zoom all the way out with the mouse wheel (user, 1.2.0).
				if (!zoomedOut && !paused && !config.dryRun() && System.currentTimeMillis() >= zoomRetryAtMs)
				{
					try
					{
						// Retried until it runs (1.2.1: the 1.2.0 attempt met the click that switched the
						// plugin on, gave up silently and never zoomed); 10 tries at most.
						zoomedOut = net.runelite.client.plugins.microbot.cgprep.actions.HumanCamera.get().zoomOutFully() || ++zoomTries >= 10;
						zoomRetryAtMs = System.currentTimeMillis() + 2500;
					}
					catch (InterruptedException ie)
					{
						Thread.currentThread().interrupt();
						return;
					}
				}
				step();
				stuckWatchdog();
				// Menuing: a chain of start-room clicks (crafts, combines, equips) runs back to back,
				// paced by Pace instead of the loop delay. Bounded, and it stops for any pause.
				for (int chained = 0; fastFollow && chained < 12; chained++)
				{
					fastFollow = false;
					chaining = true;
					try
					{
						observe();
					}
					finally
					{
						chaining = false;
					}
					if (paused || !mayAct())
					{
						break;
					}
					step();
				}
				fastFollow = false;
			}
			catch (Exception ex)
			{
				log.warn("[Auto Gauntlet] loop error", ex);
				PrepLog p = plog;
				if (p != null)
				{
					p.error("loop", ex);
				}
			}
		}, 0, 15, TimeUnit.MILLISECONDS);
		return true;
	}

	/** Keeps Microbot's break handler out of the Gauntlet (1.5.0). */
	private final net.runelite.client.plugins.microbot.cgprep.antiban.BreakGate breakGate = new net.runelite.client.plugins.microbot.cgprep.antiban.BreakGate();
	private long breakWaitSinceMs;
	private boolean breakWaitGaveUp;
	/** Longest wait in the lobby for a due break to start (then the run goes ahead anyway). */
	private static final long BREAK_WAIT_MAX_MS = 3 * 60_000L;

	private long lastOutsideLogMs;
	private long deathsAtRunStart = -1;

	/**
	 * The only desktop notification (user, 1.6.3/1.6.4): a run ended in a kill or a death, whether
	 * the loop is on or off.
	 */
	private void notifyRunEnd()
	{
		if (!config.notifyRunEnd() || Microbot.getNotifier() == null)
		{
			return;
		}
		net.runelite.client.plugins.microbot.cgprep.stats.RunStats rs = stats;
		boolean died = rs != null && deathsAtRunStart >= 0 && rs.setupDeaths() + rs.bossDeaths() > deathsAtRunStart;
		String next = !config.autoLoop() ? " Press " + config.continueKey() + " for the next run." : bossOnly() ? " Next run starting now: prep it yourself." : "";
		String msg = (died ? "Auto Gauntlet: you died." : "Auto Gauntlet: Hunllef killed!") + next;
		Microbot.getNotifier().notify(msg);
		pl("LOBBY", "notification: " + msg);
	}

	// ---- global stuck watchdog (1.6.2)
	private WorldPoint wdPos;
	private int wdInv;
	private long wdProgressMs;
	private int wdStrikes;
	private long wdLastCallMs;
	/** No change at all (tile, bag, animation, combat) for this long during prep = stuck. */
	private static final long STUCK_MS = 20_000;

	/**
	 * The actions each guard their own retries, and every hang so far was a path one of them
	 * didn't cover: it kept reporting "walking" or "no reaction" while nothing happened (pump 50 s,
	 * range 114 s, a grym node 100 s). This watches the outcome instead of the reports: if in prep
	 * the tile, the bag, our animation and combat all stay the same for 20 s, whatever we were doing
	 * is abandoned (the node / room is skipped for a minute, paths are re-planned), and on a 2nd
	 * strike we walk a few tiles toward the start room to get a fresh view.
	 */
	private void stuckWatchdog()
	{
		long now = System.currentTimeMillis();
		if (getCurrentState() != CgState.PREP || paused || config.dryRun() || bossOnly() || (brewChain != null && brewChain.active()))
		{
			wdProgressMs = now;
			return;
		}
		WorldPoint pos = net.runelite.client.plugins.microbot.util.player.Rs2Player.getWorldLocation();
		int inv = Rs2Inventory.items().mapToInt(it -> it.getId() * 31 + it.getQuantity()).sum();
		boolean busy = net.runelite.client.plugins.microbot.util.player.Rs2Player.isAnimating()
			|| prayer.attackersNear > 0 || prayer.engagedCount > 0;
		boolean moved = pos != null && !pos.equals(wdPos);
		// A gap in calls (paused by antiban / a blocking event / the user) isn't us being stuck.
		boolean gap = now - wdLastCallMs > 3000;
		wdLastCallMs = now;
		if (busy || gap || pos == null || moved || inv != wdInv || wdProgressMs == 0)
		{
			wdPos = pos;
			wdInv = inv;
			wdProgressMs = now;
			if (busy || moved)
			{
				wdStrikes = 0;
			}
			return;
		}
		if (now - wdProgressMs < STUCK_MS)
		{
			return;
		}
		wdProgressMs = now;
		wdStrikes++;
		PrepGoal g = goal;
		pl("WARN", "STUCK: nothing changed for " + STUCK_MS / 1000 + " s (goal " + g + ", last: " + lastAction + "); strike " + wdStrikes
			+ ": skipping it" + (wdStrikes >= 2 ? " and stepping toward the start room" : ""));
		gather.skipCurrentNode(60_000);
		if (g != null && g.type == PrepGoal.Type.EXPLORE && g.room != null)
		{
			maze.blockExplore(g.room[0], g.room[1], 60_000);
		}
		nav.reset();
		pendingLoot = false;
		if (wdStrikes >= 2 && maze.startRoomX() >= 0)
		{
			WorldPoint me = maze.playerRaw();
			WorldPoint home = me == null ? null : maze.grid().centreOf(maze.startRoomX(), maze.startRoomZ(), me.getPlane());
			if (home != null)
			{
				nav.step(home, 3);
			}
		}
	}
	private int rapidTries;

	/**
	 * Before the boss (1.6.1): attack style slot 2 (varp ATTACK_STYLE = 1), which is Rapid on the bow
	 * (4-tick swings instead of 5) and Accurate on the staff. 1.6.0's 5th run went in with the bow
	 * on Accurate. Combat tab by its F-key, the style, back to the inventory. At most 3 tries a run.
	 * True if it acted this loop.
	 */
	private boolean ensureRapid()
	{
		if (rapidTries >= 3 || (secondsLeft >= 0 && secondsLeft < 4)
			|| Microbot.getVarbitPlayerValue(net.runelite.api.VarPlayer.ATTACK_STYLE) == 1)
		{
			return false;
		}
		rapidTries++;
		net.runelite.client.plugins.microbot.globval.enums.InterfaceTab combat = net.runelite.client.plugins.microbot.globval.enums.InterfaceTab.COMBAT;
		if (!net.runelite.client.plugins.microbot.util.tabs.Rs2Tab.isCurrentTab(combat))
		{
			int key = combat.getHotkey();
			if (key != -1)
			{
				net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard.keyPress(key);
			}
			else
			{
				net.runelite.client.plugins.microbot.util.tabs.Rs2Tab.switchTo(combat);
			}
			Global.sleepUntil(() -> net.runelite.client.plugins.microbot.util.tabs.Rs2Tab.isCurrentTab(combat), 800);
		}
		pace.before(Pace.Kind.INVENTORY);
		boolean sent = net.runelite.client.plugins.microbot.util.combat.Rs2Combat.setAttackStyle(net.runelite.api.widgets.WidgetInfo.COMBAT_STYLE_TWO);
		pace.after(Pace.Kind.INVENTORY);
		boolean ok = sent && Global.sleepUntil(() -> Microbot.getVarbitPlayerValue(net.runelite.api.VarPlayer.ATTACK_STYLE) == 1, 1500);
		net.runelite.client.plugins.microbot.cgprep.antiban.Pace.ensureInventoryTab();
		act("attack style: Rapid for the bow " + (ok ? "set" : "not confirmed (try " + rapidTries + ")"));
		return true;
	}

	/** Top-up (1.6.0): raw paddlefish lying around {@code at}: walk over, then take them one by one. */
	private ActionResult pickUpFishAt(WorldPoint at)
	{
		if (at == null)
		{
			return ActionResult.NOT_AVAILABLE;
		}
		net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel fish = Microbot.getRs2TileItemCache().query().withId(CgIds.RAW_PADDLEFISH).toList().stream()
			.filter(it -> it.getWorldLocation() != null && it.getWorldLocation().distanceTo2D(at) <= 2)
			.findFirst().orElse(null);
		if (fish == null)
		{
			return ActionResult.NOT_AVAILABLE;
		}
		if (Rs2Inventory.emptySlotCount() == 0)
		{
			return ActionResult.NOT_AVAILABLE;
		}
		if (!net.runelite.client.plugins.microbot.cgprep.actions.Aim.onScreen(fish.getLocalLocation()))
		{
			net.runelite.client.plugins.microbot.util.walker.Rs2Walker.walkMiniMap(fish.getWorldLocation());
			return ActionResult.WALKING;
		}
		int before = Rs2Inventory.count(CgIds.RAW_PADDLEFISH);
		if (!fish.pickup())
		{
			return ActionResult.NO_REACTION;
		}
		boolean got = Global.sleepUntil(() -> Rs2Inventory.count(CgIds.RAW_PADDLEFISH) > before, 3000);
		return got ? ActionResult.CLICKED : ActionResult.NO_REACTION;
	}
	/** Items that wouldn't drop this run (id -> tries). */
	private final java.util.Map<Integer, Integer> junkDropFails = new java.util.concurrent.ConcurrentHashMap<>();

	/**
	 * Prep done (user, 1.6.0): drop everything except the cooked fish, the potions and the spare
	 * weapon (bow / staff) before the boss. One item (or stack) per loop, through its "Drop"
	 * option. Skipped in the last 5 s of the timer (the game puts us in the boss room at 0 anyway).
	 * True while something was dropped this loop.
	 */
	private boolean dropJunkBeforeBoss()
	{
		if (secondsLeft >= 0 && secondsLeft < 5)
		{
			return false;
		}
		CgMode m = mode();
		java.util.Set<Integer> keep = new java.util.HashSet<>();
		keep.add(CgIds.PADDLEFISH);
		keep.add(CgIds.CORRUPTED_PADDLEFISH);
		for (int id : CgIds.EGNIOL_ALL)
		{
			keep.add(id);
		}
		for (int id : m.bow)
		{
			keep.add(id);
		}
		for (int id : m.staff)
		{
			keep.add(id);
		}
		net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel junk = Rs2Inventory.items(it -> it != null && !keep.contains(it.getId())
				&& junkDropFails.getOrDefault(it.getId(), 0) < 3) // one that won't drop never holds up the boss
			.findFirst().orElse(null);
		if (junk == null)
		{
			return false;
		}
		int id = junk.getId();
		int before = Rs2Inventory.count(id);
		pace.before(Pace.Kind.INVENTORY);
		boolean sent = Rs2Inventory.interact(id, "Drop");
		pace.after(Pace.Kind.INVENTORY);
		boolean gone = sent && Global.sleepUntil(() -> Rs2Inventory.count(id) < before, 1200);
		if (!gone)
		{
			junkDropFails.merge(id, 1, Integer::sum);
		}
		act("clear the bag for the boss: drop " + junk.getName() + (gone ? "" : sent ? " (not gone yet)" : " (click didn't go out)"));
		return true;
	}
	private long prayersOffAtMs;

	/**
	 * Back in the lobby (a kill, a death or leaving): every prayer off, quick prayers too (user,
	 * 1.5.3: "make absolutely sure"). Checked every loop while in POST_RUN / LOBBY_SETUP, so one
	 * click that doesn't land is simply sent again. True if anything was on (clicks were sent).
	 */
	private boolean prayersOff(String where)
	{
		if (System.currentTimeMillis() - prayersOffAtMs < 900)
		{
			return false; // the last clicks may not show yet: a 2nd click would turn them back on
		}
		java.util.List<net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum> on = new java.util.ArrayList<>();
		for (net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum p : net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum.values())
		{
			if (Rs2Prayer.isPrayerActive(p))
			{
				on.add(p);
			}
		}
		boolean quick = Rs2Prayer.isQuickPrayerEnabled();
		if (on.isEmpty() && !quick)
		{
			return false;
		}
		prayersOffAtMs = System.currentTimeMillis();
		if (quick)
		{
			Rs2Prayer.toggleQuickPrayer(false);
		}
		Rs2Prayer.disableAllPrayers();
		boolean off = Global.sleepUntil(() ->
		{
			for (net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum p : net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum.values())
			{
				if (Rs2Prayer.isPrayerActive(p))
				{
					return false;
				}
			}
			return !Rs2Prayer.isQuickPrayerEnabled();
		}, 1500);
		pl("PRAYER", "turned off " + on + (quick ? " + quick prayers" : "") + " " + where + (off ? "" : " (not confirmed yet, checking again)"));
		return true;
	}

	/** Zoomed out once since this start (1.2.0). */
	private volatile boolean zoomedOut;
	private int zoomTries;
	private long zoomRetryAtMs;

	// ------------------------------------------------------------------ observation

	private CgMode mode()
	{
		return config.mode();
	}

	/** Timing of the last observe() (ms): maze / timer+ledger / planner inputs+decide. For PERF lines. */
	private long perfMaze;
	private long perfLedger;
	private long perfPlan;
	private long lastPerfLogMs;

	private void observe()
	{
		long t0 = System.nanoTime();
		maze.refresh(mode());
		if (!chaining && maze.inGauntlet() && !maze.bossStarted() && maze.grid().isCalibrated())
		{
			maze.setPaths(nav.distanceField());
		}
		perfMaze = (System.nanoTime() - t0) / 1_000_000;
		perfLedger = 0;
		perfPlan = 0;
		inGauntlet = maze.inGauntlet();
		bossStarted = maze.bossStarted();
		syncBossSettings();
		breakGate.sync(inGauntlet, s -> pl("BREAK", s));

		if (inGauntlet && !wasInGauntlet)
		{
			// A new run started (or we started the plugin mid-run).
			maze.resetForNewRun();
			nav.reset();
			combat.reset();
			gather.reset();
			prayer.reset();
			toolStorageSeen = false;
			antiban.newRun();
			handover.clear();
			fight.reset();
			boss.reset();
			timer.start(mode().prepSeconds);
			runSeed = config.varyOrder() ? 1 + new java.util.Random().nextInt(1 << 16) : 0;
			dosesDrunk = 0;
			lastDoseTotal = -1;
			brewedDoses = 0;
			lastUnf = 0;
			fleeUntilMs = 0;
			queued = null;
			sticky = null;
			vialsMadeThisRun = 0;
			droppedTools.clear();
			exploreDoor = null;
			exploreDoorRoom = -1;
			routeFor = null;
			maze.setRoute(null, null);
			log.info("[Auto Gauntlet] run started ({})", mode());
			if (station != null)
			{
				station.resetRun();
			}
			junkDropFails.clear();
			rapidTries = 0;
			net.runelite.client.plugins.microbot.cgprep.stats.RunStats rs = stats;
			deathsAtRunStart = rs == null ? -1 : rs.setupDeaths() + rs.bossDeaths();
			pl("RUN", "started mode=" + mode() + " profile=" + config.profile() + " dryRun=" + config.dryRun()
				+ " skipDemi=" + config.skipDemiBosses() + " fishTarget=" + config.fishTarget()
				+ " antiban=" + antiban.isActive() + " seed=" + runSeed + " autoMode=" + config.autoMode() + (bossAuto() ? " (bot fights, no handover)" : ""));
		}
		if (!inGauntlet && wasInGauntlet)
		{
			timer.clear();
			net.runelite.client.plugins.microbot.cgprep.stats.RunStats st = stats;
			if (st != null)
			{
				st.runEnded();
				pl("STATS", "run ended: runs " + st.runs() + ", setup deaths " + st.setupDeaths() + ", boss deaths " + st.bossDeaths() + ", loot " + st.gp());
			}
		}
		wasInGauntlet = inGauntlet;
		if (!inGauntlet)
		{
			inLobby = Microbot.getRs2TileObjectCache().query().withId(CgIds.OBJ_LOBBY_ENTRANCE).nearest() != null;
		}
		else
		{
			inLobby = false;
			reenterAtMs = -1;
		}

		if (inGauntlet && !bossStarted)
		{
			long t1 = System.nanoTime();
			secondsLeft = timer.secondsLeft();
			// Inventory, equipment, gear on the floor, crystal, weapon style: ONE client-thread call.
			// From this thread each Microbot call waits for the client's next frame on its own;
			// on the client thread they run inline (0.3.16: ledger + plan inputs took 100-350 ms).
			Object[] read = Microbot.getClientThread().runOnClientThreadOptional(() ->
			{
				Ledger x = Ledger.observe(mode());
				countGearOnGround(x);
				return new Object[]{x, Rs2Inventory.contains(mode().teleportCrystal),
					net.runelite.client.plugins.microbot.util.player.Rs2Player.isInteracting(),
					wearingAny(net.runelite.client.plugins.microbot.cgprep.data.GearSlot.BOW)
						? net.runelite.client.plugins.microbot.cgprep.fight.PrayerGuard.Style.RANGED
						: wearingAny(net.runelite.client.plugins.microbot.cgprep.data.GearSlot.STAFF)
						? net.runelite.client.plugins.microbot.cgprep.fight.PrayerGuard.Style.MAGIC
						// The starting sceptre (or the halberd, or nothing) hits with melee: Piety, not
						// Mystic Vigour (user, 0.3.22 log: "MYSTIC_VIGOUR on: attacking WEAK" in loop 1).
						: net.runelite.client.plugins.microbot.cgprep.fight.PrayerGuard.Style.MELEE};
			}).orElse(null);
			if (read == null)
			{
				return;
			}
			Ledger l = (Ledger) read[0];
			boolean teleHeld = (Boolean) read[1];
			boolean interactingNow = (Boolean) read[2];
			net.runelite.client.plugins.microbot.cgprep.fight.PrayerGuard.Style styleNow =
				(net.runelite.client.plugins.microbot.cgprep.fight.PrayerGuard.Style) read[3];
			net.runelite.client.plugins.microbot.cgprep.fight.WorldScan scan = net.runelite.client.plugins.microbot.cgprep.fight.WorldScan.latest();
			perfLedger = (System.nanoTime() - t1) / 1_000_000;
			long t2 = System.nanoTime();
			int potions = config.potionTarget();
			// Doses drunk = every drop in the bag's Egniol dose total (decanting keeps it, brewing
			// adds). 0.3.29: a sip that landed after the "drink" check gave up wasn't counted, so the
			// planner thought a potion was missing and fetched a 4th grym leaf.
			// 0.3.30: a brew and a sip between two reads still hid the sip. Count what was BREWED
			// instead (every unfinished potion that disappears became a 3-dose) and take what's
			// left in the bag away from it.
			if (lastDoseTotal < 0)
			{
				brewedDoses = l.potionDoses; // started mid-run: what's in the bag counts as brewed
			}
			else if (l.unfinishedPotions < lastUnf)
			{
				brewedDoses += 3 * (lastUnf - l.unfinishedPotions);
			}
			lastUnf = l.unfinishedPotions;
			lastDoseTotal = l.potionDoses;
			dosesDrunk = Math.max(0, brewedDoses - l.potionDoses);
			l.dosesDrunk = dosesDrunk;
			boolean decant = config.decantMethod();
			Needs n = Needs.compute(l, net.runelite.client.plugins.microbot.cgprep.plan.PrepPlanner.profileNow(l, config.profile()), net.runelite.client.plugins.microbot.cgprep.plan.PrepPlanner.fishTargetNow(l, config.profile(), config.fishTarget(), config.fishMin()), config.shardBuffer(), potions, decant, vialsSmash());
			boolean fishLater = net.runelite.client.plugins.microbot.cgprep.plan.PrepPlanner.fishTargetNow(l, config.profile(), config.fishTarget(), config.fishMin()) < config.fishTarget();
			ledger = l;
			needs = n;

			PrepPlanner.Inputs in = new PrepPlanner.Inputs();
			in.ledger = l;
			in.needs = n;
			in.maze = maze;
			in.profile = net.runelite.client.plugins.microbot.cgprep.plan.PrepPlanner.profileNow(l, config.profile());
			in.armourLater = in.profile != config.profile();
			in.secondsLeft = secondsLeft;
			int hr = bossAuto() ? Math.min(8, config.handoverReserve()) : config.handoverReserve();
			in.finishReserve = config.finishReserve() + hr;
			in.handoverReserve = hr;
			in.skipDemiBosses = config.skipDemiBosses();
			in.demiBossBudget = config.demiBossBudget();
			// Players drop tools once done with them; only a tool still needed for gathering counts.
			// A tool we dropped ourselves is never fetched again (0.3.30: dropped the harpoon on the
			// final trip, then walked to the tool storage for a new one and got stuck there).
			in.toolsMissing = (n.get(net.runelite.client.plugins.microbot.cgprep.data.Resource.ORE) > 0 && !l.pickaxe && !droppedTools.contains(net.runelite.client.plugins.microbot.cgprep.data.Resource.ORE))
				|| (n.get(net.runelite.client.plugins.microbot.cgprep.data.Resource.BARK) > 0 && !l.axe && !droppedTools.contains(net.runelite.client.plugins.microbot.cgprep.data.Resource.BARK))
				|| (n.get(net.runelite.client.plugins.microbot.cgprep.data.Resource.RAW_FISH) > 0 && !l.harpoon && !droppedTools.contains(net.runelite.client.plugins.microbot.cgprep.data.Resource.RAW_FISH));
			// Scene-wide object query: expensive, and the answer never changes once true.
			if (!toolStorageSeen && System.currentTimeMillis() - lastToolCheckMs > 5000)
			{
				lastToolCheckMs = System.currentTimeMillis();
				toolStorageSeen = Microbot.getRs2TileObjectCache().query().withId(mode().objTools).first() != null;
			}
			in.toolStorageKnown = toolStorageSeen;
			in.runSeed = runSeed;
			in.committedGather = gather.committedResource(maze);
			java.util.Set<net.runelite.client.plugins.microbot.cgprep.data.Resource> dropped = java.util.EnumSet.noneOf(net.runelite.client.plugins.microbot.cgprep.data.Resource.class);
			long nowMs = System.currentTimeMillis();
			dropSentMs.forEach((res, at) ->
			{
				if (nowMs - at < 1500)
				{
					dropped.add(res);
				}
			});
			in.droppedRecently = dropped;
			maze.hazard = demiHazard();
			in.weakNearby = false;
			if (scan != null)
			{
				int[] weakIds = Monster.WEAK.ids(mode());
				int[] strongIds = Monster.STRONG.ids(mode());
				for (net.runelite.client.plugins.microbot.cgprep.fight.WorldScan.Npc npc : scan.npcs)
				{
					if (npc.dead)
					{
						continue;
					}
					if (java.util.Arrays.stream(weakIds).anyMatch(id -> id == npc.id))
					{
						in.weakTiles = Math.min(in.weakTiles, npc.tiles);
						in.weakOnUs |= npc.targetingUs && npc.tiles <= 2;
					}
					else if (java.util.Arrays.stream(strongIds).anyMatch(id -> id == npc.id))
					{
						in.strongTiles = Math.min(in.strongTiles, npc.tiles);
					}
				}
				in.weakNearby = in.weakTiles <= 15;
			}
			in.hasTeleport = teleHeld;
			in.fieldBrewBlocked = System.currentTimeMillis() < fieldBrewBlockedUntilMs;
			in.pumpBlocked = station != null && station.pumpGivenUp();
			in.timerTeleports = bossAuto();
			// Raw paddlefish on the floor nearby (monster drops), for the end-of-prep top-up (1.6.0).
			try
			{
				WorldPoint me = net.runelite.client.plugins.microbot.util.player.Rs2Player.getWorldLocation();
				net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel near = me == null ? null : Microbot.getRs2TileItemCache().query().withId(CgIds.RAW_PADDLEFISH).within(30).toList().stream()
					.filter(it -> it.getWorldLocation() != null)
					.min(java.util.Comparator.comparingInt(it -> it.getWorldLocation().distanceTo2D(me))).orElse(null);
				if (near != null)
				{
					WorldPoint at = near.getWorldLocation();
					in.groundFishAt = at;
					in.groundFishCount = (int) Microbot.getRs2TileItemCache().query().withId(CgIds.RAW_PADDLEFISH).toList().stream()
						.filter(it -> it.getWorldLocation() != null && it.getWorldLocation().distanceTo2D(at) <= 2).count();
					in.groundFishTiles = (int) Math.round(at.distanceTo2D(me) * 1.4) + 1;
				}
			}
			catch (Exception e)
			{
				in.groundFishAt = null;
			}
			in.hpPct = scan != null ? scan.hpPct() : pct(net.runelite.api.Skill.HITPOINTS);
			in.prayPct = scan != null ? scan.prayerPct() : pct(net.runelite.api.Skill.PRAYER);
			in.inDanger = prayer.engagedCount > 0;
			in.demiOnUs = prayer.demiOnUs;
			in.vialsMade = vialsMadeThisRun;
			// Last loop's fight, still under way (we're attacking it on purpose).
			Monster ft = prayer.fightTarget;
			in.demiFighting = ft != null && ft != Monster.WEAK && ft != Monster.STRONG && prayer.attackingOnPurpose ? ft : null;
			in.mobFighting = (ft == Monster.WEAK || ft == Monster.STRONG) && prayer.attackingOnPurpose ? ft : null;
			in.potionTarget = potions;
			in.fishMin = config.fishMin();
			in.fishLater = fishLater;
			// Escape: low HP with no cooked food while something is on us (0.3.12 stood still and died).
			if (maze.inStartRoom())
			{
				fleeUntilMs = 0;
			}
			// 0.3.14 only fled at 0% (it waited for the last fish to be eaten at 18%): go while there's
			// still HP to walk/teleport on.
			else if (((in.hpPct < 40 && l.cookedFish == 0) || (in.hpPct < 30 && l.cookedFish <= 1))
				&& prayer.engagedCount > 0 && fleeUntilMs == 0)
			{
				fleeUntilMs = System.currentTimeMillis() + 25_000;
				pl("FLEE", "hp " + in.hpPct + "%, no cooked food, " + prayer.attackersNear + " on us: escaping home");
			}
			in.flee = System.currentTimeMillis() < fleeUntilMs;
			prayer.potionSpare = l.potionDoses > 0 && dosesDrunk < n.prepDoseAllowance;
			net.runelite.client.plugins.microbot.cgprep.log.Snapshot.Cfg cfg = new net.runelite.client.plugins.microbot.cgprep.log.Snapshot.Cfg();
			cfg.profile = config.profile();
			cfg.fishTarget = config.fishTarget();
			cfg.potions = potions;
			cfg.decant = decant;
			cfg.shardBuffer = config.shardBuffer();
			cfg.finishReserve = in.finishReserve;
			cfg.handoverReserve = in.handoverReserve;
			cfg.demiBossBudget = in.demiBossBudget;
			cfg.skipDemi = in.skipDemiBosses;
			cfg.vialsSmash = vialsSmash();
			String snap;
			try
			{
				snap = net.runelite.client.plugins.microbot.cgprep.log.Snapshot.encode(in, cfg, maze);
			}
			catch (Exception ex)
			{
				snap = "unavailable: " + ex;
			}
			// Lookahead (user, 0.3.24): while harvesting a node, decide now what comes after it,
			// so the next click goes out the moment it's done instead of 1-2 s later.
			PrepGoal fresh = planner.decide(in);
			WorldPoint routeHint = planner.routeHint(); // before the lookahead's decide overwrites it
			java.util.List<net.runelite.client.plugins.microbot.cgprep.plan.ExplorePlanner.Option> realRanking = planner.lastRanking();
			PrepGoal next = lookahead(in, l, potions, decant);
			planner.restoreRanking(realRanking);
			goal = handOff(fresh, l);
			updateRoute(goal, routeHint);
			if (next != null)
			{
				queued = next;
				queuedSig = nextSig;
				queuedRes = in.committedGather;
				if (!goalKey(String.valueOf(next)).equals(goalKey(lastNextStr)))
				{
					lastNextStr = String.valueOf(next);
					pl("NEXT", "after this " + queuedRes + " node: " + next);
				}
			}
			// Prayer inputs (the guard runs every tick on its own thread): offensive prayer only while
			// we're deliberately attacking our FIGHT target, in the wielded weapon's style.
			PrepGoal gNow = goal;
			boolean fighting = gNow != null && gNow.type == PrepGoal.Type.FIGHT && !config.dryRun();
			prayer.fightTarget = fighting ? gNow.monster : null;
			prayer.attackingOnPurpose = fighting && interactingNow;
			prayer.weaponStyle = styleNow;
			gather.weave = config.weaveAttacks();
			prayer.lazyFlick = config.lazyFlick();
			gather.meleeWeapon = styleNow == net.runelite.client.plugins.microbot.cgprep.fight.PrayerGuard.Style.MELEE;
			lootNeeds = in.gather != null ? in.gather : n;
			perfPlan = (System.nanoTime() - t2) / 1_000_000;
			long total = perfMaze + perfLedger + perfPlan;
			if (total > 150 && System.currentTimeMillis() - lastPerfLogMs > 5000)
			{
				lastPerfLogMs = System.currentTimeMillis();
				pl("PERF", "observe " + total + "ms (maze " + perfMaze + " [snapshot " + maze.perfSnapMs + ", node scan " + maze.perfNodesMs
					+ ", demi scan " + maze.perfDemiMs + "], timer+ledger " + perfLedger + ", plan " + perfPlan + ")");
			}

			String goalStr = String.valueOf(goal);
			net.runelite.client.plugins.microbot.cgprep.fight.WorldScan rs = net.runelite.client.plugins.microbot.cgprep.fight.WorldScan.latest();
			WorldPoint meNow = maze.playerRaw();
			String ctx = (rs == null ? "" : "run " + rs.runEnergy + "%" + (rs.runOn ? "" : " (off)") + " | ") + "t-" + secondsLeft + "s room " + maze.roomX() + "," + maze.roomZ()
				+ (meNow == null ? "" : " me " + meNow.getX() + "," + meNow.getY()) + " grid " + maze.grid().describe()
				+ " | missing " + n + " | " + l.compact();
			// Same goal with only numbers changing (tiles counting down while walking) isn't a new
			// decision: one GOAL/SNAP line per decision, and none once prep has handed over.
			String goalKey = goalKey(goalStr);
			if (getCurrentState() == CgState.PREP && !goalKey.equals(goalKey(lastGoalStr)))
			{
				lastGoalStr = goalStr;
				String opts = "";
				if (goal != null && (goal.type == PrepGoal.Type.EXPLORE || goal.type == PrepGoal.Type.GATHER))
				{
					java.util.List<net.runelite.client.plugins.microbot.cgprep.plan.ExplorePlanner.Option> rk = planner.lastRanking();
					opts = " | options: " + rk.subList(0, Math.min(4, rk.size()));
				}
				pl("GOAL", goalStr + (config.dryRun() ? " [dry run]" : "") + " | " + ctx + opts);
				pl("SNAP", snap); // everything the planner saw, for the offline replay (tools/sim/Replay.java)
			}
			else if (System.currentTimeMillis() - lastStatusMs > 10000)
			{
				lastStatusMs = System.currentTimeMillis();
				pl("STATUS", "state=" + getCurrentState() + " goal=" + goalStr + " last=" + lastAction + " | " + ctx);
			}

			if (config.verbose() && System.currentTimeMillis() - lastPlanLogMs > 3000)
			{
				lastPlanLogMs = System.currentTimeMillis();
				log.info("[Auto Gauntlet] t-{}s room {},{} grid {} | goal {} | missing {} | {}", secondsLeft,
					maze.roomX(), maze.roomZ(), maze.grid().describe(), goal, n, l.compact());
			}
		}
	}

	// ------------------------------------------------------------------ state machine

	@Override
	protected CgState initialState()
	{
		return CgState.OUTSIDE;
	}

	@Override
	protected List<Transition<CgState>> defineTransitions()
	{
		List<Transition<CgState>> t = new ArrayList<>();

		// Pause from anywhere (highest priority), resume back to OUTSIDE (re-derives from there).
		for (CgState s : CgState.values())
		{
			if (s != CgState.PAUSED)
			{
				t.add(Transition.<CgState>from(s).when(() -> paused, "paused").because("Pause hotkey").goTo(CgState.PAUSED));
			}
		}
		t.add(Transition.<CgState>from(CgState.PAUSED).when(() -> !paused, "!paused").because("Resumed").goTo(CgState.OUTSIDE));

		// Boss started: always wins over prep/handover (forced teleport at 0:00 included).
		t.add(Transition.<CgState>from(CgState.PREP).when(() -> bossStarted, "bossStarted").because("In the boss room").goTo(CgState.BOSS));
		t.add(Transition.<CgState>from(CgState.HANDOVER).when(() -> bossStarted, "bossStarted").because("Player entered the boss room").goTo(CgState.BOSS));

		// Left the instance (death, escape, finished).
		t.add(Transition.<CgState>from(CgState.PREP).when(() -> !inGauntlet, "!inGauntlet").because("Left the Gauntlet during prep").goTo(CgState.POST_RUN));
		t.add(Transition.<CgState>from(CgState.HANDOVER).when(() -> !inGauntlet, "!inGauntlet").because("Left the Gauntlet").goTo(CgState.POST_RUN));
		t.add(Transition.<CgState>from(CgState.BOSS).when(() -> !inGauntlet, "!inGauntlet").because("Fight over").goTo(CgState.POST_RUN));

		// Prep finished.
		t.add(Transition.<CgState>from(CgState.PREP).when(() -> goal != null && goal.type == PrepGoal.Type.HANDOVER,
			"goal==HANDOVER").because("Prep complete").goTo(CgState.HANDOVER));

		// Entering a run.
		t.add(Transition.<CgState>from(CgState.OUTSIDE).when(() -> inGauntlet && !bossStarted, "inGauntlet").because("Run in progress").goTo(CgState.PREP));
		t.add(Transition.<CgState>from(CgState.OUTSIDE).when(() -> inGauntlet && bossStarted, "inGauntlet&&bossStarted").because("Started mid-fight").goTo(CgState.BOSS));
		t.add(Transition.<CgState>from(CgState.LOBBY_SETUP).when(() -> inGauntlet, "inGauntlet").because("Entered").goTo(CgState.PREP));
		t.add(Transition.<CgState>from(CgState.OUTSIDE).when(() -> !inGauntlet && config.autoLobby(), "autoLobby").because("Auto lobby on").goTo(CgState.LOBBY_SETUP));
		t.add(Transition.<CgState>from(CgState.OUTSIDE).when(() -> !inGauntlet && config.autoLoop() && inLobby, "autoLoop&&inLobby").because("In the Gauntlet lobby, loop on").goTo(CgState.LOBBY_SETUP));
		// Continue hotkey with the loop off (1.5.2): one run: loot the chest if a reward waits, then enter.
		t.add(Transition.<CgState>from(CgState.OUTSIDE).when(() -> !inGauntlet && continueRequested && inLobby, "continueRequested&&inLobby").because("Continue hotkey").goTo(CgState.LOBBY_SETUP));
		t.add(Transition.<CgState>from(CgState.LOBBY_SETUP).when(() -> !config.autoLobby() && !config.autoLoop() && !continueRequested, "!autoLobby&&!autoLoop&&!continue").because("Auto lobby and loop off").goTo(CgState.OUTSIDE));

		// After a run.
		t.add(Transition.<CgState>from(CgState.POST_RUN).when(() -> !inGauntlet && continueRequested && !config.dryRun(), "continueRequested").because("Continue hotkey: loot the chest and go again").goTo(CgState.LOBBY_SETUP));
		t.add(Transition.<CgState>from(CgState.POST_RUN).when(() -> !inGauntlet && config.autoLoop() && !config.dryRun(), "autoLoop").because("Run over: loot the chest and go again").goTo(CgState.LOBBY_SETUP));
		t.add(Transition.<CgState>from(CgState.POST_RUN).when(() -> inGauntlet, "inGauntlet").because("Player started another run").goTo(CgState.PREP));
		return t;
	}

	@Override
	protected void onTransition(CgState from, CgState to, String reason)
	{
		super.onTransition(from, to, reason);
		log.info("[Auto Gauntlet] {} -> {} ({})", from, to, reason);
		pl("STATE", from + " -> " + to + " (" + reason + ")");
		if (to == CgState.HANDOVER)
		{
			Ledger l = ledger;
			if (bossAuto())
			{
				pl("HANDOVER", "skipped: the bot fights the Hunllef (Auto mode = " + config.autoMode() + ")" + (bossOnly() ? "" : "; entering the boss room"));
			}
			else if (l != null)
			{
				handover.announce(l, config.profile(), secondsLeft, config.popup(), config.notifyRunEnd());
				pl("HANDOVER", String.join(" | ", handover.lines()));
			}
		}
		if (to == CgState.BOSS)
		{
			syncBossSettings(); // the fight resets itself on its first tick (BossFight.newFight)
			pl("FIGHT", "START mode=" + boss.mode + " input=" + config.bossInput() + " offense=" + boss.brainConfig.offense + " eat<=" + boss.brainConfig.eatBelow
				+ " drink<=" + boss.brainConfig.drinkBelow + " clicks/tick=" + boss.clicksPerTick);
		}
		if (from == CgState.BOSS)
		{
			boss.summary(to == CgState.POST_RUN ? "left the boss room" : "-> " + to);
		}
		if (from == CgState.LOBBY_SETUP)
		{
			reenterAtMs = -1;
		}
		if (to == CgState.POST_RUN)
		{
			runsCompleted++;
			continueRequested = false;
			handover.clear();
			notifyRunEnd();
			if (!config.dryRun())
			{
				// Boss dead (or run over): the inventory goes up so a watching player sees what the
				// chest gives (user, 1.3.0). Script thread; the tab's F-key.
				net.runelite.client.plugins.microbot.cgprep.antiban.Pace.ensureInventoryTab();
			}
		}
		if (to == CgState.PREP)
		{
			continueRequested = false; // that run has started
		}
		// Boss Only: the player's prayers during their own prep are left alone (1.4.0).
		if (((to == CgState.PAUSED && from != CgState.BOSS) || to == CgState.HANDOVER) && !config.dryRun() && !(bossOnly() && from != CgState.BOSS))
		{
			// Script thread (step() runs here), never the client thread: toggling prayers clicks.
			Rs2Prayer.disableAllPrayers();
		}
	}

	@Override
	protected CgState onError(CgState state, Exception e)
	{
		log.warn("[Auto Gauntlet] error in {}: {}", state, e.toString());
		PrepLog p = plog;
		if (p != null)
		{
			p.error("state " + state, e);
		}
		return state; // stay put; the next observe() re-derives what to do
	}

	@Override
	protected void onState(CgState s)
	{
		switch (s)
		{
			case PREP:
				if (bossOnly())
				{
					lastAction = "Boss Only: you prep, the bot fights the Hunllef once you're in the boss room";
					return;
				}
				if (config.dryRun())
				{
					lastAction = "DRY RUN: would do " + goal;
					return; // the GOAL line in the decision log already records what it would do
				}
				// Prayers run on their own per-tick thread (onGameTick), not here.
				executeGoal();
				break;
			case LOBBY_SETUP:
				if (!config.dryRun())
				{
					if (prayersOff("in the lobby"))
					{
						break; // one thing per loop: the prayers first
					}
					lobbySetup();
				}
				break;
			case OUTSIDE:
				maybeAutoRetaliateOff("outside the Gauntlet");
				// Nothing to do out here: say why every 10 s (1.5.1: a start sat in OUTSIDE with no
				// clue in the log).
				if (System.currentTimeMillis() - lastOutsideLogMs > 10_000)
				{
					lastOutsideLogMs = System.currentTimeMillis();
					net.runelite.api.coords.WorldPoint wp = net.runelite.client.plugins.microbot.util.player.Rs2Player.getWorldLocation();
					String why = !config.autoLoop() ? "loop off: press " + config.continueKey() + " to start a run"
						: !inLobby ? "walk to the Gauntlet lobby" : "waiting";
					pl("OUTSIDE", why + " | inLobby=" + inLobby + " autoLoop=" + config.autoLoop()
						+ " at " + (wp == null ? "?" : wp.getX() + "," + wp.getY() + "," + wp.getPlane() + " region " + wp.getRegionID()));
					lastAction = "outside: " + why;
				}
				break;
			case HANDOVER:
				if (!bossOnly() && !config.dryRun() && dropJunkBeforeBoss())
				{
					break; // one drop per loop, then the boss barrier
				}
				if (bossAuto() && !bossOnly() && ensureRapid())
				{
					break;
				}
				maybeEnterBoss();
				break;
			case POST_RUN:
				if (!config.dryRun() && !inGauntlet)
				{
					prayersOff("after the run");
				}
				break;
			case BOSS:
			case PAUSED:
			default:
				// No input from this loop. BOSS: the fight (if on) runs on its own per-tick thread (boss.BossFight).
				break;
		}
	}

	// ------------------------------------------------------------------ goal execution (one action)

	private void executeGoal()
	{
		PrepGoal g = goal;
		Ledger l = ledger;
		if (g == null || l == null)
		{
			return;
		}
		// Food first (user): below the HP % setting, eat a cooked paddlefish whatever we're doing.
		if (maybeEat())
		{
			return;
		}
		// Mid-teleport: nothing to click until we land at home (0.4.6 re-planned from the old room
		// during the animation, clicked a doorway there and waited 6 s).
		if (System.currentTimeMillis() < teleportingUntilMs && !maze.inStartRoom())
		{
			lastAction = "teleporting home";
			return;
		}
		net.runelite.client.plugins.microbot.cgprep.actions.BrewChain chain = brewChain;
		if (chain != null && chain.note != null)
		{
			act(chain.note);
			chain.note = null;
		}
		boolean brewing = chain != null && chain.active();
		if (brewing && prayer.attackersNear > 0)
		{
			chain.stop(); // attacks interrupt it every time; deal with the attacker first
			act("brew chain: stopped, something is attacking us");
			brewing = false;
		}
		if (!brewing && maybeDrink(l, needs))
		{
			return;
		}
		keepRunOn();
		// The potion chain owns these few ticks (0.4.6: its click cancelled the walk to a doorway
		// clicked right after, and the loop stood there 10 s waiting for the room to light up).
		// Grym and dust repeat by themselves until another click interrupts them, so no world
		// clicks until it's done; it ends itself when finished or stalled.
		if (brewing)
		{
			lastAction = "brewing (the potion chain is running)";
			return;
		}
		// Loot: after a kill, and any time something we can use is lying within 3 tiles.
		if (!pendingLoot && LootAction.nearestWanted(mode(), l, lootNeeds, 3) != null)
		{
			pendingLoot = true;
			lootTile = null;
			lootUntilMs = 0;
		}
		// Another demi-boss already on us (0.3.23: the Bear right after the Dark Beast): fight first,
		// the pile stays on the floor for a minute.
		boolean demiOnUs = g.type == PrepGoal.Type.FIGHT && g.monster != Monster.WEAK && g.monster != Monster.STRONG
			&& prayer.attackersNear > 0;
		if (pendingLoot && !demiOnUs)
		{
			ActionResult r = loot.step(mode(), l, lootNeeds, lootTile);
			if (loot.droppedFish > 0)
			{
				act("loot: dropped " + loot.droppedFish + " raw paddlefish to make room for a drop");
				loot.droppedFish = 0;
			}
			if (r == ActionResult.DONE && System.currentTimeMillis() < lootUntilMs)
			{
				lastAction = "loot: waiting for the drop";
				return; // the drop appears after the death animation; keep watching the kill tile
			}
			record("loot", r);
			if (r == ActionResult.DONE || r == ActionResult.NOT_AVAILABLE)
			{
				pendingLoot = false;
				lootTile = null;
			}
			return;
		}

		if (maze.inStartRoom() && (g.type == PrepGoal.Type.CRAFT_GEAR || g.type == PrepGoal.Type.MAKE_VIALS)
			&& !net.runelite.client.plugins.microbot.util.widget.Rs2Widget.isProductionWidgetOpen() && prayer.engagedCount == 0
			&& maybeAutoRetaliateOff("at the bowl"))
		{
			return;
		}
		maybeFastChoreFirst(g, l);
		if (maybeBreakBetweenTasks(g))
		{
			return; // an antiban cooldown just started; the loop resumes when it ends
		}

		ActionResult r;
		switch (g.type)
		{
			case GET_TOOLS:
				r = station.takeTools(mode());
				break;
			case GATHER:
				if (g.groundItem)
				{
					r = pickUpFishAt(g.at);
					break;
				}
				gather.prefer = g.at;
				r = gather.step(mode(), maze, g.resource);
				if (gather.weaveNote != null)
				{
					act(gather.weaveNote);
					gather.weaveNote = null;
				}
				if (weaver != null && weaver.note != null)
				{
					act(weaver.note);
					weaver.note = null;
				}
				break;
			case FIGHT:
				CombatAction.Outcome o = combat.step(mode(), maze, g.monster);
				if (o == CombatAction.Outcome.KILLED)
				{
					boolean demi = g.monster != Monster.WEAK && g.monster != Monster.STRONG;
					if (demi)
					{
						maze.markKilled(g.monster);
					}
					pendingLoot = true;
					lootTile = combat.lastTargetTile();
					lootUntilMs = System.currentTimeMillis() + (demi ? 7000 : 2400);
					pl("LOOT", "killed " + g.monster + " at " + lootTile + ", watching for its drop");
				}
				if (o == CombatAction.Outcome.BAIL && fleeUntilMs == 0)
				{
					fleeUntilMs = System.currentTimeMillis() + 25_000;
					pl("FLEE", "fight " + g.monster + " bailed (low hp, no food): escaping home");
				}
				lastAction = "fight " + g.monster + ": " + o;
				if (combat.hideNote != null)
				{
					act(combat.hideNote);
					combat.hideNote = null;
				}
				act(lastAction);
				if (o == CombatAction.Outcome.WALKING)
				{
					maybeChoreOnTheMove(g, l, ActionResult.WALKING);
				}
				failStreak = o == CombatAction.Outcome.NO_TARGET ? failStreak + 1 : 0;
				if (failStreak >= 10 && g.monster == Monster.WEAK)
				{
					// No monsters around: wander to the nearest unexplored room so new ones load.
					explore(maze.nearestUnexploredRoom());
				}
				return;
			case EXPLORE:
				r = explore(g.room);
				trackExploreFailure(g.room, r);
				break;
			case RETURN_TO_START:
				r = returnToStart(g.finalTrip, g.teleportNow);
				break;
			case CRAFT_GEAR:
				r = station.craftNextTier(mode(), l, g.slot);
				break;
			case FILL_VIALS:
				r = station.fillAtFishingSpot(mode(), maze);
				break;
			case MAKE_CRYSTAL:
				r = station.makeAtBowl(mode(), mode().teleportCrystal);
				break;
			case MAKE_VIALS:
				r = station.makeAtBowl(mode(), mode().vial);
				if (r == ActionResult.DONE)
				{
					vialsMadeThisRun++;
				}
				break;
			case CRUSH_SPIKE:
			case DROP_TOOL:
			case DROP_SURPLUS:
			case DECANT:
			case BREW:
				r = performChore(g, l);
				break;
			case DRINK:
			{
				int[] before = potionAndVialCounts();
				r = station.drinkSmallest();
				if (r == ActionResult.CLICKED)
				{
					learnVials(before); // the dose itself is counted from the bag (observe)
				}
				break;
			}
			case COOK:
				r = station.cookAll(mode());
				break;
			case EQUIP:
				r = station.equipStep(mode());
				break;
			case HANDOVER:
			default:
				return;
		}
		record(g.type.name().toLowerCase(), r);
		if (g == sticky && (r == ActionResult.NO_PATH || r == ActionResult.NOT_AVAILABLE))
		{
			stickyFailed = true;
		}
		if ((r == ActionResult.NO_PATH || r == ActionResult.NOT_AVAILABLE || r == ActionResult.NO_REACTION) && this.station.lastApproachNote != null)
		{
			pl("WARN", g.type + ": " + this.station.lastApproachNote);
			this.station.lastApproachNote = null;
		}
		String navNote = nav.lastNote;
		if (navNote != null)
		{
			nav.lastNote = null;
			pl("WARN", g.type + " walking: " + navNote);
		}
		maybeChoreOnTheMove(g, l, r);
		boolean station = g.type == PrepGoal.Type.CRAFT_GEAR || g.type == PrepGoal.Type.BREW
			|| g.type == PrepGoal.Type.COOK || g.type == PrepGoal.Type.EQUIP || g.type == PrepGoal.Type.MAKE_VIALS
			|| g.type == PrepGoal.Type.DROP_TOOL || g.type == PrepGoal.Type.DROP_SURPLUS || g.type == PrepGoal.Type.DECANT
			|| g.type == PrepGoal.Type.MAKE_CRYSTAL;
		if (r == ActionResult.DONE || r == ActionResult.CLICKED)
		{
			if (station)
			{
				// Station work is paced per click (Pace); chain straight into the next step.
				fastFollow = true;
			}
			else
			{
				pace.reset();
				antiban.reactionGap();
			}
		}
	}

	/**
	 * Inventory chores (crush the spike, drop a finished tool, decant, brew steps) are done right
	 * after a WALKING click, while the character runs (user, 0.3.15). Item-on-item and drops don't
	 * stop movement; after clicking an OBJECT they would cancel the interaction, so only after
	 * plain walking, or once the main action is finished/blocked.
	 */
	private void maybeChoreOnTheMove(PrepGoal g, Ledger l, ActionResult r)
	{
		PrepGoal c = g.chore;
		if (c == null || l == null)
		{
			return;
		}
		boolean walking = r == ActionResult.WALKING;
		boolean idle = r == ActionResult.DONE || r == ActionResult.NOT_AVAILABLE || r == ActionResult.NO_PATH;
		boolean stationGoal = g.type != PrepGoal.Type.GATHER && g.type != PrepGoal.Type.EXPLORE
			&& g.type != PrepGoal.Type.RETURN_TO_START && g.type != PrepGoal.Type.FIGHT;
		// At a station only while walking up to it: between bowl crafts an inventory click would
		// close the make menu.
		if (!walking && (!idle || stationGoal))
		{
			return;
		}
		if (brewChain != null && brewChain.active())
		{
			return; // the potion chain owns the inventory for these few ticks
		}
		if (c.type == PrepGoal.Type.BREW)
		{
			if (g.type == PrepGoal.Type.FIGHT)
			{
				return; // inventory clicks on arrival would cancel the first attacks
			}
			// The whole chain in one go, on a run long enough for it (user, 0.3.33).
			maybeBrewChain(g, l, false, "while running");
			return;
		}
		// Chores are inventory clicks only: the brew chore never walks to the pump (that's a goal).
		ActionResult cr = c.type == PrepGoal.Type.EQUIP ? station.equipStep(mode()) : performChore(c, l);
		act("on the move: " + c.type.name().toLowerCase() + (c.resource != null ? " " + c.resource.label : "")
			+ " -> " + cr + (walking ? " (while running)" : ""));
	}

	/** One inventory chore. */
	private ActionResult performChore(PrepGoal g, Ledger l)
	{
		ActionResult r;
		switch (g.type)
		{
			case CRUSH_SPIKE:
				r = station.crushSpike(mode());
				if (r != ActionResult.CLICKED)
				{
					dropSentMs.put(net.runelite.client.plugins.microbot.cgprep.data.Resource.SPIKE, System.currentTimeMillis());
				}
				return r;
			case DROP_TOOL:
			{
				int tool = g.resource == net.runelite.client.plugins.microbot.cgprep.data.Resource.ORE ? mode().pickaxe
					: g.resource == net.runelite.client.plugins.microbot.cgprep.data.Resource.BARK ? mode().axe
					: g.resource == net.runelite.client.plugins.microbot.cgprep.data.Resource.SHARDS ? CgIds.PESTLE : mode().harpoon;
				if (Rs2Inventory.count(tool) == 0)
				{
					return ActionResult.DONE;
				}
				// Fire and move on (user): the ledger confirms the drop next loop; a missed one is retried.
				pace.before(Pace.Kind.INVENTORY);
				boolean sent = Rs2Inventory.interact(tool, "Drop");
				pace.after(Pace.Kind.INVENTORY);
				if (sent)
				{
					dropSentMs.put(g.resource, System.currentTimeMillis());
					droppedTools.add(g.resource);
				}
				return sent ? ActionResult.CLICKED : ActionResult.NO_REACTION;
			}
			case DROP_SURPLUS:
			{
				int item = mode().itemFor(g.resource);
				if (item <= 0 || Rs2Inventory.count(item) == 0)
				{
					return ActionResult.DONE;
				}
				pace.before(Pace.Kind.INVENTORY);
				boolean sent = Rs2Inventory.interact(item, "Drop");
				pace.after(Pace.Kind.INVENTORY);
				if (sent)
				{
					dropSentMs.put(g.resource, System.currentTimeMillis());
				}
				return sent ? ActionResult.CLICKED : ActionResult.NO_REACTION;
			}
			case DECANT:
				return station.decant();
			case BREW:
				if (brewChain != null && brewChain.active())
				{
					return ActionResult.IN_PROGRESS;
				}
				// Inventory steps as one back-to-back chain (the pump / bowl steps stay single).
				if (!(maze.inStartRoom() && l.emptyVials > 0) && maybeBrewChain(g, l, true, g.why))
				{
					return ActionResult.CLICKED;
				}
				r = station.brewStep(mode(), l, maze.inStartRoom());
				if (!maze.inStartRoom() && r != ActionResult.CLICKED && r != ActionResult.IN_PROGRESS)
				{
					// A field brew step that can't run isn't retried for 15 s (0.3.11 looped "crush
					// shards" with a full inventory for half a minute).
					fieldBrewBlockedUntilMs = System.currentTimeMillis() + 15_000;
				}
				return r;
			default:
				return ActionResult.NOT_AVAILABLE;
		}
	}

	/**
	 * Sip an Egniol: planned sips (decant method: the 2 spare doses) once prayer is at or below 50%,
	 * or an emergency sip below 15% with something on us. An emergency sip beyond the allowance
	 * lowers the brews counted as done, so the planner adds a brew to make up for it.
	 */
	private boolean maybeDrink(Ledger l, Needs n)
	{
		if (l == null || n == null || l.potionDoses == 0)
		{
			return false;
		}
		long now = System.currentTimeMillis();
		int pp = pct(net.runelite.api.Skill.PRAYER);
		boolean planned = pp <= 50 && dosesDrunk < n.prepDoseAllowance;
		boolean emergency = pp <= 15 && prayer.attackersNear > 0;
		if ((!planned && !emergency) || now - lastDrinkMs < 1800)
		{
			return false;
		}
		lastDrinkMs = now;
		int[] before = potionAndVialCounts();
		ActionResult r = station.drinkSmallest();
		if (r == ActionResult.CLICKED)
		{
			learnVials(before); // the dose itself is counted from the bag (observe)
		}
		lastAction = "drink egniol at " + pp + "% prayer (" + (planned ? "planned sip " + dosesDrunk + "/" + n.prepDoseAllowance : "emergency") + "): " + r;
		act(lastAction);
		return r == ActionResult.CLICKED;
	}

	/** "EXPLORE room 2,2 (why with numbers)" -> head kept exact, numbers in the reason ignored. */
	/** What comes after the node being harvested (decided while harvesting it), and the bag we expect then. */
	private PrepGoal queued;
	private String queuedSig;
	private net.runelite.client.plugins.microbot.cgprep.data.Resource queuedRes;
	private String nextSig;
	private String lastNextStr = "";

	/**
	 * Plan the step after the current node: the bag as it will be once the node is done (its
	 * remaining yield, or as much as is still needed) and that node gone. Null when not harvesting.
	 */
	private PrepGoal lookahead(PrepPlanner.Inputs in, Ledger l, int potions, boolean decant)
	{
		net.runelite.client.plugins.microbot.cgprep.data.Resource r = in.committedGather;
		GauntletMaze.Node hn = gather.harvestingNode(maze);
		if (r == null || hn == null || hn.type != r || in.flee)
		{
			return null;
		}
		Needs g = in.gather != null ? in.gather : in.needs;
		int left = gather.leftOnNode();
		int gain = Math.min(left, Math.max(0, g.get(r)));
		if (gain <= 0)
		{
			return null;
		}
		Ledger p = l.copy();
		p.have.merge(r, gain, Integer::sum);
		if (!PrepPlanner.stackable(r))
		{
			p.freeSlots = Math.max(0, p.freeSlots - gain);
		}
		else if (l.get(r) == 0)
		{
			p.freeSlots = Math.max(0, p.freeSlots - 1);
		}
		PrepPlanner.Inputs pi = in.copy();
		pi.ledger = p;
		pi.needs = Needs.compute(p, net.runelite.client.plugins.microbot.cgprep.plan.PrepPlanner.profileNow(p, config.profile()), net.runelite.client.plugins.microbot.cgprep.plan.PrepPlanner.fishTargetNow(p, config.profile(), config.fishTarget(), config.fishMin()), config.shardBuffer(), potions, decant, vialsSmash());
		pi.committedGather = null;
		pi.projection = true;
		if (gain >= left)
		{
			maze.pretendGone(hn.raw);
		}
		try
		{
			PrepGoal q = planner.decide(pi);
			nextSig = p.signature();
			return q;
		}
		finally
		{
			maze.pretendGone(null);
		}
	}

	/**
	 * The node we queued for is done: take the queued step as long as the bag came out as
	 * projected (a monster drop that changes the plan, e.g. the last grym leaf, forces a re-plan,
	 * user). Anything urgent (a fight, fleeing, going home) always wins over the queue.
	 */
	private PrepGoal handOff(PrepGoal fresh, Ledger l)
	{
		PrepGoal s = sticky;
		if (s != null)
		{
			// Keep the queued step until it's under way (a yield from the new node / the room lit),
			// so a near-tie in the ranking can't flip it on the way there.
			boolean movement = fresh != null && (fresh.type == PrepGoal.Type.GATHER || fresh.type == PrepGoal.Type.EXPLORE);
			boolean underWay = s.type == PrepGoal.Type.GATHER ? gather.harvestingNode(maze) != null
				: s.room != null && maze.found(s.room[0], s.room[1]);
			if (movement && !underWay && !stickyFailed && l.signature().equals(stickySig)
				&& System.currentTimeMillis() < stickyUntilMs)
			{
				s.chore = fresh.chore;
				return s;
			}
			sticky = null;
		}
		PrepGoal q = queued;
		if (q == null)
		{
			return fresh;
		}
		boolean stillOnIt = fresh != null && fresh.type == PrepGoal.Type.GATHER && fresh.resource == queuedRes
			&& gather.harvestingNode(maze) != null;
		if (stillOnIt)
		{
			return fresh; // not done yet; the queue is refreshed every loop
		}
		queued = null;
		boolean movement = fresh != null && (fresh.type == PrepGoal.Type.GATHER || fresh.type == PrepGoal.Type.EXPLORE)
			&& (q.type == PrepGoal.Type.GATHER || q.type == PrepGoal.Type.EXPLORE);
		if (!movement)
		{
			return fresh;
		}
		if (!l.signature().equals(queuedSig))
		{
			if (!goalKey(String.valueOf(fresh)).equals(goalKey(String.valueOf(q))))
			{
				pl("REPLAN", "the bag isn't what the queue expected (a drop or a chore changed it): " + fresh + " instead of " + q);
			}
			return fresh;
		}
		q.chore = fresh.chore; // chores are re-derived from the real bag
		sticky = q;
		stickySig = queuedSig;
		stickyUntilMs = System.currentTimeMillis() + 10_000;
		stickyFailed = false;
		return q;
	}

	/** Vials made at the bowl this run (2 during prep, user 0.3.26). */
	private volatile int vialsMadeThisRun;
	/** Tools we dropped this run (never fetched again from the storage). */
	private final java.util.Set<net.runelite.client.plugins.microbot.cgprep.data.Resource> droppedTools = java.util.EnumSet.noneOf(net.runelite.client.plugins.microbot.cgprep.data.Resource.class);
	/** The doorway node explore() is heading for, and its room (x * 100 + z). */
	private volatile WorldPoint exploreDoor;
	private volatile int exploreDoorRoom = -1;
	private WorldPoint routeFor;
	private long routeAtMs;

	/**
	 * Where the current movement goal is heading (the doorway being lit, or home), with walking
	 * distances from there, so the planner can judge nodes by the detour they add (user, 0.3.27).
	 * Kept while we step aside for a node (GATHER), cleared for anything else.
	 */
	private void updateRoute(PrepGoal g, WorldPoint hint)
	{
		if (hint != null && g != null && maze.inGauntlet() && maze.grid().isCalibrated() && g.type != PrepGoal.Type.HANDOVER)
		{
			// The planner's own "where next" (the doorway into the next route room, or home).
			setRouteTarget(hint);
			return;
		}
		if (g == null || !maze.inGauntlet() || !maze.grid().isCalibrated())
		{
			maze.setRoute(null, null);
			routeFor = null;
			return;
		}
		WorldPoint target = null;
		if (g.type == PrepGoal.Type.EXPLORE && g.room != null && exploreDoor != null && exploreDoorRoom == g.room[0] * 100 + g.room[1])
		{
			target = exploreDoor;
		}
		else if (g.type == PrepGoal.Type.RETURN_TO_START && !g.teleportNow && !g.finalTrip && maze.startRoomX() >= 0)
		{
			WorldPoint me = maze.playerRaw();
			target = maze.grid().centreOf(maze.startRoomX(), maze.startRoomZ(), me == null ? 0 : me.getPlane());
		}
		else if (g.type == PrepGoal.Type.GATHER)
		{
			return; // stepping aside for a node: keep the route we were on
		}
		if (target == null)
		{
			if (g.type != PrepGoal.Type.EXPLORE)
			{
				maze.setRoute(null, null);
				routeFor = null;
			}
			return;
		}
		WorldPoint me = maze.playerRaw();
		if (me != null && InstanceNav.chebyshev(me, target) <= 2)
		{
			maze.setRoute(null, null); // there: nothing left to detour from
			routeFor = null;
			return;
		}
		setRouteTarget(target);
	}

	private void setRouteTarget(WorldPoint target)
	{
		WorldPoint me = maze.playerRaw();
		if (me != null && InstanceNav.chebyshev(me, target) <= 2)
		{
			maze.setRoute(null, null);
			routeFor = null;
			return;
		}
		// Recompute for a new target, and every 3 s (newly lit rooms join the scene).
		if (!target.equals(routeFor) || System.currentTimeMillis() - routeAtMs > 3000)
		{
			routeFor = target;
			routeAtMs = System.currentTimeMillis();
			maze.setRoute(target, nav.distanceFieldFrom(target));
		}
	}

	private String fastChoreGoal = "";

	/**
	 * Quick inventory chores (wear new gear, drop a finished tool / leftovers) as we set off, not
	 * only after a minimap click: since 0.3.27 nodes and doors are clicked directly, so there was
	 * often no "walking" result to hang them on (0.3.28 wore its armour 30 s after crafting it).
	 * Done when a movement goal starts, or while standing (not mid-harvest), then the goal's click.
	 */
	private void maybeFastChoreFirst(PrepGoal g, Ledger l)
	{
		PrepGoal c = g.chore;
		if (c == null || l == null || (brewChain != null && brewChain.active()))
		{
			return;
		}
		boolean fast = c.type == PrepGoal.Type.EQUIP || c.type == PrepGoal.Type.DROP_TOOL || c.type == PrepGoal.Type.DROP_SURPLUS;
		boolean movement = g.type == PrepGoal.Type.GATHER || g.type == PrepGoal.Type.EXPLORE
			|| g.type == PrepGoal.Type.RETURN_TO_START || g.type == PrepGoal.Type.FILL_VIALS;
		if (!fast || !movement || (g.type == PrepGoal.Type.GATHER && gather.harvestingNode(maze) != null))
		{
			return;
		}
		String key = goalKey(String.valueOf(g));
		boolean newGoal = !key.equals(fastChoreGoal);
		if (!newGoal && net.runelite.client.plugins.microbot.util.player.Rs2Player.isMoving())
		{
			return; // already on the way to something we clicked: the walking click will carry it
		}
		fastChoreGoal = key;
		ActionResult cr = c.type == PrepGoal.Type.EQUIP ? station.equipStep(mode()) : performChore(c, l);
		act("setting off: " + c.type.name().toLowerCase() + (c.resource != null ? " " + c.resource.label : "") + " -> " + cr);
	}

	private boolean retaliateChecked;

	/**
	 * Auto-retaliate off, once per session (setting "autoRetaliateOff"): with it on, the character
	 * stops to fight anything that hits it whenever it's idle for a moment (0.3.26: waited for a
	 * spider on the way home). Returns true when it clicked something this call.
	 */
	private boolean maybeAutoRetaliateOff(String where)
	{
		if (retaliateChecked || !config.autoRetaliateOff() || config.dryRun() || !Microbot.isLoggedIn())
		{
			return false;
		}
		retaliateChecked = true;
		if (Microbot.getVarbitPlayerValue(172) != 0)
		{
			return false; // already off (varp 172: 0 = on, 1 = off)
		}
		boolean ok = net.runelite.client.plugins.microbot.util.combat.Rs2Combat.setAutoRetaliate(false);
		net.runelite.client.plugins.microbot.util.tabs.Rs2Tab.switchTo(net.runelite.client.plugins.microbot.globval.enums.InterfaceTab.INVENTORY);
		pl(ok ? "ACT" : "WARN", ok ? "auto-retaliate turned off (" + where + ")"
			: "auto-retaliate is ON and couldn't be turned off: please switch it off in the Combat tab (the character stops to fight whatever hits it)");
		return true;
	}

	private PrepGoal sticky;
	private String stickySig;
	private long stickyUntilMs;
	/** The sticky goal's action failed (no path / nothing there): drop it, re-plan. */
	private volatile boolean stickyFailed;

	private static String goalKey(String g)
	{
		if (g == null)
		{
			return "";
		}
		int p = g.indexOf(" (");
		return p < 0 ? g : g.substring(0, p) + g.substring(p).replaceAll("\\d+(\\.\\d+)?", "#");
	}

	private boolean wearingAny(net.runelite.client.plugins.microbot.cgprep.data.GearSlot slot)
	{
		for (int id : slot.ids(mode()))
		{
			if (Rs2Equipment.isWearing(id))
			{
				return true;
			}
		}
		return false;
	}

	private long lastRunToggleMs;
	private boolean runEmptyLogged;

	/** Run back on whenever it's off with energy to spend; log when energy runs out (walking = half speed). */
	private void keepRunOn()
	{
		net.runelite.client.plugins.microbot.cgprep.fight.WorldScan scan = net.runelite.client.plugins.microbot.cgprep.fight.WorldScan.latest();
		if (scan == null)
		{
			return;
		}
		long now = System.currentTimeMillis();
		if (!scan.runOn && scan.runEnergy >= 3 && now - lastRunToggleMs > 5000)
		{
			lastRunToggleMs = now;
			net.runelite.client.plugins.microbot.util.player.Rs2Player.toggleRunEnergy(true);
			act("run on (energy " + scan.runEnergy + "%)");
		}
		if (scan.runEnergy <= 1 && !runEmptyLogged)
		{
			runEmptyLogged = true;
			pl("RUN", "run energy empty at t-" + secondsLeft + "s: walking at half speed until it regenerates");
		}
		else if (scan.runEnergy >= 20)
		{
			runEmptyLogged = false;
		}
	}

	/** Set once vials were seen to vanish (or not); falls back to the stored setting. */
	private volatile Boolean vialsSmashSeen;

	private boolean vialsSmash()
	{
		Boolean seen = vialsSmashSeen;
		return seen != null ? seen : config.vialsSmash();
	}

	/** {Egniol items, empty vials} in the bag. */
	private int[] potionAndVialCounts()
	{
		int pots = 0;
		for (int id : CgIds.EGNIOL_ALL)
		{
			pots += Rs2Inventory.count(id);
		}
		return new int[]{pots, Rs2Inventory.count(mode().vial) + Rs2Inventory.count(CgIds.VIAL_EMPTY_ALT)};
	}

	/**
	 * After a drink: if a potion was drunk empty, did an empty vial appear? Remember the answer
	 * (setting "vialsSmash"), it decides whether the last brew can reuse that vial.
	 */
	private void learnVials(int[] before)
	{
		if (!Global.sleepUntil(() -> potionAndVialCounts()[0] < before[0], 600))
		{
			return; // a dose was drunk but the potion isn't empty yet: nothing to learn
		}
		Global.sleepUntil(() -> potionAndVialCounts()[1] > before[1], 1200);
		boolean smashed = potionAndVialCounts()[1] <= before[1];
		if (vialsSmashSeen == null || vialsSmashSeen != smashed)
		{
			vialsSmashSeen = smashed;
			pl("VIALS", smashed ? "the emptied Egniol left no vial: one vial per potion from now on (setting vialsSmash=true)"
				: "the emptied Egniol left a vial: the last brew can reuse it (setting vialsSmash=false)");
			java.util.function.BiConsumer<String, String> save = saveSetting;
			if (save != null)
			{
				save.accept("vialsSmash", String.valueOf(smashed));
			}
		}
	}

	private static int pct(net.runelite.api.Skill s)
	{
		net.runelite.client.plugins.microbot.cgprep.fight.WorldScan scan = net.runelite.client.plugins.microbot.cgprep.fight.WorldScan.latest();
		if (scan != null && System.currentTimeMillis() - scan.atMs < 1000)
		{
			return s == net.runelite.api.Skill.PRAYER ? scan.prayerPct() : scan.hpPct();
		}
		int real = Math.max(1, net.runelite.client.plugins.microbot.util.player.Rs2Player.getRealSkillLevel(s));
		return net.runelite.client.plugins.microbot.util.player.Rs2Player.getBoostedSkillLevel(s) * 100 / real;
	}

	/**
	 * Eat one cooked paddlefish when HP is below the setting (default 70%), at most once per eat
	 * delay (3 ticks). Cooked fish eaten now raises the fish still needed, so the planner gathers
	 * the replacement on its own.
	 */
	private boolean maybeEat()
	{
		int hp = net.runelite.client.plugins.microbot.util.player.Rs2Player.getBoostedSkillLevel(net.runelite.api.Skill.HITPOINTS);
		int pct = pct(net.runelite.api.Skill.HITPOINTS);
		long now = System.currentTimeMillis();
		// At home the fish are the boss's food and every bite breaks a menu chain (0.3.29's final
		// prep went bowl -> range -> eat -> bowl): only below 35%, and never with a make menu open.
		int below = maze.inStartRoom() ? Math.min(35, config.eatBelowPercent()) : config.eatBelowPercent();
		if (pct >= below || now - lastEatMs < 1800 || !Rs2Inventory.contains(CgIds.PADDLEFISH)
			|| (maze.inStartRoom() && net.runelite.client.plugins.microbot.util.widget.Rs2Widget.isProductionWidgetOpen()))
		{
			return false;
		}
		lastEatMs = now;
		pace.before(Pace.Kind.INVENTORY);
		boolean sent = Rs2Inventory.interact(CgIds.PADDLEFISH, "Eat");
		pace.after(Pace.Kind.INVENTORY);
		if (!sent)
		{
			return false;
		}
		boolean ate = Global.sleepUntil(() -> net.runelite.client.plugins.microbot.util.player.Rs2Player
			.getBoostedSkillLevel(net.runelite.api.Skill.HITPOINTS) > hp, 1500);
		lastAction = "eat at " + pct + "% hp: " + (ate ? "ate" : "no change");
		act(lastAction);
		return true;
	}

	/**
	 * Antiban cooldowns go at natural break points: when the start-room work moves from one kind
	 * of task to another (crafting -> potion -> cooking), never in the middle of a menu chain.
	 */
	private boolean maybeBreakBetweenTasks(PrepGoal g)
	{
		boolean station = g.type == PrepGoal.Type.CRAFT_GEAR || g.type == PrepGoal.Type.BREW
			|| g.type == PrepGoal.Type.COOK || g.type == PrepGoal.Type.EQUIP || g.type == PrepGoal.Type.MAKE_VIALS
			|| g.type == PrepGoal.Type.DROP_TOOL;
		if (!station)
		{
			lastStationType = null;
			return false;
		}
		PrepGoal.Type prev = lastStationType;
		lastStationType = g.type;
		boolean timeToSpare = secondsLeft < 0 || secondsLeft > config.handoverReserve() + 45;
		if (prev != null && prev != g.type && maze.inStartRoom() && timeToSpare)
		{
			antiban.maybeCooldown(prev.name().toLowerCase() + " -> " + g.type.name().toLowerCase());
			pace.reset();
			return antiban.holding();
		}
		return false;
	}

	/** Why the bot is currently holding off (for the overlay), or null. */
	volatile String waitingReason;
	private long lastWaitLogMs;

	/**
	 * Microbot's Script.run(): false while "pause all" / an antiban cooldown is active, a blocking
	 * event is handled, or InputArbiter thinks a human is using the mouse/keyboard (any real key or
	 * button held, or real input in the last 1.8 s). Moving your mouse therefore pauses the bot.
	 *
	 * 0.3.0 test: the bot sat idle in the Gauntlet. A key InputArbiter believes is still held (e.g.
	 * the Ctrl/Shift of our own Ctrl+Shift+P, release missed) blocks it forever. So: say WHY we're
	 * waiting (log + overlay), and if keys look held with no real input for 4 s, clear them.
	 */
	private boolean mayAct()
	{
		if (super.run())
		{
			waitingReason = null;
			return true;
		}
		String why;
		if (Microbot.pauseAllScripts.get() && Microbot.cantReachTarget)
		{
			// Microbot's can't-reach recovery paused everything after its own retries. Our planner
			// copes with unreachable targets, so clear it and re-plan rather than sit forever.
			Microbot.cantReachTarget = false;
			Microbot.cantReachTargetRetries = 0;
			Microbot.pauseAllScripts.set(false);
			PrepGoal g = goal;
			if (g != null && g.type == PrepGoal.Type.EXPLORE && g.room != null)
			{
				maze.blockExplore(g.room[0], g.room[1], 60_000);
			}
			pl("WAIT", "Microbot paused all scripts after 'can't reach'; cleared it and re-planning (goal " + g + ")");
			return false;
		}
		if (Microbot.pauseAllScripts.get())
		{
			why = "Microbot pause-all / antiban cooldown (check Microbot's pause button)";
		}
		else if (net.runelite.client.plugins.microbot.util.input.InputArbiter.isHuman())
		{
			long quiet = net.runelite.client.plugins.microbot.util.input.InputArbiter.millisSinceRealActivity();
			if (net.runelite.client.plugins.microbot.util.input.InputArbiter.isRealButtonOrKeyDown())
			{
				why = "a key or mouse button looks held down: let go of it (clears itself in a few seconds)";
				if (quiet > 4000)
				{
					net.runelite.client.plugins.microbot.util.input.InputArbiter.onFocusLost(); // clears the held sets
					pl("WAIT", why + " (keys=" + net.runelite.client.plugins.microbot.util.input.InputArbiter.realKeysDown()
						+ " buttons=" + net.runelite.client.plugins.microbot.util.input.InputArbiter.realButtonsDown()
						+ ") with no input for " + quiet + " ms: treating as stuck, cleared");
					return false; // next loop re-checks after the 1.8 s idle window
				}
			}
			else
			{
				why = "you're using the mouse/keyboard (" + quiet + " ms ago); resumes 1.8 s after you stop";
			}
		}
		else
		{
			why = "blocking event / not ready";
		}
		waitingReason = why;
		if (System.currentTimeMillis() - lastWaitLogMs > 5000)
		{
			lastWaitLogMs = System.currentTimeMillis();
			pl("WAIT", why);
		}
		return false;
	}

	/**
	 * Gear lying at our feet (crafted with a full inventory drops it, 0.3.10) is still ours: count
	 * its tier so the planner doesn't re-craft it (that sent the bot to the tool storage for new
	 * tools), and let looting pick it up once there's a free slot.
	 */
	private void countGearOnGround(Ledger l)
	{
		java.util.List<Integer> all = new java.util.ArrayList<>();
		for (net.runelite.client.plugins.microbot.cgprep.data.GearSlot s : net.runelite.client.plugins.microbot.cgprep.data.GearSlot.values())
		{
			for (int id : s.ids(mode()))
			{
				all.add(id);
			}
		}
		java.util.Set<Integer> onGround = new java.util.HashSet<>();
		for (net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel it : Microbot.getRs2TileItemCache().query()
			.withIds(all.stream().mapToInt(Integer::intValue).toArray()).within(6).toList())
		{
			onGround.add(it.getId());
		}
		if (onGround.isEmpty())
		{
			return;
		}
		for (net.runelite.client.plugins.microbot.cgprep.data.GearSlot s : net.runelite.client.plugins.microbot.cgprep.data.GearSlot.values())
		{
			int[] ids = s.ids(mode());
			for (int t = ids.length - 1; t >= 0; t--)
			{
				if (onGround.contains(ids[t]) && t + 1 > l.tier.getOrDefault(s, 0))
				{
					l.tier.put(s, t + 1);
					l.gearOnGround = true;
					break;
				}
			}
		}
	}

	private String exploreFailKey = "";
	private int exploreFails;

	/** Two failed attempts at the same room (no path / no reaction) -> leave it alone for a minute. */
	private void trackExploreFailure(int[] room, ActionResult r)
	{
		if (room == null)
		{
			return;
		}
		String key = room[0] + "," + room[1];
		if (r == ActionResult.NO_PATH || r == ActionResult.NO_REACTION || r == ActionResult.NOT_AVAILABLE)
		{
			exploreFails = key.equals(exploreFailKey) ? exploreFails + 1 : 1;
			exploreFailKey = key;
			if (exploreFails >= 2)
			{
				maze.blockExplore(room[0], room[1], 60_000);
				pl("EXPLORE", "giving up on room " + key + " for 60 s after " + exploreFails + " failures (" + r + ")");
				exploreFails = 0;
			}
		}
		else if (r == ActionResult.DONE)
		{
			exploreFails = 0;
		}
	}

	private ActionResult explore(int[] room)
	{
		if (room == null)
		{
			return ActionResult.NOT_AVAILABLE;
		}
		WorldPoint me = maze.playerRaw();
		WorldPoint target = maze.grid().centreOf(room[0], room[1], me == null ? 0 : me.getPlane());

		// Recon: unlit rooms don't exist in the scene until a "Node" next to the doorway is lit.
		// Light an unlit node that sits IN A LIT, NON-BOSS ROOM NEXT TO the target (its doorway into
		// the target). 0.2.9 bug: "closest to the target's centre" picked the boss room's node on
		// the shared wall; the player can't stand there and got stuck.
		if (target != null && me != null)
		{
			final int tx = room[0];
			final int tz = room[1];
			final WorldPoint tgtCentre = target;
			final WorldPoint meNow = me;
			final WorldPoint bossCentre = maze.grid().centreOf(CgIds.BOSS_ROOM_X, CgIds.BOSS_ROOM_Z, me.getPlane());
			Rs2TileObjectModel node = Microbot.getRs2TileObjectCache().query()
				.where(o -> CgIds.NODE_UNLIT.contains(o.getId()) && o.getWorldLocation() != null)
				.toList().stream()
				.filter(o ->
				{
					WorldPoint p = o.getWorldLocation();
					// Never the boss room's own doorway nodes (inside it or on its walls).
					if (bossCentre != null && Math.abs(p.getX() - bossCentre.getX()) <= CgIds.ROOM_SIZE_TILES / 2 + 1
						&& Math.abs(p.getY() - bossCentre.getY()) <= CgIds.ROOM_SIZE_TILES / 2 + 1)
					{
						return false;
					}
					// A doorway node can sit on either side of the boundary: allow the target room's
					// edge tiles and lit rooms next to it (0.3.0/0.3.1 only allowed the latter and
					// so skipped the real doorway and clicked unreachable ones).
					int[] c = maze.grid().roomOf(p);
					if (c == null)
					{
						return false;
					}
					boolean isTarget = c[0] == tx && c[1] == tz;
					boolean litNeighbour = maze.found(c[0], c[1]) && Math.abs(c[0] - tx) + Math.abs(c[1] - tz) == 1;
					return isTarget || litNeighbour;
				})
				// A doorway INTO the target sits on its boundary (within ~9 tiles of its centre). Of
				// those, the one nearest to US (0.3.5: "nearest the target centre" sent you to a far
				// torch on the other side when a nearer doorway existed).
				.filter(o -> o.getWorldLocation().distanceTo2D(tgtCentre) <= CgIds.ROOM_SIZE_TILES / 2 + 2)
				.min(java.util.Comparator.<Rs2TileObjectModel>comparingInt(o -> o.getWorldLocation().distanceTo2D(meNow))
					.thenComparingInt(o -> o.getWorldLocation().distanceTo2D(tgtCentre)))
				.orElse(null);
			if (node != null)
			{
				WorldPoint at = node.getWorldLocation();
				exploreDoor = at;
				exploreDoorRoom = tx * 100 + tz;
				// Click the doorway node itself whenever it's on screen within ~a room (user, 0.3.27:
				// the first click of a run was a minimap click 2 tiles ahead, then the door; a
				// player clicks the door). The server walks the rest. Farther: minimap hops.
				int far = InstanceNav.chebyshev(me, at);
				if (far > 10 && (far > 22 || !net.runelite.client.plugins.microbot.cgprep.actions.Aim.onScreen(node.getLocalLocation())))
				{
					InstanceNav.Result res = nav.step(at, 10);
					return res == InstanceNav.Result.NO_PATH ? ActionResult.NO_PATH : ActionResult.WALKING;
				}
				int varbit = CgIds.roomFoundVarbit(room[0], room[1]);
				// Microbot's "can't reach" recovery is global and sticky: stale state from an earlier
				// failure made it retry and then PAUSE ALL SCRIPTS ("Your bot tried to interact with an
				// object for N times..."). We handle unreachable doorways ourselves (blacklist + re-plan).
				Microbot.cantReachTarget = false;
				Microbot.cantReachTargetRetries = 0;
				if (!net.runelite.client.plugins.microbot.cgprep.actions.Aim.onScreen(node.getLocalLocation()))
				{
					nav.step(at, 3); // still off screen after the camera turn: get closer first
					return ActionResult.WALKING;
				}
				if (!node.click(CgIds.NODE_OPTION))
				{
					return ActionResult.NO_REACTION;
				}
				// The walk to a doorway clicked from up to 22 tiles away takes longer than 4.5 s
				// (0.3.28: "explore: NO_REACTION" then a second click, 5 s each time).
				// Stops early once we've stopped walking (an interrupted walk is re-clicked at once).
				boolean lit = net.runelite.client.plugins.microbot.cgprep.actions.Waits.untilOrStopped(
					() -> Microbot.getVarbitValue(varbit) != 0 || Microbot.cantReachTarget, 2500 + 350 * far)
					&& Microbot.getVarbitValue(varbit) != 0;
				if (Microbot.cantReachTarget)
				{
					Microbot.cantReachTarget = false;
					Microbot.cantReachTargetRetries = 0;
					pl("EXPLORE", "can't reach the doorway node at " + at + " for room " + room[0] + "," + room[1]);
					return ActionResult.NO_PATH;
				}
				return lit ? ActionResult.DONE : ActionResult.NO_REACTION;
			}
		}
		if (target == null && me != null)
		{
			// Grid not calibrated yet: head one room-width in the right direction.
			int dx = Integer.signum(room[0] - maze.roomX()) * CgIds.ROOM_SIZE_TILES;
			int dz = Integer.signum(room[1] - maze.roomZ()) * CgIds.ROOM_SIZE_TILES;
			target = new WorldPoint(me.getX() + dx, me.getY() + dz, me.getPlane());
		}
		if (target == null)
		{
			return ActionResult.NOT_AVAILABLE;
		}
		InstanceNav.Result res = nav.step(target, 3);
		return res == InstanceNav.Result.NO_PATH ? ActionResult.NO_PATH
			: res == InstanceNav.Result.ARRIVED ? ActionResult.DONE : ActionResult.WALKING;
	}

	private ActionResult returnToStart(boolean finalTrip, boolean teleportNow)
	{
		// Teleport crystal (user, 2026-10-28): always for the final trip home; mid-run only for the
		// "bag full, fish near home" trip (teleportNow) and escapes. Other trips home walk, unless a
		// spare crystal (looted) is in the bag and home is 3+ rooms away.
		int dist = maze.roomX() < 0 || maze.startRoomX() < 0 ? 0
			: net.runelite.client.plugins.microbot.cgprep.plan.ExplorePlanner.roomDistance(maze, maze.startRoomX(), maze.startRoomZ());
		int crystals = Rs2Inventory.count(mode().teleportCrystal);
		boolean fleeing = System.currentTimeMillis() < fleeUntilMs;
		if (crystals > 0 && !maze.inStartRoom() && (finalTrip || teleportNow || fleeing || (crystals >= 2 && dist >= 3)))
		{
			ActionResult t = station.teleportToStart(mode());
			if (t == ActionResult.CLICKED)
			{
				teleportingUntilMs = System.currentTimeMillis() + 3600;
			}
			return t;
		}
		// Done only when we're actually IN the start room. 0.3.9: "within 4 tiles of the bowl" was
		// true from the neighbouring room on the other side of the wall, so it stood there forever.
		if (maze.inStartRoom())
		{
			return ActionResult.DONE;
		}
		WorldPoint me = maze.playerRaw();
		WorldPoint target = maze.startRoomX() < 0 ? null
			: maze.grid().centreOf(maze.startRoomX(), maze.startRoomZ(), me == null ? 0 : me.getPlane());
		if (target == null)
		{
			Rs2TileObjectModel bowl = Microbot.getRs2TileObjectCache().query().withId(mode().objBowl).first();
			target = bowl == null ? null : bowl.getWorldLocation();
		}
		if (target == null)
		{
			return ActionResult.NOT_AVAILABLE;
		}
		InstanceNav.Result res = nav.step(target, 3);
		if (res == InstanceNav.Result.ARRIVED && !maze.inStartRoom())
		{
			nav.reset();
			res = nav.step(target, 0); // "arrived" through a wall: walk the real path in
		}
		return res == InstanceNav.Result.NO_PATH ? ActionResult.NO_PATH
			: maze.inStartRoom() ? ActionResult.DONE : ActionResult.WALKING;
	}

	/** Deposit everything, then enter. TODO(recon): entrance object + option for Corrupted. */
	/**
	 * Lobby, between runs: open the Reward Chest if a reward is waiting (loop on) and keep the loot
	 * (the Gauntlet holds your items while you're inside and gives them back when you leave); bank
	 * everything at the deposit box only when fewer than "Bank loot below free slots" are free before
	 * opening the chest (or the chest won't open twice in a row with space short). Then wait 1-12 s
	 * (countdown in the overlay) and "Enter-corrupted". Same after a kill or a death.
	 */
	private void lobbySetup()
	{
		if (!mayAct())
		{
			return;
		}
		long now = System.currentTimeMillis();
		if (now - lastLobbyClickMs < 1200)
		{
			return; // the last click is still being walked to / processed
		}
		boolean reward = (config.autoLoop() || continueRequested) && Microbot.getVarbitValue(CgIds.VB_REWARD_AVAILABLE) != 0;
		int free = Rs2Inventory.emptySlotCount();
		boolean needBank = reward && (free < config.bankBelowFreeSlots() || (chestFails >= 2 && free < 8));
		if (needBank || Rs2DepositBox.isOpen())
		{
			reenterAtMs = -1;
			if (!Rs2DepositBox.isOpen())
			{
				lastLobbyClickMs = now;
				ActionResult r = clickLobbyObject(new int[]{CgIds.OBJ_LOBBY_DEPOSIT_BOX}, "Deposit");
				record("lobby: deposit box (" + free + " free slots)", r);
				if (r == ActionResult.CLICKED && !Global.sleepUntil(Rs2DepositBox::isOpen, 6000))
				{
					pl("LOBBY", "deposit box didn't open (retrying)");
				}
				return;
			}
			Rs2DepositBox.depositAll();
			boolean done = Global.sleepUntil(() -> Rs2Inventory.count() == 0, 3000);
			Rs2DepositBox.closeDepositBox();
			chestFails = 0;
			pl("LOBBY", "bag nearly full (" + free + " free): banked the loot" + (done ? "" : " (not confirmed yet, checking again)"));
			return;
		}
		if (reward)
		{
			reenterAtMs = -1;
			lastLobbyClickMs = now;
			// The loot goes straight into the bag: show it (user, 1.3.0).
			net.runelite.client.plugins.microbot.cgprep.antiban.Pace.ensureInventoryTab();
			ActionResult r = clickLobbyObject(new int[]{CgIds.OBJ_LOBBY_CHEST, CgIds.OBJ_LOBBY_CHEST_HM}, "Open");
			record("lobby: open the Reward Chest", r);
			if (r == ActionResult.CLICKED)
			{
				boolean opened = Global.sleepUntil(() -> Microbot.getVarbitValue(CgIds.VB_REWARD_AVAILABLE) == 0, 8000);
				chestFails = opened ? 0 : chestFails + 1;
				pl("LOBBY", "Reward Chest " + (opened ? "opened (" + Rs2Inventory.emptySlotCount() + " free slots now)" : "click sent, reward still waiting (retrying)"));
			}
			return;
		}
		// Ready: a human pause before going back in.
		if (reenterAtMs < 0)
		{
			reenterAtMs = now + 1000 + new java.util.Random().nextInt(11001);
			pl("LOBBY", String.format("ready (%d free slots): going back in in %.1f s", free, (reenterAtMs - now) / 1000.0));
		}
		if (now < reenterAtMs)
		{
			lastAction = "lobby: waiting " + ((reenterAtMs - now + 999) / 1000) + " s before going back in";
			return;
		}
		// A break due: take it here, between runs (1.5.0). The break handler was locked during the
		// run; it's unlocked now and gets on with it. Capped, in case it never starts one.
		if (net.runelite.client.plugins.microbot.cgprep.antiban.BreakGate.breakDue())
		{
			if (breakWaitSinceMs == 0)
			{
				breakWaitSinceMs = now;
				pl("BREAK", "a break is due: waiting in the lobby for the break handler");
			}
			if (now - breakWaitSinceMs < BREAK_WAIT_MAX_MS)
			{
				lastAction = "lobby: waiting for the break handler";
				return;
			}
			if (!breakWaitGaveUp)
			{
				breakWaitGaveUp = true;
				pl("BREAK", "WARN the break handler hasn't started its break after " + BREAK_WAIT_MAX_MS / 60000 + " min: going back in");
			}
		}
		else
		{
			breakWaitSinceMs = 0;
			breakWaitGaveUp = false;
		}
		if (enterBlocked != null)
		{
			if (enterBlockedMode == mode() && !continueRequested)
			{
				lastAction = "lobby: stopped, can't enter (see chat)";
				return;
			}
			enterBlocked = null; // the Gauntlet setting changed or Continue was pressed: try again
			enterFails = 0;
		}
		lastLobbyClickMs = now;
		ActionResult r = clickLobbyObject(new int[]{CgIds.OBJ_LOBBY_ENTRANCE}, mode().entranceOption());
		record("lobby: " + mode().entranceOption(), r);
		if (r == ActionResult.CLICKED)
		{
			boolean in = Global.sleepUntil(() -> Microbot.getVarbitValue(CgIds.VB_PLAYER_IN_GAUNTLET) != 0, 10000);
			pl("LOBBY", mode().entranceOption() + (in ? ": entered" : ": not in yet (" + (enterFails + 1) + "/" + ENTER_GIVE_UP + ")"));
			if (in)
			{
				reenterAtMs = -1;
				enterFails = 0;
			}
			else if (++enterFails >= ENTER_GIVE_UP)
			{
				blockEntering();
			}
		}
	}

	/** Several entrance clicks didn't get us in: stop and say why, once (review). */
	private void blockEntering()
	{
		boolean corrupted = mode() == CgMode.CORRUPTED;
		String msg = corrupted
			? "Auto Gauntlet: tried " + ENTER_GIVE_UP + " times and couldn't get into the Corrupted Gauntlet, so it stopped. If you haven't finished a normal Gauntlet yet, do that first (it unlocks Corrupted) or set Gauntlet to Normal. Then press "
				+ config.continueKey() + " to try again."
			: "Auto Gauntlet: can't enter the Gauntlet (" + ENTER_GIVE_UP + " tries). Check you can go in by hand, then press " + config.continueKey() + " to try again.";
		enterBlocked = corrupted ? "can't enter Corrupted (see chat)" : "can't enter the Gauntlet (see chat)";
		enterBlockedMode = mode();
		continueRequested = false;
		reenterAtMs = -1;
		pl("LOBBY", "WARN " + msg);
		Microbot.getClientThread().invoke(() ->
			Microbot.getClient().addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "", "<col=ff0000>" + msg + "</col>", null));
	}

	/** Click a lobby object (first of the ids found), turning the camera without keys; walk closer if it's off screen. */
	private ActionResult clickLobbyObject(int[] ids, String action)
	{
		Rs2TileObjectModel obj = null;
		for (int id : ids)
		{
			obj = Microbot.getRs2TileObjectCache().query().withId(id).nearest();
			if (obj != null)
			{
				break;
			}
		}
		if (obj == null)
		{
			lastAction = "lobby: object " + java.util.Arrays.toString(ids) + " not found";
			return ActionResult.NOT_AVAILABLE;
		}
		if (!net.runelite.client.plugins.microbot.cgprep.actions.Aim.onScreen(obj.getLocalLocation()))
		{
			WorldPoint at = obj.getWorldLocation();
			if (at != null)
			{
				net.runelite.client.plugins.microbot.util.walker.Rs2Walker.walkMiniMap(at);
			}
			return ActionResult.WALKING;
		}
		return obj.click(action) ? ActionResult.CLICKED : ActionResult.NO_REACTION;
	}

	private void record(String what, ActionResult r)
	{
		lastAction = what + ": " + r;
		act(lastAction);
		if (r == ActionResult.NO_REACTION || r == ActionResult.NO_PATH)
		{
			failStreak++;
			if (failStreak >= 6)
			{
				log.warn("[Auto Gauntlet] {} failing repeatedly ({}), resetting nav", what, r);
				nav.reset();
				failStreak = 0;
			}
		}
		else
		{
			failStreak = 0;
		}
	}

	// ------------------------------------------------------------------ decision log

	/** Opens the decision log (called by the plugin on start-up). */
	public void openLog(java.io.File dir)
	{
		PrepLog p = new PrepLog(() -> Microbot.getClient() == null ? -1 : Microbot.getClient().getTickCount());
		try
		{
			p.open(dir);
			plog = p;
			p.line("START", "Auto Gauntlet " + CgPrepPlugin.version + " mode=" + config.mode() + " profile=" + config.profile()
				+ " dryRun=" + config.dryRun() + " skipDemi=" + config.skipDemiBosses() + " fishTarget=" + config.fishTarget()
				+ " potions=" + config.potionTarget() + " finishReserve=" + config.finishReserve() + " handoverReserve=" + config.handoverReserve()
				+ " eatBelow=" + config.eatBelowPercent() + "%" + " autoLobby=" + config.autoLobby());
			Microbot.log("[Auto Gauntlet] decision log: " + p.getFile().getAbsolutePath());
		}
		catch (Exception e)
		{
			Microbot.log("[Auto Gauntlet] could not open decision log in " + dir + ": " + e.getMessage());
		}
	}

	public void closeLog()
	{
		PrepLog p = plog;
		plog = null;
		if (p != null)
		{
			p.line("STOP", "runs=" + runsCompleted);
			p.close();
		}
	}

	private void pl(String type, String body)
	{
		PrepLog p = plog;
		if (p != null)
		{
			p.line(type, body);
		}
	}

	/** Action results, de-duplicated: same action+result is logged at most every 3 s. */
	private void act(String what)
	{
		long now = System.currentTimeMillis();
		if (!what.equals(lastActKey) || now - lastActMs > 3000)
		{
			lastActKey = what;
			lastActMs = now;
			pl("ACT", what);
		}
	}

	// ------------------------------------------------------------------ control

	public void togglePause()
	{
		paused = !paused;
		if (paused)
		{
			// The weave and the potion chain run on their own threads: stop them too (review).
			net.runelite.client.plugins.microbot.cgprep.actions.Weaver w = weaver;
			if (w != null)
			{
				w.disarm();
			}
			net.runelite.client.plugins.microbot.cgprep.actions.BrewChain bc = brewChain;
			if (bc != null)
			{
				bc.stop();
			}
		}
	}

	public void requestContinue()
	{
		continueRequested = true;
		pl("LOBBY", "Continue hotkey: " + (inGauntlet ? "already in a run" : inLobby ? "starting a run" : "not in the Gauntlet lobby yet; starts when you're there"));
	}

	/** Tick-exact attack weaving while gathering (fed by the plugin's StatChanged / GameTick). */
	volatile net.runelite.client.plugins.microbot.cgprep.actions.Weaver weaver;
	/** The whole potion chain, one item-on-item per tick (fed by the plugin's GameTick). */
	volatile net.runelite.client.plugins.microbot.cgprep.actions.BrewChain brewChain;
	private long brewReadySinceMs;

	/**
	 * Start the potion chain (all herb + vial, crushes and dust steps back to back, user 0.3.33)
	 * when there's a run long enough to do it on (2 tiles a tick), or it's urgent (room needed, no
	 * potion and low prayer, at home). A chain that's been ready for 20 s without a long enough run
	 * goes anyway. Returns true when it started.
	 */
	private boolean maybeBrewChain(PrepGoal g, Ledger l, boolean urgent, String why)
	{
		net.runelite.client.plugins.microbot.cgprep.actions.BrewChain bc = brewChain;
		Needs n = needs;
		if (bc == null || bc.active() || l == null || n == null || n.potionsToMake <= 0)
		{
			return false;
		}
		int k = bc.plan(mode(), l.waterVials, l.get(net.runelite.client.plugins.microbot.cgprep.data.Resource.GRYM), l.unfinishedPotions,
			l.dust, l.get(net.runelite.client.plugins.microbot.cgprep.data.Resource.SHARDS), l.pestle, l.freeSlots, n.potionsToMake);
		if (k == 0)
		{
			brewReadySinceMs = 0;
			return false;
		}
		long now = System.currentTimeMillis();
		int left = remainingWalk(g);
		if (!urgent)
		{
			boolean longRun = left >= 2 * k + 2;
			if (brewReadySinceMs == 0)
			{
				brewReadySinceMs = now;
			}
			if (!longRun && now - brewReadySinceMs < 20_000)
			{
				return false; // wait for a longer run
			}
		}
		brewReadySinceMs = 0;
		bc.start(why + (left > 0 ? ", " + left + " tiles to go" : ""));
		return true;
	}

	/** Walking tiles left on the current goal's way (0 = unknown / there). */
	private int remainingWalk(PrepGoal g)
	{
		if (nav.lastPathLen > 0 && System.currentTimeMillis() - nav.lastPathAtMs < 1500)
		{
			return nav.lastPathLen;
		}
		WorldPoint me = maze.playerRaw();
		WorldPoint to = g == null ? null
			: g.type == PrepGoal.Type.EXPLORE ? exploreDoor
			: g.type == PrepGoal.Type.GATHER ? g.at
			: g.type == PrepGoal.Type.RETURN_TO_START && maze.startRoomX() >= 0 && me != null
				? maze.grid().centreOf(maze.startRoomX(), maze.startRoomZ(), me.getPlane()) : null;
		return me == null || to == null ? 0 : InstanceNav.chebyshev(me, to);
	}

	/** The prayer thread; made again after a shutdown (1.2.2, see BossFight.thread()). */
	private java.util.concurrent.ExecutorService prayerExec;

	private synchronized java.util.concurrent.ExecutorService prayerExec()
	{
		if (prayerExec == null || prayerExec.isShutdown())
		{
			prayerBusy.set(false); // a tick dropped by the shutdown never cleared it
			prayerExec = java.util.concurrent.Executors.newSingleThreadExecutor(r ->
			{
				Thread t = new Thread(r, "cg-prep-prayer");
				t.setDaemon(true);
				return t;
			});
		}
		return prayerExec;
	}
	private final java.util.concurrent.atomic.AtomicBoolean prayerBusy = new java.util.concurrent.atomic.AtomicBoolean();

	/** Prep prayers clicked on the prayer tab with the natural mouse (Fight mouse = NATURAL). */
	private final net.runelite.client.plugins.microbot.cgprep.boss.PrepPrayerInput prayerInput = new net.runelite.client.plugins.microbot.cgprep.boss.PrepPrayerInput();
	private String lastPrayerWarning;

	/**
	 * Called by the plugin every game tick (client thread: only schedules). Prayer decisions used
	 * to ride the main loop (300 ms + up to 0.4 s of observing), so they landed a tick late and the
	 * first hit got through. Now they're made the tick something changes, on a separate thread.
	 * With Fight mouse = NATURAL the clicks go through the prayer tab with the cursor (user, 0.4.7);
	 * each is one gesture under Microbot's input lock, so they queue behind the main loop's clicks
	 * instead of fighting them.
	 */
	public void onGameTick()
	{
		if (!inGauntlet || bossStarted || paused || config.dryRun() || bossOnly() || getCurrentState() != CgState.PREP)
		{
			return;
		}
		if (!prayerBusy.compareAndSet(false, true))
		{
			return;
		}
		prayerExec().execute(() ->
		{
			try
			{
				prayer.clicker = config.bossInput() == CgPrepConfig.FightInput.NATURAL ? prayerInput : null;
				String p = prayer.tick(mode());
				if (p != null)
				{
					act(p);
				}
				String w = prayerInput.warning;
				if (w != null)
				{
					prayerInput.warning = null;
					if (!w.equals(lastPrayerWarning))
					{
						lastPrayerWarning = w;
						pl("WARN", "prayers: " + w);
					}
				}
			}
			catch (InterruptedException ie)
			{
				Thread.currentThread().interrupt();
			}
			catch (Exception ex)
			{
				log.debug("[Auto Gauntlet] prayer tick", ex);
			}
			finally
			{
				prayerBusy.set(false);
			}
		});
	}

	private long lastEnterClickMs;

	/** The bot fights the boss itself: Auto mode Prep & Boss or Boss Only, not in dry run. */
	private boolean bossAuto()
	{
		return !config.dryRun() && config.autoMode() != CgPrepConfig.AutoMode.PREP_ONLY;
	}

	/** Auto mode Boss Only (1.4.0): the player preps; no prep input at all (goals, prayers, eating). */
	private boolean bossOnly()
	{
		return config.autoMode() == CgPrepConfig.AutoMode.BOSS_ONLY;
	}

	/** No handover when the bot fights: Quick-pass the boss barrier (every 4 s until the fight starts). */
	private void maybeEnterBoss()
	{
		if (!bossAuto() || bossOnly() || bossStarted || !mayAct())
		{
			return;
		}
		long now = System.currentTimeMillis();
		if (now - lastEnterClickMs < 4000)
		{
			return;
		}
		lastEnterClickMs = now;
		net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel barrier = station.station(mode().objBossBarrier);
		if (barrier == null)
		{
			pl("FIGHT", "WARN enter: boss barrier " + mode().objBossBarrier + " not found");
			return;
		}
		boolean sent = net.runelite.client.plugins.microbot.cgprep.actions.Aim.onScreen(barrier.getLocalLocation()) && barrier.click("Quick-pass");
		pl("FIGHT", "enter: Quick-pass on the boss barrier " + (sent ? "sent" : "NOT sent (off screen?)"));
	}

	/** Copy the boss-fight settings over (cheap; every loop and at fight start). */
	private void syncBossSettings()
	{
		net.runelite.client.plugins.microbot.cgprep.boss.BossFight.Mode m = config.autoMode() == CgPrepConfig.AutoMode.PREP_ONLY
			? net.runelite.client.plugins.microbot.cgprep.boss.BossFight.Mode.OFF
			: config.dryRun() ? net.runelite.client.plugins.microbot.cgprep.boss.BossFight.Mode.DRY_RUN
			: net.runelite.client.plugins.microbot.cgprep.boss.BossFight.Mode.ON;
		boss.mode = m;
		boss.clicksPerTick = config.bossClicksPerTick();
		boss.naturalInput = config.bossInput() == CgPrepConfig.FightInput.NATURAL;
		boss.brainConfig.offense = config.bossOffense();
		boss.brainConfig.eatBelow = config.bossEatBelow();
		boss.brainConfig.eatBelowSoft = config.bossEatBelow() + 12;
		boss.brainConfig.drinkBelow = config.bossDrinkBelow();
		boss.hold = paused;
		boss.log = this::pl;
		net.runelite.client.plugins.microbot.cgprep.fight.OffensePrayers.melee = config.meleePrayer();
		net.runelite.client.plugins.microbot.cgprep.fight.OffensePrayers.ranged = config.rangedPrayer();
		net.runelite.client.plugins.microbot.cgprep.fight.OffensePrayers.magic = config.magicPrayer();
	}

	@Override
	public void shutdown()
	{
		boss.shutdown();
		breakGate.release(); // sets "stopped": a last loop pass can't lock it again
		breakWaitSinceMs = 0;
		breakWaitGaveUp = false;
		synchronized (this)
		{
			if (prayerExec != null)
			{
				prayerExec.shutdownNow();
			}
		}
		net.runelite.client.plugins.microbot.cgprep.actions.Weaver w = weaver;
		if (w != null)
		{
			w.shutdown();
		}
		net.runelite.client.plugins.microbot.cgprep.actions.BrewChain bc = brewChain;
		if (bc != null)
		{
			bc.shutdown();
		}
		super.shutdown();
		breakGate.release(); // again, after the loop's last pass (review)
		antiban.stop();
		handover.clear();
	}
}
