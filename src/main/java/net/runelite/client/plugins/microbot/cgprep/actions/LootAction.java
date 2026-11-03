package net.runelite.client.plugins.microbot.cgprep.actions;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.cgprep.data.CgIds;
import net.runelite.client.plugins.microbot.cgprep.data.CgMode;
import net.runelite.client.plugins.microbot.cgprep.data.Resource;
import net.runelite.client.plugins.microbot.cgprep.plan.Ledger;
import net.runelite.client.plugins.microbot.cgprep.plan.Needs;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

/**
 * Pick up drops we can use (recon: drops land on the ground, "Take").
 *
 * What counts (user): anything still on the shopping list: shards, a needed frame, T3 components
 * (a spike crushes into 80 shards), grym leaves and raw paddlefish while still needed,
 * ore/bark/linum, and teleport crystals (up to 3: each one is a free trip home).
 *
 * Where (0.3.5 run: the Dark Beast's bowstring was never picked up): drops land where the monster
 * DIED, which with the staff is ~7 tiles away, and only after its death animation. So looting
 * looks around the kill tile as well as around the player, and keeps waiting for the drop for a
 * few seconds after the kill.
 *
 * How (user: "spam-click the pile like a player"): every wanted item on the pile is clicked in
 * turn, each as soon as the previous one is gone, with a short human gap, re-clicking an item
 * that hasn't been taken within about a tick and a half. One call clears one pile.
 */
public final class LootAction
{
	public static final int RADIUS = 6;
	private static final int MAX_TELEPORTS = 3;
	private final Random rng = new Random();
	/** Raw paddlefish dropped to make room for drops (for the log; read and reset by the script). */
	public volatile int droppedFish;

	/** Ids worth picking up right now, given what we hold and still need. */
	public static int[] wanted(CgMode mode, Ledger l, Needs n)
	{
		List<Integer> ids = new ArrayList<>();
		int free = l == null ? 0 : l.freeSlots;
		// A raw paddlefish can make room for a drop we need (user, 0.3.29), so a full bag with fish
		// in it still wants the drop.
		boolean fishToSpare = l != null && l.get(Resource.RAW_FISH) > 0;
		ids.add(mode.shard);
		if (has(n, Resource.ORE) || (l != null && l.get(Resource.ORE) > 0)) ids.add(mode.ore);
		if (has(n, Resource.BARK) || (l != null && l.get(Resource.BARK) > 0)) ids.add(mode.bark);
		if (has(n, Resource.LINUM) || (l != null && l.get(Resource.LINUM) > 0)) ids.add(mode.linum);
		if (free > 0 || fishToSpare)
		{
			if (has(n, Resource.FRAME)) ids.add(mode.frame);
			ids.add(mode.bowstring);
			ids.add(mode.orb);
			ids.add(mode.spike);
			if (has(n, Resource.GRYM)) ids.add(mode.grym);
			if (has(n, Resource.RAW_FISH) && free > 0) ids.add(CgIds.RAW_PADDLEFISH);
			if (Rs2Inventory.count(mode.teleportCrystal) < MAX_TELEPORTS) ids.add(mode.teleportCrystal);
			for (net.runelite.client.plugins.microbot.cgprep.data.GearSlot g : net.runelite.client.plugins.microbot.cgprep.data.GearSlot.values())
			{
				for (int id : g.ids(mode))
				{
					ids.add(id); // our own crafted gear, dropped when crafted with a full inventory
				}
			}
		}
		return ids.stream().mapToInt(Integer::intValue).toArray();
	}

	private static boolean has(Needs n, Resource r)
	{
		return n != null && n.get(r) > 0;
	}

	/** Wanted drops within `radius` of the player, or within 2 tiles of `around` (a kill tile). */
	public static List<Rs2TileItemModel> wantedNear(CgMode mode, Ledger l, Needs n, int radius, WorldPoint around)
	{
		int[] ids = wanted(mode, l, n);
		List<Rs2TileItemModel> out = new ArrayList<>();
		if (ids.length == 0)
		{
			return out;
		}
		out.addAll(Microbot.getRs2TileItemCache().query().withIds(ids).within(radius).toList());
		if (around != null)
		{
			for (Rs2TileItemModel it : Microbot.getRs2TileItemCache().query().withIds(ids).toList())
			{
				WorldPoint p = it.getWorldLocation();
				if (p != null && p.distanceTo2D(around) <= 2 && out.stream().noneMatch(o -> o.getHash() == it.getHash()))
				{
					out.add(it);
				}
			}
		}
		return out;
	}

	public static Rs2TileItemModel nearestWanted(CgMode mode, Ledger l, Needs n, int radius)
	{
		List<Rs2TileItemModel> items = wantedNear(mode, l, n, radius, null);
		return items.isEmpty() ? null : items.get(0);
	}

	/**
	 * Clear the nearest wanted pile. DONE when nothing wanted is left (and, with a kill tile, the
	 * caller decides how long to keep waiting for the drop to appear).
	 */
	public ActionResult step(CgMode mode, Ledger l, Needs n, WorldPoint killTile)
	{
		List<Rs2TileItemModel> items = wantedNear(mode, l, n, RADIUS, killTile);
		if (items.isEmpty())
		{
			return ActionResult.DONE;
		}
		// The pile nearest the kill tile (or us): everything on that tile, components first.
		WorldPoint ref = killTile;
		Rs2TileItemModel first = items.stream()
			.min(Comparator.comparingInt(i -> ref == null || i.getWorldLocation() == null ? 0 : i.getWorldLocation().distanceTo2D(ref)))
			.orElse(items.get(0));
		WorldPoint pile = first.getWorldLocation();
		List<Rs2TileItemModel> onPile = new ArrayList<>();
		for (Rs2TileItemModel i : items)
		{
			if (pile == null || pile.equals(i.getWorldLocation()))
			{
				onPile.add(i);
			}
		}
		onPile.sort(Comparator.comparingInt(i -> priority(mode, i.getId())));

		// Bounded (0.3.23 sat 23 s in here on one pile): at most ~3 s per call, one retry per item;
		// whatever's left is picked up on the next loop.
		long deadline = System.currentTimeMillis() + 3000;
		int taken = 0;
		for (Rs2TileItemModel item : onPile)
		{
			if (System.currentTimeMillis() > deadline)
			{
				break;
			}
			if (!item.isStackable() && Rs2Inventory.emptySlotCount() == 0)
			{
				// Full bag: a raw paddlefish goes for a drop we need (never for another fish).
				if (item.getId() == CgIds.RAW_PADDLEFISH || !Rs2Inventory.contains(CgIds.RAW_PADDLEFISH)
					|| !Rs2Inventory.interact(CgIds.RAW_PADDLEFISH, "Drop")
					|| !sleepUntil(() -> Rs2Inventory.emptySlotCount() > 0, 1200))
				{
					continue;
				}
				droppedFish++;
			}
			if (!Aim.onScreen(item.getLocalLocation()))
			{
				WorldPoint at = item.getWorldLocation();
				if (at != null)
				{
					net.runelite.client.plugins.microbot.util.walker.Rs2Walker.walkMiniMap(at);
				}
				return ActionResult.CLICKED; // walking over to it; the next loop picks it up
			}
			int id = item.getId();
			int before = Rs2Inventory.itemQuantity(id);
			boolean got = false;
			for (int attempt = 0; attempt < 2 && !got; attempt++)
			{
				if (!item.pickup())
				{
					break;
				}
				// The first pickup includes the walk to the pile; later ones are a tick each.
				long left = Math.max(300, deadline - System.currentTimeMillis());
				got = sleepUntil(() -> Rs2Inventory.itemQuantity(id) > before || item.isDespawned(),
					(int) Math.min(left, taken == 0 && attempt == 0 ? 2400 : 900));
			}
			if (got)
			{
				taken++;
				Global.sleep(60 + rng.nextInt(110)); // human gap before the next click on the pile
			}
		}
		return taken > 0 ? ActionResult.CLICKED : ActionResult.NO_REACTION;
	}

	private static int priority(CgMode mode, int id)
	{
		if (id == mode.bowstring || id == mode.orb || id == mode.spike || id == mode.frame) return 0;
		for (net.runelite.client.plugins.microbot.cgprep.data.GearSlot g : net.runelite.client.plugins.microbot.cgprep.data.GearSlot.values())
		{
			for (int x : g.ids(mode))
			{
				if (x == id) return 0;
			}
		}
		if (id == mode.teleportCrystal) return 1;
		if (id == mode.shard) return 3;
		return 2;
	}
}
