package net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream;

import java.util.List;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.util.scene.TargetRange;

/**
 * The order to attack her sleepwalkers in, so that as few as possible reach her. Once it starts walking, each takes
 * a tile a tick toward her, straight or diagonally, and she takes it in on the tick it starts next to her, before
 * any hit lands on it that tick. A throw lands two ticks after it goes from up to two tiles away and three ticks from
 * farther, a melee hit lands the tick after, and the next attack goes two ticks after the last. The player runs two
 * tiles a tick, and takes both before stopping in throwing range. A sleepwalker farther than the click range cannot be clicked, so the player waits for it to come
 * closer. Every order is tried, with the last sleepwalker hit in melee or by a throw, whichever loses fewer.
 */
public final class SleepwalkerPlan
{
    private static final int CLOSE_RANGE = 2;
    /** An NPC farther than this is not clicked. */
    private static final int CLICK_RANGE = TargetRange.NPC_SEARCH_RADIUS;
    private static final int ATTACK_TICKS = 2;
    private static final int RUN_TILES = 2;
    /** No attack is planned this many ticks ahead. */
    private static final int HORIZON = 40;

    private SleepwalkerPlan()
    {
    }

    /** The sleepwalker to attack next, and whether it is hit in melee. */
    public static final class Step
    {
        private final int index;
        private final boolean melee;

        Step(int index, boolean melee)
        {
            this.index = index;
            this.melee = melee;
        }

        public int getIndex()
        {
            return index;
        }

        public boolean isMelee()
        {
            return melee;
        }
    }

    /**
     * The first step of the best order. Ticks count from the next one, when a click sent now is taken.
     *
     * @param walkIn the tick on which each sleepwalker takes its first step, at least 1
     * @param walkLate the same if they wait longer; an order is judged by the worse of the two
     * @param readyIn the tick on which the next attack can go, at least 1
     * @return the step, or null with no sleepwalkers
     */
    public static Step next(WorldPoint player, List<WorldPoint> walkers, int[] walkIn, int[] walkLate, WorldArea body,
        int readyIn, int throwRange)
    {
        int count = walkers.size();
        if (count == 0)
        {
            return null;
        }
        int[] order = new int[count];
        for (int index = 0; index < count; index++)
        {
            order[index] = index;
        }
        Step[] best = {null};
        long[] bestScore = {Long.MAX_VALUE};
        permute(order, 0, () ->
        {
            for (boolean melee : new boolean[] {true, false})
            {
                long score = Math.max(play(player, walkers, walkIn, body, readyIn, throwRange, order, melee),
                    play(player, walkers, walkLate, body, readyIn, throwRange, order, melee));
                if (score < bestScore[0])
                {
                    bestScore[0] = score;
                    best[0] = new Step(order[0], melee && count == 1);
                }
            }
        });
        return best[0];
    }

    private static void permute(int[] order, int from, Runnable each)
    {
        if (from == order.length)
        {
            each.run();
            return;
        }
        for (int index = from; index < order.length; index++)
        {
            swap(order, from, index);
            permute(order, from + 1, each);
            swap(order, from, index);
        }
    }

    private static void swap(int[] order, int a, int b)
    {
        int held = order[a];
        order[a] = order[b];
        order[b] = held;
    }

    /** Plays the order out: the sleepwalkers that reach her first, then the tick the last hit lands. */
    static long play(WorldPoint player, List<WorldPoint> walkers, int[] walkIn, WorldArea body, int readyIn, int throwRange,
        int[] order, boolean meleeLast)
    {
        WorldPoint at = player;
        int tick = 1;
        int ready = readyIn;
        int reached = 0;
        int landed = 0;
        for (int step = 0; step < order.length; step++)
        {
            int index = order[step];
            boolean melee = meleeLast && step == order.length - 1;
            int lands = -1;
            boolean clicked = false;
            while (tick <= HORIZON)
            {
                WorldPoint walker = position(walkers.get(index), walkIn[index], tick, body);
                // The click taken on this tick was sent on the one before, when it had to be in range; walking a tick
                // later than assumed keeps it farther away, so the click waits for that.
                clicked |= at.distanceTo(position(walkers.get(index), walkIn[index] + 1, tick - 1, body)) <= CLICK_RANGE;
                if (!clicked)
                {
                    tick++;
                    continue;
                }
                at = melee ? approachSide(at, walker) : approach(at, walker, throwRange);
                int distance = at.distanceTo(walker);
                if ((melee ? beside(at, walker) : distance <= throwRange) && tick >= ready)
                {
                    lands = tick + (melee ? 1 : distance <= CLOSE_RANGE ? 2 : 3);
                    break;
                }
                tick++;
            }
            if (lands < 0 || lands >= taken(walkers.get(index), walkIn[index], body))
            {
                reached++;
            }
            landed = Math.max(landed, lands < 0 ? HORIZON : lands);
            ready = tick + ATTACK_TICKS;
            tick++;
        }
        return (long) reached * (HORIZON * 4) + landed;
    }

    /** Where the sleepwalker stands once it has walked on the tick. */
    static WorldPoint position(WorldPoint from, int walkIn, int tick, WorldArea body)
    {
        WorldPoint at = from;
        for (int step = walkIn; step <= tick && !nextTo(at, body); step++)
        {
            at = towards(at, body);
        }
        return at;
    }

    /** The tick she takes the sleepwalker in. */
    static int taken(WorldPoint from, int walkIn, WorldArea body)
    {
        WorldPoint at = from;
        int tick = walkIn;
        while (!nextTo(at, body))
        {
            at = towards(at, body);
            tick++;
        }
        return tick;
    }

    private static boolean nextTo(WorldPoint at, WorldArea body)
    {
        return body.distanceTo(at) <= 1;
    }

    private static WorldPoint towards(WorldPoint from, WorldArea body)
    {
        int dx = from.getX() < body.getX() ? 1 : from.getX() >= body.getX() + body.getWidth() ? -1 : 0;
        int dy = from.getY() < body.getY() ? 1 : from.getY() >= body.getY() + body.getHeight() ? -1 : 0;
        return from.dx(dx).dy(dy);
    }

    /** Runs toward the sleepwalker until it is in throwing range, taking both steps of the tick before it stops. */
    private static WorldPoint approach(WorldPoint at, WorldPoint walker, int throwRange)
    {
        if (at.distanceTo(walker) <= throwRange)
        {
            return at;
        }
        for (int step = 0; step < RUN_TILES && at.distanceTo(walker) > 1; step++)
        {
            at = at.dx(Integer.signum(walker.getX() - at.getX())).dy(Integer.signum(walker.getY() - at.getY()));
        }
        return at;
    }

    /** Runs toward the nearest tile on one of the sleepwalker's four sides, where melee reaches it. */
    private static WorldPoint approachSide(WorldPoint at, WorldPoint walker)
    {
        for (int step = 0; step < RUN_TILES && !beside(at, walker); step++)
        {
            WorldPoint side = null;
            for (WorldPoint tile : new WorldPoint[] {walker.dx(1), walker.dx(-1), walker.dy(1), walker.dy(-1)})
            {
                if (side == null || at.distanceTo(tile) < at.distanceTo(side))
                {
                    side = tile;
                }
            }
            at = at.dx(Integer.signum(side.getX() - at.getX())).dy(Integer.signum(side.getY() - at.getY()));
        }
        return at;
    }

    private static boolean beside(WorldPoint at, WorldPoint walker)
    {
        return Math.abs(at.getX() - walker.getX()) + Math.abs(at.getY() - walker.getY()) == 1;
    }
}
