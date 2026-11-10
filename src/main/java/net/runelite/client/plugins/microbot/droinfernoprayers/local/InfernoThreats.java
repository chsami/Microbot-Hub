/* Copyright (c) 2026, DRO (droplugins). SPDX-License-Identifier: BSD-2-Clause */
package net.runelite.client.plugins.microbot.droinfernoprayers.local;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.client.plugins.microbot.droinfernoprayers.local.InfernoCollision.Tile;
import net.runelite.client.plugins.microbot.droinfernoprayers.local.InfernoSnapshot.Npc;
import net.runelite.client.plugins.microbot.droinfernoprayers.local.InfernoSnapshot.Role;

/** Per-spawn clocks; only observed animations establish a known attack phase. */
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
        /** Seen appear mid-session, so {@link #spawn} is its real spawn tick. */
        boolean fresh;
        long lastAttack = -1, digTick = -1, lastDamage = -1;
        /** Blob: first tick it saw the player again after its attack phase lapsed; -1 otherwise. */
        long sightSince = -1;
        /** Last tick the NPC's tile changed (its spawn tick until then). */
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
    private long lastTick = -1;
    long lastPlayerShot = -1;
    int lastPlayerWeapon = -1, lastPlayerTarget = -1, lastPlayerStyle = -1;
    private int playerX, playerY;
    /** The player's tile changed since the previous observed tick. */
    private boolean playerStepped;

    void reset()
    {
        tracks.clear(); lifecycleBoundaries.clear(); history.clear(); lastTick = -1; lastPlayerShot = -1;
        lastPlayerWeapon = lastPlayerTarget = lastPlayerStyle = -1;
    }

    void observe(InfernoSnapshot f, boolean gap)
    {
        if (f.tick == lastTick) return;
        if (gap) reset();
        lifecycleBoundaries.clear();
        int weapon = f.weapon() == null ? -1 : f.weapon().id;
        int style = InfernoSnapshot.integer(f.player, "attackStyle", 1);
        if (lastTick >= 0 && (weapon != lastPlayerWeapon || f.interacting != lastPlayerTarget
            || style != lastPlayerStyle || f.x != playerX || f.y != playerY)) lastPlayerShot = -1;
        lastPlayerWeapon = weapon; lastPlayerTarget = f.interacting; lastPlayerStyle = style;
        playerStepped = lastTick >= 0 && (f.x != playerX || f.y != playerY);
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
                // A snapshot can retain an earlier animation after a capture gap. Only
                // a fresh event below is allowed to anchor this new track's phase.
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
        history.put(f.tick, Guard.active(f)); history.keySet().removeIf(tick -> tick < f.tick - 16);
        lastTick = f.tick;
    }

    private void animation(Npc n, Track t, int animation, long tick)
    {
        if (n.role == Role.MELEE && animation == 7600) { t.digTick = tick; t.lastAttack = -1; }
        // Successful and both death recordings show the next mage launch eight
        // ticks after this ability. It is a receipt, not a damage attack.
        if (n.role == Role.MAGE && animation == 7611) { t.resurrectionTick = tick; return; }
        Guard guard = attack(n.role, animation);
        // A blob can melee between ranged/magic launches (recorded at 158/161).
        // That separate attack must not postpone its six-tick scan-driven phase.
        if (n.role == Role.BLOB && guard == Guard.MELEE) return;
        // Jad's melee receipt must not erase a ranged/magic shot whose observed
        // impact boundary has not resolved yet. Keep latest-receipt fields intact.
        if (n.role == Role.JAD && (guard == Guard.MAGIC || guard == Guard.RANGE))
        { t.lastJadShot = tick; t.jadShot = guard; }
        if (guard != Guard.NONE || n.role == Role.ZUK && animation == 7566)
        { t.lastAttack = tick; t.attack = guard; t.resurrectionTick = -1; }
    }

    Track track(Npc n) { return tracks.get(n.index); }
    /**
     * Event order is part of actor identity. A snapshot may contain a new NPC at
     * an index whose old attack/damage/graphic occurred earlier in this same tick.
     * Only receipts after the last observed spawn/despawn belong to the live actor.
     */
    boolean currentEvent(Npc n, int eventOrdinal)
    {
        Track current = track(n);
        return current != null && current.id == n.id && eventOrdinal >= 0
            && eventOrdinal > lifecycleBoundaries.getOrDefault(n.index, -1);
    }
    boolean damaged(Npc n) { Track t = track(n); return t != null && t.lastDamage >= t.spawn; }
    boolean digging(Npc n, long tick) { Track t = track(n); return t != null && t.digTick >= 0 && tick - t.digTick <= 3; }

    static boolean playerAttack(int animation)
    {
        return animation == 426 || animation == 5061 || animation == 13144 || animation == 13145
            || animation == 1167 || animation == 1978
            || animation == 1979 || animation == 10092 || animation == 7618 || animation == 4230
            || animation == 7552;
    }

    /** Continuous blowpipe attacks can retain one animation for many cycles. */
    boolean playerShotNext(InfernoSnapshot f, int period)
    {
        if (period <= 0 || lastPlayerShot < 0) return true;
        long boundary = f.tick + 1 - lastPlayerShot;
        return boundary > 0 && boundary % period == 0;
    }

    static Guard attack(Role role, int animation)
    {
        // Animation identity belongs to an actor role, not merely to a numeric
        // value found somewhere in this snapshot's event stream.
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
            // Yt-HurKot 2636 is its ready pose; only 2637 anchors its attack clock.
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
            if (next <= now) return true; // Missing the expected receipt invalidates the phase.
            return boundary >= next && (boundary - next) % period(n.role, wave, false) == 0;
        }
        // Recorded blobs most often fire first exactly four ticks after spawning.
        if (t != null && t.lastAttack < 0 && n.role == Role.BLOB && t.fresh && now < t.spawn + 4)
            return boundary == t.spawn + 4;
        // A blob without a live phase that has just (re)gained sight of the player scans at once and launches
        // four ticks later (success run 184 -> lost sight -> seen at 190 -> Magic 190, Range 193, launch 194).
        if (t != null && n.role == Role.BLOB && t.sightSince >= 0 && (t.lastAttack < 0 || t.lastAttack < t.sightSince))
        {
            long next = t.sightSince + 4;
            if (now > next) return true;
            return boundary >= next && (boundary - next) % period(n.role, wave, false) == 0;
        }
        if (t == null || t.lastAttack < 0) return true;
        int period = period(n.role, wave, false);
        // A missed receipt keeps the last observed phase: the recordings keep guarding the old rhythm (wave 7:
        // a blob that stopped attacking after 422 is still answered on 428/434, never every tick).
        if (t.lastAttack + LAPSED * period <= now) return true; // Long silent: phase is unknown.
        return boundary >= t.lastAttack + period && (boundary - t.lastAttack) % period == 0;
    }

    private static boolean jadShotDue(Track track, long boundary)
    { return track != null && track.lastJadShot >= 0 && boundary == track.lastJadShot + 3; }

    private static boolean jadMeleeDue(Track track, long boundary)
    { return track != null && track.lastAttack >= 0 && track.attack == Guard.MELEE && boundary == track.lastAttack + 1; }

    private boolean exposed(InfernoSnapshot f, Npc n, Tile tile, InfernoCollision grid)
    {
        if (!n.enemy() || n.role == Role.ZUK || n.role == Role.ZUK_HEALER) return false;
        // Normal nibblers damage pillars. Only a live player-target receipt makes
        // one a player threat (for example after the last pillar is lost).
        if (n.role == Role.NIBBLER && !n.targetsPlayer()) return false;
        Guard fixed = fixed(n.role);
        if (fixed == Guard.MELEE)
            return grid.meleeNext(n, tile);
        // The successful run's attack footprints cap at 4 tiles for bats and 15
        // for blobs/rangers/mages. Preserve one NPC step of uncertainty; distant
        // sight alone is not an attack. Jad's delayed observed attack is not range-filtered.
        int range = n.role == Role.BAT ? 4
            : n.role == Role.BLOB || n.role == Role.RANGER || n.role == Role.MAGE ? 15 : Integer.MAX_VALUE;
        // A bat that has stopped moving gets no step margin (success run wave 14: a parked bat five tiles away is
        // never prayed against); one still closing in does (opening wire 29), and so does a parked bat the player
        // has just stepped away from: it follows and fires on the same tick (live 2026-11-03 waves 2 and 6).
        boolean here = tile.x == f.x && tile.y == f.y;
        boolean parkedBat = n.role == Role.BAT && track(n) != null && f.tick - track(n).lastStep > 1
            && here && !playerStepped;
        if (range != Integer.MAX_VALUE && n.distance(tile.x, tile.y) > range + (parkedBat ? 0 : 1)) return false;
        // This footprint is observed now. It can establish a trap at the observed
        // player tile, but not at a future route tile after unmodelled NPC movement.
        // Keep the conservative one-step range margin for those projected tiles.
        if (n.role == Role.BAT && here && !grid.batCanReachNext(n, tile)) return false;
        if (grid.npcSight(n, tile)) return true;
        // A bat still closing in can step into sight and fire on that tick.
        return n.role == Role.BAT && !parkedBat && grid.batSightNext(n, tile);
    }

    private int cost(InfernoSnapshot f, List<Npc> enemies, InfernoCollision grid, Tile tile,
        long boundary, Guard selected, Guard scanned, int wave)
    {
        int cost = 0;
        for (Npc n : enemies)
        {
            boolean exposed = exposed(f, n, tile, grid)
                // A blob scans before a planned move can land: keep its scan if it sees the current tile.
                || n.role == Role.BLOB && exposed(f, n, new Tile(f.x, f.y), grid);
            boolean attackDue = due(n, f.tick, boundary, wave);
            Track observed = track(n);
            boolean pendingJadShot = n.role == Role.JAD && jadShotDue(observed, boundary);
            // Cover cannot cancel an observed Jad projectile before its impact.
            // due() limits this exception to the existing resolution boundary.
            if (!exposed && !pendingJadShot) continue;
            // These hybrid enemies can melee diagonally with line of sight. The
            // cardinal movement check remains appropriate for pure melee NPCs.
            boolean hybridAdjacent = (n.role == Role.BLOB || n.role == Role.RANGER || n.role == Role.MAGE)
                && n.distance(tile.x, tile.y) <= 1;
            boolean independentMelee = exposed && (n.role == Role.BLOB && hybridAdjacent
                || n.role == Role.JAD && (grid.melee(n, tile) || jadMeleeDue(observed, boundary)));
            // Adjacency remains dangerous between the blob's ranged boundaries,
            // including while a requested escape has not yet moved the player.
            // Jad's fast melee also cannot wait for a ranged warning/impact.
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
                // The evening recording contains melee, ranged and magic attacks
                // from the same adjacent blob. Melee protection cannot certify
                // that tile safe from its scan-driven ranged/magic attack.
                if (style == Guard.NONE) { cost += maximum(n.role); continue; }
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

    /**
     * Cost of one tick with a protection up that no attack needs. Above two toggles, so a one-tick gap between
     * attacks is flicked off and back on like the recordings (success run 5913-5917: Magic on/off/on/off/on
     * against a mage and blob) instead of being held.
     */
    static final int HELD = 3;
    /** Attack cycles without a receipt before an NPC's phase counts as unknown. */
    static final int LAPSED = 1;

    /** Six boundaries preserve the blob's three-tick observation history. */
    Choice choose(InfernoSnapshot f, List<Npc> enemies, InfernoCollision grid, List<Tile> path, int wave)
    {
        Tile standing = new Tile(f.x, f.y);
        for (Npc n : enemies)
        {
            Track t = track(n);
            if (t == null || n.role != Role.BLOB) continue;
            boolean lapsed = t.lastAttack < 0 ? !(t.fresh && f.tick < t.spawn + 4) : t.lastAttack + period(n.role, wave, false) <= f.tick;
            if (!exposed(f, n, standing, grid) || !lapsed && t.lastAttack >= t.sightSince) t.sightSince = -1;
            else if (lapsed && t.sightSince < 0) t.sightSince = f.tick;
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
            // Attacks lock on the position before this boundary's player movement.
            // Successful wire5557->5558 keeps Range while running out of the bat's
            // reach; using path[offset*speed] would remove it one tick too soon.
            Tile tile = path.get(Math.min(path.size() - 1, (offset - 1) * speed));
            // Either blob scan is safe when answered. The recordings keep a Range already up for the scan,
            // otherwise raise Magic so it fires Range. A planning preference: reported risk is unchanged.
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
                        + (blobScan && state >>> 4 == Guard.RANGE.ordinal() ? 10 : 0);
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
        // Establish an actual scan history before emerging from cover toward a
        // blob. Otherwise an all-NONE history could indefinitely veto that route.
        // A blob hidden behind a pillar never scans; the recordings keep prayers off until it sees the player
        // (success run wave 13: 14 ticks of Magic held against a blocked blob, none in the recording).
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

    private static void ArraysFill(int[] array, int value) { java.util.Arrays.fill(array, value); }
}
