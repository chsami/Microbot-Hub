package net.runelite.client.plugins.microbot.cgprep.actions;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.cgprep.antiban.Pace;
import net.runelite.client.plugins.microbot.cgprep.data.CgIds;
import net.runelite.client.plugins.microbot.cgprep.data.CgMode;
import net.runelite.client.plugins.microbot.cgprep.data.GearSlot;
import net.runelite.client.plugins.microbot.cgprep.plan.Ledger;
import net.runelite.client.plugins.microbot.cgprep.nav.InstanceNav;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

/**
 * Everything done in the start room: singing bowl crafts, potion chain, cooking, equipping, tools.
 * Each public method sends at most one game action and reports what it observed.
 *
 * Recon-confirmed: bowl option "Sing-crystal" opens the standard skill-multi make menu (interface
 * 270); each recipe is an entry named after the item ("Make | Crystal staff (basic)"), so
 * makeAtBowl clicks the entry directly (see clickMakeEntry). Pestle on shards crushes exactly 10.
 */
public final class StationActions
{
	private final InstanceNav nav;
	private final Pace pace;

	public StationActions(InstanceNav nav, Pace pace)
	{
		this.nav = nav;
		this.pace = pace;
	}

	// ------------------------------------------------------------------ singing bowl

	/** Craft (or upgrade into) the next tier of {@code slot}. */
	public ActionResult craftNextTier(CgMode mode, Ledger l, GearSlot slot)
	{
		int cur = l.tier.getOrDefault(slot, 0);
		if (cur >= 3)
		{
			return ActionResult.DONE;
		}
		int targetId = slot.ids(mode)[cur];
		if (Rs2Inventory.contains(targetId) || Rs2Equipment.isWearing(targetId))
		{
			return ActionResult.DONE;
		}
		return makeAtBowl(mode, targetId);
	}

	/**
	 * Make one item from the bowl's menu, identified by its item id (name looked up live).
	 *
	 * Live test (0.2.4): the make menu (interface 270) stays open after each craft. Clicking the
	 * bowl again reloads it, and a "Make" sent in that same moment hits the old menu and is lost
	 * (2 of 7 crafts). So: if the menu is already open, click the entry directly; only click the
	 * bowl when it's closed, and wait for the freshly loaded menu before choosing.
	 */
	public ActionResult makeAtBowl(CgMode mode, int itemId)
	{
		String name = itemName(itemId);
		if (name == null)
		{
			return ActionResult.NOT_AVAILABLE;
		}
		if (!Rs2Widget.isProductionWidgetOpen())
		{
			ActionResult walk = approach(mode.objBowl);
			if (walk != ActionResult.DONE)
			{
				return walk;
			}
			Rs2TileObjectModel bowl = station(mode.objBowl);
			if (bowl == null)
			{
				return ActionResult.NO_REACTION;
			}
			pace.before(Pace.Kind.WORLD);
			boolean clicked = Aim.onScreen(bowl.getLocalLocation()) && bowl.click("Sing-crystal");
			pace.after(Pace.Kind.WORLD);
			if (!clicked)
			{
				return ActionResult.NO_REACTION;
			}
			if (!Waits.untilOrStopped(() -> Rs2Widget.isProductionWidgetOpen() && Rs2Widget.findWidget(name) != null, walkWait(bowl, 3000)))
			{
				return ActionResult.NO_REACTION;
			}
		}
		int before = madeCount(mode, itemId);
		// One make-menu selection per game tick: the previous one's item is already in the
		// inventory (we waited for it), so only the short human gap remains.
		pace.before(Pace.Kind.MENU);
		boolean chosen = clickMakeEntry(name);
		pace.after(Pace.Kind.MENU);
		if (!chosen)
		{
			return ActionResult.NO_REACTION;
		}
		boolean made = sleepUntil(() -> madeCount(mode, itemId) > before, 3000);
		if (made)
		{
			pace.observed();
		}
		return made ? ActionResult.DONE : ActionResult.NO_REACTION;
	}

	/**
	 * Click the make-menu entry for this item directly. Not Rs2Widget.handleProcessingInterface:
	 * when the entry has a number-key shortcut (the bowl's do) it sleeps 600 ms before the key,
	 * 600 ms after it, then waits up to 1.2-1.6 s for the menu to close (the bowl's never closes),
	 * which is why crafts landed only every 3 ticks in the 0.2.7/0.2.8 tests.
	 */
	private static boolean clickMakeEntry(String name)
	{
		net.runelite.api.widgets.Widget root = Rs2Widget.getWidget(270, 0);
		if (root == null)
		{
			return false;
		}
		net.runelite.api.widgets.Widget entry = Rs2Widget.findWidget(name, new java.util.ArrayList<>(java.util.List.of(root)), false);
		return entry != null && Rs2Widget.clickWidget(entry);
	}

	/** How many of the item we hold; the bowl's vial shows as 23839 in the menu and 23879 in hand. */
	private static int madeCount(CgMode mode, int itemId)
	{
		// Upgrading the wielded staff puts the new one straight in the weapon slot (0.3.16: two
		// 5 s "no reaction" waits on a T3 staff that had been made).
		int n = Rs2Inventory.count(itemId) + (Rs2Equipment.isWearing(itemId) ? 1 : 0);
		if (itemId == mode.vial)
		{
			n += Rs2Inventory.count(CgIds.VIAL_EMPTY_ALT);
		}
		return n;
	}

	// ------------------------------------------------------------------ potions

	/**
	 * Advance the egniol chain by one step: vial -> water -> unf -> +dust -> potion.
	 * TODO(recon): vial source (bowl item?), pestle behaviour on a stack of shards.
	 */
	public ActionResult brewStep(CgMode mode, Ledger l)
	{
		return brewStep(mode, l, true);
	}

	/**
	 * One step of the Egniol chain, in BULK order (user, 0.3.8: it did vial -> fill -> grym ->
	 * crush -> dust for one potion, then started over): fill every empty vial, grym into every
	 * water vial, crush dust for every unfinished potion, then dust into each. Each step repeats
	 * (fast-follow) until it's done for all potions before the next starts. Vials themselves are
	 * made beforehand in one bowl batch (MAKE_VIALS). Away from home (atHome=false) only the
	 * inventory-only steps run: no walking to the pump or bowl.
	 */
	public ActionResult brewStep(CgMode mode, Ledger l, boolean atHome)
	{
		int grym = l.get(net.runelite.client.plugins.microbot.cgprep.data.Resource.GRYM);
		if (atHome && l.emptyVials > 0)
		{
			return fillAtPump(mode);
		}
		if (l.waterVials > 0 && grym > 0)
		{
			return combineBatch(BATCH_HERB, CgIds.VIAL_WATER, mode.grym, CgIds.POTION_UNF);
		}
		// Crushing needs a slot for the dust stack unless some dust is already held. 0.3.11: with a
		// full inventory it tried to crush every 5 s for half a minute.
		if (l.unfinishedPotions > 0 && l.dust < 10 * l.unfinishedPotions && l.pestle
			&& l.get(net.runelite.client.plugins.microbot.cgprep.data.Resource.SHARDS) >= 10
			&& (l.dust > 0 || l.freeSlots > 0))
		{
			return combine(CgIds.PESTLE, mode.shard, mode.dust);
		}
		if (l.unfinishedPotions > 0 && l.dust >= 10)
		{
			return combineBatch(BATCH_DUST, mode.dust, CgIds.POTION_UNF, CgIds.EGNIOL_3);
		}
		if (!atHome)
		{
			return ActionResult.NOT_AVAILABLE;
		}
		if (l.emptyVials + l.waterVials == 0 && grym > 0)
		{
			return makeAtBowl(mode, mode.vial);
		}
		return ActionResult.DONE;
	}

	/**
	 * Decant: use the smaller part-full Egniol on the bigger one, so one becomes a 4-dose and the
	 * rest stays in the other (3 + 3 -> 4 + 2; 1 + 3 -> 4 + an empty vial for the next brew).
	 */
	public ActionResult decant()
	{
		java.util.List<net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel> part = new java.util.ArrayList<>();
		for (int d = 1; d <= 3; d++)
		{
			int id = egniolId(d);
			Rs2Inventory.items().filter(i -> i.getId() == id).forEach(part::add);
		}
		if (part.size() < 2)
		{
			return ActionResult.NOT_AVAILABLE;
		}
		// Smallest first: pour part[0] into the biggest part-full one.
		net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel from = part.get(0);
		net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel into = part.get(part.size() - 1);
		int full = Rs2Inventory.count(CgIds.EGNIOL_4);
		int partBefore = part.size();
		pace.before(Pace.Kind.INVENTORY);
		boolean sent = Rs2Inventory.combine(from, into);
		pace.after(Pace.Kind.INVENTORY);
		if (!sent)
		{
			return ActionResult.NO_REACTION;
		}
		boolean done = sleepUntil(() -> Rs2Inventory.count(CgIds.EGNIOL_4) > full || partCount() < partBefore, 2400);
		if (done)
		{
			pace.observed();
		}
		return done ? ActionResult.CLICKED : ActionResult.NO_REACTION;
	}

	/** Drink one dose from the smallest Egniol. CLICKED only when the dose total actually dropped. */
	public ActionResult drinkSmallest()
	{
		for (int d = 1; d <= 4; d++)
		{
			int id = egniolId(d);
			if (Rs2Inventory.contains(id))
			{
				int before = totalDoses();
				pace.before(Pace.Kind.INVENTORY);
				boolean sent = Rs2Inventory.interact(id, "Drink");
				pace.after(Pace.Kind.INVENTORY);
				if (!sent)
				{
					return ActionResult.NO_REACTION;
				}
				return sleepUntil(() -> totalDoses() < before, 1800) ? ActionResult.CLICKED : ActionResult.NO_REACTION;
			}
		}
		return ActionResult.NOT_AVAILABLE;
	}

	public static int totalDoses()
	{
		int doses = 0;
		for (int d = 1; d <= 4; d++)
		{
			doses += d * Rs2Inventory.count(egniolId(d));
		}
		return doses;
	}

	private static int partCount()
	{
		return Rs2Inventory.count(CgIds.EGNIOL_1) + Rs2Inventory.count(CgIds.EGNIOL_2) + Rs2Inventory.count(CgIds.EGNIOL_3);
	}

	private static int egniolId(int doses)
	{
		switch (doses)
		{
			case 1:
				return CgIds.EGNIOL_1;
			case 2:
				return CgIds.EGNIOL_2;
			case 3:
				return CgIds.EGNIOL_3;
			default:
				return CgIds.EGNIOL_4;
		}
	}

	/**
	 * Fill empty vials at the nearest fishing spot, fished out or not (user: vials are filled at
	 * fishing spots, not the pump). One use fills them all: the game repeats it a vial per tick until
	 * none are left or we're interrupted (user, 0.4.7), so while the water count keeps rising we just
	 * watch; it's clicked again only when it stalls with empties left.
	 */
	public ActionResult fillAtFishingSpot(CgMode mode, net.runelite.client.plugins.microbot.cgprep.map.GauntletMaze maze)
	{
		int vial = Rs2Inventory.contains(mode.vial) ? mode.vial
			: Rs2Inventory.contains(CgIds.VIAL_EMPTY_ALT) ? CgIds.VIAL_EMPTY_ALT : -1;
		if (vial == -1)
		{
			return ActionResult.DONE;
		}
		net.runelite.client.plugins.microbot.cgprep.map.GauntletMaze.Node spot = maze.nearestFishingSpot();
		WorldPoint me = maze.playerRaw();
		if (spot == null || me == null)
		{
			return ActionResult.NOT_AVAILABLE;
		}
		if (InstanceNav.chebyshev(me, spot.raw) > 8)
		{
			InstanceNav.Result walk = nav.step(spot.raw, 8);
			if (walk == InstanceNav.Result.NO_PATH)
			{
				spot.avoidUntilMs = System.currentTimeMillis() + 30_000;
				return ActionResult.NO_PATH;
			}
			return ActionResult.WALKING;
		}
		Rs2TileObjectModel obj = Microbot.getRs2TileObjectCache().query()
			.withIds(mode.nodeFor(net.runelite.client.plugins.microbot.cgprep.data.Resource.RAW_FISH), mode.objPondDepleted)
			.at(spot.raw).first();
		if (obj == null)
		{
			spot.avoidUntilMs = System.currentTimeMillis() + 30_000;
			return ActionResult.NOT_AVAILABLE;
		}
		int water = Rs2Inventory.count(CgIds.VIAL_WATER);
		if (batchRunning(BATCH_FILL, water))
		{
			return ActionResult.IN_PROGRESS; // still filling one per tick
		}
		pace.before(Pace.Kind.WORLD);
		boolean used = Aim.useItemOn(vial, obj);
		pace.after(Pace.Kind.WORLD);
		if (!used)
		{
			return ActionResult.NO_REACTION;
		}
		boolean first = Waits.untilOrStopped(() -> Rs2Inventory.count(CgIds.VIAL_WATER) > water, walkWait(obj, 3000));
		if (first)
		{
			batchStarted(BATCH_FILL, Rs2Inventory.count(CgIds.VIAL_WATER));
		}
		return first ? ActionResult.CLICKED : ActionResult.NO_REACTION;
	}

	// ------------------------------------------------------------------ auto-repeating actions

	/**
	 * Filling vials, grym into water vials and dust into unfinished potions repeat by themselves, one
	 * per game tick, until the items run out or something interrupts (a click elsewhere, an attack).
	 * We track the count that changes with each one: while it keeps changing the batch is running;
	 * when it hasn't changed for {@link #BATCH_STALL_MS} it was interrupted and is clicked again.
	 */
	static final int BATCH_FILL = 1;
	static final int BATCH_HERB = 2;
	static final int BATCH_DUST = 3;
	static final long BATCH_STALL_MS = 1500;
	private int batchKind;
	private int batchCount = -1;
	private long batchMs;

	private boolean batchRunning(int kind, int count)
	{
		long now = System.currentTimeMillis();
		if (kind != batchKind || now - batchMs >= BATCH_STALL_MS)
		{
			batchKind = 0; // never started, or stalled (interrupted / finished): click again
			return false;
		}
		if (count != batchCount)
		{
			batchCount = count;
			batchMs = now;
			return true;
		}
		return now - batchMs < BATCH_STALL_MS;
	}

	private void batchStarted(int kind, int count)
	{
		batchKind = kind;
		batchCount = count;
		batchMs = System.currentTimeMillis();
	}

	/** Failed vial-on-pump clicks in a row this run (1.6.0). */
	private int pumpFailures;
	/** After this many, the pump is left alone for the rest of the run (the last brew is skipped). */
	static final int PUMP_GIVE_UP = 6;

	/** The pump failed too often this run: the planner skips the last-resort pump brew. */
	public boolean pumpGivenUp()
	{
		return pumpFailures >= PUMP_GIVE_UP;
	}

	/** A new run. */
	public void resetRun()
	{
		pumpFailures = 0;
	}

	private ActionResult fillAtPump(CgMode mode)
	{
		if (Rs2Player.isAnimating() || batchRunning(BATCH_FILL, Rs2Inventory.count(CgIds.VIAL_WATER)))
		{
			return ActionResult.IN_PROGRESS; // still filling, one vial per tick (one use fills them all)
		}
		{
			ActionResult walk = approach(mode.objSink);
			if (walk != ActionResult.DONE)
			{
				return walk;
			}
			int before = Rs2Inventory.count(CgIds.VIAL_WATER);
			pace.before(Pace.Kind.INVENTORY);
			int vialId = Rs2Inventory.contains(mode.vial) ? mode.vial : CgIds.VIAL_EMPTY_ALT;
			Rs2TileObjectModel pump = station(mode.objSink);
			boolean used = Aim.useItemOn(vialId, pump);
			pace.after(Pace.Kind.WORLD);
			if (!used)
			{
				pumpFailures++;
				lastApproachNote = "pump: the vial-on-pump click didn't go out: " + Aim.lastFail + " (try " + pumpFailures + ", pump "
					+ (pump == null ? "not found" : "at " + pump.getWorldLocation()) + ")";
				// 1.6.0: "close enough" is 16 tiles, so a pump off screen was retried from where we stood
				// for 50 s (user had to step in). Walk next to it, which also puts it on screen.
				if (pump != null && pump.getWorldLocation() != null)
				{
					nav.step(pump.getWorldLocation(), 1);
				}
				return pumpFailures >= PUMP_GIVE_UP ? ActionResult.NOT_AVAILABLE : ActionResult.NO_REACTION;
			}
			pumpFailures = 0;
			boolean first = Waits.untilOrStopped(() -> Rs2Inventory.count(CgIds.VIAL_WATER) > before, walkWait(pump, 3000));
			if (first)
			{
				batchStarted(BATCH_FILL, Rs2Inventory.count(CgIds.VIAL_WATER));
			}
			return first ? ActionResult.CLICKED : ActionResult.NO_REACTION;
		}
	}

	// ------------------------------------------------------------------ cooking

	public ActionResult cookAll(CgMode mode)
	{
		if (!Rs2Inventory.contains(CgIds.RAW_PADDLEFISH))
		{
			return ActionResult.DONE;
		}
		if (Rs2Player.isAnimating())
		{
			return ActionResult.IN_PROGRESS;
		}
		ActionResult walk = approach(mode.objRange);
		if (walk != ActionResult.DONE)
		{
			return walk;
		}
		int before = Rs2Inventory.count(CgIds.RAW_PADDLEFISH);
		// Recon: "Cook" on the Range itself cooks the whole stack, one per tick, no prompt.
		Rs2TileObjectModel range = station(mode.objRange);
		if (range != null)
		{
			pace.before(Pace.Kind.WORLD);
		}
		boolean seen = range != null && Aim.onScreen(range.getLocalLocation());
		boolean cooking = seen && range.click("Cook");
		if (range != null)
		{
			pace.after(Pace.Kind.WORLD);
		}
		if (!cooking)
		{
			lastApproachNote = "range: " + (range == null ? "not found" : !seen ? "not on screen" : "the Cook click didn't go out");
			return ActionResult.NO_REACTION;
		}
		return Waits.untilOrStopped(() -> Rs2Inventory.count(CgIds.RAW_PADDLEFISH) < before, walkWait(range, 3000))
			? ActionResult.CLICKED : ActionResult.NO_REACTION;
	}

	// ------------------------------------------------------------------ equipping & tools

	/**
	 * Wear every armour piece in the inventory and wield the best weapon in ONE quick batch, the
	 * way a player does (several wear/wield clicks fit in a single game tick), then verify.
	 */
	public ActionResult equipStep(CgMode mode)
	{
		java.util.List<Integer> clicked = new java.util.ArrayList<>();
		for (GearSlot s : new GearSlot[]{GearSlot.HELM, GearSlot.BODY, GearSlot.LEGS})
		{
			int[] ids = s.ids(mode);
			for (int t = ids.length - 1; t >= 0; t--)
			{
				int id = ids[t];
				if (Rs2Inventory.contains(id))
				{
					pace.before(Pace.Kind.INVENTORY);
					if (Rs2Inventory.interact(id, "Wear"))
					{
						clicked.add(id);
					}
					pace.after(Pace.Kind.INVENTORY);
					break; // best tier of this slot only
				}
			}
		}
		int weapon = CombatAction.bestUnwornWeapon(mode);
		if (weapon != -1)
		{
			pace.before(Pace.Kind.INVENTORY);
			if (Rs2Inventory.interact(weapon, "Wield"))
			{
				clicked.add(weapon);
			}
			pace.after(Pace.Kind.INVENTORY);
		}
		if (clicked.isEmpty())
		{
			return ActionResult.DONE;
		}
		boolean allOn = sleepUntil(() -> clicked.stream().allMatch(Rs2Equipment::isWearing), 2400);
		return allOn ? ActionResult.CLICKED : ActionResult.NO_REACTION;
	}

	/**
	 * Wiki + recon: every run starts with the tools. Tool Storage only re-issues lost ones.
	 * TODO: confirm the storage option name if this is ever needed.
	 */
	public ActionResult takeTools(CgMode mode)
	{
		ActionResult walk = approach(mode.objTools);
		if (walk != ActionResult.DONE)
		{
			return walk;
		}
		Rs2TileObjectModel tools = station(mode.objTools);
		if (tools == null || !Aim.onScreen(tools.getLocalLocation()) || !tools.click())
		{
			return ActionResult.NOT_AVAILABLE;
		}
		return Waits.untilOrStopped(() -> Rs2Inventory.contains(mode.pickaxe), walkWait(tools, 3000)) ? ActionResult.DONE : ActionResult.NO_REACTION;
	}

	/** Use the teleport crystal ("Activate", recon-confirmed) to jump back to the start room. */
	public ActionResult teleportToStart(CgMode mode)
	{
		if (!Rs2Inventory.contains(mode.teleportCrystal))
		{
			return ActionResult.NOT_AVAILABLE;
		}
		if (!Rs2Inventory.interact(mode.teleportCrystal, "Activate"))
		{
			return ActionResult.NO_REACTION;
		}
		return sleepUntil(() -> !Rs2Inventory.contains(mode.teleportCrystal) || Rs2Player.isAnimating(), 3000)
			? ActionResult.CLICKED : ActionResult.NO_REACTION;
	}

	// ------------------------------------------------------------------ helpers

	/**
	 * Pestle on the spike -> 80 shards. The game asks a Yes/No question first (0.3.11: the user had
	 * to answer it by hand), so answer "Yes" with its number key, the way a player does.
	 */
	public ActionResult crushSpike(CgMode mode)
	{
		int before = Rs2Inventory.itemQuantity(mode.shard);
		if (!yesPromptOpen())
		{
			if (!Rs2Inventory.contains(CgIds.PESTLE) || !Rs2Inventory.contains(mode.spike))
			{
				return ActionResult.NOT_AVAILABLE;
			}
			pace.before(Pace.Kind.INVENTORY);
			boolean sent = Rs2Inventory.combine(CgIds.PESTLE, mode.spike);
			pace.after(Pace.Kind.INVENTORY);
			if (!sent)
			{
				return ActionResult.NO_REACTION;
			}
			sleepUntil(() -> yesPromptOpen() || Rs2Inventory.itemQuantity(mode.shard) > before, 2400);
		}
		if (yesPromptOpen())
		{
			pace.before(Pace.Kind.MENU);
			if (!Rs2Dialogue.keyPressForDialogueOption("Yes"))
			{
				Rs2Dialogue.clickOption("Yes");
			}
			pace.after(Pace.Kind.MENU);
		}
		boolean crushed = sleepUntil(() -> Rs2Inventory.itemQuantity(mode.shard) > before || !Rs2Inventory.contains(mode.spike), 3000);
		if (crushed)
		{
			pace.observed();
		}
		return crushed ? ActionResult.CLICKED : ActionResult.NO_REACTION;
	}

	private static boolean yesPromptOpen()
	{
		return Rs2Dialogue.hasSelectAnOption() || Rs2Dialogue.hasDialogueOption("Yes");
	}

	/**
	 * An item-on-item the game repeats by itself (grym into every water vial, dust into every
	 * unfinished potion): one click, then IN_PROGRESS while the product count keeps rising.
	 */
	private ActionResult combineBatch(int kind, int a, int b, int expectProduct)
	{
		if (batchRunning(kind, Rs2Inventory.itemQuantity(expectProduct)))
		{
			return ActionResult.IN_PROGRESS;
		}
		ActionResult r = combine(a, b, expectProduct);
		if (r == ActionResult.CLICKED)
		{
			batchStarted(kind, Rs2Inventory.itemQuantity(expectProduct));
		}
		return r;
	}

	private ActionResult combine(int a, int b, int expectProduct)
	{
		// Both must be in hand: Rs2Inventory.combine NPEs on a missing second item (0.3.10 log).
		if (!Rs2Inventory.contains(a) || !Rs2Inventory.contains(b))
		{
			return ActionResult.NOT_AVAILABLE;
		}
		int before = Rs2Inventory.itemQuantity(expectProduct);
		// "Use A" -> "on B" is one quick motion (Rs2Inventory.combine spaces the halves 100-175 ms);
		// the next combine follows as soon as this product shows up.
		pace.before(Pace.Kind.INVENTORY);
		boolean sent = Rs2Inventory.combine(a, b);
		pace.after(Pace.Kind.INVENTORY);
		if (!sent)
		{
			return ActionResult.NO_REACTION;
		}
		boolean made = sleepUntil(() -> Rs2Inventory.itemQuantity(expectProduct) > before, 3000);
		if (made)
		{
			pace.observed();
		}
		return made ? ActionResult.CLICKED : ActionResult.NO_REACTION;
	}

	/**
	 * How long to wait for a station click to show a result: the server walks us there first,
	 * and stations are clicked from up to 16 tiles away (0.3.30: the pump click "failed" 8 times
	 * in a row, 35 s, because the walk alone took longer than the 4 s wait and each retry re-clicked).
	 */
	private static int walkWait(Rs2TileObjectModel o, int base)
	{
		WorldPoint at = o == null ? null : o.getWorldLocation();
		WorldPoint me = Microbot.getClientThread().runOnClientThreadOptional(
			() -> Microbot.getClient().getLocalPlayer() == null ? null : Microbot.getClient().getLocalPlayer().getWorldLocation()).orElse(null);
		int d = at == null || me == null ? 0 : InstanceNav.chebyshev(me, at);
		return Math.min(9000, base + 350 * d);
	}

	/** Last station-approach failure, for the log (where the object was vs where we stood). */
	public volatile String lastApproachNote;

	/**
	 * The nearest object with this id, by RAW instance distance. The queryable's nearest() is
	 * nearestReachable() measured from Rs2PlayerModel's location; 0.3.22 stood still next to the bowl
	 * for 50 s "walking" to the range (cook: WALKING / NO_PATH, the player never moved).
	 */
	public Rs2TileObjectModel station(int objectId)
	{
		WorldPoint me = Microbot.getClientThread().runOnClientThreadOptional(
			() -> Microbot.getClient().getLocalPlayer() == null ? null : Microbot.getClient().getLocalPlayer().getWorldLocation())
			.orElse(null);
		Rs2TileObjectModel best = null;
		int bestD = Integer.MAX_VALUE;
		for (Rs2TileObjectModel o : Microbot.getRs2TileObjectCache().query().withId(objectId).toList())
		{
			WorldPoint at = o.getWorldLocation();
			int d = me == null || at == null ? Integer.MAX_VALUE - 1 : InstanceNav.chebyshev(me, at);
			if (best == null || d < bestD)
			{
				best = o;
				bestD = d;
			}
		}
		return best;
	}

	/**
	 * DONE once the station is close enough to just click: clicking an object pathfinds
	 * server-side, so anywhere in the same room (<= 16 tiles) will do. Farther: walk with nav.
	 */
	private ActionResult approach(int objectId)
	{
		Rs2TileObjectModel obj = station(objectId);
		if (obj == null)
		{
			lastApproachNote = "object " + objectId + " not in the scene";
			return ActionResult.NOT_AVAILABLE;
		}
		WorldPoint at = obj.getWorldLocation();
		WorldPoint me = Microbot.getClientThread().runOnClientThreadOptional(
			() -> Microbot.getClient().getLocalPlayer().getWorldLocation()).orElse(null);
		if (at != null && me != null && me.getPlane() == at.getPlane() && InstanceNav.chebyshev(me, at) <= 16)
		{
			// Close enough only if we can actually see it (1.6.1): with the station off screen, every
			// click on it failed in place (1.6.0 log: 114 s of "cook: NO_REACTION", into the boss with
			// 14 raw fish; earlier the pump, 50 s). Turn the camera to it; if that doesn't do it, walk
			// up to it, which puts it on screen at any camera angle.
			if (Aim.onScreen(obj.getLocalLocation()))
			{
				return ActionResult.DONE;
			}
			if (InstanceNav.chebyshev(me, at) <= 2)
			{
				return ActionResult.DONE; // right next to it: let the click try (and report) anyway
			}
			lastApproachNote = "object " + objectId + " at " + at + " not on screen from " + InstanceNav.chebyshev(me, at) + " tiles: walking up to it";
			InstanceNav.Result near = nav.step(at, 1);
			return near == InstanceNav.Result.ARRIVED ? ActionResult.DONE : near == InstanceNav.Result.NO_PATH ? ActionResult.NO_PATH : ActionResult.WALKING;
		}
		InstanceNav.Result r = nav.step(at, 8);
		switch (r)
		{
			case ARRIVED:
				return ActionResult.DONE;
			case NO_PATH:
				lastApproachNote = "object " + objectId + " at " + at + ", me " + me + ": no path";
				return ActionResult.NO_PATH;
			default:
				return ActionResult.WALKING;
		}
	}

	private static String itemName(int id)
	{
		return Microbot.getClientThread().runOnClientThreadOptional(
			() -> Microbot.getClient().getItemDefinition(id).getName()).orElse(null);
	}
}
