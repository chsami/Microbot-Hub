package net.runelite.client.plugins.microbot.droslayer.walker;

import java.util.Arrays;
import java.util.BitSet;
import java.util.function.IntConsumer;

/**
 * Local collision-grid helper for Efficient Walker.
 */
final class LocalCollisionGrid
{
    private static final int[] DELTA_X = {-1, 1, 0, 0, -1, 1, -1, 1};
    private static final int[] DELTA_Y = {0, 0, -1, 1, -1, -1, 1, 1};

    private static final int BLOCK_WEST = 19136776;
    private static final int BLOCK_EAST = 19136896;
    private static final int BLOCK_SOUTH = 19136770;
    private static final int BLOCK_NORTH = 19136800;

    private static final int BLOCK_SOUTH_WEST = 19136782;
    private static final int BLOCK_NORTH_WEST = 19136824;
    private static final int BLOCK_SOUTH_EAST = 19136899;
    private static final int BLOCK_NORTH_EAST = 19136992;

    /** RuneLite's full-movement collision mask used by the original helper. */
    private static final int BLOCK_MOVEMENT_FULL = 2359552;

    private LocalCollisionGrid()
    {
        // Utility class.
    }

    /**
     * Breadth-first path through a rectangular local collision grid.
     *
     * @return flattened tile indexes from start to target, inclusive; empty when unreachable.
     */
    static int[] findPath(int[][] collision, BitSet overrides, int startX, int startY, int targetX, int targetY)
    {
        if (!isWithinGridBounds(collision, startX, startY) || !isWithinGridBounds(collision, targetX, targetY))
        {
            return new int[0];
        }

        final int width = collision.length;
        final int height = collision[0].length;
        final int start = tileIndex(startX, startY, width);
        final int target = tileIndex(targetX, targetY, width);

        final int[] parents = new int[width * height];
        final int[] queue = new int[width * height];
        Arrays.fill(parents, -2);
        parents[start] = -1;

        int read = 0;
        int write = 0;
        queue[write++] = start;

        while (read < write && parents[target] == -2)
        {
            final int current = queue[read++];
            final int x = unpackX(current, width);
            final int y = unpackY(current, width);

            for (int direction = 0; direction < DELTA_X.length; direction++)
            {
                final int nextX = x + DELTA_X[direction];
                final int nextY = y + DELTA_Y[direction];

                if (!isWithinGridBounds(collision, nextX, nextY))
                {
                    continue;
                }

                final int next = tileIndex(nextX, nextY, width);
                if (parents[next] != -2)
                {
                    continue;
                }

                if (!canStep(collision, overrides, x, y, nextX, nextY))
                {
                    continue;
                }

                parents[next] = current;
                queue[write++] = next;
            }
        }

        if (parents[target] == -2)
        {
            return new int[0];
        }

        int length = 1;
        int cursor = target;
        while (cursor != start)
        {
            length++;
            cursor = parents[cursor];
        }

        final int[] path = new int[length];
        cursor = target;
        for (int index = length - 1; index >= 0; index--)
        {
            path[index] = cursor;
            cursor = parents[cursor];
        }

        return path;
    }

    /**
     * Tests one cardinal or diagonal step.
     */
    static boolean canStep(int[][] collision, BitSet overrides, int x, int y, int nextX, int nextY)
    {
        final int dx = nextX - x;
        final int dy = nextY - y;

        if (Math.abs(dx) > 1 || Math.abs(dy) > 1 || (dx == 0 && dy == 0))
        {
            return false;
        }

        if (dx == 0 || dy == 0)
        {
            return canTraverseCardinalEdge(collision, overrides, x, y, nextX, nextY);
        }

        final int diagonalMask;
        if (dx < 0)
        {
            diagonalMask = dy < 0 ? BLOCK_SOUTH_WEST : BLOCK_NORTH_WEST;
        }
        else
        {
            diagonalMask = dy < 0 ? BLOCK_SOUTH_EAST : BLOCK_NORTH_EAST;
        }

        final int width = collision.length;
        final int height = collision[0].length;
        final int horizontalEdge = cardinalEdgeIndex(width, height, x, y, x + dx, y);
        final int verticalEdge = cardinalEdgeIndex(width, height, x, y, x, y + dy);

        // v1.11.2 intentionally refuses a diagonal across either explicitly overridden
        // cardinal edge.
        if (overrides.get(horizontalEdge) || overrides.get(verticalEdge))
        {
            return false;
        }

        return (collision[nextX][nextY] & diagonalMask) == 0
            && canTraverseCardinalEdge(collision, overrides, x, y, x + dx, y)
            && canTraverseCardinalEdge(collision, overrides, x, y, x, y + dy);
    }

    /**
     * Encodes the undirected cardinal edge between adjacent tiles into the edge namespace
     * at the beginning of the override BitSet.
     */
    static int cardinalEdgeIndex(int width, int height, int x, int y, int nextX, int nextY)
    {
        final int dx = nextX - x;
        final int dy = nextY - y;

        if (Math.abs(dx) + Math.abs(dy) != 1)
        {
            return -1;
        }

        final int minX = dx < 0 ? nextX : x;
        final int minY = dy < 0 ? nextY : y;

        if (minX < 0 || minY < 0 || minX >= width || minY >= height)
        {
            return -1;
        }

        if ((dx != 0 && minX >= width - 1) || (dy != 0 && minY >= height - 1))
        {
            return -1;
        }

        return (tileIndex(minX, minY, width) << 1) | (dy != 0 ? 1 : 0);
    }

    /**
     * Encodes a per-tile collision override into the second namespace of the BitSet.
     */
    static int tileOverrideIndex(int width, int height, int x, int y)
    {
        return width * height * 2 + tileIndex(x, y, width);
    }

    /**
     * Converts RuneLite movement-direction bits into the cardinal edge indexes touched by them.
     */
    static void forEachDirectionalEdge(int width, int height, int x, int y, int movementMask, IntConsumer consumer)
    {
        for (int bit = 1; bit <= 128; bit <<= 1)
        {
            if ((movementMask & bit) == 0)
            {
                continue;
            }

            switch (bit)
            {
                case 1:
                    emitValidCardinalEdge(width, height, x, y, x - 1, y, consumer);
                    break;
                case 2:
                    emitValidCardinalEdge(width, height, x, y, x, y + 1, consumer);
                    break;
                case 4:
                    emitValidCardinalEdge(width, height, x, y, x + 1, y, consumer);
                    break;
                case 8:
                    emitValidCardinalEdge(width, height, x, y, x, y - 1, consumer);
                    break;
                case 16:
                    emitValidCardinalEdge(width, height, x, y, x - 1, y, consumer);
                    emitValidCardinalEdge(width, height, x, y, x, y + 1, consumer);
                    break;
                case 32:
                    emitValidCardinalEdge(width, height, x, y, x, y + 1, consumer);
                    emitValidCardinalEdge(width, height, x, y, x + 1, y, consumer);
                    break;
                case 64:
                    emitValidCardinalEdge(width, height, x, y, x + 1, y, consumer);
                    emitValidCardinalEdge(width, height, x, y, x, y - 1, consumer);
                    break;
                case 128:
                    emitValidCardinalEdge(width, height, x, y, x, y - 1, consumer);
                    emitValidCardinalEdge(width, height, x, y, x - 1, y, consumer);
                    break;
                default:
                    break;
            }
        }
    }

    static int tileIndex(int x, int y, int width)
    {
        return y * width + x;
    }

    static int unpackX(int value, int divisor)
    {
        return value % divisor;
    }

    static int unpackY(int value, int divisor)
    {
        return value / divisor;
    }

    /**
     * Cardinal movement check. A normal collision-free edge passes immediately. A collision
     * may be explicitly opened only when both the edge and any fully-blocked endpoint tile are
     * present in the v1.11.2 override BitSet.
     */
    private static boolean canTraverseCardinalEdge(int[][] collision, BitSet overrides, int x, int y, int nextX, int nextY)
    {
        if (!isWithinGridBounds(collision, x, y) || !isWithinGridBounds(collision, nextX, nextY))
        {
            return false;
        }

        final int movementMask;
        if (nextX > x)
        {
            movementMask = BLOCK_EAST;
        }
        else if (nextX < x)
        {
            movementMask = BLOCK_WEST;
        }
        else if (nextY > y)
        {
            movementMask = BLOCK_NORTH;
        }
        else if (nextY < y)
        {
            movementMask = BLOCK_SOUTH;
        }
        else
        {
            return false;
        }

        if ((collision[nextX][nextY] & movementMask) == 0)
        {
            return true;
        }

        final int width = collision.length;
        final int height = collision[0].length;
        final int edge = cardinalEdgeIndex(width, height, x, y, nextX, nextY);

        return edge >= 0
            && overrides.get(edge)
            && isStandableWithOverrides(collision, overrides, x, y)
            && isStandableWithOverrides(collision, overrides, nextX, nextY);
    }

    /**
     * A tile is normally usable when it lacks the full-movement mask. The second BitSet namespace
     * explicitly opens an otherwise fully-blocked tile; this behavior is required for special
     * local scene transitions handled by Efficient Walker.
     */
    private static boolean isStandableWithOverrides(int[][] collision, BitSet overrides, int x, int y)
    {
        final int flags = collision[x][y];
        if ((flags & BLOCK_MOVEMENT_FULL) == 0)
        {
            return true;
        }

        return overrides.get(tileOverrideIndex(collision.length, collision[0].length, x, y));
    }

    private static void emitValidCardinalEdge(int width, int height, int x, int y, int nextX, int nextY, IntConsumer consumer)
    {
        final int edge = cardinalEdgeIndex(width, height, x, y, nextX, nextY);
        if (edge >= 0)
        {
            consumer.accept(edge);
        }
    }

    private static boolean isWithinGridBounds(int[][] collision, int x, int y)
    {
        return collision != null
            && collision.length > 0
            && collision[0] != null
            && x >= 0
            && x < collision.length
            && y >= 0
            && y < collision[0].length;
    }
}
