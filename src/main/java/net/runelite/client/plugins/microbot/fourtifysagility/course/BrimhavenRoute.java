package net.runelite.client.plugins.microbot.fourtifysagility.course;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import net.runelite.api.coords.WorldPoint;

/** Route between the 25 platforms of the Brimhaven Agility Arena. */
public final class BrimhavenRoute
{
    public static final int PLANE = 3;
    public static final int WEST = 2761;
    public static final int SOUTH = 9546;
    public static final int SPACING = 11;
    private static final int SIZE = 5;
    private static final int PLATFORM_COUNT = SIZE * SIZE;

    // Each row runs west to east; rows run south to north. Each entry is the
    // obstacle type placed between two neighbouring platforms.
    private static final Obstacle[][] EAST = {
        {Obstacle.LEDGE, Obstacle.MONKEY_BARS, Obstacle.HAND_HOLDS, Obstacle.LEDGE},
        {Obstacle.PLANK, Obstacle.SPINNING_BLADES, Obstacle.DARTS, Obstacle.PRESSURE_PAD},
        {Obstacle.ROPE_SWING, Obstacle.DARTS, Obstacle.PILLAR, Obstacle.FLOOR_SPIKES},
        {Obstacle.LOG_BALANCE, Obstacle.SPINNING_BLADES, Obstacle.BLADE, Obstacle.PRESSURE_PAD},
        {Obstacle.LEDGE, Obstacle.LOW_WALL, Obstacle.HAND_HOLDS, Obstacle.PLANK},
    };
    private static final Obstacle[][] NORTH = {
        {Obstacle.PILLAR, Obstacle.FLOOR_SPIKES, Obstacle.BLADE, Obstacle.BALANCING_ROPE, Obstacle.LOG_BALANCE},
        {Obstacle.HAND_HOLDS, Obstacle.BALANCING_ROPE, Obstacle.LOW_WALL, Obstacle.MONKEY_BARS, Obstacle.LOW_WALL},
        {Obstacle.FLOOR_SPIKES, Obstacle.MONKEY_BARS, Obstacle.SPINNING_BLADES, Obstacle.DARTS, Obstacle.PILLAR},
        {Obstacle.BLADE, Obstacle.PRESSURE_PAD, Obstacle.BALANCING_ROPE, Obstacle.LOG_BALANCE, Obstacle.ROPE_SWING},
    };

    public enum Obstacle
    {
        BLADE(1, 5),
        ROPE_SWING(1, 4),
        LOW_WALL(1, 5),
        PLANK(1, 9),
        BALANCING_ROPE(1, 9),
        LOG_BALANCE(1, 9),
        LEDGE(1, 9),
        MONKEY_BARS(1, 13),
        PILLAR(1, 9),
        PRESSURE_PAD(20, 4),
        FLOOR_SPIKES(20, 4),
        HAND_HOLDS(20, 10),
        SPINNING_BLADES(40, 5),
        DARTS(40, 10);

        private final int minimumLevel;
        private final int cost;

        Obstacle(int minimumLevel, int cost)
        {
            this.minimumLevel = minimumLevel;
            this.cost = cost;
        }

        public int minimumLevel()
        {
            return minimumLevel;
        }
    }

    public static final class Step
    {
        private final WorldPoint from;
        private final WorldPoint to;
        private final Obstacle obstacle;

        private Step(WorldPoint from, WorldPoint to, Obstacle obstacle)
        {
            this.from = from;
            this.to = to;
            this.obstacle = obstacle;
        }

        public WorldPoint from()
        {
            return from;
        }

        public WorldPoint to()
        {
            return to;
        }

        public Obstacle obstacle()
        {
            return obstacle;
        }
    }

    private BrimhavenRoute()
    {
    }

    /** Returns the nearest platform centre, or null outside the arena. */
    public static WorldPoint platformAt(WorldPoint point)
    {
        if (point == null || point.getPlane() != PLANE
            || point.getX() < WEST - 2 || point.getX() > WEST + 4 * SPACING + 2
            || point.getY() < SOUTH - 2 || point.getY() > SOUTH + 4 * SPACING + 2)
        {
            return null;
        }
        int x = Math.floorDiv(point.getX() - WEST + SPACING / 2, SPACING);
        int y = Math.floorDiv(point.getY() - SOUTH + SPACING / 2, SPACING);
        return centre(x, y);
    }

    /** Returns an empty optional when no route is available at the current level. */
    public static Optional<List<Step>> find(WorldPoint origin, WorldPoint target, int agility)
    {
        WorldPoint start = platformAt(origin);
        WorldPoint end = platformAt(target);
        if (start == null || end == null || agility < 1)
        {
            return Optional.empty();
        }
        int source = index(start);
        int destination = index(end);
        int[] costs = new int[PLATFORM_COUNT];
        int[] previous = new int[PLATFORM_COUNT];
        boolean[] visited = new boolean[PLATFORM_COUNT];
        Arrays.fill(costs, Integer.MAX_VALUE);
        Arrays.fill(previous, -1);
        costs[source] = 0;
        for (int count = 0; count < PLATFORM_COUNT; count++)
        {
            int current = -1;
            for (int i = 0; i < PLATFORM_COUNT; i++)
            {
                if (!visited[i] && costs[i] != Integer.MAX_VALUE && (current < 0 || costs[i] < costs[current]))
                {
                    current = i;
                }
            }
            if (current < 0 || current == destination)
            {
                break;
            }
            visited[current] = true;
            int x = current % SIZE;
            int y = current / SIZE;
            for (int[] direction : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}})
            {
                int nextX = x + direction[0];
                int nextY = y + direction[1];
                if (nextX < 0 || nextX >= SIZE || nextY < 0 || nextY >= SIZE)
                {
                    continue;
                }
                Obstacle obstacle = obstacle(x, y, nextX, nextY);
                int next = nextY * SIZE + nextX;
                int cost = costs[current] + obstacle.cost;
                if (agility >= obstacle.minimumLevel && cost < costs[next])
                {
                    costs[next] = cost;
                    previous[next] = current;
                }
            }
        }
        if (costs[destination] == Integer.MAX_VALUE)
        {
            return Optional.empty();
        }
        List<Step> steps = new ArrayList<>();
        for (int cursor = destination; cursor != source; cursor = previous[cursor])
        {
            int from = previous[cursor];
            int x = from % SIZE;
            int y = from / SIZE;
            int nextX = cursor % SIZE;
            int nextY = cursor / SIZE;
            steps.add(new Step(centre(x, y), centre(nextX, nextY), obstacle(x, y, nextX, nextY)));
        }
        Collections.reverse(steps);
        return Optional.of(Collections.unmodifiableList(steps));
    }

    private static int index(WorldPoint centre)
    {
        return ((centre.getY() - SOUTH) / SPACING) * SIZE + (centre.getX() - WEST) / SPACING;
    }

    private static WorldPoint centre(int x, int y)
    {
        return new WorldPoint(WEST + x * SPACING, SOUTH + y * SPACING, PLANE);
    }

    private static Obstacle obstacle(int x, int y, int nextX, int nextY)
    {
        return x != nextX ? EAST[y][Math.min(x, nextX)] : NORTH[Math.min(y, nextY)][x];
    }
}
