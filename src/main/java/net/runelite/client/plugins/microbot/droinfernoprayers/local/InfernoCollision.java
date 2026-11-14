package net.runelite.client.plugins.microbot.droinfernoprayers.local;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.client.plugins.microbot.droinfernoprayers.local.InfernoSnapshot.Npc;
import net.runelite.client.plugins.microbot.droinfernoprayers.local.InfernoSnapshot.Role;

final class InfernoCollision
{
    private static final int UNLOADED = 0x1000000;
    private static final int BLOCKED = 0x100 | 0x40000 | 0x200000 | UNLOADED;
    private static final int MISSILE_BLOCKED = 0x20000 | UNLOADED;
    private final int x1, y1, width, height;
    private final int[] flags;
    private final boolean[] occupied;
    private final boolean valid;
    private final InfernoSnapshot snapshot;
    private final Map<Long, Boolean> nextMelee = new HashMap<>();
    private final Map<Long, Boolean> nextBatRange = new HashMap<>();
    private final Map<Long, Integer> approach = new HashMap<>();

    InfernoCollision(InfernoSnapshot snapshot)
    {
        this.snapshot = snapshot;
        JsonObject collision = InfernoSnapshot.object(snapshot.raw, "collision");
        x1 = InfernoSnapshot.integer(collision, "x1", 2240);
        y1 = InfernoSnapshot.integer(collision, "y1", 5312);
        int w = InfernoSnapshot.integer(collision, "x2", x1 - 1) - x1 + 1;
        int h = InfernoSnapshot.integer(collision, "y2", y1 - 1) - y1 + 1;
        JsonArray data = InfernoSnapshot.array(collision, "flags");
        valid = w > 0 && h > 0 && w <= 104 && h <= 104 && data.size() == w * h;
        width = valid ? w : 1; height = valid ? h : 1;
        flags = new int[width * height]; occupied = new boolean[flags.length];
        if (!valid) { Arrays.fill(flags, UNLOADED); return; }
        for (int i = 0; i < flags.length; i++)
        {
            JsonElement e = data.get(i);
            try { flags[i] = e.getAsInt(); }
            catch (RuntimeException invalid) { flags[i] = UNLOADED; }
        }
        for (Npc npc : snapshot.npcs) if (npc.alive())
            for (int x = npc.x; x < npc.x + npc.size; x++)
                for (int y = npc.y; y < npc.y + npc.size; y++)
                    if (inside(x, y)) occupied[index(x, y)] = true;
    }

    boolean available() { return valid && open(snapshot.x, snapshot.y); }
    boolean inside(int x, int y) { return x >= x1 && y >= y1 && x < x1 + width && y < y1 + height; }
    int flag(int x, int y) { return inside(x, y) ? flags[index(x, y)] : UNLOADED; }
    boolean open(int x, int y) { return valid && inside(x, y) && (flag(x, y) & BLOCKED) == 0; }
    boolean free(int x, int y) { return open(x, y) && !occupied[index(x, y)]; }
    private int index(int x, int y) { return (y - y1) * width + x - x1; }
    private Tile tile(int index) { return new Tile(x1 + index % width, y1 + index / width); }

    boolean cardinal(int x, int y, int nx, int ny)
    {
        int dx = nx - x, dy = ny - y;
        if (Math.abs(dx) + Math.abs(dy) != 1 || !open(nx, ny)) return false;
        int out = dx > 0 ? 8 : dx < 0 ? 128 : dy > 0 ? 2 : 32;
        int in = dx > 0 ? 128 : dx < 0 ? 8 : dy > 0 ? 32 : 2;
        return (flag(x, y) & out) == 0 && (flag(nx, ny) & in) == 0;
    }

    boolean step(Tile from, Tile to)
    {
        int dx = to.x - from.x, dy = to.y - from.y;
        if (Math.abs(dx) > 1 || Math.abs(dy) > 1 || !free(to.x, to.y)) return false;
        if (dx == 0 && dy == 0) return true;
        if (dx == 0 || dy == 0) return cardinal(from.x, from.y, to.x, to.y);
        int outgoing = dx < 0 ? (dy > 0 ? 1 : 64) : (dy > 0 ? 4 : 16);
        int incoming = dx < 0 ? (dy > 0 ? 16 : 4) : (dy > 0 ? 64 : 1);
        return (flag(from.x, from.y) & outgoing) == 0 && (flag(to.x, to.y) & incoming) == 0
            && free(from.x + dx, from.y) && free(from.x, from.y + dy)
            && cardinal(from.x, from.y, from.x + dx, from.y)
            && cardinal(from.x, from.y, from.x, from.y + dy)
            && cardinal(from.x + dx, from.y, to.x, to.y)
            && cardinal(from.x, from.y + dy, to.x, to.y);
    }

    boolean ray(int x, int y, int tx, int ty)
    {
        return ray(x, y, tx, ty, false);
    }

    private boolean ray(int x, int y, int tx, int ty, boolean unknownMayExpose)
    {
        if (!valid || !inside(x, y) || !inside(tx, ty)) return false;
        if (!unknownMayExpose && ((flag(x, y) | flag(tx, ty)) & UNLOADED) != 0) return false;
        int dx = tx - x, dy = ty - y;
        if (dx == 0 && dy == 0) return true;
        int xMask = MISSILE_BLOCKED | ((dx < 0 ? 8 : 128) << 9);
        int yMask = MISSILE_BLOCKED | ((dy < 0 ? 2 : 32) << 9);
        if (Math.abs(dx) > Math.abs(dy))
            return rayAxis(x - x1, y - y1, tx - x1, dy, Math.abs(dx), xMask, yMask, true, unknownMayExpose);
        return rayAxis(y - y1, x - x1, ty - y1, dx, Math.abs(dy), yMask, xMask, false, unknownMayExpose);
    }

    private boolean rayAxis(int major, int minor, int target, int minorDelta, int majorDistance,
                            int majorMask, int minorMask, boolean horizontal, boolean unknownMayExpose)
    {
        int minorFixed = (minor << 16) + 0x8000 - (minorDelta < 0 ? 1 : 0);
        int slope = (minorDelta << 16) / majorDistance;
        int direction = Integer.signum(target - major);
        while (major != target)
        {
            major += direction;
            int currentMinor = minorFixed >>> 16;
            if ((rayFlag(major, currentMinor, horizontal, unknownMayExpose) & majorMask) != 0) return false;
            minorFixed += slope;
            int nextMinor = minorFixed >>> 16;
            if (nextMinor != currentMinor && (rayFlag(major, nextMinor, horizontal, unknownMayExpose) & minorMask) != 0) return false;
        }
        return true;
    }

    private int rayFlag(int major, int minor, boolean horizontal, boolean unknownMayExpose)
    {
        int value = horizontal ? flag(x1 + major, y1 + minor) : flag(x1 + minor, y1 + major);

        return unknownMayExpose && (value & UNLOADED) != 0 ? 0 : value;
    }


    boolean npcSight(Npc npc, Tile player)
    {
        if (!valid || !inside(player.x, player.y) || !inside(npc.x, npc.y)
            || !inside(npc.x + npc.size - 1, npc.y + npc.size - 1)) return true;

        if ((flag(player.x, player.y) & MISSILE_BLOCKED & ~UNLOADED) != 0) return false;

        int x = Math.max(npc.x, Math.min(player.x, npc.x + npc.size - 1));
        int y = Math.max(npc.y, Math.min(player.y, npc.y + npc.size - 1));

        return ray(player.x, player.y, x, y, true);
    }

    boolean npcSight(int nx, int ny, int size, Tile player)
    {
        if (!valid || !inside(player.x, player.y) || !inside(nx, ny) || !inside(nx + size - 1, ny + size - 1)) return true;
        if ((flag(player.x, player.y) & MISSILE_BLOCKED & ~UNLOADED) != 0) return false;
        int x = Math.max(nx, Math.min(player.x, nx + size - 1));
        int y = Math.max(ny, Math.min(player.y, ny + size - 1));
        return ray(player.x, player.y, x, y, true);
    }


    boolean melee(Npc npc, Tile tile)
    {
        if (npc.distance(tile.x, tile.y) == 0) return true;
        if (npc.distance(tile.x, tile.y) > 1) return false;
        for (int x = npc.x; x < npc.x + npc.size; x++)
            for (int y = npc.y; y < npc.y + npc.size; y++)
                if (Math.abs(x - tile.x) + Math.abs(y - tile.y) == 1 && cardinal(x, y, tile.x, tile.y)) return true;
        return false;
    }

    boolean meleeNext(Npc npc, Tile tile)
    {
        if (melee(npc, tile)) return true;
        if (npc.distance(tile.x, tile.y) > 2) return false;
        if (!valid) return true;
        long key = ((long) npc.index << 32) | ((long) tile.x << 16) | tile.y;
        Boolean cached = nextMelee.get(key);
        if (cached != null) return cached;
        if (!loadedMeleeStepArea(npc, tile))
        {
            nextMelee.put(key, true);
            return true;
        }
        int ax = Math.max(0, Math.max(npc.x - tile.x, tile.x - (npc.x + npc.size - 1)));
        int ay = Math.max(0, Math.max(npc.y - tile.y, tile.y - (npc.y + npc.size - 1)));
        int dx = Integer.signum(tile.x - npc.x), dy = Integer.signum(tile.y - npc.y);
        Tile next = null;
        if (ax + ay != 1)
        {
            if (ax == 1 && ay == 1)
            {
                if (npcStep(npc, dx, 0, tile)) next = new Tile(npc.x + dx, npc.y);
            }
            else if (npcStep(npc, dx, dy, tile)) next = new Tile(npc.x + dx, npc.y + dy);
            else if (dx != 0 && npcStep(npc, dx, 0, tile)) next = new Tile(npc.x + dx, npc.y);
            else if (dy != 0 && Math.max(Math.abs(tile.x - npc.x), Math.abs(tile.y - npc.y)) > 1
                && npcStep(npc, 0, dy, tile)) next = new Tile(npc.x, npc.y + dy);
        }
        boolean result = false;
        if (next != null)
            for (int x = next.x; x < next.x + npc.size; x++)
                for (int y = next.y; y < next.y + npc.size; y++)
                    if (Math.abs(x - tile.x) + Math.abs(y - tile.y) == 1 && cardinal(x, y, tile.x, tile.y)) result = true;
        nextMelee.put(key, result);
        return result;
    }

    private boolean loadedMeleeStepArea(Npc npc, Tile target)
    {
        return loadedStepArea(npc.x, npc.y, npc.size, target);
    }

    private boolean loadedStepArea(int nx, int ny, int size, Tile target)
    {
        if ((flag(target.x, target.y) & UNLOADED) != 0) return false;

        for (int x = nx - 1; x <= nx + size; x++)
            for (int y = ny - 1; y <= ny + size; y++)
                if ((flag(x, y) & UNLOADED) != 0) return false;
        return true;
    }

    static final int MELEE_HORIZON = 6;

    int meleeSteps(Npc npc, Tile tile)
    {
        if (melee(npc, tile)) return 0;
        if (npc.distance(tile.x, tile.y) > MELEE_HORIZON + 1) return MELEE_HORIZON + 1;
        if (!valid) return 1;
        long key = ((long) npc.index << 32) | ((long) tile.x << 16) | tile.y;
        Integer cached = approach.get(key);
        if (cached != null) return cached;
        int x = npc.x, y = npc.y, result = MELEE_HORIZON + 1;
        for (int step = 1; step <= MELEE_HORIZON; step++)
        {
            if (!loadedStepArea(x, y, npc.size, tile)) { result = step; break; }
            Tile next = chaseStep(npc.index, x, y, npc.size, tile);
            if (next == null) break;
            x = next.x; y = next.y;
            if (contact(x, y, npc.size, tile)) { result = step; break; }
        }
        approach.put(key, result);
        return result;
    }

    private Tile chaseStep(int index, int x, int y, int size, Tile tile)
    {
        int ax = Math.max(0, Math.max(x - tile.x, tile.x - (x + size - 1)));
        int ay = Math.max(0, Math.max(y - tile.y, tile.y - (y + size - 1)));
        if (ax + ay <= 1) return null;
        int dx = Integer.signum(tile.x - x), dy = Integer.signum(tile.y - y);
        if (ax == 1 && ay == 1) return npcStep(index, x, y, size, dx, 0, tile, false) ? new Tile(x + dx, y) : null;
        if (npcStep(index, x, y, size, dx, dy, tile, false)) return new Tile(x + dx, y + dy);
        if (dx != 0 && npcStep(index, x, y, size, dx, 0, tile, false)) return new Tile(x + dx, y);
        if (dy != 0 && Math.max(Math.abs(tile.x - x), Math.abs(tile.y - y)) > 1 && npcStep(index, x, y, size, 0, dy, tile, false))
            return new Tile(x, y + dy);
        return null;
    }

    private boolean contact(int nx, int ny, int size, Tile tile)
    {
        for (int x = nx; x < nx + size; x++)
            for (int y = ny; y < ny + size; y++)
                if (Math.abs(x - tile.x) + Math.abs(y - tile.y) == 1 && cardinal(x, y, tile.x, tile.y)) return true;
        return false;
    }

    boolean batCanReachNext(Npc npc, Tile target)
    {
        int distance = npc.distance(target.x, target.y);
        if (distance <= 4) return true;
        if (distance > 5) return false;
        if (!valid) return true;
        long key = ((long) npc.index << 32) | ((long) target.x << 16) | target.y;
        Boolean cached = nextBatRange.get(key);
        if (cached != null) return cached;
        if (!loadedMeleeStepArea(npc, target)) { nextBatRange.put(key, true); return true; }
        int dx = Integer.signum(target.x - npc.x), dy = Integer.signum(target.y - npc.y);
        int nx = npc.x, ny = npc.y;

        if (npcStep(npc, dx, dy, target, true)) { nx += dx; ny += dy; }
        else if (dx != 0 && npcStep(npc, dx, 0, target, true)) nx += dx;
        else if (dy != 0 && npcStep(npc, 0, dy, target, true)) ny += dy;
        int nextDistance = Math.max(Math.max(0, Math.max(nx - target.x, target.x - (nx + npc.size - 1))),
            Math.max(0, Math.max(ny - target.y, target.y - (ny + npc.size - 1))));
        boolean result = nextDistance <= 4;
        nextBatRange.put(key, result);
        return result;
    }

    boolean batSightNext(Npc npc, Tile player)
    {
        if (!valid || npc.distance(player.x, player.y) > 5) return false;
        int dx = Integer.signum(player.x - npc.x), dy = Integer.signum(player.y - npc.y);
        int nx = npc.x, ny = npc.y;
        if (npcStep(npc, dx, dy, player, true)) { nx += dx; ny += dy; }
        else if (dx != 0 && npcStep(npc, dx, 0, player, true)) nx += dx;
        else if (dy != 0 && npcStep(npc, 0, dy, player, true)) ny += dy;
        else return false;
        if (!inside(player.x, player.y) || !inside(nx, ny) || !inside(nx + npc.size - 1, ny + npc.size - 1)) return false;
        if ((flag(player.x, player.y) & MISSILE_BLOCKED & ~UNLOADED) != 0) return false;
        int x = Math.max(nx, Math.min(player.x, nx + npc.size - 1));
        int y = Math.max(ny, Math.min(player.y, ny + npc.size - 1));
        return ray(player.x, player.y, x, y, true);
    }

    private boolean npcStep(Npc npc, int dx, int dy, Tile player)
    {
        return npcStep(npc, dx, dy, player, false);
    }

    private boolean npcStep(Npc npc, int dx, int dy, Tile player, boolean stationaryOnly)
    {
        return npcStep(npc.index, npc.x, npc.y, npc.size, dx, dy, player, stationaryOnly);
    }

    private boolean npcStep(int index, int x0, int y0, int size, int dx, int dy, Tile player, boolean stationaryOnly)
    {
        if (dx == 0 && dy == 0) return false;
        for (int x = x0; x < x0 + size; x++)
            for (int y = y0; y < y0 + size; y++)
            {
                int nx = x + dx, ny = y + dy;
                if (!npcFree(index, nx, ny, player, stationaryOnly)) return false;
                if (dx == 0 || dy == 0)
                {
                    if (!cardinal(x, y, nx, ny)) return false;
                }
                else
                {
                    int out = dx < 0 ? (dy > 0 ? 1 : 64) : (dy > 0 ? 4 : 16);
                    int in = dx < 0 ? (dy > 0 ? 16 : 4) : (dy > 0 ? 64 : 1);
                    if ((flag(x, y) & out) != 0 || (flag(nx, ny) & in) != 0
                        || !npcFree(index, nx, y, player, stationaryOnly) || !npcFree(index, x, ny, player, stationaryOnly)
                        || !cardinal(x, y, nx, y) || !cardinal(x, y, x, ny)
                        || !cardinal(nx, y, nx, ny) || !cardinal(x, ny, nx, ny)) return false;
                }
            }
        return true;
    }

    private boolean npcFree(int self, int x, int y, Tile player, boolean stationaryOnly)
    {
        if (!open(x, y) || x == player.x && y == player.y) return false;
        for (Npc other : snapshot.npcs)
            if (other.index != self && other.alive()
                && (!stationaryOnly || other.role == Role.PILLAR)
                && x >= other.x && x < other.x + other.size && y >= other.y && y < other.y + other.size) return false;
        return true;
    }

    Paths paths(Tile start, int maxDistance) { return new Paths(start, maxDistance); }

    final class Paths
    {
        private final int[] previous = new int[flags.length], distances = new int[flags.length];
        final List<Tile> reachable = new ArrayList<>();
        final Tile start;
        Paths(Tile start, int maxDistance)
        {
            this.start = start;
            Arrays.fill(previous, -1); Arrays.fill(distances, -1);
            if (!open(start.x, start.y)) return;
            int[] queue = new int[flags.length]; int head = 0, tail = 0, origin = index(start.x, start.y);
            previous[origin] = origin; distances[origin] = 0; queue[tail++] = origin;
            while (head < tail)
            {
                int at = queue[head++]; Tile from = tile(at); reachable.add(from);
                if (distances[at] >= maxDistance) continue;
                for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++)
                {
                    if (dx == 0 && dy == 0) continue;
                    Tile to = new Tile(from.x + dx, from.y + dy);
                    if (!inside(to.x, to.y) || previous[index(to.x, to.y)] >= 0 || !step(from, to)) continue;
                    int next = index(to.x, to.y); previous[next] = at; distances[next] = distances[at] + 1;
                    queue[tail++] = next;
                }
            }
        }
        int distance(Tile target) { return inside(target.x, target.y) ? distances[index(target.x, target.y)] : -1; }
        List<Tile> to(Tile target)
        {
            if (distance(target) < 0) return Collections.emptyList();
            List<Tile> result = new ArrayList<>();
            int at = index(target.x, target.y);
            for (int steps = 0; steps < previous.length; steps++)
            {
                result.add(tile(at));
                if (previous[at] == at) { Collections.reverse(result); return result; }
                at = previous[at];
                if (at < 0) return Collections.emptyList();
            }
            return Collections.emptyList();
        }
    }

    static final class Tile
    {
        final int x, y;
        Tile(int x, int y) { this.x = x; this.y = y; }
        int distance(Tile other) { return Math.max(Math.abs(x - other.x), Math.abs(y - other.y)); }
        @Override public boolean equals(Object obj) { return obj instanceof Tile && x == ((Tile) obj).x && y == ((Tile) obj).y; }
        @Override public int hashCode() { return 31 * x + y; }
        @Override public String toString() { return x + "," + y; }
    }
}
