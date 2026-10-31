package net.runelite.client.plugins.microbot.cgprep.boss;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.cgprep.data.CgIds;
import net.runelite.client.plugins.microbot.cgprep.data.CgMode;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

/**
 * The automated Hunllef fight (CG Prep BOSS state).
 *
 * Client thread (plugin @Subscribe handlers): only records. Events (boss/our animations, boss
 * projectiles, floor tiles, the "prayers disabled" message) set flags; on GameTick
 * {@link #onGameTick} reads the rest straight from the client into one {@link FightObs} and queues
 * it. Fight thread: feeds every queued observation to the {@link FightModel}, asks the
 * {@link HunllefBrain} for the next tick's plan, turns it into clicks ({@link PlanExecutor}) and
 * sends them (prayers first). Model, brain and executor are the exact classes the simulator
 * (tools/sim/FightBench) was tuned with.
 */
public final class BossFight
{
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(BossFight.class);

	public enum Mode
	{
		OFF, DRY_RUN, ON
	}

	/** Our bow / staff attack animations (recon: 426 bow, 1167 staff, both tiers and modes). */
	static final int ANIM_BOW = 426;
	static final int ANIM_STAFF = 1167;
	/** Boss HP HUD varbits (recon: 6099 current, 6100 max = 1000 at the start of the fight). */
	static final int VARBIT_BOSS_HP = 6099;
	static final int VARBIT_BOSS_HP_MAX = 6100;

	// ---- settings (written by the script before each fight)
	public volatile Mode mode = Mode.OFF;
	public volatile int clicksPerTick = 4;
	/** Natural: the cursor travels to every target and tabs are opened with their F-keys. */
	public volatile boolean naturalInput = true;
	private final HumanInput human = new HumanInput();
	private final Humanizer humanizer = new Humanizer(new java.util.Random());
	private final java.util.Random rnd = new java.util.Random();
	private InputTiming.Zone cursorZone = InputTiming.Zone.SCENE;
	private int fightTicks;
	private String lastTabWarning;
	public final BrainConfig brainConfig = new BrainConfig();
	/** Decision log sink: (type, body). */
	public volatile BiConsumer<String, String> log = (a, b) -> { };
	/** Set by the script: the user paused / global pause. */
	public volatile boolean hold;

	// ---- event flags (client thread)
	private volatile int bossAnim = -1;
	private volatile boolean swung;
	private volatile int projR;
	private volatile int projM;
	private volatile int projD;
	private volatile boolean disabledMsg;
	private final Map<Long, Byte> floorRaw = new ConcurrentHashMap<>();

	// ---- fight thread
	private final ArrayDeque<FightObs> queue = new ArrayDeque<>();
	private final AtomicBoolean busy = new AtomicBoolean();
	/**
	 * The fight thread. Made again after a shutdown (1.2.2): the plugin's script, and this with it,
	 * is the same object every time the plugin is switched on, so a final executor stayed shut down
	 * after an off/on and every tick threw RejectedExecutionException (the bot stood in the
	 * Gauntlet doing nothing).
	 */
	private ExecutorService thread;

	private synchronized ExecutorService thread()
	{
		if (thread == null || thread.isShutdown())
		{
			busy.set(false); // a drain dropped by the shutdown never cleared it
			thread = Executors.newSingleThreadExecutor(r ->
			{
				Thread t = new Thread(r, "cgprep-boss");
				t.setDaemon(true);
				return t;
			});
		}
		return thread;
	}
	private FightModel model = new FightModel();
	private HunllefBrain brain = new HunllefBrain(brainConfig);
	private final PlanExecutor exec = new PlanExecutor();

	// ---- shared with the overlay (read-only snapshots)
	public volatile FightObs lastObs;
	public volatile FightPlan lastPlan;
	public volatile List<Click> lastClicks = List.of();
	public volatile String lastNote = "";
	public volatile int originX;
	public volatile int originY;
	public volatile int plane;
	public volatile boolean active;

	// ---- per-fight ids found on the client thread
	private volatile int bossIndex = -1;
	private volatile int invBowId = -1;
	private volatile int invStaffId = -1;
	private volatile int bowSlot = -1;
	private volatile int staffSlot = -1;
	private volatile int fishSlot = -1;
	private volatile int doseSlot = -1;
	private volatile int doseId = -1;
	private volatile Client clientRef;
	private boolean fightPrepared;
	private volatile Rs2PrayerEnum bestMage;
	private volatile Rs2PrayerEnum bestRange;
	private volatile long tickStartMs;
	private int ticksLogged;
	private boolean warnedSlowBow;
	private String holdReason;

	/** New run / new fight: forget everything. Any thread. */
	public void reset()
	{
		floorRaw.clear();
		bossAnim = -1;
		swung = false;
		projR = projM = projD = 0;
		disabledMsg = false;
		synchronized (queue)
		{
			queue.clear();
		}
		thread().execute(() ->
		{
			model = new FightModel();
			brain = new HunllefBrain(brainConfig);
			exec.reset();
		});
		lastObs = null;
		lastPlan = null;
		lastClicks = List.of();
		active = false;
		ticksLogged = 0;
		warnedSlowBow = false;
		fightPrepared = false;
		fightTicks = 0;
		cursorZone = InputTiming.Zone.SCENE;
	}

	public synchronized void shutdown()
	{
		if (thread != null)
		{
			thread.shutdownNow();
		}
	}

	/** A fight starts: forget the last one's model and memos (the floor and ids are kept). */
	private void newFight()
	{
		synchronized (queue)
		{
			queue.clear();
		}
		thread().execute(() ->
		{
			model = new FightModel();
			brain = new HunllefBrain(brainConfig);
			exec.reset();
			humanizer.reset();
			fightPrepared = false;
			warnedSlowBow = false;
			fightTicks = 0;
			cursorZone = InputTiming.Zone.SCENE;
		});
	}

	// ------------------------------------------------------------------ plugin events (client thread)

	public void onBossAnimation(int anim)
	{
		if (anim != -1)
		{
			bossAnim = anim;
		}
	}

	public void onOurAnimation(int anim)
	{
		if (anim == ANIM_BOW || anim == ANIM_STAFF)
		{
			swung = true;
		}
	}

	/** Called once per boss projectile (the plugin dedupes ProjectileMoved). */
	public void onProjectile(int id)
	{
		if (CgIds.PROJ_HUNLLEF_PRAYER_DISABLE.contains(id))
		{
			projD++;
		}
		else if (CgIds.PROJ_HUNLLEF_MAGIC.contains(id))
		{
			projM++;
		}
		else if (CgIds.PROJ_HUNLLEF_RANGED.contains(id))
		{
			projR++;
		}
	}

	public void onFloor(int objectId, WorldPoint raw, boolean spawned)
	{
		if (raw == null)
		{
			return;
		}
		long key = ((long) raw.getX() << 20) | raw.getY();
		byte state = CgIds.FLOOR_WARNING.contains(objectId) ? FightObs.FLOOR_WARN
			: CgIds.FLOOR_HIT.contains(objectId) ? FightObs.FLOOR_HIT : FightObs.FLOOR_NONE;
		if (spawned && state != FightObs.FLOOR_NONE)
		{
			floorRaw.put(key, state);
		}
		else if (!spawned)
		{
			// Despawn of the state we hold (a newer spawn on the same tile replaces it first).
			floorRaw.remove(key, state);
		}
	}

	public void onChat(String message)
	{
		if (message != null && message.contains("prayers have been disabled"))
		{
			disabledMsg = true;
		}
	}

	/**
	 * CLIENT THREAD, once per game tick while the boss fight is on: read the client into one
	 * observation and hand it to the fight thread. Returns false when there's no boss to fight.
	 */
	public boolean onGameTick(Client client, CgMode cgMode)
	{
		tickStartMs = System.currentTimeMillis();
		FightObs o = capture(client, cgMode);
		bossAnim = -1;
		swung = false;
		projR = projM = projD = 0;
		disabledMsg = false;
		if (o == null)
		{
			active = false;
			return false;
		}
		if (!active)
		{
			newFight(); // first tick of a fight: fresh model, before this tick's observation
		}
		active = true;
		synchronized (queue)
		{
			queue.add(o);
		}
		if (busy.compareAndSet(false, true))
		{
			thread().execute(this::drain);
		}
		return true;
	}

	private FightObs capture(Client client, CgMode cgMode)
	{
		Player p = client.getLocalPlayer();
		if (p == null || client.getTopLevelWorldView() == null)
		{
			return null;
		}
		NPC boss = null;
		FightObs o = new FightObs();
		for (NPC n : client.getTopLevelWorldView().npcs())
		{
			if (n == null)
			{
				continue;
			}
			int id = n.getId();
			if (CgIds.HUNLLEF_IDS.contains(id) && id != net.runelite.api.gameval.NpcID.CRYSTAL_HUNLLEF_DEATH
				&& id != net.runelite.api.gameval.NpcID.CRYSTAL_HUNLLEF_DEATH_HM && !n.isDead())
			{
				boss = n;
			}
		}
		if (boss == null)
		{
			return null;
		}
		WorldPoint bw = boss.getWorldLocation();
		WorldPoint me = p.getWorldLocation();
		int x0 = Arena.originOf(bw.getX());
		int y0 = Arena.originOf(bw.getY());
		originX = x0;
		originY = y0;
		plane = bw.getPlane();
		bossIndex = boss.getIndex();
		for (NPC n : client.getTopLevelWorldView().npcs())
		{
			if (n != null && CgIds.TORNADO_IDS.contains(n.getId()))
			{
				WorldPoint w = n.getWorldLocation();
				o.addTornado(n.getIndex(), w.getX() - x0, w.getY() - y0);
			}
		}
		o.tick = client.getTickCount();
		o.px = me.getX() - x0;
		o.py = me.getY() - y0;
		o.hp = client.getBoostedSkillLevel(Skill.HITPOINTS);
		o.hpMax = client.getRealSkillLevel(Skill.HITPOINTS);
		o.prayer = client.getBoostedSkillLevel(Skill.PRAYER);
		o.prayerMax = client.getRealSkillLevel(Skill.PRAYER);
		try
		{
			o.protRanged = Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_RANGE);
			o.protMagic = Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_MAGIC);
			o.protMelee = Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_MELEE);
			// The player's picks (settings, 1.3.0), re-read every tick so a change applies at once.
			bestMage = net.runelite.client.plugins.microbot.cgprep.fight.OffensePrayers.mage();
			bestRange = net.runelite.client.plugins.microbot.cgprep.fight.OffensePrayers.range();
			boolean mageOn = bestMage != null && Rs2Prayer.isPrayerActive(bestMage);
			boolean rangeOn = bestRange != null && Rs2Prayer.isPrayerActive(bestRange);
			o.offense = mageOn || rangeOn;
			o.offenseStyle = rangeOn ? Style.RANGED : mageOn ? Style.MAGIC : null;
			o.tab = net.runelite.client.plugins.microbot.util.tabs.Rs2Tab.isCurrentTab(net.runelite.client.plugins.microbot.globval.enums.InterfaceTab.PRAYER) ? InputTiming.Tab.PRAYER
				: net.runelite.client.plugins.microbot.util.tabs.Rs2Tab.isCurrentTab(net.runelite.client.plugins.microbot.globval.enums.InterfaceTab.INVENTORY) ? InputTiming.Tab.INVENTORY
				: InputTiming.Tab.OTHER;
		}
		catch (Exception ignored)
		{
			// prayer varbits not readable this tick
		}
		ItemContainer eq = client.getItemContainer(net.runelite.api.gameval.InventoryID.WORN);
		Item w = eq == null ? null : eq.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx());
		int wid = w == null ? -1 : w.getId();
		o.weapon = contains(cgMode.bow, wid) ? Style.RANGED : contains(cgMode.staff, wid) ? Style.MAGIC : Style.MELEE;
		ItemContainer inv = client.getItemContainer(net.runelite.api.gameval.InventoryID.INV);
		int bowId = -1;
		int staffId = -1;
		int bSlot = -1;
		int sSlot = -1;
		int fSlot = -1;
		int dSlot = -1;
		int dId = -1;
		int dLeast = 99;
		if (inv != null)
		{
			Item[] items = inv.getItems();
			for (int slot = 0; slot < items.length; slot++)
			{
				int id = items[slot].getId();
				int dose = id == CgIds.EGNIOL_1 ? 1 : id == CgIds.EGNIOL_2 ? 2 : id == CgIds.EGNIOL_3 ? 3 : id == CgIds.EGNIOL_4 ? 4 : 0;
				if (id == CgIds.PADDLEFISH)
				{
					o.fish++;
					if (fSlot < 0)
					{
						fSlot = slot;
					}
				}
				else if (dose > 0)
				{
					o.doses += dose;
					if (dose < dLeast)
					{
						dLeast = dose; // finish the emptiest potion first (frees a slot)
						dSlot = slot;
						dId = id;
					}
				}
				else if (contains(cgMode.bow, id) && id > bowId)
				{
					bowId = id;
					bSlot = slot;
				}
				else if (contains(cgMode.staff, id) && id > staffId)
				{
					staffId = id;
					sSlot = slot;
				}
			}
		}
		invBowId = bowId;
		invStaffId = staffId;
		bowSlot = bSlot;
		staffSlot = sSlot;
		fishSlot = fSlot;
		doseSlot = dSlot;
		doseId = dId;
		clientRef = client;
		o.swung = swung;
		o.attacking = p.getInteracting() == boss;
		o.prayersDisabled = disabledMsg;
		o.bossPresent = true;
		o.bx = bw.getX() - x0;
		o.by = bw.getY() - y0;
		int id = boss.getId();
		o.bossProtects = CgIds.HUNLLEF_PROTECT_RANGED.contains(id) ? Style.RANGED
			: CgIds.HUNLLEF_PROTECT_MAGIC.contains(id) ? Style.MAGIC
			: CgIds.HUNLLEF_PROTECT_MELEE.contains(id) ? Style.MELEE : null;
		o.bossAnim = bossAnim;
		int hpMax = client.getVarbitValue(VARBIT_BOSS_HP_MAX);
		int hp = client.getVarbitValue(VARBIT_BOSS_HP);
		if (hpMax > 0)
		{
			o.bossHp = hp * 1000 / hpMax;
		}
		else if (boss.getHealthScale() > 0 && boss.getHealthRatio() >= 0)
		{
			o.bossHp = 1000 * boss.getHealthRatio() / boss.getHealthScale();
		}
		o.projRanged = projR;
		o.projMagic = projM;
		o.projDisable = projD;
		for (Map.Entry<Long, Byte> e : floorRaw.entrySet())
		{
			int fx = (int) (e.getKey() >> 20) - x0;
			int fy = (int) (e.getKey() & 0xFFFFF) - y0;
			if (Arena.in(fx, fy))
			{
				o.floor[Arena.t(fx, fy)] = e.getValue();
			}
		}
		return o;
	}

	private static boolean contains(int[] ids, int id)
	{
		if (ids == null)
		{
			return false;
		}
		for (int i : ids)
		{
			if (i == id)
			{
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ fight thread

	private void drain()
	{
		try
		{
			FightObs o = null;
			while (true)
			{
				FightObs next;
				synchronized (queue)
				{
					next = queue.poll();
				}
				if (next == null)
				{
					break;
				}
				model.update(next);
				o = next;
				log.accept("FOBS", next.encode());
			}
			if (o == null)
			{
				return;
			}
			if (mode == Mode.ON && !fightPrepared)
			{
				fightPrepared = true;
				prepareFight();
			}
			if (!warnedSlowBow && model.attackSpeed(Style.RANGED) == 5)
			{
				warnedSlowBow = true;
				log.accept("FIGHT", "WARN the bow is swinging every 5 ticks: set it to Rapid (4 ticks) in the combat tab");
			}
			fightTicks++;
			human.fatigue = net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings.simulateFatigue
				? 1 + 0.06 * Math.min(1.0, fightTicks / 400.0) : 1.0;
			FightPlan plan = brain.decide(model);
			List<Click> clicks = exec.clicks(plan, o, o.tick, naturalInput ? 6 : Math.max(1, clicksPerTick));
			lastObs = o;
			lastPlan = plan;
			lastClicks = clicks;
			String line = "t" + o.tick + " me " + o.px + "," + o.py + " hp " + o.hp + " pr " + o.prayer
				+ " boss " + o.bossHp + (o.bossProtects == null ? "" : " prot " + o.bossProtects)
				+ " next " + model.nextAttackTick() + ":" + model.nextStyle + " | " + plan + " | " + clicks;
			if (mode != Mode.ON)
			{
				lastNote = "DRY RUN";
				log.accept("FIGHT", "[dry] " + line);
				return;
			}
			String why = holdReason();
			if (why != null)
			{
				if (!why.equals(holdReason))
				{
					log.accept("FIGHT", "HOLD " + why);
				}
				holdReason = why;
				lastNote = "HOLD: " + why;
				return;
			}
			holdReason = null;
			log.accept("FIGHT", line);
			Humanizer.Tick ht = null;
			if (naturalInput)
			{
				ht = humanizer.plan(clicks, model, plan);
				clicks = ht.now;
				if (ht.slip != null)
				{
					log.accept("FIGHT", ht.slip);
				}
			}
			send(clicks, plan, o.tick, ht);
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
		}
		catch (Exception e)
		{
			log.accept("FIGHT", "ERROR " + e);
			LOG.warn("[CG Prep] boss fight tick", e);
		}
		finally
		{
			busy.set(false);
			boolean more;
			synchronized (queue)
			{
				more = !queue.isEmpty();
			}
			if (more && busy.compareAndSet(false, true))
			{
				thread().execute(this::drain);
			}
		}
	}

	/** Once per fight: inventory tab open (item clicks need its widgets) and run on. */
	private void prepareFight()
	{
		try
		{
			if (naturalInput)
			{
				human.ensureTab(InputTiming.Tab.INVENTORY);
			}
			else if (!net.runelite.client.plugins.microbot.util.tabs.Rs2Tab.isCurrentTab(net.runelite.client.plugins.microbot.globval.enums.InterfaceTab.INVENTORY))
			{
				net.runelite.client.plugins.microbot.util.tabs.Rs2Tab.switchTo(net.runelite.client.plugins.microbot.globval.enums.InterfaceTab.INVENTORY);
			}
			if (!net.runelite.client.plugins.microbot.util.player.Rs2Player.isRunEnabled())
			{
				net.runelite.client.plugins.microbot.util.player.Rs2Player.toggleRunEnergy(true);
			}
			log.accept("FIGHT", "prepared: inventory tab open, run on, input " + (naturalInput ? "NATURAL" : "INSTANT"));
		}
		catch (Exception e)
		{
			log.accept("FIGHT", "WARN prepare: " + e);
		}
	}

	private String holdReason()
	{
		if (hold)
		{
			return "paused";
		}
		if (Microbot.pauseAllScripts.get())
		{
			return "Microbot pause-all";
		}
		if (InputArbiter.isRealButtonOrKeyDown())
		{
			return "you're holding a key/button";
		}
		return null;
	}

	/**
	 * Send the clicks in order while they fit in this tick. Natural input: each click's time is
	 * estimated (tab key + Fitts travel + settle + press, {@link InputTiming}) and one that wouldn't
	 * be done by 540 ms into the tick waits; it's re-planned next tick (only clicks that went out are
	 * marked sent). Instant input: everything goes, until 480 ms.
	 */
	private void send(List<Click> clicks, FightPlan plan, int tick, Humanizer.Tick ht) throws InterruptedException
	{
		if (ht != null)
		{
			// A person reacts a moment after the tick, quicker when it matters.
			long wait = Math.round(ht.reactMs) - (System.currentTimeMillis() - tickStartMs);
			if (wait > 0)
			{
				Thread.sleep(wait);
			}
		}
		StringBuilder deferred = null;
		StringBuilder timing = new StringBuilder();
		for (Click c : clicks)
		{
			long into = System.currentTimeMillis() - tickStartMs;
			boolean fits;
			if (ht != null)
			{
				InputTiming.Tab need = InputTiming.tabFor(c);
				FightObs o = lastObs;
				boolean switchTab = need != null && (o == null || o.tab != need);
				double est = (switchTab ? InputTiming.KEY_MS : 0) + InputTiming.estimate(cursorZone, InputTiming.zoneFor(c)) * human.fatigue;
				fits = into + est <= ht.budgetFor(c);
			}
			else
			{
				fits = into <= 480;
			}
			if (!fits)
			{
				deferred = deferred == null ? new StringBuilder() : deferred;
				deferred.append(c).append(' ');
				if (ht != null)
				{
					break; // keep the priority order: later clicks wait too
				}
				continue;
			}
			boolean ok = ht != null ? clickNatural(c, plan) : click(c, plan);
			long done = System.currentTimeMillis() - tickStartMs;
			timing.append(c).append(" +").append(into).append('-').append(done).append(ok ? "" : " FAILED").append("  ");
			if (ok)
			{
				exec.sent(c, tick);
				// While running, a person sometimes clicks the tile again (only when nothing else is waiting).
				if (ht != null && c.type == Click.Type.MOVE && c == clicks.get(clicks.size() - 1) && rnd.nextDouble() < 0.15 && done < 380)
				{
					QuickClicks.Target again = QuickClicks.walk(clientRef, new WorldPoint(originX + c.x, originY + c.y, plane));
					if (again != null && human.click(again))
					{
						timing.append("(again +").append(System.currentTimeMillis() - tickStartMs).append(")  ");
					}
				}
			}
			else
			{
				lastNote = "click failed: " + c;
				log.accept("FIGHT", "WARN click failed: " + c);
			}
		}
		if (deferred != null)
		{
			log.accept("FIGHT", "next tick (no time left in this one): " + deferred.toString().trim());
		}
		if (ht != null)
		{
			long into = System.currentTimeMillis() - tickStartMs;
			// 1.5.5: your mouse moved over the game in the last 4 s: only clicks move the cursor
			// (a pointer parked still over the game doesn't fight the bot, so then drift/hover stay).
			boolean yoursOverGame = HumanInput.playerMovingOverGame();
			if (ht.hover != null && into < 420 && !yoursOverGame)
			{
				InputTiming.Tab need = InputTiming.tabFor(ht.hover);
				FightObs o = lastObs;
				if (need == null || (o != null && o.tab == need))
				{
					QuickClicks.Target ht2 = hoverTarget(ht.hover);
					if (ht2 != null && human.hover(ht2))
					{
						cursorZone = InputTiming.zoneFor(ht.hover);
						timing.append("hover ").append(ht.hover).append(" +").append(into).append("  ");
					}
				}
			}
			else if (clicks.isEmpty() && !yoursOverGame && rnd.nextDouble() < 0.05)
			{
				human.drift();
			}
			if (timing.length() > 0 || !ht.later.isEmpty())
			{
				log.accept("FCLK", "t" + tick + String.format(" react %.0f", ht.reactMs) + " | " + timing.toString().trim()
					+ (ht.later.isEmpty() ? "" : " | later " + ht.later));
			}
		}
		String w = human.tabWarning;
		if (w != null && !w.equals(lastTabWarning))
		{
			lastTabWarning = w;
			log.accept("FIGHT", "WARN " + w);
		}
	}

	/** Where a held-back click would go (for a drift of the cursor), without clicking. */
	private QuickClicks.Target hoverTarget(Click c)
	{
		switch (c.type)
		{
			case PROTECT:
				return QuickClicks.prayer(clientRef, c.style == Style.RANGED ? Rs2PrayerEnum.PROTECT_RANGE : Rs2PrayerEnum.PROTECT_MAGIC);
			case OFFENSE_ON:
				return QuickClicks.prayer(clientRef, c.style == Style.RANGED ? bestRange : bestMage);
			case WIELD:
				return c.style == Style.RANGED ? QuickClicks.item(clientRef, bowSlot, invBowId, "Wield")
					: QuickClicks.item(clientRef, staffSlot, invStaffId, "Wield");
			case EAT:
				return QuickClicks.item(clientRef, fishSlot, CgIds.PADDLEFISH, "Eat");
			case DRINK:
				return QuickClicks.item(clientRef, doseSlot, doseId, "Drink");
			case ATTACK:
				return QuickClicks.attack(clientRef, bossIndex);
			default:
				return null;
		}
	}

	/** Natural input: open the right tab, travel to the target, click inside it. */
	private boolean clickNatural(Click c, FightPlan plan) throws InterruptedException
	{
		InputTiming.Tab need = InputTiming.tabFor(c);
		if (need != null && !human.ensureTab(need))
		{
			return false;
		}
		QuickClicks.Target t = null;
		switch (c.type)
		{
			case PROTECT:
			{
				Rs2PrayerEnum pr = c.style == Style.RANGED ? Rs2PrayerEnum.PROTECT_RANGE : Rs2PrayerEnum.PROTECT_MAGIC;
				if (Rs2Prayer.isPrayerActive(pr))
				{
					return true; // already on (a click would turn it off)
				}
				t = QuickClicks.prayer(clientRef, pr);
				break;
			}
			case PROTECT_OFF:
			{
				Rs2PrayerEnum pr = Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_RANGE) ? Rs2PrayerEnum.PROTECT_RANGE
					: Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_MAGIC) ? Rs2PrayerEnum.PROTECT_MAGIC
					: Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_MELEE) ? Rs2PrayerEnum.PROTECT_MELEE : null;
				if (pr == null)
				{
					return true;
				}
				t = QuickClicks.prayer(clientRef, pr);
				break;
			}
			case OFFENSE_ON:
			{
				Rs2PrayerEnum pr = c.style == Style.RANGED ? bestRange : bestMage;
				if (pr == null)
				{
					return true; // "None" picked for this style (or none unlocked): nothing to click
				}
				if (Rs2Prayer.isPrayerActive(pr))
				{
					return true;
				}
				t = QuickClicks.prayer(clientRef, pr); // turning it on turns the other style's off
				break;
			}
			case OFFENSE_OFF:
			{
				Rs2PrayerEnum pr = bestMage != null && Rs2Prayer.isPrayerActive(bestMage) ? bestMage
					: bestRange != null && Rs2Prayer.isPrayerActive(bestRange) ? bestRange : null;
				if (pr == null)
				{
					return true;
				}
				t = QuickClicks.prayer(clientRef, pr);
				break;
			}
			case WIELD:
				t = c.style == Style.RANGED ? QuickClicks.item(clientRef, bowSlot, invBowId, "Wield")
					: QuickClicks.item(clientRef, staffSlot, invStaffId, "Wield");
				break;
			case EAT:
				t = QuickClicks.item(clientRef, fishSlot, CgIds.PADDLEFISH, "Eat");
				break;
			case DRINK:
				t = QuickClicks.item(clientRef, doseSlot, doseId, "Drink");
				break;
			case MOVE:
				t = QuickClicks.walk(clientRef, new WorldPoint(originX + c.x, originY + c.y, plane));
				if (t == null)
				{
					return Rs2Walker.walkFastCanvas(new WorldPoint(originX + c.x, originY + c.y, plane), false);
				}
				break;
			case ATTACK:
				t = QuickClicks.attack(clientRef, bossIndex);
				break;
			default:
				return false;
		}
		if (t == null || (t.rect == null && t.shape == null && t.fixed == null))
		{
			return false; // not on screen (tab didn't open / target out of view): re-planned next tick
		}
		boolean ok = human.click(t);
		if (ok)
		{
			cursorZone = InputTiming.zoneFor(c);
		}
		return ok;
	}

	private boolean click(Click c, FightPlan plan)
	{
		switch (c.type)
		{
			// Rs2Prayer.toggle(prayer, on) waits up to 10 s for the varbit to flip (a whole tick at
			// best), which would hold back every click after it. The one-argument toggle only sends
			// the click; PlanExecutor already decided it's needed and won't repeat it too soon.
			case PROTECT:
				Rs2Prayer.toggle(c.style == Style.RANGED ? Rs2PrayerEnum.PROTECT_RANGE : Rs2PrayerEnum.PROTECT_MAGIC);
				return true;
			case PROTECT_OFF:
				for (Rs2PrayerEnum pr : new Rs2PrayerEnum[]{Rs2PrayerEnum.PROTECT_RANGE, Rs2PrayerEnum.PROTECT_MAGIC, Rs2PrayerEnum.PROTECT_MELEE})
				{
					if (Rs2Prayer.isPrayerActive(pr))
					{
						Rs2Prayer.toggle(pr);
					}
				}
				return true;
			case OFFENSE_ON:
			{
				Rs2PrayerEnum pr = c.style == Style.RANGED ? bestRange : bestMage;
				if (pr == null)
				{
					return true; // "None" picked for this style (or none unlocked): nothing to click
				}
				if (!Rs2Prayer.isPrayerActive(pr))
				{
					Rs2Prayer.toggle(pr);
				}
				return true;
			}
			case OFFENSE_OFF:
				for (Rs2PrayerEnum pr : new Rs2PrayerEnum[]{bestMage, bestRange})
				{
					if (pr != null && Rs2Prayer.isPrayerActive(pr))
					{
						Rs2Prayer.toggle(pr);
					}
				}
				return true;
			case WIELD:
			{
				int slot = c.style == Style.RANGED ? bowSlot : staffSlot;
				int id = c.style == Style.RANGED ? invBowId : invStaffId;
				return QuickClicks.sendQuick(QuickClicks.item(clientRef, slot, id, "Wield"));
			}
			case MOVE:
			{
				WorldPoint wp = new WorldPoint(originX + c.x, originY + c.y, plane);
				// The tile is nearly always in view (we step at most 2 tiles); otherwise Microbot's walker.
				return QuickClicks.sendQuick(QuickClicks.walk(clientRef, wp)) || Rs2Walker.walkFastCanvas(wp, false);
			}
			case ATTACK:
				return QuickClicks.sendQuick(QuickClicks.attack(clientRef, bossIndex));
			case EAT:
				return QuickClicks.sendQuick(QuickClicks.item(clientRef, fishSlot, CgIds.PADDLEFISH, "Eat"));
			case DRINK:
				return QuickClicks.sendQuick(QuickClicks.item(clientRef, doseSlot, doseId, "Drink"));
			default:
				return false;
		}
	}

	/** End of fight / run: one summary line. */
	public void summary(String outcome)
	{
		FightModel m = model;
		FightObs o = lastObs;
		log.accept("FIGHT", "END " + outcome + (o == null ? "" : " hp " + o.hp + " fish " + o.fish + " doses " + o.doses + " boss " + o.bossHp)
			+ " attacks " + m.attacksTotal + " swings " + m.swings + " style misses " + m.styleMisses + "/" + m.styleChecks);
	}
}
