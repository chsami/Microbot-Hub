package net.runelite.client.plugins.microbot.droinfernoprayers.local;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.client.plugins.microbot.droinfernoprayers.local.InfernoCollision.Tile;
import net.runelite.client.plugins.microbot.droinfernoprayers.local.InfernoSnapshot.Npc;
import net.runelite.client.plugins.microbot.droinfernoprayers.local.InfernoSnapshot.Role;

final class InfernoThreats
{
    enum Guard
    {
        NONE(""), MAGIC("PROTECT_FROM_MAGIC"), RANGE("PROTECT_FROM_MISSILES"), MELEE("PROTECT_FROM_MELEE");
        final String prayer;
        Guard(String prayer) { this.prayer = prayer; }
        static Guard active(InfernoSnapshot f)
        {
            Guard result = NONE;
            for (Guard g : values()) if (g != NONE && f.prayers.contains(g.prayer))
            {
                if (result != NONE) return NONE;
                result = g;
            }
            return result;
        }
    }

    static final class Track
    {
        final int id;
        final long spawn;

        boolean fresh;
        long lastAttack = -1, digTick = -1, lastDamage = -1;

        long sightSince = -1;

        boolean sightStep;
        long firstLaunch() { return sightSince + (sightStep ? 3 : 4); }

        long lastStep;
        long lastJadShot = -1, resurrectionTick = -1;
        int lastAnimation = -1, x, y, hpRatio;
        Guard attack = Guard.NONE;
        Guard jadShot = Guard.NONE;
        Track(Npc n, long tick) { id = n.id; spawn = tick; lastStep = tick; x = n.x; y = n.y; hpRatio = n.hpRatio; }
    }

    private final Map<Integer, Track> tracks = new HashMap<>();
    private final Map<Integer, Integer> lifecycleBoundaries = new HashMap<>();
    private final Map<Long, Guard> history = new HashMap<>();
    private final Map<Long, Integer> inFlight = new HashMap<>();
    private long lastTick = -1;
    long lastPlayerShot = -1;
    int lastPlayerWeapon = -1, lastPlayerTarget = -1, lastPlayerStyle = -1;
    private int playerX, playerY;
    private int previousX, previousY;

    private boolean playerStepped;
    private long readNextTick = -1;
    private boolean readNext;
    static final int BLOB_RANGE_PROJECTILE = 1378, BLOB_MAGIC_PROJECTILE = 1380;
    static final int DIG = 3, DIG_ATTACK = 9;

    void reset()
    {
        tracks.clear(); lifecycleBoundaries.clear(); history.clear(); inFlight.clear(); lastTick = -1; lastPlayerShot = -1;
        lastPlayerWeapon = lastPlayerTarget = lastPlayerStyle = -1;
    }

    void observe(InfernoSnapshot f, boolean gap)
    {
        if (f.tick == lastTick) return;
        if (gap) reset();
        boolean first = lastTick < 0;
        lifecycleBoundaries.clear();
        int weapon = f.weapon() == null ? -1 : f.weapon().id;
        int style = InfernoSnapshot.integer(f.player, "attackStyle", 1);
        if (lastTick >= 0 && (weapon != lastPlayerWeapon || f.interacting != lastPlayerTarget
            || style != lastPlayerStyle || f.x != playerX || f.y != playerY)) lastPlayerShot = -1;
        lastPlayerWeapon = weapon; lastPlayerTarget = f.interacting; lastPlayerStyle = style;
        playerStepped = lastTick >= 0 && (f.x != playerX || f.y != playerY);
        previousX = lastTick >= 0 ? playerX : f.x; previousY = lastTick >= 0 ? playerY : f.y;
        playerX = f.x; playerY = f.y;
        Set<Integer> spawned = new HashSet<>();
        for (int ordinal = 0; ordinal < f.events.size(); ordinal++)
        {
            JsonElement element = f.events.get(ordinal);
            if (!element.isJsonObject()) continue;
            JsonObject event = element.getAsJsonObject();
            String type = InfernoSnapshot.string(event, "e", "");
            if ("spawn".equals(type) || "despawn".equals(type))
            {
                int index = InfernoSnapshot.integer(event, "index", -1);
                lifecycleBoundaries.put(index, ordinal);
                tracks.remove(index); if ("spawn".equals(type)) spawned.add(index);
            }
        }
        Set<Integer> alive = new HashSet<>();
        for (Npc n : f.npcs) if (n.alive())
        {
            alive.add(n.index);
            Track t = tracks.get(n.index);
            if (t == null || t.id != n.id || spawned.contains(n.index))
            {
                t = new Track(n, f.tick); t.lastAnimation = n.animation; tracks.put(n.index, t);
                t.fresh = lastTick >= 0 && !gap;
            }
            if (n.role == Role.MELEE && Math.max(Math.abs(t.x - n.x), Math.abs(t.y - n.y)) > 1) t.lastAttack = -1;
            if (t.x != n.x || t.y != n.y) t.lastStep = f.tick;
            if (n.animation != t.lastAnimation) animation(n, t, n.animation, f.tick);
            if (n.hpRatio >= 0 && t.hpRatio > n.hpRatio) t.lastDamage = f.tick;
            t.lastAnimation = n.animation; t.x = n.x; t.y = n.y; t.hpRatio = n.hpRatio;
        }
        tracks.keySet().removeIf(index -> !alive.contains(index));
        for (int ordinal = 0; ordinal < f.events.size(); ordinal++)
        {
            JsonElement element = f.events.get(ordinal);
            if (!element.isJsonObject()) continue;
            JsonObject event = element.getAsJsonObject();
            JsonElement actor = event.get("actor");
            String type = InfernoSnapshot.string(event, "e", "");
            if (actor == null || !actor.isJsonPrimitive()) continue;
            if ("player".equals(actor.getAsString()))
            {
                int animation = InfernoSnapshot.integer(event, "anim", -1);
                if ("anim".equals(type) && playerAttack(animation))
                {
                    lastPlayerShot = f.tick;
                }
                continue;
            }
            int index;
            try { index = actor.getAsInt(); } catch (RuntimeException ignored) { continue; }
            Npc npc = f.npc(index); Track track = tracks.get(index);
            if (npc == null || track == null || !currentEvent(npc, ordinal)) continue;
            if ("anim".equals(type)) animation(npc, track, InfernoSnapshot.integer(event, "anim", -1), f.tick);
            if ("hitsplat".equals(type) && InfernoSnapshot.integer(event, "amount", 0) > 0) track.lastDamage = f.tick;
        }
        observeLaunches(f, first);
        history.put(f.tick, Guard.active(f)); history.keySet().removeIf(tick -> tick < f.tick - 16);
        lastTick = f.tick;
    }

    private void observeLaunches(InfernoSnapshot f, boolean first)
    {
        Map<Long, Integer> previous = new HashMap<>(inFlight);
        inFlight.clear();
        List<long[]> current = new ArrayList<>();
        for (JsonElement element : f.projectiles)
        {
            if (!element.isJsonObject()) continue;
            JsonObject p = element.getAsJsonObject();
            if (!"player".equals(InfernoSnapshot.string(p, "target", ""))) continue;
            int id = InfernoSnapshot.integer(p, "id", -1), left = InfernoSnapshot.integer(p, "ticksLeft", -1);
            if (id < 0 || left < 0) continue;
            inFlight.merge(flight(id, left), 1, Integer::sum);
            current.add(new long[]{id, left, 0});
        }
        for (long[] p : current) if (consume(previous, flight((int) p[0], (int) p[1] + 1))) p[2] = 1;
        for (long[] p : current) if (p[2] == 0 && consume(previous, flight((int) p[0], (int) p[1]))) p[2] = 1;
        if (first) return;
        List<Track> animated = new ArrayList<>();
        for (Npc n : f.npcs)
        {
            Track t = n.alive() && n.role == Role.BLOB ? track(n) : null;
            if (t != null && t.lastAttack == f.tick) animated.add(t);
        }
        for (long[] p : current)
        {
            Guard style = p[0] == BLOB_RANGE_PROJECTILE ? Guard.RANGE : p[0] == BLOB_MAGIC_PROJECTILE ? Guard.MAGIC : Guard.NONE;
            if (p[2] != 0 || style == Guard.NONE || p[1] < 2) continue;
            Track explained = animated.stream().filter(t -> t.attack == style).findFirst().orElse(null);
            if (explained != null) { animated.remove(explained); continue; }
            Track launcher = blobLauncher(f);
            if (launcher != null) { launcher.lastAttack = f.tick; launcher.attack = style; }
        }
    }

    private static long flight(int id, int ticksLeft) { return (long) id << 16 | ticksLeft & 0xFFFF; }

    private static boolean consume(Map<Long, Integer> counts, long key)
    {
        Integer count = counts.get(key);
        if (count == null || count <= 0) return false;
        counts.put(key, count - 1);
        return true;
    }

    private Track blobLauncher(InfernoSnapshot f)
    {
        Track only = null, due = null;
        int candidates = 0, phased = 0;
        for (Npc n : f.npcs)
        {
            if (!n.alive() || n.role != Role.BLOB) continue;
            Track t = track(n);
            if (t == null || t.lastAttack >= f.tick - 2) continue;
            candidates++; only = t;
            if (t.lastAttack >= 0 && (f.tick - t.lastAttack) % period(Role.BLOB, 0, false) == 0) { phased++; due = t; }
        }
        return candidates == 1 ? only : phased == 1 ? due : null;
    }

    private void animation(Npc n, Track t, int animation, long tick)
    {
        if (n.role == Role.MELEE && animation == 7600) { t.digTick = tick; t.lastAttack = -1; }

        if (n.role == Role.MAGE && animation == 7611) { t.resurrectionTick = tick; return; }
        Guard guard = attack(n.role, animation);

        if (n.role == Role.BLOB && guard == Guard.MELEE) return;

        if (n.role == Role.JAD && (guard == Guard.MAGIC || guard == Guard.RANGE))
        { t.lastJadShot = tick; t.jadShot = guard; }
        if (guard != Guard.NONE || n.role == Role.ZUK && animation == 7566)
        { t.lastAttack = tick; t.attack = guard; t.resurrectionTick = -1; }
    }

    Track track(Npc n) { return tracks.get(n.index); }

    boolean currentEvent(Npc n, int eventOrdinal)
    {
        Track current = track(n);
        return current != null && current.id == n.id && eventOrdinal >= 0
            && eventOrdinal > lifecycleBoundaries.getOrDefault(n.index, -1);
    }

    static boolean playerAttack(int animation)
    {
        return animation == 426 || animation == 5061 || animation == 13144 || animation == 13145
            || animation == 1167 || animation == 1978
            || animation == 1979 || animation == 10092 || animation == 7618 || animation == 4230
            || animation == 7552;
    }


    static Guard attack(Role role, int animation)
    {
        switch (role)
        {
            case NIBBLER: return animation == 7574 ? Guard.MELEE : Guard.NONE;
            case BAT: return animation == 7578 ? Guard.RANGE : Guard.NONE;
            case BLOB:
                return animation == 7581 ? Guard.MAGIC : animation == 7583 ? Guard.RANGE
                    : animation == 7582 ? Guard.MELEE : Guard.NONE;
            case MINI_MAGE: return animation == 7581 ? Guard.MAGIC : Guard.NONE;
            case MINI_RANGE: return animation == 7583 ? Guard.RANGE : Guard.NONE;
            case MINI_MELEE: return animation == 7582 ? Guard.MELEE : Guard.NONE;
            case MELEE: return animation == 7597 ? Guard.MELEE : Guard.NONE;
            case RANGER: return animation == 7605 ? Guard.RANGE : animation == 7604 ? Guard.MELEE : Guard.NONE;
            case MAGE: return animation == 7610 ? Guard.MAGIC : animation == 7612 ? Guard.MELEE : Guard.NONE;
            case JAD:
                return animation == 7592 ? Guard.MAGIC : animation == 7593 ? Guard.RANGE
                    : animation == 7590 ? Guard.MELEE : Guard.NONE;

            case JAD_HEALER: return animation == 2637 ? Guard.MELEE : Guard.NONE;
            default: return Guard.NONE;
        }
    }

    static Guard fixed(Role role)
    {
        switch (role)
        {
            case BAT: case MINI_RANGE: case RANGER: return Guard.RANGE;
            case MINI_MAGE: case MAGE: return Guard.MAGIC;
            case NIBBLER: case MINI_MELEE: case MELEE: case JAD_HEALER: return Guard.MELEE;
            default: return Guard.NONE;
        }
    }

    static int period(Role role, int wave, boolean zukFinal)
    {
        if (role == Role.BAT) return 3;
        if (role == Role.BLOB) return 6;
        if (role == Role.JAD) return wave == 68 ? 9 : 8;
        if (role == Role.ZUK) return zukFinal ? 7 : 10;
        return 4;
    }

    static int maximum(Role role)
    {
        switch (role)
        {
            case NIBBLER: return 4;
            case BAT: return 19;
            case BLOB: return 29;
            case MINI_MAGE: case MINI_RANGE: case MINI_MELEE: return 18;
            case MELEE: return 49;
            case RANGER: return 46;
            case MAGE: return 70;
            case JAD: return 250;
            case JAD_HEALER: return 18;
            default: return 0;
        }
    }

    boolean due(Npc n, long now, long boundary, int wave)
    {
        Track t = track(n);
        if (n.role == Role.JAD)
            return jadShotDue(t, boundary) || jadMeleeDue(t, boundary);
        if (t != null && n.role == Role.MAGE && t.resurrectionTick >= 0)
        {
            long next = t.resurrectionTick + 8;
            if (next <= now) return true;
            return boundary >= next && (boundary - next) % period(n.role, wave, false) == 0;
        }

        if (t != null && n.role == Role.MELEE && t.digTick >= 0 && t.lastAttack < t.digTick
            && boundary < t.digTick + DIG + DIG_ATTACK) return false;

        if (t != null && t.lastAttack < 0 && n.role == Role.BLOB && t.fresh && now < t.spawn + 4)
            return boundary == t.spawn + 4;

        if (t != null && n.role == Role.BLOB && t.sightSince >= 0 && (t.lastAttack < 0 || t.lastAttack < t.sightSince))
        {
            long next = t.firstLaunch();
            if (boundary == next) return true;
            if (t.lastAttack < 0)
            {
                if (now > next) return true;
                return boundary >= next && (boundary - next) % period(n.role, wave, false) == 0;
            }
        }
        if (t == null || t.lastAttack < 0) return true;
        int period = period(n.role, wave, false);

        if (t.lastAttack + LAPSED * period <= now) return true;
        return boundary >= t.lastAttack + period && (boundary - t.lastAttack) % period == 0;
    }

    private boolean phased(Npc n, long now, int wave)
    {
        Track t = track(n);
        if (t == null) return false;
        if (n.role == Role.BLOB && t.lastAttack < 0 && t.fresh && now < t.spawn + 4) return true;
        if (n.role == Role.BLOB && t.sightSince >= 0 && (t.lastAttack < 0 || t.lastAttack < t.sightSince))
            return now <= t.firstLaunch();
        return t.lastAttack >= 0 && t.lastAttack + LAPSED * period(n.role, wave, false) > now;
    }

    private static boolean jadShotDue(Track track, long boundary)
    { return track != null && track.lastJadShot >= 0 && boundary == track.lastJadShot + 3; }

    private static boolean jadMeleeDue(Track track, long boundary)
    { return track != null && track.lastAttack >= 0 && track.attack == Guard.MELEE && boundary == track.lastAttack + 1; }

    private boolean exposed(InfernoSnapshot f, Npc n, Tile tile, InfernoCollision grid)
    {
        return exposed(f, n, tile, grid, 1);
    }

    private boolean exposed(InfernoSnapshot f, Npc n, Tile tile, InfernoCollision grid, int offset)
    {
        if (!n.enemy() || n.role == Role.ZUK || n.role == Role.ZUK_HEALER) return false;

        if (n.role == Role.NIBBLER && !n.targetsPlayer()) return false;
        Guard fixed = fixed(n.role);
        if (n.role == Role.MELEE && n.distance(tile.x, tile.y) == 0) return false;
        if (n.role == Role.MELEE && offset > 1) return grid.meleeSteps(n, tile) <= offset;
        if (fixed == Guard.MELEE)
            return grid.meleeNext(n, tile);

        int range = n.role == Role.BAT ? 4
            : n.role == Role.BLOB || n.role == Role.RANGER || n.role == Role.MAGE ? 15 : Integer.MAX_VALUE;

        boolean here = tile.x == f.x && tile.y == f.y;
        boolean parkedBat = n.role == Role.BAT && track(n) != null && f.tick - track(n).lastStep > 1
            && here && !playerStepped;
        if (range != Integer.MAX_VALUE && n.distance(tile.x, tile.y) > range + (parkedBat ? 0 : 1)) return false;

        if (n.role == Role.BAT && here && !grid.batCanReachNext(n, tile)) return false;
        if (grid.npcSight(n, tile)) return true;

        return n.role == Role.BAT && !parkedBat && grid.batSightNext(n, tile);
    }

    private int cost(InfernoSnapshot f, List<Npc> enemies, InfernoCollision grid, Tile tile,
        long boundary, Guard selected, Guard scanned, int wave)
    {
        int cost = 0;
        int offset = (int) Math.max(1, boundary - f.tick);
        for (Npc n : enemies)
        {
            boolean exposed = exposed(f, n, tile, grid, offset)

                || n.role == Role.BLOB && exposed(f, n, new Tile(f.x, f.y), grid);
            boolean attackDue = due(n, f.tick, boundary, wave);
            Track observed = track(n);
            boolean pendingJadShot = n.role == Role.JAD && jadShotDue(observed, boundary);

            boolean pendingBlob = n.role == Role.BLOB && observed != null && observed.sightSince >= 0
                && observed.lastAttack < observed.sightSince && boundary == observed.firstLaunch();
            if (!exposed && !pendingJadShot && !pendingBlob) continue;

            boolean hybridAdjacent = (n.role == Role.BLOB || n.role == Role.RANGER || n.role == Role.MAGE)
                && n.distance(tile.x, tile.y) <= 1;
            boolean independentMelee = exposed && (n.role == Role.BLOB && hybridAdjacent
                || n.role == Role.JAD && (grid.melee(n, tile) || jadMeleeDue(observed, boundary)));

            if (independentMelee && selected != Guard.MELEE)
                cost += maximum(n.role);
            if (!attackDue) continue;
            Guard style = fixed(n.role);
            if (n.role == Role.JAD)
            {
                style = observed == null ? Guard.NONE : pendingJadShot ? observed.jadShot : observed.attack;
            }
            if (n.role == Role.BLOB)
            {
                style = scanned == Guard.MAGIC ? Guard.RANGE : scanned == Guard.RANGE ? Guard.MAGIC : Guard.NONE;

                if (style == Guard.NONE)
                {
                    boolean guess = (selected == Guard.RANGE || selected == Guard.MAGIC) && phased(n, f.tick, wave);
                    cost += guess ? maximum(n.role) / 2 : maximum(n.role);
                    continue;
                }
            }
            if (n.role == Role.RANGER || n.role == Role.MAGE)
            {
                if (hybridAdjacent && selected != Guard.MELEE) cost += maximum(n.role);
            }
            if (selected != style && style != Guard.NONE && !(independentMelee && style == Guard.MELEE))
                cost += maximum(n.role);
        }
        return cost;
    }

    static final class Choice
    {
        final Guard guard;
        final int immediate, total;
        Choice(Guard guard, int immediate, int total) { this.guard = guard; this.immediate = immediate; this.total = total; }
    }

    static final int HELD = 3;

    static final int LAPSED = 1;

    Choice choose(InfernoSnapshot f, List<Npc> enemies, InfernoCollision grid, List<Tile> path, int wave)
    {
        Tile standing = new Tile(f.x, f.y);
        if (readNextTick != f.tick)
        {
            readNextTick = f.tick; readNext = false;
            for (Npc n : enemies)
            {
                Track t = track(n);
                if (t == null || n.role != Role.BLOB || !n.alive()) continue;
                if (t.sightSince >= 0 && (t.lastAttack >= t.sightSince || f.tick > t.firstLaunch())) t.sightSince = -1;
                Tile held = new Tile(previousX, previousY);
                int reach = n.distance(held.x, held.y);
                if (t.sightSince == f.tick - 1 && !(reach >= 1 && reach <= 15 && grid.npcSight(n, held))) t.sightSince = -1;
                boolean lapsed = t.lastAttack < 0 ? !(t.fresh && f.tick < t.spawn + 4) : f.tick + 1 >= t.lastAttack + 3;
                if (t.sightSince < 0 && lapsed && readsNext(f, n, standing, grid))
                { t.sightSince = f.tick; t.sightStep = false; readNext = true; }
            }
        }
        final int states = 64, infinity = 1_000_000;
        int[] costs = new int[states], firstCost = new int[states], firstGuard = new int[states];
        ArraysFill(costs, infinity);
        int historyKey = history.getOrDefault(f.tick - 2, Guard.NONE).ordinal() * 16
            + history.getOrDefault(f.tick - 1, Guard.NONE).ordinal() * 4
            + history.getOrDefault(f.tick, Guard.NONE).ordinal();
        costs[historyKey] = 0;
        int speed = f.runOn && f.runEnergy > 0 ? 2 : 1;
        for (int offset = 1; offset <= 6; offset++)
        {
            int[] next = new int[states], nextFirst = new int[states], nextGuard = new int[states];
            ArraysFill(next, infinity);

            Tile tile = path.get(Math.min(path.size() - 1, (offset - 1) * speed));

            boolean blobScan = offset > 1 && blobScanDue(f, enemies, grid, tile, f.tick + offset, wave);
            int[][] rawCosts = new int[4][4];
            for (Guard scanned : Guard.values()) for (Guard selected : Guard.values())
                rawCosts[scanned.ordinal()][selected.ordinal()] = cost(f, enemies, grid, tile,
                    f.tick + offset, selected, scanned, wave);
            for (int state = 0; state < states; state++) if (costs[state] < infinity)
                for (Guard guard : Guard.values())
                {
                    int raw = rawCosts[state >>> 4][guard.ordinal()];
                    int immediate = offset == 1 ? raw : firstCost[state];
                    int nextState = ((state & 15) << 2) | guard.ordinal();
                    int weighted = costs[state] + raw * (offset == 1 ? 1000 : 10)
                        + (guard == Guard.NONE ? 0 : HELD) + ((state & 3) == guard.ordinal() ? 0 : 1)
                        + (blobScan && state >>> 4 == Guard.RANGE.ordinal() ? 10 : 0)
                        + (offset == 1 && readNext ? guard == Guard.MAGIC ? 0 : guard == Guard.RANGE ? 10 : READ_BARE : 0);
                    if (weighted < next[nextState])
                    {
                        next[nextState] = weighted; nextFirst[nextState] = immediate;
                        nextGuard[nextState] = offset == 1 ? guard.ordinal() : firstGuard[state];
                    }
                }
            costs = next; firstCost = nextFirst; firstGuard = nextGuard;
        }
        int best = 0;
        for (int i = 1; i < states; i++) if (costs[i] < costs[best]) best = i;
        Guard guard = Guard.values()[firstGuard[best]];
        boolean unknownJad = enemies.stream().anyMatch(n -> n.role == Role.JAD
            && (track(n) == null || track(n).lastAttack < 0));
        if (guard == Guard.NONE && unknownJad) guard = Guard.active(f);

        if (guard == Guard.NONE && enemies.stream().anyMatch(n -> n.role == Role.BLOB
            && (track(n) == null || track(n).lastAttack < 0)
            && path.stream().limit(3).anyMatch(t -> exposed(f, n, t, grid)))) guard = Guard.MAGIC;
        return new Choice(guard, firstCost[best], costs[best]);
    }

    private boolean blobScanDue(InfernoSnapshot f, List<Npc> enemies, InfernoCollision grid, Tile tile, long boundary, int wave)
    {
        for (Npc n : enemies)
            if (n.role == Role.BLOB && due(n, f.tick, boundary, wave)
                && (exposed(f, n, tile, grid) || exposed(f, n, new Tile(f.x, f.y), grid))) return true;
        return false;
    }

    static final int READ_BARE = 14_000;

    private boolean readsNext(InfernoSnapshot f, Npc n, Tile standing, InfernoCollision grid)
    {
        if (!grid.available()) return false;
        int d = n.distance(standing.x, standing.y);
        if (d >= 1 && d <= 15 && grid.npcSight(n, standing)) return true;
        List<Npc> others = new ArrayList<>();
        for (Npc other : f.npcs) if (other.index != n.index && other.alive()) others.add(other);
        int[] step = blobStep(grid, n, standing, others);
        int x = step[0], y = step[1];
        if (x == n.x && y == n.y) return false;
        int gap = Math.max(Math.max(x - f.x, f.x - x - n.size + 1), Math.max(y - f.y, f.y - y - n.size + 1));
        return gap >= 1 && gap <= 15 && grid.npcSight(x, y, n.size, standing);
    }

    private static int[] blobStep(InfernoCollision grid, Npc u, Tile target, List<Npc> others)
    {
        int dx = target.x < u.x ? -1 : target.x > u.x + u.size - 1 ? 1 : 0;
        int dy = target.y < u.y ? -1 : target.y > u.y + u.size - 1 ? 1 : 0;
        if (dx == 0 && dy == 0) return new int[]{u.x, u.y};
        dx = Integer.signum(target.x - u.x); dy = Integer.signum(target.y - u.y);
        if (dx != 0 && dy != 0)
        {
            int ax = Math.max(0, Math.max(u.x - target.x, target.x - (u.x + u.size - 1)));
            int ay = Math.max(0, Math.max(u.y - target.y, target.y - (u.y + u.size - 1)));
            if (ax == 1 && ay == 1) return stepFree(grid, u, dx, 0, target, others) ? new int[]{u.x + dx, u.y} : new int[]{u.x, u.y};
            if (stepFree(grid, u, dx, dy, target, others)) return new int[]{u.x + dx, u.y + dy};
        }
        if (dx != 0 && stepFree(grid, u, dx, 0, target, others)) return new int[]{u.x + dx, u.y};
        if (dy != 0 && stepFree(grid, u, 0, dy, target, others)) return new int[]{u.x, u.y + dy};
        return new int[]{u.x, u.y};
    }

    private static boolean stepFree(InfernoCollision grid, Npc u, int dx, int dy, Tile player, List<Npc> others)
    {
        for (int x = u.x; x < u.x + u.size; x++)
            for (int y = u.y; y < u.y + u.size; y++)
            {
                int nx = x + dx, ny = y + dy;
                if (covers(u, nx, ny)) continue;
                if (!grid.open(nx, ny) || nx == player.x && ny == player.y || taken(others, nx, ny)) return false;
            }
        if (dx != 0 && dy != 0 && u.size > 1) return true;
        for (int x = u.x; x < u.x + u.size; x++)
            for (int y = u.y; y < u.y + u.size; y++)
            {
                if (dx != 0 && dy != 0)
                {
                    if (!covers(u, x + dx, y) && !grid.cardinal(x, y, x + dx, y)) return false;
                    if (!covers(u, x, y + dy) && !grid.cardinal(x, y, x, y + dy)) return false;
                    if (taken(others, x + dx, y) || taken(others, x, y + dy)) return false;
                }
                else if (!covers(u, x + dx, y + dy) && !grid.cardinal(x, y, x + dx, y + dy)) return false;
            }
        return true;
    }

    private static boolean covers(Npc n, int x, int y)
    {
        return x >= n.x && x < n.x + n.size && y >= n.y && y < n.y + n.size;
    }

    private static boolean taken(List<Npc> others, int x, int y)
    {
        for (Npc o : others) if (covers(o, x, y)) return true;
        return false;
    }

    private static void ArraysFill(int[] array, int value) { java.util.Arrays.fill(array, value); }
}
