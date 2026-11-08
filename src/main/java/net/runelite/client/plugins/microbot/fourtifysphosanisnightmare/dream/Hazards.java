package net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream;

import java.util.Collection;
import java.util.function.Predicate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;

/**
 * Tiles to keep off: her rifts until they land, the tiles under her while they are out, the three-by-three
 * area around each spore, the unsafe quarters of her flowers and the lane of a coming surge. Picks the nearest tile clear of all of them.
 */
public class Hazards
{
    /** A surge charges four ticks after she reappears and takes a few more to cross the arena. */
    static final int SURGE_TICKS = 8;
    /** Walking toward a player, she steps every other tick, so one tick standing still does not end her walk. */
    private static final int WALK_GAP_TICKS = 2;
    private final Map<WorldPoint, Integer> rifts = new HashMap<>();
    private final Set<WorldPoint> spores = new HashSet<>();
    private FlowerLayout flowers;
    private WorldArea surgeFrom;
    private WorldArea surgeTo;
    private boolean surgeAcross;
    private int surgeUntil = -1;
    private WorldPoint centre;
    private WorldArea body;
    private WorldPoint player;
    private boolean walking;
    private int stillTicks = WALK_GAP_TICKS;
    private int now;

    public void reset()
    {
        rifts.clear();
        spores.clear();
        flowers = null;
        surgeFrom = null;
        surgeTo = null;
        surgeUntil = -1;
        centre = null;
        body = null;
        player = null;
        walking = false;
        stillTicks = WALK_GAP_TICKS;
    }

    /** Where she first stands in the dream: the arena's centre, where she sleeps and returns for her flowers. */
    public void observeBoss(WorldArea area)
    {
        boolean moved = body != null && area != null && (area.getX() != body.getX() || area.getY() != body.getY());
        stillTicks = moved ? 0 : stillTicks + 1;
        walking = stillTicks < WALK_GAP_TICKS;
        body = area;
        if (centre == null && area != null)
        {
            centre = new WorldPoint(area.getX(), area.getY(), area.getPlane());
        }
    }

    /** Her sleeping place at the arena's centre, where she also charges her blast; none before she is seen. */
    public WorldArea home()
    {
        return centre == null || body == null ? null : new WorldArea(centre, body.getWidth(), body.getHeight());
    }

    /**
     * She reappeared after leaving. Back at the centre it is her flowers; anywhere else she stands at the start
     * of a surge lane, facing along it.
     */
    public void observeArrival(WorldArea area, int orientation, int tick)
    {
        if (area != null && !new WorldPoint(area.getX(), area.getY(), area.getPlane()).equals(centre))
        {
            // She turns toward the lane's end over several frames, so her facing on arrival can still be the old one.
            observeSurge(area, centre == null ? direction(orientation) : laneDirection(centre, area), tick);
        }
    }

    /**
     * She surges from the edge of the arena where she reappears across to the far side: 0 south, 1 west, 2 north,
     * 3 east. The edge is the side of her sleeping place she stands farthest out on.
     */
    static int laneDirection(WorldPoint centre, WorldArea from)
    {
        int dx = from.getX() - centre.getX();
        int dy = from.getY() - centre.getY();
        if (Math.abs(dx) > Math.abs(dy))
        {
            return dx < 0 ? 3 : 1;
        }
        return dy < 0 ? 2 : 0;
    }

    /** Where the player stands this tick; she steps toward a player out of her reach. */
    public void observePlayer(WorldPoint tile)
    {
        player = tile;
    }

    /**
     * A rift opened {@code age} ticks before this tick lands four ticks after it opened. Its portal stays in view
     * for a while after the claws land, so a landed one is not counted again.
     */
    public void observeRift(WorldPoint tile, int tick, int age)
    {
        int lands = tick - age + DreamData.RIFT_TICKS;
        if (lands >= tick)
        {
            rifts.putIfAbsent(tile, lands);
        }
    }

    public void setSpores(Collection<WorldPoint> tiles)
    {
        spores.clear();
        spores.addAll(tiles);
    }

    public void setFlowers(FlowerLayout layout)
    {
        flowers = layout;
    }

    /**
     * She reappeared at an edge facing along her lane: 0 south, 1 west, 2 north or 3 east. East and west charges
     * cross rows, the others columns, and she lands at the far end.
     */
    public void observeSurge(WorldArea from, int direction, int tick)
    {
        boolean centreColumn = centre != null && (direction == 0 || direction == 2) && from.getX() == centre.getX();
        int length = centreColumn ? DreamData.CENTRE_SURGE_LENGTH : DreamData.SURGE_LENGTH;
        int dx = direction == 3 ? length : direction == 1 ? -length : 0;
        int dy = direction == 2 ? length : direction == 0 ? -length : 0;
        surgeFrom = from;
        surgeTo = new WorldArea(from.getX() + dx, from.getY() + dy, from.getWidth(), from.getHeight(), from.getPlane());
        surgeAcross = direction == 1 || direction == 3;
        surgeUntil = tick + SURGE_TICKS;
    }

    /** Every surge lane starts at the arena's edge, at least half a lane from her centre. */
    public boolean atLaneStart(WorldArea area)
    {
        return centre != null && area != null && Math.max(Math.abs(area.getX() - centre.getX()),
            Math.abs(area.getY() - centre.getY())) >= DreamData.SURGE_LENGTH / 2;
    }

    /** Where her coming surge ends, so melee can wait there; none while no surge is coming. */
    public WorldArea surgeLanding()
    {
        return surgeFrom == null ? null : surgeTo;
    }

    /** She has charged across: the lane is clear again. */
    public void surgeLanded()
    {
        surgeFrom = null;
        surgeTo = null;
    }

    /**
     * The direction she faces, rounded to the nearest of the four: 0 south, 1 west, 2 north, 3 east. She faces the
     * lane's far end from her centre, a little off the straight line (orientation 0 south, 512 west, 1024 north,
     * 1536 east).
     */
    public static int direction(int orientation)
    {
        return (orientation + 256) / 512 % 4;
    }

    public void expire(int tick)
    {
        now = tick;
        rifts.values().removeIf(lands -> lands < tick);
        if (tick > surgeUntil)
        {
            surgeFrom = null;
        }
    }

    public boolean hasRifts()
    {
        return !rifts.isEmpty();
    }

    /** Any hazard is out: rifts, spores, flowers or a coming surge. */
    public boolean isActive()
    {
        return !rifts.isEmpty() || !spores.isEmpty() || flowers != null || surgeFrom != null;
    }

    public boolean flowersActive()
    {
        return flowers != null;
    }

    public boolean isDangerous(WorldPoint tile)
    {
        return clawed(tile) || dangerBesideClaws(tile);
    }

    /** A spore, an unsafe flower quarter or her surge lane. */
    private boolean dangerBesideClaws(WorldPoint tile)
    {
        if (nearSpore(tile))
        {
            return true;
        }
        if (flowers != null && !flowers.isSafe(tile))
        {
            return true;
        }
        return inSurgeLane(tile);
    }

    /**
     * Her claws will come up here: a rift's tile, or under her while her rifts are out. While she walks toward a
     * player out of her reach, any tile next to her can end up under her too.
     */
    private boolean clawed(WorldPoint tile)
    {
        if (tile == null || rifts.containsKey(tile))
        {
            return true;
        }
        if (rifts.isEmpty() || body == null)
        {
            return false;
        }
        boolean coming = walking && player != null && !new WorldArea(player, 1, 1).isInMeleeDistance(body);
        return coming ? new WorldArea(body.getX() - 1, body.getY() - 1, body.getWidth() + 2, body.getHeight() + 2,
            body.getPlane()).contains(tile) : body.contains(tile);
    }

    /** Within a tile of a spore, which bursts and slows the player. */
    private boolean nearSpore(WorldPoint tile)
    {
        return spores.stream().anyMatch(spore -> spore.getPlane() == tile.getPlane()
            && Math.abs(spore.getX() - tile.getX()) <= 1 && Math.abs(spore.getY() - tile.getY()) <= 1);
    }

    private boolean inSurgeLane(WorldPoint tile)
    {
        if (surgeFrom == null)
        {
            return false;
        }
        if (surgeAcross)
        {
            return tile.getY() >= surgeFrom.getY() && tile.getY() < surgeFrom.getY() + surgeFrom.getHeight();
        }
        return tile.getX() >= surgeFrom.getX() && tile.getX() < surgeFrom.getX() + surgeFrom.getWidth();
    }

    /** From outside her coming lane, the way to a tile on its far side runs through the lane. */
    private boolean crossesSurgeLane(WorldPoint player, WorldPoint tile)
    {
        if (surgeFrom == null || inSurgeLane(player))
        {
            return false;
        }
        int from = surgeAcross ? player.getY() : player.getX();
        int to = surgeAcross ? tile.getY() : tile.getX();
        int low = surgeAcross ? surgeFrom.getY() : surgeFrom.getX();
        return from < low != to < low;
    }

    /**
     * The nearest reachable tile clear of every hazard and outside her body, preferring one beside her so
     * melee can continue; none when the player is already safe or no tile is clear.
     */
    public WorldPoint escape(WorldPoint player, Collection<WorldPoint> reachable, WorldArea boss)
    {
        if (!isDangerous(player) && (boss == null || !boss.contains(player)))
        {
            return null;
        }
        return nearestSafe(player, reachable, boss, false, tile -> true);
    }

    /** The nearest safe reachable tile beside her, or none; melee waits there instead of crossing a hazard. */
    public WorldPoint beside(WorldPoint player, Collection<WorldPoint> reachable, WorldArea boss)
    {
        return boss == null ? null : nearestSafe(player, reachable, boss, true, tile -> true);
    }

    /** The nearest safe reachable tile outside her body from which {@code reaches} holds, or none. */
    public WorldPoint inReach(WorldPoint player, Collection<WorldPoint> reachable, WorldArea boss, Predicate<WorldPoint> reaches)
    {
        return nearestSafe(player, reachable, boss, false, reaches);
    }

    /**
     * The nearest tile clear of her hazards, preferring one whose way there is clear too. The game checks her claws
     * and spores against the tile the player ends a tick on, and a slowed player ends a tick on every tile of the
     * way. So with no clear way, the way may still cross a flower or surge lane, but not her claws or a spore: the
     * player waits instead, unless a hit will land where the player stands.
     */
    private WorldPoint nearestSafe(WorldPoint player, Collection<WorldPoint> reachable, WorldArea boss, boolean besideOnly,
        Predicate<WorldPoint> wanted)
    {
        WorldPoint best = nearestSafe(player, reachable, boss, besideOnly, wanted, this::isDangerous);
        if (best == null)
        {
            // A player beside a spore is slowed already, so another costs nothing more.
            boolean slowed = nearSpore(player);
            best = nearestSafe(player, reachable, boss, besideOnly, wanted, tile -> clawed(tile) || !slowed && nearSpore(tile));
        }
        return best != null || !harmful(player) ? best : nearestSafe(player, reachable, boss, besideOnly, wanted, step -> false);
    }

    /** A hit lands here: her claws, an unsafe flower quarter or her surge lane. A spore only slows the player. */
    private boolean harmful(WorldPoint tile)
    {
        return clawed(tile) || flowers != null && !flowers.isSafe(tile) || inSurgeLane(tile);
    }

    private WorldPoint nearestSafe(WorldPoint player, Collection<WorldPoint> reachable, WorldArea boss, boolean besideOnly,
        Predicate<WorldPoint> wanted, Predicate<WorldPoint> blocksWay)
    {
        WorldPoint best = null;
        int bestScore = Integer.MAX_VALUE;
        for (WorldPoint tile : reachable)
        {
            // Melee reaches only the sides, not the corners.
            boolean beside = boss != null && new WorldArea(tile, 1, 1).isInMeleeDistance(boss);
            int distance = Math.max(Math.abs(tile.getX() - player.getX()), Math.abs(tile.getY() - player.getY()));
            // Her claws hit only the tile the player stands on as they land. A walk over before the next land,
            // even at a slowed tile a tick, may cross them; other hazards still block it.
            Predicate<WorldPoint> way = now + distance < nextLanding()
                ? step -> blocksWay.test(step) && dangerBesideClaws(step) : blocksWay;
            if (isDangerous(tile) || crossesSurgeLane(player, tile) || boss != null && boss.contains(tile)
                || besideOnly && !beside || !wanted.test(tile) || !wayClear(player, tile, way))
            {
                continue;
            }
            int score = distance * 2 + (beside ? 0 : 1);
            if (score < bestScore)
            {
                best = tile;
                bestScore = score;
            }
        }
        return best;
    }

    /** The tick the next of her claws land, or never while none are out. */
    private int nextLanding()
    {
        return rifts.values().stream().mapToInt(Integer::intValue).min().orElse(Integer.MAX_VALUE);
    }

    /**
     * No hazard lies on the way from the player to the tile. The walk takes one of two shapes on an open floor,
     * diagonal steps first or last, so both must be clear.
     */
    boolean wayClear(WorldPoint player, WorldPoint tile)
    {
        return wayClear(player, tile, this::isDangerous);
    }

    private boolean wayClear(WorldPoint player, WorldPoint tile, Predicate<WorldPoint> blocksWay)
    {
        return wayClear(player, tile, true, blocksWay) && wayClear(player, tile, false, blocksWay);
    }

    private boolean wayClear(WorldPoint player, WorldPoint tile, boolean diagonalFirst, Predicate<WorldPoint> blocksWay)
    {
        int x = player.getX();
        int y = player.getY();
        while (x != tile.getX() || y != tile.getY())
        {
            int dx = tile.getX() - x;
            int dy = tile.getY() - y;
            boolean diagonal = dx != 0 && dy != 0 && (diagonalFirst || Math.abs(dx) == Math.abs(dy));
            if (diagonal || Math.abs(dx) > Math.abs(dy))
            {
                x += Integer.signum(dx);
            }
            if (diagonal || Math.abs(dy) > Math.abs(dx))
            {
                y += Integer.signum(dy);
            }
            WorldPoint step = new WorldPoint(x, y, player.getPlane());
            if (!step.equals(tile) && blocksWay.test(step))
            {
                return false;
            }
        }
        return true;
    }
}
