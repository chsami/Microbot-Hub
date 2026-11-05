package net.runelite.client.plugins.microbot.cgprep.plan;

import net.runelite.client.plugins.microbot.cgprep.data.GearSlot;
import net.runelite.client.plugins.microbot.cgprep.data.Monster;
import net.runelite.client.plugins.microbot.cgprep.data.PrepProfile;
import net.runelite.client.plugins.microbot.cgprep.data.Resource;
import net.runelite.client.plugins.microbot.cgprep.map.GauntletMaze;

/**
 * Picks the next {@link PrepGoal} from observed state. Stateless: every call re-derives the answer
 * from the ledger, the maze and the timer, so a missed click or an interrupted action just gets
 * re-planned next tick instead of leaving the bot out of sync.
 *
 * Priority (top wins):
 *  0. Mid-way through a node -> finish it (never hop between half-harvested nodes).
 *  1. Time is up (or everything is gathered) -> go home, craft, brew, cook, equip, hand over.
 *  2. No weapon yet and a frame in hand -> craft it (fighting unarmed is slow).
 *  3. T3 component missing, demi-boss known and time allows -> fight it.
 *  4. Frame or shards missing -> fight weak/strong monsters.
 *  5. Node resource missing and a live node known -> gather from the NEAREST such node.
 *  6. Something still missing -> explore the nearest unexplored room.
 *  7. Nothing reachable left to do -> go home and finish with what we have.
 */
public final class PrepPlanner
{
	public static final class Inputs
	{
		public Ledger ledger;
		public Needs needs;
		public GauntletMaze maze;
		public PrepProfile profile;
		public int secondsLeft;
		/** Seconds reserved at the end for walking home + crafting + cooking + brewing. */
		public int finishReserve;
		/** Skip demi-bosses entirely (dev toggle). */
		public boolean skipDemiBosses;
		/** Seconds a demi-boss fight is assumed to take (incl. walking). */
		public int demiBossBudget;
		public boolean toolsMissing;
		public boolean toolStorageKnown;
		/**
		 * Per-run random seed for orderings that don't matter to the outcome (which affordable
		 * piece to craft first, cook before or after crafting). 0 = fixed priority order.
		 */
		public int runSeed;
		/** Resource whose node we're mid-way through (GatherAction's commitment), or null. */
		public Resource committedGather;
		/** A live weak monster (rat/spider/bat) within reach right now. */
		public boolean weakNearby;
		/** Tiles to the nearest live weak / medium monster (MAX_VALUE = none in the scene). */
		public int weakTiles = Integer.MAX_VALUE;
		public int strongTiles = Integer.MAX_VALUE;
		/** A weak monster is attacking us (targeting us, next to us). */
		public boolean weakOnUs;
		/** Holding a teleport crystal (cheap trip home, so exploring far is fine). */
		public boolean hasTeleport;
		/** Seconds the user wants left at handover (part of finishReserve). */
		public int handoverReserve;
		/** Tools (by the resource they gather) a drop was just sent for: don't re-issue it. */
		public java.util.Set<Resource> droppedRecently = java.util.Collections.emptySet();
		/** A field brew just failed; don't choose it again for a while. */
		public boolean fieldBrewBlocked;
		/** The pump failed too often this run (1.6.0): no last-resort pump brew. */
		public boolean pumpBlocked;
		/** The game teleports us to the boss when the timer ends and the bot fights (Prep & Boss, Boss Only). */
		public boolean timerTeleports;
		/** Nearest raw paddlefish lying on the floor (1.6.0): where, how many there, walking tiles; null = none. */
		public net.runelite.api.coords.WorldPoint groundFishAt;
		public int groundFishCount;
		public int groundFishTiles = Integer.MAX_VALUE;
		public int hpPct = 100;
		public int prayPct = 100;
		/** Something is attacking us right now (no inventory chores mid-fight). */
		public boolean inDanger;
		/** The demi-boss that is attacking / chasing us right now, or null (PrayerGuard). */
		public Monster demiOnUs;
		/** Vials made at the bowl this run (user, 0.3.26: 2 during prep, a 3rd only on the final trip). */
		public int vialsMade;
		/** Egniol potions wanted (setting; 3 = the 3rd is a nice-to-have, user 0.3.29). */
		public int potionTarget = 3;
		/** Fish that are enough (user, 0.3.32: at least 12, ideally the target of 16). */
		public int fishMin = 12;
		/** More fish come later (the setting is above {@link PrepPlanner#fishTargetNow} for now): keep the harpoon. */
		public boolean fishLater;
		/** The armour target rises later (T3 setting, see {@link PrepPlanner#profileNow}): keep the pickaxe and axe. */
		public boolean armourLater;
		/** The demi-boss we're in the middle of fighting (attacking it on purpose), or null. */
		public Monster demiFighting;
		/** A weak / medium monster we're in the middle of fighting on purpose (shards), or null. */
		public Monster mobFighting;
		/** A "what if" decision (the lookahead): must not change the planner's own state. */
		public boolean projection;
		/** Low HP, no food, under attack: get out (teleport crystal / walk home). */
		public boolean flee;
		/** What to go out and gather now: the needs with fish capped by the slot budget (set by decide). */
		public Needs gather;
		/**
		 * What's still REQUIRED (set by decide): the needs without the loose extras (fish past the
		 * minimum, the last herb). Gear (T2 armour, both T3 weapons) is always required (user).
		 */
		public Needs required;

		/** Shallow copy for the lookahead (ledger/needs are swapped in by the caller). */
		public Inputs copy()
		{
			Inputs c = new Inputs();
			c.ledger = ledger;
			c.needs = needs;
			c.maze = maze;
			c.profile = profile;
			c.secondsLeft = secondsLeft;
			c.finishReserve = finishReserve;
			c.skipDemiBosses = skipDemiBosses;
			c.demiBossBudget = demiBossBudget;
			c.toolsMissing = toolsMissing;
			c.toolStorageKnown = toolStorageKnown;
			c.runSeed = runSeed;
			c.committedGather = committedGather;
			c.weakNearby = weakNearby;
			c.weakTiles = weakTiles;
			c.strongTiles = strongTiles;
			c.weakOnUs = weakOnUs;
			c.hasTeleport = hasTeleport;
			c.handoverReserve = handoverReserve;
			c.droppedRecently = droppedRecently;
			c.fieldBrewBlocked = fieldBrewBlocked;
			c.pumpBlocked = pumpBlocked;
			c.timerTeleports = timerTeleports;
			c.groundFishAt = groundFishAt;
			c.groundFishCount = groundFishCount;
			c.groundFishTiles = groundFishTiles;
			c.hpPct = hpPct;
			c.prayPct = prayPct;
			c.inDanger = inDanger;
			c.demiOnUs = demiOnUs;
			c.vialsMade = vialsMade;
			c.potionTarget = potionTarget;
			c.fishMin = fishMin;
			c.fishLater = fishLater;
			c.armourLater = armourLater;
			c.demiFighting = demiFighting;
			c.mobFighting = mobFighting;
			c.projection = projection;
			c.flee = flee;
			return c;
		}
	}

	static final int OPENING_SHARDS = 100;
	/** T1 -> T2 weapon upgrade (wiki: T2 = +50 shards). */
	static final int T2_WEAPON_SHARDS = 50;
	static final int VIAL_COST = 10;
	static final int DUST_COST = 10;
	static final int CRYSTAL_COST = 50;
	private final ExplorePlanner explorer = new ExplorePlanner();
	/** Last ranking, for the decision log. */
	private volatile java.util.List<ExplorePlanner.Option> lastRanking = java.util.Collections.emptyList();

	public java.util.List<ExplorePlanner.Option> lastRanking()
	{
		return lastRanking;
	}

	/** Put back the ranking of the real decision after a "what if" decide (lookahead). */
	public void restoreRanking(java.util.List<ExplorePlanner.Option> r)
	{
		lastRanking = r;
	}

	/**
	 * The next goal. Inventory chores (crush the spike, drop a finished tool, decant, brew steps)
	 * don't get a turn of their own any more when there's walking to do (user, 0.3.15: "process
	 * while running"): they ride along on the movement goal and the script does them right after
	 * its walking click. At home they wait for the station work, except the spike (its shards are
	 * needed for crafting) and anything left before the handover.
	 */
	/**
	 * Where we'd walk next if nothing on the way were worth a stop (the doorway into the next
	 * route room, or home), for the "on the way" detours and their order (0.3.29). Null = unknown.
	 */
	private volatile net.runelite.api.coords.WorldPoint routeHint;

	public net.runelite.api.coords.WorldPoint routeHint()
	{
		return routeHint;
	}

	public PrepGoal decide(Inputs in)
	{
		routeHint = null;
		PrepGoal main = decideMain(in);
		if (main != null && main.type == PrepGoal.Type.RETURN_TO_START && routeHint == null)
		{
			routeHint = homeHint(in);
		}
		if (main == null || in.flee)
		{
			return main;
		}
		PrepGoal chore = chore(in);
		if (chore == null)
		{
			return main;
		}
		if (main.type == PrepGoal.Type.HANDOVER || chore.type == PrepGoal.Type.CRUSH_SPIKE && maze(in).inStartRoom())
		{
			return chore; // nothing walks any more (or its shards are needed at the bowl): do it now
		}
		if (chore.type != main.type)
		{
			// Rides along: done right after the goal's next walking click (also the short walks to
			// the bowl/pump/range at home), so it overlaps with running (user, 0.3.20).
			main.chore = chore;
		}
		return main;
	}

	private static GauntletMaze maze(Inputs in)
	{
		return in.maze;
	}

	/** Holding the pestle and mortar with nothing left for it to do: every brew still to make has its dust, no spike. */
	static boolean pestleDone(Ledger l, Needs n)
	{
		return l.pestle && l.get(Resource.SPIKE) == 0
			&& n.potionsToMake * net.runelite.client.plugins.microbot.cgprep.data.CgIds.DUST_PER_POTION <= l.dust;
	}

	/** The inventory chore due now, or null. */
	PrepGoal chore(Inputs in)
	{
		Ledger l = in.ledger;
		Needs n = in.needs;
		// A corrupted spike (Bear) isn't one of our upgrades: crush it into 80 shards (user).
		if (l.get(Resource.SPIKE) > 0 && l.pestle && !in.droppedRecently.contains(Resource.SPIKE))
		{
			return PrepGoal.of(PrepGoal.Type.CRUSH_SPIKE, "spike -> 80 shards");
		}
		// New armour / the crafted weapon go on while walking (user, 0.3.20: "process while moving"
		// for everything that's only inventory clicks). Wear/wield doesn't stop running.
		boolean weaponMade = l.tier.getOrDefault(GearSlot.STAFF, 0) + l.tier.getOrDefault(GearSlot.BOW, 0)
			+ l.tier.getOrDefault(GearSlot.HALBERD, 0) > 0;
		if (l.armourInInventory || (weaponMade && !l.weaponEquipped))
		{
			return PrepGoal.of(PrepGoal.Type.EQUIP, "wear the new gear");
		}
		// Tools go as soon as their job is done (user): pickaxe after the ore, axe after the bark,
		// harpoon after the fish (not mid-spot).
		// Wrapping up (the final trip): no more gathering, so every gathering tool goes (frees the
		// slots the last crafts need; 0.3.29 juggled bowl / range / eating for room).
		boolean wrapUp = in.secondsLeft >= 0 && in.secondsLeft <= in.finishReserve;
		if (l.pickaxe && ((n.get(Resource.ORE) == 0 && !in.armourLater) || wrapUp) && !in.droppedRecently.contains(Resource.ORE))
		{
			return PrepGoal.dropTool(Resource.ORE, "done with ore");
		}
		if (l.axe && ((n.get(Resource.BARK) == 0 && !in.armourLater) || wrapUp) && !in.droppedRecently.contains(Resource.BARK))
		{
			return PrepGoal.dropTool(Resource.BARK, "done with bark");
		}
		// 1.2.0: the harpoon stays while a spot near home could still give fish with the time left
		// at the end (see topUp); mid-prep only while the bag has room for it.
		boolean keepHarpoon = topUpSeed == in.runSeed && in.runSeed != 0
			|| (topUpPossible(in) && (wrapUp || l.freeSlots >= 3));
		if (l.harpoon && !keepHarpoon && ((n.get(Resource.RAW_FISH) == 0 && !in.fishLater) || wrapUp) && in.committedGather != Resource.RAW_FISH
			&& !in.droppedRecently.contains(Resource.RAW_FISH))
		{
			return PrepGoal.dropTool(Resource.RAW_FISH, "done fishing");
		}
		// The pestle and mortar (user, 0.4.7): once no more dust will be crushed (every brew still to
		// make already has its dust, no spike to crush) it goes when a fish needs its slot.
		if (pestleDone(l, n) && l.freeSlots == 0 && n.get(Resource.RAW_FISH) > 0 && !in.droppedRecently.contains(Resource.SHARDS))
		{
			return PrepGoal.dropTool(Resource.SHARDS, "pestle and mortar not needed any more: room for a fish");
		}
		// Leftover material nothing still to be made needs: drop it once everything that uses it
		// is made (user, 0.3.26: 9 linum gathered for 7). One stack / item per chore.
		for (Resource r : new Resource[]{Resource.ORE, Resource.BARK, Resource.LINUM, Resource.GRYM, Resource.FRAME})
		{
			// A spare grym leaf is kept while a 3rd brew could still be needed (a sip calls for it).
			boolean keepSpareGrym = r == Resource.GRYM && (l.potionDoses + l.dosesDrunk) / 3 + l.unfinishedPotions < in.potionTarget
				&& l.get(Resource.GRYM) <= 1;
			if (l.get(r) > 0 && n.demand.getOrDefault(r, 0) == 0 && !keepSpareGrym && !in.droppedRecently.contains(r))
			{
				return PrepGoal.dropSurplus(r, l.get(r) + " " + r.label + " left over, nothing needs it");
			}
		}
		// Decanting (user, 0.3.26): only a 3-dose + 1-dose, next to a fishing spot, with a grym leaf
		// and the dust (or shards + pestle) for a fresh brew: 3 + 1 -> 4 + an empty vial, filled at
		// the spot and brewed straight away. Otherwise the 3rd potion is made on the final trip.
		if (n.potionsToMake > 0 && l.doseCounts[3] > 0 && l.doseCounts[1] > 0 && freshBrewReady(l) && nearFishingSpot(in))
		{
			return PrepGoal.of(PrepGoal.Type.DECANT, "3 + 1 -> 4 + a vial for a fresh brew at the fishing spot (" + doses(l) + ")");
		}
		// (At home too: only filling needs the pump; grym, dust and combining are inventory clicks.)
		if (!in.fieldBrewBlocked && n.potionsToMake > 0 && fieldBrewable(l))
		{
			return PrepGoal.of(PrepGoal.Type.BREW, "potion in the field (" + doses(l) + ")");
		}
		return null;
	}

	private PrepGoal decideMain(Inputs in)
	{
		Ledger l = in.ledger;
		Needs n = in.needs;
		GauntletMaze maze = in.maze;

		// Escape beats everything: 0.3.12 died to the Dragon standing still with 0 prayer and no food.
		if (in.flee && !maze.inStartRoom())
		{
			return PrepGoal.of(PrepGoal.Type.RETURN_TO_START, "escape: hp " + in.hpPct + "%, no food");
		}

		// Home with low HP and nothing cooked (0.4.7: stood in the start room at 21% for a minute,
		// picking a medium monster and bailing out of it every 3 s): cook the fish now and eat.
		if (maze.inStartRoom() && in.hpPct < 50 && l.cookedFish == 0 && l.get(Resource.RAW_FISH) > 0)
		{
			return PrepGoal.of(PrepGoal.Type.COOK, "hp " + in.hpPct + "%, nothing cooked: cook to eat");
		}

		// A demi-boss fight under way is finished whatever the clock says, when its drop is one we
		// need (user, 0.3.30: teleported away mid-Dragon at 2:20 because "demi time" had run out,
		// and never got the orb). Its loot is picked up before anything else (script).
		// Any demi-boss counts while a T3 part is missing, not just "its own" type (1.2.1): a demi
		// drops its own part if we don't have it, else one we're missing (wiki), so a 2nd Dark Beast
		// with the bowstring in hand drops the orb. 1.2.0 walked off a 2nd Dark Beast mid-fight at
		// 11% prayer (it kept shooting) and went into the boss with a T2 staff.
		Monster df = in.demiFighting;
		if (df != null && !in.flee && (in.secondsLeft < 0 || in.secondsLeft > 15)
			&& (n.get(Resource.ORB) > 0 || n.get(Resource.BOWSTRING) > 0))
		{
			return PrepGoal.fight(df, "finish the " + df + " fight (its drop is needed)");
		}
		// Once the final trip has started it stays started (no more exploring or gathering).
		if (finishedSeed == in.runSeed && in.runSeed != 0 && !in.projection)
		{
			return finish(in, "wrapping up");
		}
		// Never on the way out: the final trip doesn't gather any more.
		boolean nearEnd = in.secondsLeft >= 0 && in.secondsLeft <= in.finishReserve + 20;
		if (in.toolsMissing && in.toolStorageKnown && !nearEnd)
		{
			return PrepGoal.of(PrepGoal.Type.GET_TOOLS, "no pickaxe/axe/harpoon");
		}

		// The finishing reserve is what the final trip really needs (getting home + what's left to
		// craft, brew and cook), plus the handover time, capped by the setting. A flat 105 s
		// ended the 0.3.8 run before the Dark Beast was found, with 1:38 still on the clock.
		in.finishReserve = Math.min(in.finishReserve, estimateFinishSeconds(in) + in.handoverReserve);
		boolean timeUp = in.secondsLeft >= 0 && in.secondsLeft <= in.finishReserve;

		// Slot budget (user, 0.3.11 run: 15 of 28 slots were paddlefish when the Dragon's loot,
		// the linum and the dust all needed room, and it froze). Fish never free their slot (cooking
		// keeps it), so they only get the slots left over after everything else still to come.
		int fishRoom = fishRoom(l, n);
		// Fish in proper batches: don't start a spot for fewer than 3 (or what's left to get).
		int fishCap = fishRoom >= Math.min(3, n.get(Resource.RAW_FISH)) ? fishRoom : 0;
		// An easy spot (right by us / on the way) doesn't wait for the loot reserve (user, 0.3.29:
		// "if unsure, just pick them up"): a mob drop that needs the slot later gets it by dropping
		// a raw paddlefish (LootAction). Never fish just to drop, so the planned non-loot items
		// (stacks still to start, grym, vials, dust) keep their slots.
		int easyRoom = Math.max(0, l.freeSlots - reservedSlots(l, n, false));
		if (easyRoom > fishCap && n.get(Resource.RAW_FISH) > 0 && easyFishingSpot(in))
		{
			fishCap = easyRoom;
			fishRoom = Math.max(fishRoom, easyRoom);
		}
		in.gather = n.withCap(Resource.RAW_FISH, fishCap);
		// The last brew (only needed after sips, to keep the boss's 6 doses): its grym leaf from
		// a node within reach, never a search across the map (user, 0.3.29).
		boolean grymOptional = in.potionTarget >= 3 && n.potionsToMake == 1 && (l.potionDoses + l.dosesDrunk) / 3 >= 2
			&& n.get(Resource.GRYM) > 0;
		boolean grymExtra = grymOptional; // a nice-to-have, even when a node close by makes it worth taking
		if (grymOptional)
		{
			GauntletMaze.Node gn = maze.nearestLiveNode(Resource.GRYM);
			if (gn == null || maze.walkTiles(gn) > OPTIONAL_GRYM_TILES)
			{
				in.gather = in.gather.withCap(Resource.GRYM, 0);
			}
			else
			{
				grymOptional = false; // one close by: worth it
			}
		}
		// Fish past the minimum (user, 0.3.32: 12 is enough, 16 ideal): only from a spot that's
		// right by us or on the way; no trips for them, and prep counts as done without them.
		int fishHave = l.cookedFish + l.get(Resource.RAW_FISH);
		boolean fishOptional = fishHave >= in.fishMin && n.get(Resource.RAW_FISH) > 0;
		boolean fishExtra = fishOptional;
		if (fishOptional)
		{
			if (easyFishingSpot(in) || fishTripFits(in, n))
			{
				fishOptional = false; // easy, or there's time for the trip: take them
			}
			else
			{
				in.gather = in.gather.withCap(Resource.RAW_FISH, 0);
			}
		}

		// What's still required (user, 0.3.34): gear always; fish past the minimum and the last herb
		// are loose. 0.3.33 teleported home at 1:58 "for fish" with 13 of the 12 needed, and walked
		// back out for them, while 134 shards for the T2 body were the real gap.
		Needs req = n;
		if (grymExtra)
		{
			req = req.withCap(Resource.GRYM, 0);
		}
		if (fishExtra)
		{
			req = req.withCap(Resource.RAW_FISH, 0);
		}
		in.required = req;

		// A shard fight under way is finished (half a medium monster is no shards at all).
		Monster mf = in.mobFighting;
		if (mf != null && !timeUp && in.demiOnUs == null && n.get(Resource.SHARDS) > 0)
		{
			return PrepGoal.fight(mf, "finish the " + (mf == Monster.STRONG ? "medium" : "weak") + " monster (shards: "
				+ n.get(Resource.SHARDS) + " still needed)");
		}

		// 0. Commitment: once a node is started, stay on it while we still need that resource.
		Resource c = in.committedGather;
		if (c != null && !stackable(c) && l.freeSlots <= 0)
		{
			c = null; // inventory full: stop instead of clicking the spot with nowhere to put it
		}
		if (c == Resource.RAW_FISH && fishRoom <= 0)
		{
			c = null; // the rest of the bag is spoken for: leave the spot, come back on a later trip
		}
		if (c != null && !timeUp)
		{
			if (in.gather.get(c) > 0)
			{
				return PrepGoal.gather(c, "finish node (need " + in.gather.get(c) + " " + c.label + ")");
			}
			if (c == Resource.RAW_FISH && n.get(c) > 0)
			{
				return PrepGoal.gather(c, "finish fishing spot (" + fishRoom + " slot(s) free for fish)");
			}
		}

		if (timeUp)
		{
			PrepGoal cr = armourCritical(in, true, false);
			if (cr != null)
			{
				return cr;
			}
		}
		if (timeUp || n.gatheringDone() || doneExceptOptional(n, grymOptional, fishOptional))
		{
			return finish(in, timeUp ? "timer " + in.secondsLeft + "s <= reserve" : "everything gathered");
		}

		// Inventory. 28 slots: tools, vials, grym and fish don't stack (ore/bark/linum/shards do).
		// 0.3.4 run: filled up at 13 of 16 fish, then clicked the spot with a full inventory, and
		// walked far for fish with 1 slot left. Free space cheaply first, else go home and process.
		PrepGoal inv = inventory(in);
		if (inv != null)
		{
			return inv;
		}

		// Passing through the start room mid-prep: process what we can (armour, staff, potions,
		// fish), the same as at the end but without handing over.
		if (maze.inStartRoom() && l.tier.getOrDefault(GearSlot.STAFF, 0) + l.tier.getOrDefault(GearSlot.BOW, 0) > 0)
		{
			PrepGoal p = process(in, "depot", false);
			if (p != null)
			{
				return p;
			}
		}

		// ---------------------------------------------------------------- the two loops (user)
		// Loop 1: shards (and the first frame) for the T2 staff + vials, from weak monsters around
		//   the start, picking up resources nearby on the way; only PEEK at outer rooms.
		//   Then home: T1 -> T2 staff, vials, cook what we have.
		// Loop 2: back out for the remaining materials and the demi-bosses (outer ring only). A
		//   demi-boss we run into is fought on the spot (with the T2 staff). Weak/medium monsters
		//   only once materials and components are done and shards (or the bow's frame) are short.
		int staffTier = l.tier.getOrDefault(GearSlot.STAFF, 0);
		boolean hasWeapon = staffTier > 0 || l.tier.get(GearSlot.BOW) > 0 || l.tier.get(GearSlot.HALBERD) > 0;
		int staffTarget = in.profile.targetTier.getOrDefault(GearSlot.STAFF, 0);
		boolean prepWeaponReady = staffTarget >= 2 ? staffTier >= 2 : hasWeapon;
		int vialsWanted = Math.min(n.vialsToMake, Math.max(0, 2 - in.vialsMade)); // 2 during prep (user)

		boolean calibrated = maze.roomX() >= 0 && maze.startX() >= 0;
		if (!prepWeaponReady)
		{
			// ----- Loop 1 = the RED route (user, 2026-10-28): the 5 rooms around the start, peeking
			// into the 3 outer-ring rooms beyond the front ones for demi-bosses. Weak monsters on the
			// way give the frame + shards; resources in those rooms are picked up on the way.
			boolean haveFrame = hasWeapon || l.get(Resource.FRAME) > 0;
			int shardGoal = (staffTarget >= 2 && staffTier < 2 ? T2_WEAPON_SHARDS : 0) + vialsWanted * VIAL_COST;
			boolean ready = haveFrame && l.get(Resource.SHARDS) >= shardGoal;
			Route.Step next = calibrated ? nextRed(in) : null;
			routeHint = next != null ? doorHint(in, next.x, next.z) : ready ? homeHint(in) : null;
			// The ONLY trip home before the T2 staff: frame + shards for the T2 staff and 2 vials, and
			// the red route walked (every run starts with it, for the peeks).
			// Needed nodes in a room we've just lit, ours or next door (a peeked one too): grab them
			// now, it beats walking back for them (user, 1.7.1). Not in a room with a demi-boss.
			PrepGoal lit = litRoomNodes(in);
			if (lit != null)
			{
				return lit;
			}
			if (ready && next == null)
			{
				return maze.inStartRoom() ? PrepGoal.craft(GearSlot.STAFF, "loop 1 done: T2 staff")
					: PrepGoal.of(PrepGoal.Type.RETURN_TO_START, "loop 1 done: frame + " + l.get(Resource.SHARDS) + "/" + shardGoal + " shards");
			}
			// Weak monsters: for the frame (their only source) anything within 15 tiles; for shards
			// alone only one that's already on us or a few steps away (user, 0.3.26: standing still
			// 5-10 s for a spider to arrive for a few shards is bad completion per minute).
			if (!ready && (haveFrame ? in.weakOnUs || in.weakTiles <= 5 : in.weakTiles <= 15))
			{
				return PrepGoal.fight(Monster.WEAK, "loop 1: " + (haveFrame ? "" : "frame + ") + "shards " + l.get(Resource.SHARDS) + "/" + shardGoal);
			}
			PrepGoal near = onTheWay(in, false);
			if (near != null)
			{
				return near;
			}
			if (next != null)
			{
				return PrepGoal.explore(new int[]{next.x, next.z}, "red route: " + (next.kind == Route.Kind.PEEK ? "peek for a demi-boss" : "loop room"));
			}
			// Red route done and still no frame: a medium monster (wolf / scorpion / unicorn) also
			// drops one, plus more shards; tankier, so only as a last resort with no weak one around
			// (user, 0.3.26).
			if (!haveFrame && in.weakTiles > 15 && in.strongTiles <= 15)
			{
				return PrepGoal.fight(Monster.STRONG, "loop 1: frame from a medium monster (red route done, no weak one near)");
			}
			// Red route done, still no frame / shards (rare): reach out into the middle ring, but
			// conservatively, it's a walk home afterwards (user).
			ExplorePlanner.Option pick = search(in, true, !haveFrame, Math.max(1, shardGoal - l.get(Resource.SHARDS)), false, 20);
			if (pick != null)
			{
				return toGoal(pick, n);
			}
			pick = search(in, false, !haveFrame, Math.max(1, shardGoal - l.get(Resource.SHARDS)), false, Integer.MAX_VALUE);
			if (pick != null)
			{
				return toGoal(pick, n);
			}
			return PrepGoal.fight(Monster.WEAK, "loop 1: " + (haveFrame ? "" : "frame + ") + "shards " + l.get(Resource.SHARDS) + "/" + shardGoal);
		}

		// ----- Loop 2
		// At the bowl: the vials (filled later at a fishing spot, never at the pump).
		if (maze.inStartRoom() && vialsWanted > 0 && l.get(Resource.SHARDS) >= VIAL_COST)
		{
			return PrepGoal.of(PrepGoal.Type.MAKE_VIALS, vialsWanted + " vial(s) to fill at a fishing spot");
		}

		boolean demisAllowed = !in.skipDemiBosses && in.secondsLeft - in.demiBossBudget > in.finishReserve;
		boolean componentsNeeded = n.get(Resource.BOWSTRING) > 0 || n.get(Resource.ORB) > 0;
		// Demi-bosses: one we've run into, or one seen earlier (e.g. on a red-route peek: "head
		// there and kill them", user), any distance.
		if (demisAllowed && componentsNeeded)
		{
			Monster close = closestDemi(maze, n, 24, 15_000);
			Monster known = close != null ? null : closestDemi(maze, n, Integer.MAX_VALUE, Long.MAX_VALUE);
			Monster target = close != null ? close : known;
			// T2 armour materials right by us come before walking off to a demi-boss we merely know
			// about (user, 0.4.7: after the Bear at ~4:00 it left needed ore/bark in the room).
			if (target != null && close == null && target != in.demiOnUs)
			{
				PrepGoal armour = armourNearby(in);
				if (armour != null)
				{
					return armour;
				}
			}
			if (target != null)
			{
				String ready = readyForDemi(in, target == in.demiOnUs);
				if (ready == null)
				{
					return PrepGoal.fight(target, (close != null ? "ran into a " : "going for the ") + target
						+ " with the T" + staffTier + " staff");
				}
				// Not fit to fight it (0.3.12: 3 cooked fish, no potion, prayer drained by the first
				// two -> died to the Dragon): leave it for later and make the potion out here. Home
				// can't help any more (no mid-prep cooking or pump; brew steps are inventory-only):
				// 0.3.23 walked in and out of the start room for 30 s "getting ready for the Bear".
			}
		}

		// Shards (user, 0.3.34: "T2 armour and both T3 weapons are absolutely required"). In any
		// number they only come from monsters: 80-100 from a medium one, 20-30 from a weak one (nodes
		// and demi-bosses give some too). When what's still to come won't cover them, a medium
		// monster close by is worth its ~15 s now, while we're here (0.3.33 walked out of a room of
		// them at 2:05 and handed over 104 short, with the T2 body's materials in the bag); a weak
		// one already on us too.
		int shardShort = shardShortfall(in, demisAllowed && componentsNeeded);
		int slack = slackSeconds(in);
		if (shardShort > 0 && in.demiOnUs == null && !hungry(in))
		{
			if (shardShort >= MEDIUM_WORTH && in.strongTiles <= HUNT_NEAR_TILES && slack >= STRONG_KILL_SECONDS + 15)
			{
				return PrepGoal.fight(Monster.STRONG, "shards short: " + n.get(Resource.SHARDS) + " needed, ~"
					+ (n.get(Resource.SHARDS) - shardShort) + " expected from the rest; a medium monster " + in.strongTiles
					+ " tiles away (80-100)");
			}
			if (in.weakOnUs && slack >= WEAK_KILL_SECONDS + 10)
			{
				return PrepGoal.fight(Monster.WEAK, "shards short by ~" + shardShort + ": the weak monster on us (20-30)");
			}
		}

		// Empty vials + a fishing spot close by (fished out or not): fill them there (user: no pump).
		if (n.potionsToMake > 0 && l.emptyVials > 0)
		{
			GauntletMaze.Node spot = maze.nearestFishingSpot();
			// Low prayer and no potion: a potion is the way back to fighting demis, so go further for water.
			int reach = in.prayPct < 30 && l.potionDoses == 0 ? 40 : NEAR_TILES;
			if (spot != null && maze.walkTiles(spot) >= 0 && maze.walkTiles(spot) <= reach)
			{
				return PrepGoal.of(PrepGoal.Type.FILL_VIALS, l.emptyVials + " vial(s) at the fishing spot " + maze.walkTiles(spot) + " tiles away");
			}
		}

		// Only fish left (no demi drop still being hunted) and a crystal in the bag: when the nearest
		// spot is a long walk but a live one is lit next to home, teleport home and fish there; the
		// final trip is then a few steps (0.3.25 walked 83 tiles for 7 fish with a crystal in the
		// bag, then all the way back).
		boolean onlyFish = n.get(Resource.RAW_FISH) > 0 && !fishOptional && slotsWanted(n) == n.get(Resource.RAW_FISH)
			&& n.get(Resource.ORE) + n.get(Resource.BARK) + n.get(Resource.LINUM) == 0 && !(demisAllowed && componentsNeeded);
		if (onlyFish && in.hasTeleport && !maze.inStartRoom() && maze.startX() >= 0 && in.committedGather != Resource.RAW_FISH)
		{
			int sx = maze.startX();
			int sz = maze.startZ();
			GauntletMaze.Node home = maze.liveNodeWhere(Resource.RAW_FISH, (x, z) -> Route.nearStart(sx, sz, x, z));
			GauntletMaze.Node nearest = maze.nearestLiveNode(Resource.RAW_FISH);
			int far = nearest == null ? Integer.MAX_VALUE : maze.walkTiles(nearest);
			if (home != null && nearest != home && far > 25)
			{
				return PrepGoal.teleportHome("only fish left, nearest spot " + (far == Integer.MAX_VALUE ? "unknown" : far + " tiles")
					+ ", one next to home: teleport, fish it, finish");
			}
		}

		boolean nodeNeeds = false;
		for (Resource r : new Resource[]{Resource.ORE, Resource.BARK, Resource.LINUM, Resource.GRYM, Resource.RAW_FISH})
		{
			// The last herb and fish past the minimum don't send us down the route (0.3.33 went
			// back out to the blue route for 3 optional fish with 134 shards still missing).
			nodeNeeds |= (r == Resource.GRYM ? in.gather.get(r) : r == Resource.RAW_FISH && fishOptional ? 0 : n.get(r)) > 0;
		}
		// Where the route goes next (for the detours and their order), before looking around.
		if (calibrated && ((demisAllowed && componentsNeeded) || nodeNeeds))
		{
			int[] rr = loop2RouteRoom(in, demisAllowed && componentsNeeded);
			if (rr != null)
			{
				routeHint = doorHint(in, rr[0], rr[1]);
			}
		}
		// Anything we still need right by us: take it as we pass (fish within the slot budget).
		PrepGoal near = onTheWay(in, true);
		if (near != null)
		{
			return near;
		}
		// T2 armour materials close by come before heading out for the T3 weapons' demi drops
		// (user, 0.4.7): only when they're within a short walk, so the hunt isn't delayed by trips.
		PrepGoal armour = armourNearby(in);
		if (armour == null)
		{
			armour = armourCritical(in, false, true);
		}
		if (armour != null)
		{
			return armour;
		}
		// The route: BLUE straight sideways to the outer ring, then the GREEN ring loop, while
		// demi drops are missing (and time allows) or resources are still to find.
		// Only with time to use what the route finds (1.7.0 replay: the blue route was taken at t-55).
		boolean routeTime = in.secondsLeft < 0 || in.secondsLeft - in.finishReserve > ROUTE_MIN_SLACK;
		if (calibrated && routeTime && ((demisAllowed && componentsNeeded) || nodeNeeds))
		{
			int sign = blueSide(in);
			for (int[] room : Route.blue(maze.startX(), maze.startZ(), sign))
			{
				if (!maze.found(room[0], room[1]) && !maze.exploreBlocked(room[0], room[1]))
				{
					return PrepGoal.explore(room, "blue route " + (sign > 0 ? "+" : "-") + " side to the outer ring");
				}
			}
			if (demisAllowed && componentsNeeded)
			{
				for (int[] room : Route.green(maze.startX(), maze.startZ(), sign))
				{
					if (!maze.found(room[0], room[1]) && !maze.exploreBlocked(room[0], room[1]))
					{
						return PrepGoal.explore(room, "green route: outer-ring lap for demi-bosses");
					}
				}
			}
		}
		// Off the route (all lit, or demis done): whatever's left, by value.
		if (nodeNeeds || (demisAllowed && componentsNeeded))
		{
			ExplorePlanner.Option pick = search(in, false, false, 0, demisAllowed && componentsNeeded, Integer.MAX_VALUE);
			if (pick != null)
			{
				return toGoal(pick, n);
			}
		}

		// End of loop 2: materials and components done (or not findable). Shards or the bow's frame
		// still short -> weak/medium monsters nearby, else find some, then home.
		boolean needFrame = n.get(Resource.FRAME) > 0 && (l.get(Resource.BOWSTRING) > 0 || l.tier.getOrDefault(GearSlot.BOW, 0) > 0);
		// Only shards (or the frame) left to get (user, 0.3.34): kill monsters until the goal is
		// reached, as long as the clock allows another kill. Medium ones first (80-100 a kill),
		// unless a weak one is already on us / right here.
		if ((n.get(Resource.SHARDS) > 0 || needFrame) && slack >= WEAK_KILL_SECONDS + 5 && !hungry(in))
		{
			String what = (needFrame ? "bow frame, " : "") + n.get(Resource.SHARDS) + " shards";
			boolean strongTime = slack >= STRONG_KILL_SECONDS + 5;
			if (in.weakOnUs || in.weakTiles <= 5)
			{
				return PrepGoal.fight(Monster.WEAK, "only shards left: " + what + " (a weak monster right here)");
			}
			if (strongTime && in.strongTiles <= 30)
			{
				return PrepGoal.fight(Monster.STRONG, "only shards left: " + what + " (a medium monster " + in.strongTiles + " tiles away)");
			}
			if (in.weakNearby)
			{
				return PrepGoal.fight(Monster.WEAK, "only shards left: " + what);
			}
			if (strongTime && in.strongTiles != Integer.MAX_VALUE)
			{
				return PrepGoal.fight(Monster.STRONG, "only shards left: " + what + " (a medium monster " + in.strongTiles + " tiles away)");
			}
			ExplorePlanner.Option pick = search(in, false, needFrame, n.get(Resource.SHARDS), false, Integer.MAX_VALUE);
			if (pick != null)
			{
				return toGoal(pick, n);
			}
		}
		// The loose extras last, with time to spare: fish past the minimum (12 is enough, 16 ideal).
		if (fishOptional && slack >= LOOSE_FISH_SLACK)
		{
			ExplorePlanner.Option pick = search(in, false, false, 0, false, Integer.MAX_VALUE);
			if (pick != null)
			{
				return toGoal(pick, n);
			}
		}
		if (in.secondsLeft < 0 || maze.roomX() >= 0)
		{
			PrepGoal cr = armourCritical(in, true, false);
			if (cr != null)
			{
				return cr;
			}
			return finish(in, "nothing more to get");
		}

		// (Old greedy steps kept below as a fallback when the grid isn't calibrated yet.)
		// 5. Nodes: walking is the real cost, so go to the NEAREST live node of anything still
		// needed (ties: the bigger need). Scarcity-first made the bot hop between nodes as the
		// counts changed (0.2.5 live test). Fish get a small handicap so they tend to come last.
		Resource best = null;
		int bestCost = Integer.MAX_VALUE;
		int bestNeed = 0;
		net.runelite.api.coords.WorldPoint me = maze.playerRaw();
		for (Resource r : new Resource[]{Resource.ORE, Resource.BARK, Resource.LINUM, Resource.GRYM, Resource.RAW_FISH})
		{
			int need = n.get(r);
			GauntletMaze.Node node = need > 0 ? maze.nearestLiveNode(r) : null;
			if (node == null)
			{
				continue;
			}
			int cost = (me == null ? 0 : node.raw.distanceTo2D(me)) + (r == Resource.RAW_FISH ? 8 : 0);
			if (cost < bestCost || (cost == bestCost && need > bestNeed))
			{
				best = r;
				bestCost = cost;
				bestNeed = need;
			}
		}
		if (best != null)
		{
			return PrepGoal.gather(best, "need " + n.get(best) + " " + best.label + ", nearest node " + bestCost + " tiles");
		}
		if (n.get(Resource.SHARDS) > 0)
		{
			return PrepGoal.fight(Monster.WEAK, "need " + n.get(Resource.SHARDS) + " shards");
		}

		// 6. Explore for missing nodes / demi-bosses.
		int[] room = maze.nearestUnexploredRoom();
		if (room != null)
		{
			return PrepGoal.explore(room, "still missing: " + n);
		}

		// 7. Nothing more to find.
		return finish(in, "nothing left to find");
	}

	private ExplorePlanner.Option search(Inputs in, boolean opening, boolean frame, int shards, boolean lap, int maxGatherTiles)
	{
		ExplorePlanner.Option o = search(in, in.gather != null ? in.gather : in.needs, opening, frame, shards, lap, maxGatherTiles);
		if (o == null && in.gather != null && in.gather.get(Resource.RAW_FISH) < in.needs.get(Resource.RAW_FISH))
		{
			// Nothing else left to look for out here: fish into the reserved slots rather than stop.
			// Only the fish cap is lifted (0.3.30: the full needs brought back the optional 3rd
			// potion's grym leaf, 87 tiles away).
			o = search(in, in.gather.withDeficit(Resource.RAW_FISH, in.needs.get(Resource.RAW_FISH)), opening, frame, shards, lap, maxGatherTiles);
		}
		return o;
	}

	private ExplorePlanner.Option search(Inputs in, Needs n, boolean opening, boolean frame, int shards, boolean lap, int maxGatherTiles)
	{
		java.util.List<ExplorePlanner.Option> ranked = explorer.rank(in.maze, n, wants(in, n, opening, frame, shards, lap, maxGatherTiles));
		lastRanking = ranked;
		return ranked.isEmpty() ? null : ranked.get(0);
	}

	private ExplorePlanner.Wants wants(Inputs in, Needs n, boolean opening, boolean frame, int shards, boolean lap, int maxGatherTiles)
	{
		ExplorePlanner.Wants w = new ExplorePlanner.Wants();
		w.frame = frame;
		w.shards = shards;
		w.bowstring = lap && n.get(Resource.BOWSTRING) > 0;
		w.orb = lap && n.get(Resource.ORB) > 0;
		w.lap = lap;
		w.anyDemi = lap && (n.get(Resource.BOWSTRING) > 0 || n.get(Resource.ORB) > 0);
		w.opening = opening;
		w.maxGatherTiles = maxGatherTiles;
		w.hasTeleport = in.hasTeleport;
		Ledger l = in.ledger;
		// Food and the first potion matter early (0.3.12 died with 3 cooked fish and no potion).
		w.foodLow = l.cookedFish + l.get(Resource.RAW_FISH) < 4;
		w.firstPotion = l.potionDoses == 0 && l.unfinishedPotions == 0 && l.get(Resource.GRYM) == 0;
		w.freeSlots = in.ledger.freeSlots;
		w.budgetTicks = in.secondsLeft < 0 ? Integer.MAX_VALUE
			: (int) Math.max(0, (in.secondsLeft - in.finishReserve) / 0.6);
		return w;
	}

	private static PrepGoal toGoal(ExplorePlanner.Option pick, Needs n)
	{
		if (pick.kind == ExplorePlanner.Kind.GATHER)
		{
			return PrepGoal.gather(pick.resource, "need " + n.get(pick.resource) + " " + pick.resource.label + ": " + pick);
		}
		return PrepGoal.explore(new int[]{pick.x, pick.z}, pick.toString());
	}

	/** Fish that are enough for the fight (the kill-chance model: past 12 each fish adds ~0.3%). */
	public static final int FISH_ENOUGH = 12;

	/**
	 * The fish target to shop for right now (0.4.9): while a T3 weapon's demi-boss drop is still
	 * missing, only {@link #FISH_ENOUGH}; the rest of the setting comes after. The prep simulator
	 * showed fish 13-16 filling the bag mid-search: depot trips home, the outer lap cut short, and
	 * the Dark Beast never found in ~10% of preps (12 first: 4%, with the shorter demi budget ~1%).
	 */
	public static int fishTargetNow(Ledger l, PrepProfile p, int setting)
	{
		return fishTargetNow(l, p, setting, FISH_ENOUGH);
	}

	/** {@link #fishTargetNow(Ledger, PrepProfile, int)} with the user's "Paddlefish minimum" as the early target. */
	public static int fishTargetNow(Ledger l, PrepProfile p, int setting, int minimum)
	{
		boolean bowPart = p.targetTier.getOrDefault(GearSlot.BOW, 0) >= 3 && l.tier.getOrDefault(GearSlot.BOW, 0) < 3
			&& l.get(Resource.BOWSTRING) == 0;
		boolean staffPart = p.targetTier.getOrDefault(GearSlot.STAFF, 0) >= 3 && l.tier.getOrDefault(GearSlot.STAFF, 0) < 3
			&& l.get(Resource.ORB) == 0;
		return bowPart || staffPart ? Math.min(setting, minimum) : setting;
	}

	/**
	 * The loadout to shop for right now. A T3-armour setting is only pursued once both T3 weapon
	 * parts are in hand: until then the bot works towards T2 armour, so the extra 300 shards and 6 of
	 * each material never delay the demi-boss hunt (0.4.9, prep simulator: chasing T3 armour from
	 * the start cost the T3 weapons in 14% of preps).
	 */
	public static PrepProfile profileNow(Ledger l, PrepProfile setting)
	{
		if (setting != PrepProfile.T3_ARMOUR_T3_BOW_STAFF)
		{
			return setting;
		}
		boolean bowDone = l.tier.getOrDefault(GearSlot.BOW, 0) >= 3 || l.get(Resource.BOWSTRING) > 0;
		boolean staffDone = l.tier.getOrDefault(GearSlot.STAFF, 0) >= 3 || l.get(Resource.ORB) > 0;
		return bowDone && staffDone ? setting : PrepProfile.T2_ARMOUR_T3_BOW_STAFF;
	}

	/** A needed T2 armour material (ore, bark, linum) this close (walking tiles) beats the demi-boss hunt. */
	static final int ARMOUR_NEAR_TILES = 20;

	/**
	 * The nearest live node of a T2 armour material still needed, if it's within
	 * {@link #ARMOUR_NEAR_TILES} walking tiles (user, 0.4.7: armour before the T3 weapons' demi
	 * drops, but only when the materials are right there). Nodes next to a live demi-boss we aren't
	 * fighting are never offered (maze hazard).
	 */
	private PrepGoal armourNearby(Inputs in)
	{
		GauntletMaze m = in.maze;
		Needs g = in.gather != null ? in.gather : in.needs;
		GauntletMaze.Node best = null;
		int bestTiles = Integer.MAX_VALUE;
		for (Resource r : new Resource[]{Resource.ORE, Resource.BARK, Resource.LINUM})
		{
			if (g.get(r) <= 0 || (!stackable(r) && in.ledger.freeSlots <= 0))
			{
				continue;
			}
			for (GauntletMaze.Node node : m.liveNodeList(r))
			{
				int w = m.walkTiles(node);
				if (w >= 0 && w <= ARMOUR_NEAR_TILES && w < bestTiles)
				{
					best = node;
					bestTiles = w;
				}
			}
		}
		if (best == null)
		{
			return null;
		}
		return PrepGoal.gatherAt(best.type, best.raw, "T2 armour first: " + best.type.label + " " + bestTiles
			+ " tiles away (need " + g.get(best.type) + ") before the demi-boss hunt");
	}

	/** Loop 1: lit-room nodes this far away (walking tiles) are taken before moving on. */
	static final int LIT_ROOM_TILES = 32;

	/**
	 * Loop 1 (user, 1.7.1): the nearest still-needed node in the room we're in or a lit room next
	 * to it (a red-route room or a peeked outer room), within {@link #LIT_ROOM_TILES}. A room with a
	 * live demi-boss in it is left alone (its nodes come with the demi-boss kill). 1.6.4 lit an
	 * outer room with 3 ore 8 tiles away, walked on, and never had ore again: no armour, a death.
	 */
	private PrepGoal litRoomNodes(Inputs in)
	{
		GauntletMaze m = in.maze;
		if (m.roomX() < 0 || m.inStartRoom())
		{
			return null;
		}
		Needs g = in.gather != null ? in.gather : in.needs;
		Ledger l = in.ledger;
		java.util.Set<Long> demiRooms = new java.util.HashSet<>();
		for (GauntletMaze.Sighting s : m.sightings().values())
		{
			int[] dr = m.grid().roomOf(s.raw);
			if (dr != null)
			{
				demiRooms.add(dr[0] * 100L + dr[1]);
			}
		}
		GauntletMaze.Node best = null;
		int bestTiles = Integer.MAX_VALUE;
		for (Resource r : new Resource[]{Resource.ORE, Resource.BARK, Resource.LINUM, Resource.GRYM, Resource.RAW_FISH})
		{
			if (g.get(r) <= 0 || (!stackable(r) && l.freeSlots <= 0))
			{
				continue;
			}
			for (GauntletMaze.Node node : m.liveNodeList(r))
			{
				int[] nr = m.roomOf(node);
				if (nr == null || Math.abs(nr[0] - m.roomX()) + Math.abs(nr[1] - m.roomZ()) > 1
					|| !m.found(nr[0], nr[1]) || (nr[0] == Route.BOSS && nr[1] == Route.BOSS)
					|| demiRooms.contains(nr[0] * 100L + nr[1]))
				{
					continue;
				}
				int w = m.walkTiles(node);
				if (w >= 0 && w <= LIT_ROOM_TILES && w < bestTiles)
				{
					best = node;
					bestTiles = w;
				}
			}
		}
		if (best == null)
		{
			return null;
		}
		return PrepGoal.gatherAt(best.type, best.raw, "lit room: " + best.type.label + " " + bestTiles
			+ " tiles away (need " + g.get(best.type) + "), grab it before moving on");
	}

	/** Seconds past the finish reserve the blue / green route needs to be worth starting a room. */
	static final int ROUTE_MIN_SLACK = 45;

	/** Walking tiles a material without which there's no armour at all is worth going for (1.7.0). */
	static final int CRITICAL_TILES = 90;
	/** Seconds before the finish reserve from which a missing armour material comes before everything else. */
	static final int CRITICAL_START_SLACK = 150;
	/** Late rescue: seconds at home for crafting and wearing the armour (cooking and brewing come on top). */
	static final int CRITICAL_HOME_SECONDS = 15;

	/**
	 * Every armour piece takes ore, bark AND linum: short of one, no armour (1.7.0; 2 boss deaths in
	 * 4 runs: no ore found all run -> no armour at all; ore found at t-150 -> a T1 set, 1 potion).
	 * The material a T1 set is still short of comes before the demi-boss route: the nearest known
	 * node within {@link #CRITICAL_TILES}, else a search for it (by value, that material alone).
	 * Late ({@code late}): a known node only, and the finish reserve may be spent on it down to the
	 * handover reserve + the trip home and the crafting.
	 */
	private PrepGoal armourCritical(Inputs in, boolean late, boolean allowSearch)
	{
		Ledger l = in.ledger;
		GauntletMaze m = in.maze;
		int pieces = 0;
		for (GearSlot s : new GearSlot[]{GearSlot.HELM, GearSlot.BODY, GearSlot.LEGS})
		{
			if (in.profile.targetTier.getOrDefault(s, 0) >= 1 && l.tier.getOrDefault(s, 0) == 0)
			{
				pieces++;
			}
		}
		if (pieces == 0 || m.roomX() < 0)
		{
			return null;
		}
		java.util.List<Resource> shortOf = new java.util.ArrayList<>();
		for (Resource r : new Resource[]{Resource.ORE, Resource.BARK, Resource.LINUM})
		{
			if (l.get(r) < pieces && in.needs.get(r) > 0 && (l.get(r) > 0 || l.freeSlots > 0))
			{
				shortOf.add(r);
			}
		}
		if (shortOf.isEmpty())
		{
			return null;
		}
		// Late: what home still has to do after the trip (raw fish cooked, ~1.3 s each; brews) must fit too:
		// a raw fish when the timer teleports us in is no food at all.
		int homeSeconds = CRITICAL_HOME_SECONDS + (int) Math.ceil(l.get(Resource.RAW_FISH) * 1.3) + 4 * Math.max(0, in.needs.potionsToMake);
		int budget = in.secondsLeft < 0 ? Integer.MAX_VALUE
			: in.secondsLeft - (late ? in.handoverReserve + homeSeconds : in.finishReserve);
		// Early on the normal route finds it (most runs do); only the last stretch makes it urgent.
		if (!late && budget > CRITICAL_START_SLACK)
		{
			return null;
		}
		GauntletMaze.Node best = null;
		int bestTiles = Integer.MAX_VALUE;
		for (Resource r : shortOf)
		{
			int items = pieces - l.get(r);
			for (GauntletMaze.Node node : m.liveNodeList(r))
			{
				int w = m.walkTiles(node);
				if (w < 0 || w > CRITICAL_TILES || w >= bestTiles)
				{
					continue;
				}
				// there (2 tiles a tick), the items (~3 ticks each), and home: a crystal, or walking back
				// (walking padded 20% for doors and turns, mining 2.4 s an item: the clock is tight late)
				double secs = w * 0.36 + items * 2.4 + 2 + (in.hasTeleport ? 6 : w * 0.36);
				if (secs <= budget)
				{
					best = node;
					bestTiles = w;
				}
			}
		}
		if (best != null)
		{
			return PrepGoal.gatherAt(best.type, best.raw, (late ? "last chance for armour: " : "no armour without it: ")
				+ best.type.label + " " + bestTiles + " tiles away (have " + l.get(best.type) + ", a T1 set needs " + pieces + ")");
		}
		if (late || !allowSearch || budget < 40)
		{
			return null;
		}
		ExplorePlanner.Option pick = search(in, in.needs.only(shortOf.toArray(new Resource[0])), false, false, 0, false, Integer.MAX_VALUE);
		if (pick == null)
		{
			return null;
		}
		String what = shortOf.get(0).label + (shortOf.size() > 1 ? " (+" + (shortOf.size() - 1) + ")" : "");
		return pick.kind == ExplorePlanner.Kind.GATHER
			? PrepGoal.gather(pick.resource, "no armour without it: " + pick)
			: PrepGoal.explore(new int[]{pick.x, pick.z}, "no armour without it: looking for " + what + ", " + pick);
	}

	/** Low HP and nothing cooked: no fights we'd pick ourselves (0.4.7 bailed out of one every 3 s at 21%). */
	static boolean hungry(Inputs in)
	{
		return in.hpPct < 40 && in.ledger.cookedFish == 0;
	}

	/** Gathering counts as "on the way" this close (walking tiles). */
	static final int NEAR_TILES = 12;
	/** ...or anywhere in the room we're in, up to this many walking tiles. */
	static final int ROOM_TILES = 22;

	/** The nearest live node of r is in the room we're standing in. */
	private static boolean inMyRoom(GauntletMaze m, Resource r)
	{
		GauntletMaze.Node n = m.nearestLiveNode(r);
		if (n == null || m.roomX() < 0)
		{
			return false;
		}
		int[] room = m.roomOf(n);
		return room != null && room[0] == m.roomX() && room[1] == m.roomZ();
	}

	/** The next red-route step not yet lit (or null when the route is done). */
	private Route.Step nextRed(Inputs in)
	{
		GauntletMaze m = in.maze;
		int first = (in.runSeed & 1) == 0 ? -1 : 1; // which way round: varies per run
		for (Route.Step st : Route.red(m.startX(), m.startZ(), first))
		{
			if (!m.found(st.x, st.z) && !m.exploreBlocked(st.x, st.z))
			{
				return st;
			}
		}
		return null;
	}

	private int sideSeed = Integer.MIN_VALUE;
	private int sideChosen;

	/**
	 * Which way to the outer ring (user): the side whose lit rooms showed fishing spots; both or
	 * neither: random. Decided once per run.
	 */
	private int blueSide(Inputs in)
	{
		if (sideSeed == in.runSeed && sideChosen != 0)
		{
			return sideChosen;
		}
		GauntletMaze m = in.maze;
		int sx = m.startX();
		int sz = m.startZ();
		boolean[] fish = new boolean[3];
		for (int z = 0; z < Route.N; z++)
		{
			for (int x = 0; x < Route.N; x++)
			{
				int side = Route.sideOf(sx, sz, x, z);
				if (side != 0 && m.found(x, z) && m.liveNodesInRoom(Resource.RAW_FISH, x, z) > 0)
				{
					fish[side + 1] = true;
				}
			}
		}
		int pick;
		if (fish[2] != fish[0])
		{
			pick = fish[2] ? 1 : -1;
		}
		else
		{
			pick = ((in.runSeed >>> 3) & 1) == 0 ? -1 : 1;
		}
		sideSeed = in.runSeed;
		sideChosen = pick;
		return pick;
	}

	/** Average shards per gathered item (every node action: a 1/3 chance of 10-30). */
	static final double NODE_SHARDS_PER_ITEM = 20.0 / 3;
	/** A demi-boss kill: 50-60 shards. */
	static final int DEMI_SHARDS = 55;
	/** A medium monster is worth a stop for shards when at least this many are short. */
	static final int MEDIUM_WORTH = 50;
	/** ...and it's this close (tiles). */
	static final int HUNT_NEAR_TILES = 15;
	/** Seconds a kill takes, walking and loot included. */
	static final int STRONG_KILL_SECONDS = 20;
	static final int WEAK_KILL_SECONDS = 10;
	/** Fish past the minimum are fetched (not just taken on the way) only with this much time to spare. */
	static final int LOOSE_FISH_SLACK = 30;

	/**
	 * Shards still needed beyond what the rest of the plan should bring on its own (required
	 * gathering at the average node rate, plus the demi-bosses still to kill).
	 */
	static int shardShortfall(Inputs in, boolean demisToCome)
	{
		Needs r = in.required != null ? in.required : in.needs;
		int items = 0;
		for (Resource x : new Resource[]{Resource.ORE, Resource.BARK, Resource.LINUM, Resource.GRYM, Resource.RAW_FISH})
		{
			items += r.get(x);
		}
		double expected = items * NODE_SHARDS_PER_ITEM;
		if (demisToCome)
		{
			expected += (r.get(Resource.BOWSTRING) + r.get(Resource.ORB)) * DEMI_SHARDS;
		}
		return in.needs.get(Resource.SHARDS) - (int) Math.round(expected);
	}

	/**
	 * The fish past the minimum are worth a trip when the time to spare covers it (0.4.11): walk to
	 * the nearest live spot, ~3 ticks a fish, plus a margin. The prep simulator: T2 preps finished
	 * with ~80 s unused and 12 fish; 16 fish add about 1 point of kill chance.
	 */
	static boolean fishTripFits(Inputs in, Needs n)
	{
		GauntletMaze m = in.maze;
		if (m == null || in.secondsLeft < 0 || !in.ledger.harpoon)
		{
			return false;
		}
		GauntletMaze.Node spot = m.nearestLiveNode(Resource.RAW_FISH);
		int tiles = spot == null ? -1 : m.walkTiles(spot);
		if (tiles < 0 || tiles == Integer.MAX_VALUE)
		{
			return false;
		}
		int fish = Math.min(n.get(Resource.RAW_FISH), ExplorePlanner.yieldPerNode(Resource.RAW_FISH));
		double seconds = (tiles / 2.0 + 2) * 0.6 + fish * 3 * 0.6 + FISH_TRIP_MARGIN;
		return seconds <= slackSeconds(in);
	}

	/** Seconds kept back on top of an extra-fish trip (clicks, a monster on the way). */
	static final int FISH_TRIP_MARGIN = 10;

	/** Seconds to spare before the final trip has to start (MAX_VALUE: no timer). */
	static int slackSeconds(Inputs in)
	{
		return in.secondsLeft < 0 ? Integer.MAX_VALUE : in.secondsLeft - in.finishReserve;
	}

	/** A node that adds at most this many walking tiles to where we're heading is "on the way". */
	static final int DETOUR_TILES = 8;
	/** The 3rd potion's grym leaf only from this close (walking tiles). */
	static final int OPTIONAL_GRYM_TILES = 30;

	/** Everything gathered apart from the optional extras (the last herb / fish past the minimum). */
	private static boolean doneExceptOptional(Needs n, boolean grym, boolean fish)
	{
		for (Resource r : Resource.values())
		{
			if (n.get(r) > 0 && !(grym && r == Resource.GRYM) && !(fish && r == Resource.RAW_FISH))
			{
				return false;
			}
		}
		return grym || fish;
	}

	/** A live fishing spot right by us or on the way (detour <= 8). */
	private static boolean easyFishingSpot(Inputs in)
	{
		GauntletMaze m = in.maze;
		for (GauntletMaze.Node node : m.liveNodeList(Resource.RAW_FISH))
		{
			int d = m.detourTiles(node);
			int w = m.walkTiles(node);
			if (w <= 8 || (d >= 0 && d <= DETOUR_TILES && w <= 30))
			{
				return true;
			}
		}
		return false;
	}

	/** The next blue / green route room still to light, or null (same order as below). */
	private int[] loop2RouteRoom(Inputs in, boolean demiLap)
	{
		GauntletMaze maze = in.maze;
		int sign = blueSide(in);
		for (int[] room : Route.blue(maze.startX(), maze.startZ(), sign))
		{
			if (!maze.found(room[0], room[1]) && !maze.exploreBlocked(room[0], room[1]))
			{
				return room;
			}
		}
		if (demiLap)
		{
			for (int[] room : Route.green(maze.startX(), maze.startZ(), sign))
			{
				if (!maze.found(room[0], room[1]) && !maze.exploreBlocked(room[0], room[1]))
				{
					return room;
				}
			}
		}
		return null;
	}


	/**
	 * Roughly where the doorway into room (rx, rz) is: the middle of its wall with the lit
	 * neighbour nearest to us, one tile inside that neighbour (doorways sit mid-wall).
	 */
	static net.runelite.api.coords.WorldPoint doorHint(Inputs in, int rx, int rz)
	{
		GauntletMaze m = in.maze;
		net.runelite.api.coords.WorldPoint me = m.playerRaw();
		if (me == null || m.roomX() < 0)
		{
			return null;
		}
		int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		int[] bestL = null;
		int bestD = Integer.MAX_VALUE;
		for (int[] d : dirs)
		{
			int lx = rx + d[0];
			int lz = rz + d[1];
			if (lx < 0 || lz < 0 || lx >= Route.N || lz >= Route.N || (lx == Route.BOSS && lz == Route.BOSS) || !m.found(lx, lz))
			{
				continue;
			}
			int dist = Math.abs(lx - m.roomX()) + Math.abs(lz - m.roomZ());
			if (dist < bestD)
			{
				bestD = dist;
				bestL = new int[]{lx, lz};
			}
		}
		if (bestL == null)
		{
			return null;
		}
		net.runelite.api.coords.WorldPoint c = m.grid().centreOf(bestL[0], bestL[1], me.getPlane());
		if (c == null)
		{
			return null;
		}
		int half = CgIdsHalf();
		return new net.runelite.api.coords.WorldPoint(c.getX() + (rx - bestL[0]) * half, c.getY() + (rz - bestL[1]) * half, c.getPlane());
	}

	private static int CgIdsHalf()
	{
		return net.runelite.client.plugins.microbot.cgprep.data.CgIds.ROOM_SIZE_TILES / 2 - 1;
	}

	static net.runelite.api.coords.WorldPoint homeHint(Inputs in)
	{
		GauntletMaze m = in.maze;
		net.runelite.api.coords.WorldPoint me = m.playerRaw();
		return me == null || m.startX() < 0 ? null : m.grid().centreOf(m.startX(), m.startZ(), me.getPlane());
	}

	/**
	 * A needed node we're passing anyway (user, 0.3.27): judged by the EXTRA walking it adds to
	 * the current route (doorway / home), not by its distance from us. Without a route to compare
	 * (nothing being walked to yet) the old rule: within 12 tiles, or in the room we're in.
	 */
	private PrepGoal onTheWay(Inputs in, boolean loop2)
	{
		GauntletMaze m = in.maze;
		if (m.routeTarget() == null)
		{
			ExplorePlanner.Option o = nearGather(in, loop2);
			return o == null ? null : toGoal(o, in.needs);
		}
		Needs g = in.gather != null ? in.gather : in.needs;
		Ledger l = in.ledger;
		// Every useful node on the way: per resource, only as many nodes as the need still fills
		// (the least-detour ones).
		java.util.List<GauntletMaze.Node> cands = new java.util.ArrayList<>();
		java.util.Map<GauntletMaze.Node, Integer> detour = new java.util.HashMap<>();
		for (Resource r : new Resource[]{Resource.ORE, Resource.BARK, Resource.LINUM, Resource.GRYM, Resource.RAW_FISH})
		{
			int need = g.get(r);
			if (need <= 0 || (!stackable(r) && l.freeSlots <= 0))
			{
				continue;
			}
			java.util.List<GauntletMaze.Node> ok = new java.util.ArrayList<>();
			for (GauntletMaze.Node node : m.liveNodeList(r))
			{
				int d = m.detourTiles(node);
				int[] nr = m.roomOf(node);
				// Beside the route, or anywhere in the room we're standing in (coming back for it
				// later costs more than the few steps now).
				boolean myRoom = nr != null && nr[0] == m.roomX() && nr[1] == m.roomZ() && m.walkTiles(node) <= ROOM_TILES;
				if (d >= 0 && ((d <= DETOUR_TILES && m.walkTiles(node) <= 40) || myRoom))
				{
					ok.add(node);
					detour.put(node, d);
				}
			}
			ok.sort(java.util.Comparator.comparingInt(detour::get));
			int nodesUseful = (need + ExplorePlanner.yieldPerNode(r) - 1) / ExplorePlanner.yieldPerNode(r);
			cands.addAll(ok.subList(0, Math.min(ok.size(), nodesUseful)));
		}
		if (cands.isEmpty())
		{
			return null;
		}
		if (cands.size() > 5)
		{
			cands.sort(java.util.Comparator.comparingInt(detour::get));
			cands = new java.util.ArrayList<>(cands.subList(0, 5));
		}
		// Visit them in the order that walks least overall and ends at the doorway (user, 0.3.29:
		// heading east, start with the westmost and work towards the exit, not back and forth).
		GauntletMaze.Node first = bestFirst(m, cands);
		int d = detour.get(first);
		Resource r = first.type;
		return PrepGoal.gatherAt(r, first.raw, "on the way: +" + d + " tiles for " + Math.min(g.get(r), ExplorePlanner.yieldPerNode(r))
			+ " " + r.label + " (" + m.walkTiles(first) + " tiles from us" + (cands.size() > 1 ? ", 1st of " + cands.size() + " in walking order" : "") + ")");
	}

	/**
	 * The node to visit first so that us -> all of them -> the route target is shortest (exact for
	 * up to 5 nodes). Between nodes: straight-line steps (they're in the same room or next door).
	 */
	static GauntletMaze.Node bestFirst(GauntletMaze m, java.util.List<GauntletMaze.Node> c)
	{
		int k = c.size();
		int[] fromMe = new int[k];
		int[] toEnd = new int[k];
		for (int i = 0; i < k; i++)
		{
			fromMe[i] = m.walkTiles(c.get(i));
			toEnd[i] = m.toRouteTarget(c.get(i));
		}
		int[] perm = new int[k];
		for (int i = 0; i < k; i++)
		{
			perm[i] = i;
		}
		int[] best = {Integer.MAX_VALUE, 0};
		permute(perm, 0, (p) ->
		{
			int total = fromMe[p[0]];
			for (int i = 1; i < p.length; i++)
			{
				net.runelite.api.coords.WorldPoint a = c.get(p[i - 1]).raw;
				net.runelite.api.coords.WorldPoint b = c.get(p[i]).raw;
				total += Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY());
			}
			total += Math.max(0, toEnd[p[p.length - 1]]);
			if (total < best[0])
			{
				best[0] = total;
				best[1] = p[0];
			}
		});
		return c.get(best[1]);
	}

	private static void permute(int[] a, int i, java.util.function.Consumer<int[]> f)
	{
		if (i == a.length)
		{
			f.accept(a);
			return;
		}
		for (int j = i; j < a.length; j++)
		{
			int t = a[i];
			a[i] = a[j];
			a[j] = t;
			permute(a, i + 1, f);
			a[j] = a[i];
			a[i] = t;
		}
	}

	/** The best needed node within NEAR_TILES walking tiles (fish within the slot budget), or null. */
	private ExplorePlanner.Option nearGather(Inputs in, boolean loop2)
	{
		Needs g = in.gather != null ? in.gather : in.needs;
		ExplorePlanner.Wants w = wants(in, g, !loop2, false, 0, loop2, ROOM_TILES);
		ExplorePlanner.Option best = null;
		for (ExplorePlanner.Option o : explorer.rank(in.maze, g, w))
		{
			// 0.3.26 ran past the linum it still needed in the same room: 14 walking tiles away,
			// over the 12-tile "on the way" limit. Anything in the room we're in counts as on the way.
			boolean near = o.tiles <= NEAR_TILES || (o.tiles <= ROOM_TILES && inMyRoom(in.maze, o.resource));
			if (o.kind == ExplorePlanner.Kind.GATHER && o.tiles >= 0 && near
				&& (best == null || o.rate() > best.rate()))
			{
				best = o;
			}
		}
		return best;
	}

	/** Null when fit for a demi-boss fight, else what's lacking. */
	static String readyForDemi(Inputs in)
	{
		return readyForDemi(in, false);
	}

	/**
	 * {@code onUs}: it's already attacking us. Walking away then costs prayer too (it follows and
	 * protection stays on), so finish it with what's left: 0.3.23 left a Bear at 24% prayer.
	 */
	static String readyForDemi(Inputs in, boolean onUs)
	{
		Ledger l = in.ledger;
		// The right protection prayer blocks a demi-boss's hits completely, so prayer is what
		// matters; food only as cover when HP is already down. 0.3.17 refused a Bear at 100% HP for
		// lack of cooked fish and walked around with it engaged for over a minute (user: just kill it).
		// No mid-prep cooking any more (0.3.23), so HP alone mostly decides.
		boolean food = in.hpPct >= 45 || l.cookedFish >= 2;
		boolean prayer = in.prayPct >= (onUs ? 12 : 30) || l.potionDoses > 0;
		if (food && prayer)
		{
			return null;
		}
		return (food ? "" : l.cookedFish + " cooked fish, hp " + in.hpPct + "%") + (food || prayer ? "" : "; ")
			+ (prayer ? "" : "prayer " + in.prayPct + "%, no potion");
	}

	/** Ingredients for one more brew in hand: a grym leaf (or an unfinished potion) and the dust for it. */
	static boolean freshBrewReady(Ledger l)
	{
		boolean herb = l.get(Resource.GRYM) > 0 || l.unfinishedPotions > 0;
		boolean dust = l.dust >= 10 || (l.pestle && l.get(Resource.SHARDS) >= 10);
		return herb && dust;
	}

	static boolean nearFishingSpot(Inputs in)
	{
		GauntletMaze m = in.maze;
		if (m == null)
		{
			return false;
		}
		GauntletMaze.Node spot = m.nearestFishingSpot();
		int t = spot == null ? -1 : m.walkTiles(spot);
		return t >= 0 && t <= NEAR_TILES;
	}

	/** A brew step that runs from the inventory alone (no pump, no bowl). */
	static boolean fieldBrewable(Ledger l)
	{
		boolean grymStep = l.waterVials > 0 && l.get(Resource.GRYM) > 0;
		boolean crushStep = l.unfinishedPotions > 0 && l.dust < 10 * l.unfinishedPotions && l.pestle
			&& l.get(Resource.SHARDS) >= 10 && (l.dust > 0 || l.freeSlots > 0);
		boolean dustStep = l.unfinishedPotions > 0 && l.dust >= 10;
		return grymStep || crushStep || dustStep;
	}

	private static String doses(Ledger l)
	{
		StringBuilder sb = new StringBuilder("egniol");
		for (int d = 4; d >= 1; d--)
		{
			for (int i = 0; i < l.doseCounts[d]; i++)
			{
				sb.append(' ').append(d);
			}
		}
		return sb.append(", unf ").append(l.unfinishedPotions).append(", water ").append(l.waterVials).toString();
	}

	/** The nearest demi-boss seen within `maxAgeMs` within `tiles`, preferring types whose drop we need. */
	private static Monster closestDemi(GauntletMaze maze, Needs n, int tiles, long maxAgeMs)
	{
		net.runelite.api.coords.WorldPoint me = maze.playerRaw();
		if (me == null)
		{
			return null;
		}
		Monster best = null;
		int bestScore = Integer.MAX_VALUE;
		for (Monster m : new Monster[]{Monster.DARK_BEAST, Monster.DRAGON, Monster.BEAR})
		{
			GauntletMaze.Sighting s = maze.lastSeen(m);
			// No "type already killed" filter: there can be two of a type, and a repeat type always
			// drops an upgrade we're missing (0.3.14 ran past a 2nd Bear for 45 s because of it).
			if (s == null || System.currentTimeMillis() - s.atMs > maxAgeMs)
			{
				continue;
			}
			int d = s.raw.distanceTo2D(me);
			if (d > tiles)
			{
				continue;
			}
			boolean direct = (m == Monster.DARK_BEAST && n.get(Resource.BOWSTRING) > 0) || (m == Monster.DRAGON && n.get(Resource.ORB) > 0);
			int score = d - (direct ? 10 : 0);
			if (score < bestScore)
			{
				bestScore = score;
				best = m;
			}
		}
		return best;
	}

	/** Seconds the final trip needs: travel home, remaining crafts, potions, cooking, equipping. */
	static int estimateFinishSeconds(Inputs in)
	{
		Ledger l = in.ledger;
		Needs n = in.needs;
		double s = 8; // margin
		GauntletMaze maze = in.maze;
		if (!maze.inStartRoom())
		{
			int rooms = ExplorePlanner.roomDistance(maze, maze.startX(), maze.startZ());
			s += in.hasTeleport ? 4 : Math.max(1, rooms < 0 ? 3 : rooms) * 5;
		}
		int tiers = 0;
		for (GearSlot g : GearSlot.values())
		{
			tiers += Math.max(0, in.profile.targetTier.getOrDefault(g, 0) - l.tier.getOrDefault(g, 0));
		}
		s += 3 + tiers * 0.8;
		s += n.potionsToMake * 3.5;
		s += 3 + (l.get(Resource.RAW_FISH) + Math.max(0, n.get(Resource.RAW_FISH))) * 0.6;
		s += 3;
		return (int) Math.ceil(s);
	}

	/** End-of-prep sequence; each step re-checked every tick so it resumes after interruptions. */
	/** Run seed for which the final trip has started (0.3.30 flip-flopped between "home" and
	 * "explore 1,2" for 15 s as the explore option came and went with the distances). */
	private int finishedSeed = Integer.MIN_VALUE;

	private PrepGoal finish(Inputs in, String why)
	{
		if (!in.projection)
		{
			finishedSeed = in.runSeed;
		}
		if (topUpSeed == in.runSeed && in.runSeed != 0)
		{
			PrepGoal t = topUp(in, why);
			if (t != null)
			{
				return t;
			}
		}
		if (!in.maze.inStartRoom())
		{
			return PrepGoal.finalTripHome(why);
		}
		return process(in, why, true);
	}

	// ------------------------------------------------------------------ end-of-prep fish top-up

	/**
	 * Most fish a top-up fills the bag to. User (1.2.0): runs entered the boss with 60-110 s unused
	 * and 11-14 fish while a spot in the room next to the start had fish (and one fight ended
	 * with none left).
	 */
	static final int TOP_UP_CAP = 24;
	/** Seconds a fish takes on the range (0.4.x logs: 11 fish in ~16 ticks, walk included). */
	static final double COOK_SECONDS_PER_FISH = 1.2;

	private int topUpSeed = Integer.MIN_VALUE;
	private int topUpTotal;
	private int topUpBackTiles;

	/** A live fishing spot within 2 rooms of the start (not the boss room), nearest first. */
	static GauntletMaze.Node homeFishSpot(GauntletMaze m)
	{
		if (m == null || m.startX() < 0)
		{
			return null;
		}
		int sx = m.startX();
		int sz = m.startZ();
		return m.nearestLiveNodeWhere(Resource.RAW_FISH, (x, z) -> Math.abs(x - sx) + Math.abs(z - sz) <= 2 && !(x == Route.BOSS && z == Route.BOSS));
	}

	/** Seconds kept back at the end of a top-up (clicks, a monster in the way). */
	static final int TOP_UP_MARGIN = 4;
	/** A raw paddlefish picked up off the floor: about 2 ticks. */
	static final double PICKUP_SECONDS = 1.2;

	/**
	 * What must be left after the cooked fish are in the bag. With the bot fighting (Prep & Boss)
	 * nothing: at 0 the game puts us in the boss room anyway (user, 1.6.0). With a handover, the
	 * player's own reserve.
	 */
	static int endReserve(Inputs in)
	{
		return in.timerTeleports ? 0 : in.handoverReserve;
	}

	/** Seconds for a top-up of {@code fish} from a spot or a floor pile {@code tiles} away (and back), cooking all the raw fish. */
	static double topUpNeed(Inputs in, int tilesThere, int tilesBack, int fish, boolean ground)
	{
		double gather = ground ? fish * PICKUP_SECONDS : fish * 3 * 0.6;
		return (tilesThere / 2.0 + 2) * 0.6 + gather + (tilesBack / 2.0 + 2) * 0.6
			+ (in.ledger.get(Resource.RAW_FISH) + fish) * COOK_SECONDS_PER_FISH + 3 + TOP_UP_MARGIN + endReserve(in);
	}

	/**
	 * Everything else done (or a top-up under way): fetch more fish when the COOKED fish can be in
	 * the bag before the timer runs out (user, 1.6.0): from a fishing spot within 2 rooms of the
	 * start, or raw paddlefish lying on the floor nearby (monster drops). As many as fit the time,
	 * re-checked every tick. Started only from the start room; null = no (more) top-up.
	 */
	private PrepGoal topUp(Inputs in, String why)
	{
		Ledger l = in.ledger;
		GauntletMaze m = in.maze;
		boolean active = topUpSeed == in.runSeed && in.runSeed != 0;
		if (in.projection)
		{
			return null;
		}
		if (m == null || in.secondsLeft < 0 || in.flee || in.hpPct < 40)
		{
			topUpSeed = Integer.MIN_VALUE;
			return null;
		}
		int have = l.cookedFish + l.get(Resource.RAW_FISH);
		// The two sources: a live spot near home (needs the harpoon), the nearest floor pile.
		GauntletMaze.Node spot = l.harpoon ? homeFishSpot(m) : null;
		int spotTiles = spot == null ? -1 : m.walkTiles(spot);
		if (spotTiles == Integer.MAX_VALUE)
		{
			spotTiles = -1;
		}
		int groundTiles = in.groundFishAt != null && in.groundFishCount > 0 ? in.groundFishTiles : -1;
		if (!active)
		{
			if (!m.inStartRoom())
			{
				return null;
			}
			boolean belowTarget = in.needs != null && in.needs.get(Resource.RAW_FISH) > 0;
			int room = Math.min(l.freeSlots, TOP_UP_CAP - have);
			PrepGoal best = null;
			int bestFish = 0;
			// Floor first when it's as good: no fishing time, nothing to deplete.
			if (groundTiles >= 0)
			{
				int fish = fitFish(in, groundTiles, Math.min(room, in.groundFishCount), true);
				if (fish >= (belowTarget ? 1 : 2))
				{
					best = PrepGoal.pickUpFish(in.groundFishAt, why + ": " + fish + " raw paddlefish on the floor " + groundTiles + " tiles away, cooked by the end ("
						+ in.secondsLeft + " s left, needs ~" + Math.round(topUpNeed(in, groundTiles, groundTiles, fish, true)) + " s)");
					bestFish = fish;
					topUpBackTiles = groundTiles;
				}
			}
			if (spotTiles >= 0)
			{
				int fish = fitFish(in, spotTiles, Math.min(room, ExplorePlanner.yieldPerNode(Resource.RAW_FISH)), false);
				if (fish >= (belowTarget ? 1 : 2) && fish > bestFish)
				{
					best = PrepGoal.gatherAt(Resource.RAW_FISH, spot.raw, why + ": " + fish + " more fish from the spot " + spotTiles + " tiles away, cooked by the end ("
						+ in.secondsLeft + " s left, needs ~" + Math.round(topUpNeed(in, spotTiles, spotTiles, fish, false)) + " s)");
					bestFish = fish;
					topUpBackTiles = spotTiles;
				}
			}
			if (best == null)
			{
				return null;
			}
			topUpSeed = in.runSeed;
			topUpTotal = have + bestFish;
			return best;
		}
		int left = topUpTotal - have;
		if (left <= 0 || l.freeSlots == 0)
		{
			topUpSeed = Integer.MIN_VALUE; // done or full: home to cook
			return null;
		}
		// Keep going with whichever source still has fish and still fits the clock.
		if (groundTiles >= 0 && topUpNeed(in, groundTiles, topUpBackTiles, Math.min(left, in.groundFishCount), true) <= in.secondsLeft)
		{
			return PrepGoal.pickUpFish(in.groundFishAt, "top-up: " + left + " more fish (" + in.secondsLeft + " s left)");
		}
		if (spotTiles >= 0 && topUpNeed(in, spotTiles, topUpBackTiles, 1, false) <= in.secondsLeft)
		{
			return PrepGoal.gatherAt(Resource.RAW_FISH, spot.raw, "top-up: " + left + " more fish (" + in.secondsLeft + " s left)");
		}
		topUpSeed = Integer.MIN_VALUE; // out of time (or the fish are gone): home to cook
		return null;
	}

	/** Most fish (up to {@code max}) whose trip + cooking fits the time left. */
	private static int fitFish(Inputs in, int tiles, int max, boolean ground)
	{
		int best = 0;
		for (int k = 1; k <= max; k++)
		{
			// 3 s of slack to start one (1.6.1: a trip started at exactly the limit was dropped
			// the next tick, after the walk had begun).
			if (topUpNeed(in, tiles, tiles, k, ground) <= in.secondsLeft - 3)
			{
				best = k;
			}
		}
		return best;
	}

	/** A top-up could still fit (1 fish from a known home spot): keep the harpoon for it. */
	static boolean topUpPossible(Inputs in)
	{
		GauntletMaze.Node spot = homeFishSpot(in.maze);
		if (spot == null || in.secondsLeft < 0)
		{
			return false;
		}
		int t = in.maze.walkTiles(spot);
		return t >= 0 && t != Integer.MAX_VALUE && topUpNeed(in, t, t, 1, false) <= in.secondsLeft;
	}

	public static boolean stackable(Resource r)
	{
		// Recon INV lines: ore, bark and linum stack (e.g. "Linum tirinum x3" in one slot), as do shards.
		return r == Resource.SHARDS || r == Resource.ORE || r == Resource.BARK || r == Resource.LINUM;
	}

	/**
	 * Slots fish may take right now: free slots minus everything else still to come that needs a
	 * slot of its own. Fish are the only big non-stacking item, and cooking doesn't free them, so
	 * they're fitted in around the rest across several trips instead of filling the bag at once.
	 */
	static int fishRoom(Ledger l, Needs n)
	{
		return Math.max(0, l.freeSlots - reservedSlots(l, n));
	}

	static int reservedSlots(Ledger l, Needs n)
	{
		return reservedSlots(l, n, true);
	}

	/** {@code loot}: also keep slots for monster drops (false: those get a dropped fish instead). */
	static int reservedSlots(Ledger l, Needs n, boolean loot)
	{
		int r = n.get(Resource.FRAME) + n.get(Resource.BOWSTRING) + n.get(Resource.ORB);
		boolean other = r > 0 || n.get(Resource.GRYM) > 0;
		for (Resource s : new Resource[]{Resource.ORE, Resource.BARK, Resource.LINUM})
		{
			if (n.get(s) > 0 && l.get(s) == 0)
			{
				r++; // a stack still to start
				other = true;
			}
		}
		// Potions: vials still to make, or grym still to pick up (each a slot until combined).
		r += Math.max(n.vialsToMake, n.get(Resource.GRYM));
		if (n.potionsToMake > 0 && l.dust == 0)
		{
			r++; // the dust stack
		}
		if (other && loot)
		{
			// Loot landing in the bag: a spike before it's crushed, a teleport crystal; a demi-boss
			// pile (frame + component + extras) while upgrades are still being hunted.
			r += n.get(Resource.BOWSTRING) + n.get(Resource.ORB) > 0 ? 2 : 1;
		}
		return r;
	}

	/** Slots still needed for things that don't stack and that we still have to pick up. */
	static int slotsWanted(Needs n)
	{
		return n.get(Resource.RAW_FISH) + n.get(Resource.GRYM) + n.get(Resource.FRAME)
			+ n.get(Resource.BOWSTRING) + n.get(Resource.ORB);
	}

	/**
	 * Tools and space. When what's left to collect won't fit, cheapest first:
	 *  0. (always) drop a tool as soon as its job is done: pickaxe after the ore, axe after the bark, harpoon after the fish;
	 *  2. brew in the field: water vial + grym -> unfinished potion (2 slots -> 1), dust in;
	 *  3. go home and process (armour crafted + worn frees the ore/bark/linum stacks, potions,
	 *     cooking), then carry on (the demi lap / remaining fish come after).
	 */
	private PrepGoal inventory(Inputs in)
	{
		Ledger l = in.ledger;
		// Only what's required decides a trip home (0.3.33: a teleport home for 3 optional fish).
		Needs n = in.required != null ? in.required : in.needs;
		int free = l.freeSlots;
		int wanted = slotsWanted(n);
		// (Finished tools are dropped as chores on the move, see chore().)
		if (free >= wanted || free >= 4)
		{
			return null; // fits, or plenty of room for now (re-checked every tick)
		}
		String why = "inventory: " + free + " free, ~" + wanted + " more to carry";
		// Only the brew step that FREES a slot is worth doing out here: grym into a water vial
		// (2 items -> 1). Crushing dust needs a slot of its own (0.3.11 got stuck on it, full).
		boolean fieldBrew = l.waterVials > 0 && l.get(Resource.GRYM) > 0 && !in.fieldBrewBlocked;
		if (fieldBrew && n.potionsToMake > 0)
		{
			return PrepGoal.of(PrepGoal.Type.BREW, why + " (brew in the field)");
		}
		boolean loop1 = l.tier.getOrDefault(GearSlot.STAFF, 0) < Math.min(2, in.profile.targetTier.getOrDefault(GearSlot.STAFF, 0));
		// A whole needed stack (ore / bark / linum) takes ONE slot: if it's close, get it before any
		// trip home (0.3.29: went home with a slot free while the linum, 9 of the 7 needed, sat in
		// the next room; came all the way back for it at 1:45). With it, home can craft the armour,
		// which frees the stacks.
		// A stack already in the bag takes no slot at all (1.7.0: went home full at t-82 with 3 ore
		// in the bag, the ore node 26 tiles off; home couldn't help and it walked in and out).
		if (!in.maze.inStartRoom())
		{
			GauntletMaze gm = in.maze;
			for (Resource r : new Resource[]{Resource.LINUM, Resource.BARK, Resource.ORE})
			{
				boolean stackInBag = l.get(r) > 0 && free <= 1 && in.demiOnUs == null;
				if (n.get(r) > 0 && (l.get(r) == 0 ? free >= 1 : stackInBag))
				{
					GauntletMaze.Node node = gm.nearestLiveNode(r);
					int t = node == null ? Integer.MAX_VALUE : gm.walkTiles(node);
					if (t <= 35)
					{
						return PrepGoal.gatherAt(r, node.raw, why + (l.get(r) > 0 ? ": " + r.label + " goes on the stack in the bag, " : ": a whole " + r.label + " stack fits in one slot, ") + t + " tiles away");
					}
				}
			}
		}
		// Bag full, fish still short, and a fishing spot known close to home (in a straight line,
		// at most 2 rooms out): teleport home, prep to free space, fish there, come back (user).
		GauntletMaze m = in.maze;
		if (free <= 1 && !loop1 && !m.inStartRoom() && in.hasTeleport && n.get(Resource.RAW_FISH) > 0 && homeFreesSlots(l, n)
			&& m.startX() >= 0 && m.liveNodeWhere(Resource.RAW_FISH, (x, z) -> Route.nearStartStraight(m.startX(), m.startZ(), x, z)) != null)
		{
			return PrepGoal.teleportHome(why + " (fish short, a spot near home: teleport, prep, fish it)");
		}
		if (free <= 1 && !loop1 && !in.maze.inStartRoom() && homeFreesSlots(l, n))
		{
			return PrepGoal.of(PrepGoal.Type.RETURN_TO_START, why + " (depot: process at home, then carry on)");
		}
		return null;
	}

	/** Would a trip home free slots? Armour craftable now (its stacks vanish, pieces get worn), or potions/staff. */
	private static boolean homeFreesSlots(Ledger l, Needs n)
	{
		boolean armour = n.craftableNow.contains(GearSlot.BODY) || n.craftableNow.contains(GearSlot.LEGS)
			|| n.craftableNow.contains(GearSlot.HELM);
		boolean potions = n.potionsToMake > 0 && l.get(Resource.GRYM) > 0 && (l.emptyVials + l.waterVials) > 0;
		return armour || potions || l.armourInInventory;
	}

	/**
	 * Start-room processing. finalTrip: craft everything (bow only as a full T3), equip, brew, cook,
	 * then hand over. Mid-prep depot: the same, but returns null when done so prep carries on.
	 */
	private PrepGoal process(Inputs in, String why, boolean finalTrip)
	{
		Ledger l = in.ledger;
		Needs n = in.needs;
		// Order (user): all the crafting first, in bulk, then the potions, then the fish. Never
		// swap back and forth between the bowl, the range and the pump.
		//   1. staff upgrades  2. ALL the vials still needed  3. armour (+ the bow as a full T3)
		//   4. equip  5. brew every potion step by step in bulk  6. cook everything.
		// Crafting with a full inventory drops the new piece on the floor (0.3.10). Wear what's
		// already made first to make room; if nothing frees a slot, skip to the steps that don't
		// need one (equip / brew / cook) and craft when there's space.
		boolean roomToCraft = l.freeSlots > 0;
		if (!roomToCraft && (l.armourInInventory || !l.weaponEquipped))
		{
			return PrepGoal.of(PrepGoal.Type.EQUIP, why + ": make room before crafting");
		}
		// Still full with crafts to do on the final trip: a tool, else one raw paddlefish, makes the
		// room (a T2 piece beats a fish; the bowl chain then runs in one go).
		boolean craftsLeft = !n.craftableNow.isEmpty() || (n.vialsToMake > 0 && l.get(Resource.SHARDS) >= VIAL_COST);
		boolean armourCraftable = n.craftableNow.contains(GearSlot.BODY) || n.craftableNow.contains(GearSlot.LEGS)
			|| n.craftableNow.contains(GearSlot.HELM);
		// Mid-prep (1.7.0): home with a full bag and armour to craft did nothing, so the depot rule
		// sent us home again as soon as we stepped out. Make room the same way (a finished tool
		// first), then the worn armour frees the material stacks.
		if (!roomToCraft && !finalTrip && armourCraftable)
		{
			for (Resource t : new Resource[]{Resource.ORE, Resource.BARK})
			{
				boolean has = t == Resource.ORE ? l.pickaxe : l.axe;
				if (has && n.get(t) == 0 && !in.droppedRecently.contains(t))
				{
					return PrepGoal.dropTool(t, why + ": room to craft the armour");
				}
			}
			if (l.get(Resource.RAW_FISH) > 0 && !in.droppedRecently.contains(Resource.RAW_FISH))
			{
				return PrepGoal.dropSurplus(Resource.RAW_FISH, why + ": room to craft the armour (one raw paddlefish)");
			}
		}
		if (!roomToCraft && finalTrip && craftsLeft)
		{
			if (pestleDone(l, n) && !in.droppedRecently.contains(Resource.SHARDS))
			{
				return PrepGoal.dropTool(Resource.SHARDS, why + ": room for the last crafts (pestle and mortar, no more dust needed)");
			}
			for (Resource t : new Resource[]{Resource.ORE, Resource.BARK, Resource.RAW_FISH})
			{
				boolean has = t == Resource.ORE ? l.pickaxe : t == Resource.BARK ? l.axe : l.harpoon;
				if (has && !in.droppedRecently.contains(t))
				{
					return PrepGoal.dropTool(t, why + ": room for the last crafts");
				}
			}
			if (l.get(Resource.RAW_FISH) > 0 && !in.droppedRecently.contains(Resource.RAW_FISH))
			{
				return PrepGoal.dropSurplus(Resource.RAW_FISH, why + ": room for the last crafts (one raw paddlefish)");
			}
		}
		if (roomToCraft && n.craftableNow.contains(GearSlot.STAFF))
		{
			return PrepGoal.craft(GearSlot.STAFF, why + ": staff first");
		}
		// Two vials during prep, never a 3rd (user, 0.3.26): the 3rd potion's vial comes from a
		// 3 + 1 decant at a fishing spot, or is made here on the final trip.
		// The final trip makes the vials of the REQUIRED brews before the gear; the loose extra
		// brew's vial only after it (user, 0.3.34: gear is required, potions are loose).
		int containersHeld = l.emptyVials + l.waterVials + l.unfinishedPotions;
		int vialsWanted = finalTrip ? Math.min(n.vialsToMake, Math.max(0, n.requiredPotionsToMake - containersHeld))
			: Math.min(n.vialsToMake, Math.max(0, 2 - in.vialsMade));
		if (roomToCraft && vialsWanted > 0 && l.get(Resource.SHARDS) >= VIAL_COST)
		{
			return PrepGoal.of(PrepGoal.Type.MAKE_VIALS, why + ": " + vialsWanted + " vial(s)");
		}
		boolean extraBrewOnly = n.requiredPotionsToMake == 0;
		if (!extraBrewOnly)
		{
			PrepGoal v = lastBrewVial(in, why, finalTrip, roomToCraft);
			if (v != null)
			{
				return v;
			}
		}
		java.util.List<GearSlot> crafts = new java.util.ArrayList<>(n.craftableNow);
		// The bow is made as a complete T3 (frame + shards + bowstring all in hand), in one batch at
		// the end. Without the bowstring it waits; on the final trip a T2 bow is made anyway (user,
		// 0.4.9: "better a T2 bow than nothing"; the fight has no unequip strategy, and the kill
		// model puts T2 bow + T3 staff at ~89% against near zero with the staff alone).
		if (l.tier.getOrDefault(GearSlot.BOW, 0) == 0 && l.get(Resource.BOWSTRING) == 0 && !finalTrip)
		{
			crafts.remove(GearSlot.BOW);
		}
		if (!crafts.isEmpty() && roomToCraft)
		{
			int size = crafts.size();
			int pick = in.runSeed == 0 ? 0 : Math.floorMod((in.runSeed >>> 1) + size * 7, size);
			return PrepGoal.craft(crafts.get(pick), why);
		}
		// The loose extra brew, with the shards the gear left (final trip).
		if (finalTrip && extraBrewOnly)
		{
			PrepGoal v = lastBrewVial(in, why, true, roomToCraft);
			if (v != null)
			{
				return v;
			}
		}
		// Wearing the new gear and the inventory-only brew steps happen as chores on the walk to the
		// range / out of the room (decide()). Vials are filled at fishing spots, never the pump
		// (user), except as a last resort on the final trip.
		if (finalTrip && n.potionsToMake > 0 && l.emptyVials > 0 && !in.pumpBlocked)
		{
			return PrepGoal.of(PrepGoal.Type.BREW, why + ": vials still empty, pump as a last resort");
		}
		// Going back out for fish near home after a teleport: buy another crystal for the trip back
		// if the shards are spare and its slot doesn't cost a fish (user).
		int spare = l.get(Resource.SHARDS) - n.demand.getOrDefault(Resource.SHARDS, 0);
		// Only with time for that trip: 0.3.25 made one at t-74 s and never used it.
		boolean timeForTrip = in.secondsLeft < 0 || in.secondsLeft - in.finishReserve > 40;
		if (!finalTrip && timeForTrip && roomToCraft && !in.hasTeleport && n.get(Resource.RAW_FISH) > 0 && spare >= CRYSTAL_COST
			&& l.freeSlots - 1 >= n.get(Resource.RAW_FISH) && l.tier.getOrDefault(GearSlot.STAFF, 0) >= 2)
		{
			return PrepGoal.of(PrepGoal.Type.MAKE_CRYSTAL, why + ": " + spare + " spare shards, a crystal for the way back");
		}
		// Spare time before the boss goes on more fish from a spot near home (1.2.0), then they're
		// all cooked at once.
		if (finalTrip)
		{
			PrepGoal t = topUp(in, why);
			if (t != null)
			{
				return t;
			}
		}
		// Fish are cooked on the final trip only (user, 0.3.23: prayers keep us safe during prep, a
		// mid-prep cook is time spent for nothing; 0.3.22 also got stuck on that cook).
		if (finalTrip && l.get(Resource.RAW_FISH) > 0)
		{
			return PrepGoal.of(PrepGoal.Type.COOK, why);
		}
		return finalTrip ? PrepGoal.of(PrepGoal.Type.HANDOVER, why) : null;
	}

	/**
	 * Decant method, last trip: the last brew needs the vial the 2-dose leaves behind. If that
	 * 2-dose wasn't sipped empty, make one more vial when shards allow, else finish sipping it.
	 */
	private static PrepGoal lastBrewVial(Inputs in, String why, boolean finalTrip, boolean roomToCraft)
	{
		Ledger l = in.ledger;
		Needs n = in.needs;
		boolean noContainer = l.emptyVials + l.waterVials + l.unfinishedPotions == 0;
		if (finalTrip && n.potionsToMake > 0 && noContainer && l.get(Resource.GRYM) > 0)
		{
			if (roomToCraft && l.get(Resource.SHARDS) >= VIAL_COST + DUST_COST)
			{
				return PrepGoal.of(PrepGoal.Type.MAKE_VIALS, why + ": a vial for the last brew");
			}
			if (!n.vialsSmash && l.doseCounts[1] + l.doseCounts[2] > 0 && l.dosesDrunk < n.prepDoseAllowance)
			{
				return PrepGoal.of(PrepGoal.Type.DRINK, why + ": empty the small potion for the last brew");
			}
		}
		return null;
	}

	/** Something brewable now with the bowl/pump at hand (vials are made in the step before). */
	private static boolean canBrewAtHome(Ledger l)
	{
		if (l.emptyVials > 0)
		{
			return true; // fill at the pump: water vials are ready for a grym found out there
		}
		int vials = l.emptyVials + l.waterVials;
		boolean grymStep = vials > 0 && l.get(Resource.GRYM) > 0;
		boolean dustStep = l.unfinishedPotions > 0
			&& (l.dust >= 10 || (l.pestle && l.get(Resource.SHARDS) >= 10 && (l.dust > 0 || l.freeSlots > 0)));
		return grymStep || dustStep;
	}

	private static boolean canBrewSomething(Ledger l)
	{
		boolean dustOrShards = l.dust >= 10 || l.get(Resource.SHARDS) >= 10;
		boolean grym = l.unfinishedPotions > 0 || l.get(Resource.GRYM) > 0;
		// TODO(recon): vials come from the bowl (cost unknown); BrewAction makes one if none in hand.
		return dustOrShards && grym;
	}
}
