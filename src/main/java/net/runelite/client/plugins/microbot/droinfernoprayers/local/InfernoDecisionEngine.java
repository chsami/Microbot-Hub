/* Copyright (c) 2026, DRO (droplugins). SPDX-License-Identifier: BSD-2-Clause */
package net.runelite.client.plugins.microbot.droinfernoprayers.local;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.runelite.client.plugins.microbot.droinfernoprayers.local.InfernoCollision.Tile;
import net.runelite.client.plugins.microbot.droinfernoprayers.local.InfernoLoadout.Mode;
import net.runelite.client.plugins.microbot.droinfernoprayers.local.InfernoSnapshot.Item;
import net.runelite.client.plugins.microbot.droinfernoprayers.local.InfernoSnapshot.Npc;
import net.runelite.client.plugins.microbot.droinfernoprayers.local.InfernoSnapshot.Role;
import net.runelite.client.plugins.microbot.droinfernoprayers.local.InfernoThreats.Guard;

/**
 * Local replacement for the missing Inferno decision service. It consumes the
 * original complete wire state and returns the original command vocabulary.
 * There are no game API calls, socket clients, sleeps, or account credentials.
 * The native command runner remains the sole input owner.
 *
 * Recording preferences are evidence-guided tie breakers; per-spawn clocks,
 * live collision, inventory receipts, and launch-time shield checks remain
 * responsible for adapting those preferences to the current run.
 */
public final class InfernoDecisionEngine
{
    private static final Pattern WAVE = Pattern.compile("(?i)\\bwave\\s*:?\\s*(\\d{1,2})\\b");
    private static final Tile HOME = new Tile(2273, 5353);
    private static final Tile RESERVE = new Tile(2275, 5358);
    private static final Tile OPENING_READY = new Tile(2274, 5355);
    private static final List<String> OFFENSE = Arrays.asList("SHARP_EYE", "HAWK_EYE", "EAGLE_EYE", "DEADEYE", "RIGOUR",
        "MYSTIC_WILL", "MYSTIC_LORE", "MYSTIC_MIGHT", "MYSTIC_VIGOUR", "AUGURY");
    private final InfernoThreats threats = new InfernoThreats();
    private final InfernoSupplies supplies = new InfernoSupplies();
    private final InfernoLoadout loadout = new InfernoLoadout();
    private final InfernoInventorySpace inventorySpace = new InfernoInventorySpace();
    private final InfernoOffense offenseClock = new InfernoOffense();
    private final Map<Integer, Long> tagged = new HashMap<>();
    /** Index -> observed spawn identity; a request is not a successful aggro receipt. */
    private final Map<Integer, Long> tagRequested = new HashMap<>();
    private final Map<Integer, Integer> confirmedBloodLevels = new HashMap<>();
    private final Map<Integer, Long> nibblerSpawns = new HashMap<>();
    private final Set<Integer> iceHitNibblers = new HashSet<>();
    private int nibblerWave = -1;
    private boolean nibblerIceObserved;
    private RecordedDecisionPolicy recorded;
    private InfernoZukReference zukReference;
    private long lastTick = -1, lastTime, lastMove = -1000, lastAttackRequest = -1000, lastSpec = -1000;
    private long lastEquip = -1000, lastArmIce = -1000, lastCamera = -1000, lastZukAttack = -1;
    // Every successful recording fights top-down at zoom 100; the server never had to set it.
    static final int CAMERA_PITCH = 383, CAMERA_ZOOM = 100, CAMERA_PITCH_LIMIT = 370, CAMERA_ZOOM_LIMIT = 128;
    private static final int FRAME_ATTEMPTS = 3;
    private long lastFrame = -1000;
    private int frameAttempts;
    private int wave, vitals = InfernoSupplies.DEFAULT_VITALS_PERCENT;
    private int rangingFrom = InfernoSupplies.DEFAULT_RANGING_FROM_WAVE, healersAtOnce = 1;
    private boolean rangeOnly, sawZuk, sawJadAtZuk, zukFinal, completed;
    private int shieldX = Integer.MIN_VALUE, shieldDirection, shieldDwell, shieldIndex = -1;
    private int activeZukHealer = -1, lastTarget = -1, lastTargetId = -1;
    private long lastTargetSpawn = -1;
    private int knownZukIndex = -1;
    private double knownZukHealth = 1.0;
    private boolean initialReserveDone;
    private boolean openingStarted, openingClosed, entryObserved, openingViaReserve;
    private long lastLoot = -1000, emptySince = -1;
    private Tile moveTarget;
    /** Zuk: the tile clicked at the last launch, kept until reached or no longer safe at the next one. */
    private Tile zukDest;
    static final int ZUK_EARLY_OFFENSE_PRAYER = 25;
    private boolean zukEarlyDecided, zukEarlyOffense;
    private long zukDestTick = -1;
    /** First tick the player stood with no shot at its target; -1 while attacking or walking. */
    private long stalledSince = -1;
    private Mode lastMode;
    private int[] movementHint;
    private Integer targetHint;
    private volatile String status = "Local Inferno ready";
    private volatile String lastResult = "";
    private volatile int observedWave;

    /** Construction is deliberately free of file IO; configure runs on the arena worker. */
    public InfernoDecisionEngine() { }

    public void configure(Map<String, Object> options)
    {
        Map<String, Object> values = options == null ? Collections.emptyMap() : options;
        int hpSetting = number(values.get("vitalsPercent"), InfernoSupplies.DEFAULT_VITALS_PERCENT);
        vitals = hpSetting <= 0 ? InfernoSupplies.DEFAULT_VITALS_PERCENT : clamp(hpSetting, 1, 99);
        int rangeSetting = number(values.get("rangingFromWave"), InfernoSupplies.DEFAULT_RANGING_FROM_WAVE);
        // These are explicit local defaults. Invalid legacy zero values cannot
        // reintroduce a hidden server-default mode.
        rangingFrom = rangeSetting <= 0 ? InfernoSupplies.DEFAULT_RANGING_FROM_WAVE : clamp(rangeSetting, 1, 69);
        healersAtOnce = clamp(number(values.get("zukHealersAtOnce"), 1), 1, 4);
        rangeOnly = Boolean.TRUE.equals(values.get("rangeOnly"));
        prayersOnly = Boolean.TRUE.equals(values.get("prayersOnly"));
        manualMovement = Boolean.TRUE.equals(values.get("manualMovement"));
        supplies.configure(vitals, rangingFrom);
        if (recorded == null) recorded = RecordedDecisionPolicy.loadDefault();
        if (zukReference == null) zukReference = InfernoZukReference.loadDefault();
    }

    public void reset()
    {
        threats.reset(); supplies.reset(); loadout.reset(); inventorySpace.reset(); offenseClock.reset(); tagged.clear(); tagRequested.clear();
        confirmedBloodLevels.clear();
        resetNibblerIce();
        lastTick = -1; lastTime = 0; wave = observedWave = 0;
        lastMove = lastAttackRequest = lastSpec = lastEquip = lastArmIce = lastCamera = lastFrame = -1000;
        frameAttempts = 0;
        lastZukAttack = -1; sawZuk = sawJadAtZuk = zukFinal = completed = false;
        shieldX = Integer.MIN_VALUE; shieldDirection = shieldDwell = 0; shieldIndex = -1;
        activeZukHealer = lastTarget = lastTargetId = -1; lastTargetSpawn = -1; moveTarget = null; lastMode = null;
        zukDest = null; zukDestTick = -1; zukEarlyDecided = zukEarlyOffense = false;
        stalledSince = -1;
        knownZukIndex = -1; knownZukHealth = 1.0;
        initialReserveDone = false; lastLoot = -1000;
        openingStarted = openingClosed = entryObserved = openingViaReserve = false; emptySince = -1;
        movementHint = null; targetHint = null; lastResult = "";
        if (recorded != null) recorded.reset();
        status = "Local Inferno ready";
    }

    /** Optional evidence preferences, consumed once. They never bypass live target/route checks. */
    public void setHints(int[] movement, Integer npcIndex)
    {
        movementHint = movement == null ? null : movement.clone(); targetHint = npcIndex;
    }

    public String status() { return status; }
    public int observedWave() { return observedWave; }

    public void onResult(JsonObject result)
    {
        if (result == null) return;
        // A runner receipt is not evidence that the game accepted a drink, hit or gear swap.
        lastResult = InfernoSnapshot.string(result, "outcome", InfernoSnapshot.string(result, "status", ""));
    }

    public List<JsonObject> decide(JsonObject snapshot)
    {
        List<JsonObject> plan = plan(snapshot);
        return recordedOrder(prayersOnly ? prayersOnly(snapshot, plan) : plan);
    }

    /** "Prayers only": the player fights; the engine only switches prayers. */
    private boolean prayersOnly;
    /**
     * "Manual movement": the player walks; the engine plays everything else from wherever the player stands. No route,
     * shield step, reserve or opening walk is planned, so attacks are not held back for one. An attack on a target out
     * of range or sight would path the player, so it is sent only with a shot from the player's tile.
     */
    private boolean manualMovement;

    /**
     * Keeps this tick's protection prayer commands. Offensive prayer is flicked for each of the player's own
     * shots at the NPC they are attacking, on the same clock and targets as the full engine (Augury/Mystic for a
     * Blood/Ice weapon, Rigour/Eagle Eye otherwise).
     */
    private List<JsonObject> prayersOnly(JsonObject snapshot, List<JsonObject> plan)
    {
        List<JsonObject> kept = new ArrayList<>();
        for (JsonObject c : plan)
        {
            String op = InfernoSnapshot.string(c, "op", "");
            if (op.startsWith("prayer.") && InfernoSnapshot.string(c.getAsJsonObject("args"), "prayer", "").startsWith("PROTECT_")) kept.add(c);
        }
        InfernoSnapshot f = new InfernoSnapshot(snapshot);
        if (!f.playable()) return kept;
        offense(f, offensive(f, false), kept);
        return kept;
    }

    /**
     * Every recording sends a tick's protection prayer first, then offensive prayers, then everything else
     * (success run: protect then offense 2,246 times, the reverse once; a potion never precedes a prayer).
     */
    static List<JsonObject> recordedOrder(List<JsonObject> commands)
    {
        if (commands.size() < 2) return commands;
        List<JsonObject> ordered = new ArrayList<>(commands);
        ordered.sort(java.util.Comparator.comparingInt(command -> {
            String op = command.has("op") ? command.get("op").getAsString() : "";
            if (!op.startsWith("prayer.")) return 2;
            JsonObject args = command.getAsJsonObject("args");
            String prayer = args != null && args.has("prayer") ? args.get("prayer").getAsString() : "";
            return prayer.startsWith("PROTECT_") ? 0 : 1;
        }));
        return ordered;
    }

    private List<JsonObject> plan(JsonObject snapshot)
    {
        if (snapshot == null) return Collections.emptyList();
        InfernoSnapshot f = new InfernoSnapshot(snapshot);
        if (f.tick == lastTick) return Collections.emptyList();
        if (!f.playable())
        {
            boolean wasComplete = completed;
            reset(); completed = wasComplete;
            status = wasComplete ? "Inferno complete" : "Waiting for a living player inside the Inferno";
            return Collections.emptyList();
        }
        if (lastTick > f.tick) reset();
        boolean gap = lastTick >= 0 && (f.tick > lastTick + 1 || lastTime > 0 && f.time > lastTime + 1800);
        if (gap)
        {
            tagRequested.clear(); moveTarget = null; zukDest = null; lastMove = -1000; lastAttackRequest = -1000; stalledSince = -1;
            shieldDirection = shieldDwell = 0; shieldX = Integer.MIN_VALUE;
            lastZukAttack = -1;
        }
        if (recorded == null) configure(Collections.emptyMap());
        if (movementHint == null && targetHint == null)
        {
            RecordedDecisionPolicy.Hint hint = recorded.suggest(snapshot);
            if (hint != null) setHints(hint.movement, hint.target);
        }
        try
        {
            threats.observe(f, gap); supplies.observe(f); loadout.observe(f); offenseClock.observe(f); observeAutocast(f);
            supplies.bloodRequirement(bloodRequirement(f));
            inventorySpace.observe(f, reservePresent(f));
            observeWave(f); observeTags(f); observeShield(f);
            observeNibblerIce(f, gap);
            boolean hostiles = f.npcs.stream().anyMatch(Npc::enemy);
            if (f.animation == 4367 || !openingStarted && f.x == 2270 && f.y == 5344) entryObserved = true;
            if (!openingStarted && !entryObserved && !f.raw.has("wave"))
            { openingClosed = true; }
            if (wave >= 1 || hostiles) openingClosed = true;
            if (hostiles) emptySince = -1;
            else if (emptySince < 0) emptySince = f.tick;
            Npc observedZuk = first(f, Role.ZUK);
            if (observedZuk != null)
            {
                if (observedZuk.index != knownZukIndex) { knownZukIndex = observedZuk.index; knownZukHealth = 1.0; }
                if (observedZuk.hpRatio >= 0 && observedZuk.hpScale > 0)
                    knownZukHealth = (double) observedZuk.hpRatio / observedZuk.hpScale;
                InfernoThreats.Track observedClock = threats.track(observedZuk);
                if (observedClock != null && observedClock.lastAttack >= 0)
                {
                    // A local session can attach after the healers have died. Fresh launch
                    // observations still establish the faster final phase without replaying time.
                    if (lastZukAttack >= 0 && observedClock.lastAttack - lastZukAttack == 7) zukFinal = true;
                    lastZukAttack = observedClock.lastAttack;
                }
            }
            lastTick = f.tick; lastTime = f.time;
            if (!InfernoSnapshot.bool(snapshot, "inputAllowed", true))
            { status = "Local Inferno observing while input is paused"; return Collections.emptyList(); }
            Npc zuk = first(f, Role.ZUK);
            if (sawZuk && f.npcs.stream().anyMatch(n -> n.role == Role.ZUK && (n.dead || n.animation == 7562)))
            {
                completed = true; status = "Inferno complete: observed Zuk death";
                List<JsonObject> off = new ArrayList<>(); protection(f, Guard.NONE, off); offense(f, "", off); return off;
            }
            if (completed) return Collections.emptyList();
            if (zuk != null) { sawZuk = true; wave = observedWave = 69; }
            List<Npc> enemies = f.npcs.stream().filter(Npc::enemy).collect(Collectors.toList());
            InfernoCollision grid = new InfernoCollision(f);
            if (zuk != null) return zuk(f, grid, enemies, zuk);
            if (sawZuk)
            {
                status = "Zuk temporarily unobserved; waiting for a fresh scene";
                return defensiveOnly(f, grid, enemies);
            }
            // A Jal-Ak that has just died splits into three: the recordings hold their gear and wait for them.
            if (enemies.isEmpty() && f.npcs.stream().anyMatch(n -> n.role == Role.BLOB && n.dead))
            {
                status = "Wave " + wave + ": waiting for the Jal-Ak split";
                List<JsonObject> hold = new ArrayList<>(); protection(f, Guard.NONE, hold); offense(f, "", hold); return hold;
            }
            if (enemies.isEmpty()) return betweenWaves(f, grid);
            List<JsonObject> out = waves(f, grid, enemies);
            // One offensive decision per tick, whichever branch ran: a walk ends the attack, otherwise the
            // shot that fires next tick gets its prayer (the player can still be shooting while the engine
            // waits on gear, sight or a target switch).
            boolean walking = out.stream().anyMatch(c -> "movement.tile".equals(InfernoSnapshot.string(c, "op", "")));
            boolean requesting = out.stream().anyMatch(c -> "npc.attack".equals(InfernoSnapshot.string(c, "op", ""))
                || "magic.armIce".equals(InfernoSnapshot.string(c, "op", "")) && c.getAsJsonObject("args").has("index"));
            offense(f, walking ? "" : offensive(f, requesting), out);
            return out;
        }
        finally { movementHint = null; targetHint = null; }
    }

    private void observeWave(InfernoSnapshot f)
    {
        int explicit = InfernoSnapshot.integer(f.raw, "wave", 0);
        if (explicit >= 1 && explicit <= 69) wave = explicit;
        for (JsonElement element : f.events) if (element.isJsonObject())
        {
            JsonObject event = element.getAsJsonObject();
            if (!"chat".equals(InfernoSnapshot.string(event, "e", ""))) continue;
            String message = InfernoSnapshot.string(event, "text", "");
            if (sawZuk && message.contains("TzKal-Zuk has become enraged and is fighting for his life")) zukFinal = true;
            Matcher matcher = WAVE.matcher(message);
            if (matcher.find())
            {
                int value = Integer.parseInt(matcher.group(1));
                if (value >= 1 && value <= 69 && value != wave)
                { wave = value; tagged.clear(); tagRequested.clear(); }
            }
        }
        long jads = f.npcs.stream().filter(n -> n.alive() && n.role == Role.JAD).count();
        if (first(f, Role.ZUK) != null) { wave = 69; sawZuk = true; }
        else if (jads > 1) wave = 68;
        else if (jads == 1 && wave < 67) wave = 67;
        if (sawZuk && jads > 0) sawJadAtZuk = true;
        if (sawZuk && first(f, Role.ZUK_HEALER) != null) zukFinal = true;
        observedWave = wave;
    }

    private void observeTags(InfernoSnapshot f)
    {
        Set<Integer> live = new HashSet<>();
        for (Npc n : f.npcs) if (n.alive())
        {
            live.add(n.index);
            InfernoThreats.Track track = threats.track(n);
            if (n.targetsPlayer() || threats.damaged(n)) tagged.put(n.index, track == null ? f.tick : track.spawn);
            if (tagged.containsKey(n.index) && track != null && tagged.get(n.index) != track.spawn) tagged.remove(n.index);
            if (tagRequested.containsKey(n.index) && track != null && tagRequested.get(n.index) != track.spawn)
                tagRequested.remove(n.index);
        }
        tagged.keySet().removeIf(index -> !live.contains(index));
        tagRequested.keySet().removeIf(index -> !live.contains(index));
    }

    private void observeShield(InfernoSnapshot f)
    {
        Npc shield = first(f, Role.SHIELD);
        if (shield == null) return;
        if (shield.index != shieldIndex)
        { shieldX = Integer.MIN_VALUE; shieldDirection = shieldDwell = 0; shieldIndex = shield.index; }
        if (shieldX != Integer.MIN_VALUE)
        {
            int delta = shield.x - shieldX;
            if (Math.abs(delta) == 1) { shieldDirection = Integer.signum(delta); shieldDwell = 0; }
            else if (delta == 0) shieldDwell++;
            else { shieldDirection = 0; shieldDwell = 0; }
        }
        shieldX = shield.x;
    }

    private List<JsonObject> defensiveOnly(InfernoSnapshot f, InfernoCollision grid, List<Npc> enemies)
    {
        List<JsonObject> out = new ArrayList<>();
        InfernoThreats.Choice protect = threats.choose(f, enemies, grid, Collections.singletonList(new Tile(f.x, f.y)), wave);
        protection(f, protect.guard, out); return out;
    }

    /**
     * Every recording raises the next wave's first threat 4-6 ticks before it spawns (9-11 ticks after
     * a clear): Magic when a mage comes (35-66), Range for a ranger or blob without one, else nothing.
     */
    private Guard prespawn(InfernoSnapshot f)
    {
        if (wave <= 0 || emptySince < 0 || f.tick - emptySince < 5) return Guard.NONE;
        int next = wave + 1;
        if (next >= 35 && next <= 66) return Guard.MAGIC;
        if (next >= 4 && next <= 7 || next >= 12 && next <= 15 || next >= 18 && next <= 34) return Guard.RANGE;
        return Guard.NONE;
    }

    private List<JsonObject> betweenWaves(InfernoSnapshot f, InfernoCollision grid)
    {
        List<JsonObject> out = new ArrayList<>(); protection(f, prespawn(f), out); offense(f, "", out);
        if (!openingClosed && wave == 0 && opening(f, grid, out)) return out;
        camera(f, wave >= 67 ? 2025 : 1049, out);
        if (recoverSwapSpace(f, grid, out)) return out;
        if (reclaimReserve(f, grid, out)) return out;
        JsonObject supply = supplies.next(f, wave); if (supply != null) out.add(supply);
        // Empty-scene recording hints cannot distinguish initial reserve staging
        // from a later wave break. The opening latch owns its own destinations.
        Tile home = wave >= 66 ? new Tile(2271, 5356) : HOME;
        if (grid.available())
        {
            InfernoCollision.Paths paths = grid.paths(new Tile(f.x, f.y), 40);
            List<Tile> path = paths.to(home);
            if (!path.isEmpty() && path.size() > 1) move(f, path.get(Math.min(path.size() - 1, 8)), out);
        }
        Mode prep = wave >= 66 ? Mode.LONG_RANGE : rangeOnly ? Mode.CHINS : Mode.ICE;
        equip(f, prep, wave >= 66, out);
        if (prep == Mode.ICE && loadout.ready(f, prep, false) && !f.spellSelected && f.iceLevel() > 0
            && emptySince >= 0 && f.tick - emptySince >= 5) prearmIce(f, 2266, 5345, out);
        if (supply == null && f.inventory.size() < 28) loot(f, out);
        status = "Wave " + wave + ": preparing next wave";
        return out;
    }

    private List<JsonObject> waves(InfernoSnapshot f, InfernoCollision grid, List<Npc> enemies)
    {
        Tile here = new Tile(f.x, f.y);
        // Gold reclaims the reserve on wave30 with one blob left behind the north pillar.
        if (enemies.size() == 1 && enemies.get(0).role == Role.BLOB)
        {
            List<JsonObject> reserve = new ArrayList<>();
            if (reclaimReserve(f, grid, reserve))
            {
                List<Tile> path = grid.paths(here, 40).to(RESERVE);
                List<JsonObject> out = new ArrayList<>();
                protection(f, threats.choose(f, enemies, grid, path.isEmpty() ? Collections.singletonList(here) : path, wave).guard, out);
                JsonObject supply = supplies.next(f, wave); if (supply != null) out.add(supply);
                out.addAll(reserve); return out;
            }
        }
        Npc target = target(f, grid, enemies);
        Mode mode = mode(f, target, enemies);
        // An empty vial can make the requested blowpipe switch possible now.
        // Do not bypass equip's space action just because a crossbow is carried.
        // With no disposable vial, preserve combat with a one-handed fallback
        // until the next clear scene can safely reserve one restore.
        if (mode == Mode.BLOWPIPE && fullShieldSwap(f) && !inventorySpace.canDropVial(f)
            && loadout.available(Mode.LONG_RANGE) && !loadout.twoHanded(Mode.LONG_RANGE)) mode = Mode.LONG_RANGE;
        int range = loadout.range(mode);
        Route route = route(f, grid, enemies, target, range);
        // Every prayer click clears a selected spell and preempts the select-then-cast gesture. Two offset blobs
        // need a prayer click almost every tick when flicked off between scans (live 2026-11-03 wave 7: 30 Ice
        // casts cancelled), so while a cast is in flight the protection is held and only switched.
        Guard guard = route.prayer.guard;
        if (guard == Guard.NONE && mode == Mode.ICE && target != null && f.interacting != target.index
            && f.tick - lastArmIce <= 2) guard = Guard.active(f);
        List<JsonObject> out = new ArrayList<>(); protection(f, guard, out);
        camera(f, wave >= 67 ? 2025 : 1049, out);
        JsonObject supply = supplies.next(f, wave); if (supply != null) out.add(supply);
        // Walking over the restore reserve mid-wave picks it up (older recordings, waves 26-28).
        if (here.equals(RESERVE) && reservePresent(f) && canTakeSupply(f) && f.tick - lastLoot >= 3)
        { out.add(command("ground.take", "name", "Super restore", "radius", 2)); lastLoot = f.tick; }
        if (!grid.available())
        { status = "Wave " + wave + ": waiting for collision snapshot"; return out; }
        if (target == null) return out;
        if (!loadout.available(mode))
        { status = "Wave " + wave + ": required carried weapon unavailable for " + mode; return out; }
        boolean ready = equip(f, mode, false, out);
        boolean shotHere = target.distance(f.x, f.y) <= range && grid.playerSight(here, target);
        // A walk click clears a selected spell; cast first when this tile is no riskier than the route. Likewise
        // the recordings open fire from where the player stands rather than finish a walk (attack onsets
        // 69% -> 74% on the success run).
        boolean castHere = (mode == Mode.ICE && f.spellSelected || mode != Mode.ICE && f.interacting != target.index) && ready && shotHere
            && route.standing.immediate <= route.prayer.immediate
            && (mode == Mode.ICE || route.standing.total <= route.prayer.total + SHOT_RISK_MARGIN);
        boolean moves = route.dest != null && !route.dest.equals(here) && !castHere;
        if (moves)
        {
            stalledSince = -1;
            move(f, route.dest, out);
            status = "Wave " + wave + ": positioning for " + target.name;
            return out;
        }
        if (!ready)
        {
            stopWalk(f, here, out);
            status = "Wave " + wave + ": " + (loadout.twoHanded(mode) && fullShieldSwap(f)
                ? inventorySpace.status() : "confirming " + mode + " equipment");
            return out;
        }
        if (!shotHere)
        {
            stopWalk(f, here, out);
            if (stalledSince < 0) stalledSince = f.tick;
            status = "Wave " + wave + ": protecting while target is blocked"; return out;
        }
        stalledSince = -1;
        if (mode == Mode.ICE)
        {
            if (f.iceLevel() == 0) { status = "Magic drained below every carried Ice spell; restoring"; return out; }
            if (!f.spellSelected)
            {
                // Already casting at this nibbler: the recordings let the autocast run instead of re-casting.
                if (f.interacting == target.index && !f.moving)
                { status = "Wave " + wave + ": Ice on " + target.name; return out; }
                if (canShoot(f, 5)) castIce(f, target, out);
                status = "Wave " + wave + ": casting Ice on " + target.name; return out;
            }
        }
        if (mode != Mode.ICE && f.spellSelected)
        {
            clearSpellSelection(f, out); return out;
        }
        special(f, target, mode, out);
        attack(f, target, mode == Mode.ICE, out);
        status = "Wave " + wave + ": " + mode + " on " + target.name;
        return out;
    }

    private Npc target(InfernoSnapshot f, InfernoCollision grid, List<Npc> enemies)
    {
        List<Npc> nibblers = enemies.stream().filter(n -> n.role == Role.NIBBLER).collect(Collectors.toList());
        boolean bloodCleanup = nibblerBloodCleanup(f);
        if (targetHint != null)
        {
            Npc preferred = f.npc(targetHint);
            if (preferred != null && preferred.enemy()
                && !(preferred.role == Role.NIBBLER && bloodCleanup && iceHitNibblers.contains(preferred.index)
                    && nibblers.stream().anyMatch(n -> !iceHitNibblers.contains(n.index)))) return preferred;
        }
        if (!nibblers.isEmpty())
            return nibblers.stream().min(Comparator.<Npc>comparingInt(n -> iceHitNibblers.contains(n.index) ? 1 : 0)
                .thenComparingInt(n -> -cluster(n, nibblers))
                .thenComparingInt(n -> n.distance(f.x, f.y)).thenComparingInt(n -> n.index)).orElse(null);
        List<Npc> jads = enemies.stream().filter(n -> n.role == Role.JAD).collect(Collectors.toList());
        if (!jads.isEmpty())
        {
            // Each healer is tagged once (one attack apiece, 5 or 3 per spawn in both long runs), then back to Jad.
            Npc healer = enemies.stream().filter(n -> n.role == Role.JAD_HEALER && !tagged.containsKey(n.index))
                .min(Comparator.comparingInt(n -> n.distance(f.x, f.y))).orElse(null);
            if (healer != null) return healer;
            Npc existing = f.npc(f.interacting);
            if (existing != null && existing.alive() && existing.role == Role.JAD) return existing;
            return jads.stream().min(Comparator.<Npc>comparingInt(n -> n.hpRatio < 0 ? 1000 : n.hpRatio)
                .thenComparingInt(n -> n.distance(f.x, f.y))).orElse(null);
        }
        Tile here = new Tile(f.x, f.y);
        // One Jal-Ak at a time: its split is cleared before the next blob is touched (recordings: 96-98% of
        // attacks while splits live go to a split; live 2026-11-03 wave 7 shot the second blob through the
        // first one's split and ended with six splits alive).
        boolean splits = enemies.stream().anyMatch(InfernoDecisionEngine::mini);
        boolean splitPending = !splits && f.npcs.stream().anyMatch(n -> n.role == Role.BLOB && n.dead);
        if (splitPending && enemies.stream().allMatch(n -> n.role == Role.BLOB))
        { status = "Wave " + wave + ": waiting for the Jal-Ak split"; return null; }
        // The recordings finish the NPC being shot (a bat closing in is taken only as the mage dies), unless a
        // meleer can hit the player now.
        Npc current = f.npc(f.interacting);
        if (current != null && current.enemy() && current.role != Role.NIBBLER && current.hpRatio != 0
            && !(current.role == Role.BLOB && (splits || splitPending))
            && grid.playerSight(here, current)
            && enemies.stream().noneMatch(n -> n.role == Role.MELEE && grid.melee(n, here))) return current;
        return enemies.stream().min(Comparator.<Npc>comparingInt(n -> grid.playerSight(here, n) ? 0 : 1)
            .thenComparingInt(n -> priority(n, f, grid)).thenComparingInt(n -> n.index == f.interacting ? 0 : 1)
            .thenComparingInt(n -> n.distance(f.x, f.y))).orElse(null);
    }

    private int priority(Npc n, InfernoSnapshot f, InfernoCollision grid)
    {
        if (n.role == Role.MELEE && grid.melee(n, new Tile(f.x, f.y))) return 0;
        if (n.role == Role.BAT && n.distance(f.x, f.y) <= 5) return 1;
        if (n.role == Role.MELEE && n.distance(f.x, f.y) <= 5) return 2;
        if (n.role == Role.MAGE) return 3;
        if (n.role == Role.RANGER) return 4;
        if (n.role == Role.BAT) return 5;
        if (n.role == Role.MELEE) return 6;
        if (mini(n)) return 7;
        if (n.role == Role.BLOB) return 8;
        return 9;
    }

    private static boolean mini(Npc n)
    { return n.role == Role.MINI_MAGE || n.role == Role.MINI_RANGE || n.role == Role.MINI_MELEE; }

    private Mode mode(InfernoSnapshot f, Npc target, List<Npc> enemies)
    {
        if (target == null) return Mode.LONG_RANGE;
        if (target.role == Role.NIBBLER)
        {
            if (!rangeOnly) return nibblerBloodCleanup(f) ? Mode.BLOOD : Mode.ICE;
            return loadout.available(Mode.CHINS) ? Mode.CHINS : loadout.available(Mode.BLOWPIPE) ? Mode.BLOWPIPE : Mode.LONG_RANGE;
        }
        long major = enemies.stream().filter(n -> n.role != Role.MINI_MAGE && n.role != Role.MINI_RANGE && n.role != Role.MINI_MELEE).count();
        boolean small = target.role == Role.MINI_MAGE || target.role == Role.MINI_RANGE || target.role == Role.MINI_MELEE;
        if (canUseBlood(f)
            && target.role != Role.JAD && target.role != Role.ZUK && target.role != Role.ZUK_HEALER
            && (f.hp < f.maxHp && major <= 1 || small && cluster(target, enemies) >= 2)) return Mode.BLOOD;
        // The ranged mini-blob is shot with the mage weapon's autocast, the other two with the blowpipe.
        if (target.role == Role.MINI_RANGE && !rangeOnly && loadout.available(Mode.ICE) && f.iceLevel() > 0) return Mode.ICE;
        if (target.distance(f.x, f.y) <= 5 && target.role != Role.ZUK && loadout.available(Mode.BLOWPIPE))
            return Mode.BLOWPIPE;
        return loadout.available(Mode.LONG_RANGE) ? Mode.LONG_RANGE : Mode.BLOWPIPE;
    }

    private boolean canUseBlood(InfernoSnapshot f)
    {
        int bloodLevel = bloodRequirement(f);
        return bloodLevel > 0 && f.magic >= bloodLevel && !supplies.recovering();
    }

    private boolean nibblerBloodCleanup(InfernoSnapshot f)
    {
        return nibblerIceObserved && !f.spellSelected && canUseBlood(f);
    }

    private void resetNibblerIce()
    {
        nibblerSpawns.clear(); iceHitNibblers.clear(); nibblerIceObserved = false; nibblerWave = -1;
    }

    /**
     * The reference opens with Ice and then uses ordinary Blood autocast on the survivors.
     * A submitted attack is not a freeze receipt: only an observed Ice Burst/Barrage impact
     * on this live nibbler cohort allows the switch. These are RuneLite GraphicID 363/369.
     * Keeping impact identity is useful for target priority; it does not claim an eternal freeze.
     */
    private void observeNibblerIce(InfernoSnapshot f, boolean gap)
    {
        if (gap || nibblerWave != wave || !InfernoSnapshot.bool(f.raw, "inputAllowed", true)) resetNibblerIce();
        if (!InfernoSnapshot.bool(f.raw, "inputAllowed", true)) return;
        nibblerWave = wave;
        Map<Integer, Long> live = new HashMap<>();
        for (Npc n : f.npcs) if (n.alive() && n.role == Role.NIBBLER)
        {
            InfernoThreats.Track track = threats.track(n);
            if (track != null) live.put(n.index, track.spawn);
        }
        boolean newSpawn = live.entrySet().stream().anyMatch(e -> !e.getValue().equals(nibblerSpawns.get(e.getKey())));
        if (live.isEmpty() || newSpawn) { iceHitNibblers.clear(); nibblerIceObserved = false; }
        nibblerSpawns.clear(); nibblerSpawns.putAll(live);
        iceHitNibblers.retainAll(live.keySet());
        for (int ordinal = 0; ordinal < f.events.size(); ordinal++)
        {
            JsonElement element = f.events.get(ordinal);
            if (!element.isJsonObject()) continue;
            JsonObject event = element.getAsJsonObject();
            if (!"graphic".equals(InfernoSnapshot.string(event, "e", ""))) continue;
            int index = InfernoSnapshot.integer(event, "actor", -1);
            Npc npc = f.npc(index);
            if (!live.containsKey(index) || npc == null || !threats.currentEvent(npc, ordinal)) continue;
            for (JsonElement value : InfernoSnapshot.array(event, "spotAnims"))
            {
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) continue;
                int graphic = value.getAsInt();
                if (graphic == 363 || graphic == 369)
                {
                    iceHitNibblers.add(index); nibblerIceObserved = true;
                }
            }
        }
    }

    private void observeAutocast(InfernoSnapshot f)
    {
        if (!InfernoSnapshot.bool(f.player, "ancientSpellbook", false))
        { confirmedBloodLevels.clear(); return; }
        Item weapon = f.weapon();
        if (weapon == null || InfernoLoadout.weaponType(weapon) != Mode.BLOOD) return;
        if (f.bloodLevel > 0) confirmedBloodLevels.put(weapon.id, f.bloodLevel);
        else confirmedBloodLevels.remove(weapon.id);
    }

    private int bloodRequirement(InfernoSnapshot f)
    {
        if (rangeOnly || !InfernoSnapshot.bool(f.player, "ancientSpellbook", false) || !loadout.available(Mode.BLOOD)) return 0;
        return confirmedBloodLevels.getOrDefault(loadout.weapon(Mode.BLOOD).id, 0);
    }

    private static int cluster(Npc centre, List<Npc> all)
    {
        int result = 0;
        for (Npc n : all) if (Math.abs(n.x - centre.x) <= 1 && Math.abs(n.y - centre.y) <= 1) result++;
        return result;
    }

    private static final class Route
    {
        /** The tile to click: the recordings click a destination and let the game path to it. */
        final Tile dest; final InfernoThreats.Choice prayer, standing; final double score;
        Route(Tile dest, InfernoThreats.Choice prayer, InfernoThreats.Choice standing, double score)
        { this.dest = dest; this.prayer = prayer; this.standing = standing; this.score = score; }
    }

    /** A walk in progress keeps its destination unless another tile is clearly better. */
    private static final double KEEP_DESTINATION_MARGIN = 100;
    /**
     * A standing player stays unless another tile is clearly better: the recordings stand and shoot, while
     * near-tied scores flipped the engine between staying and walking every tick (success run 6604-6661).
     */
    static final double STAY_MARGIN = 150;
    /**
     * Shooting from the current tile instead of walking may cost prayer toggles, never a real hit in the next six
     * ticks (an unanswerable hit there costs 10x its maximum, 180+).
     */
    static final int SHOT_RISK_MARGIN = 50;
    /** Ticks of standing without a shot before cover starts to cost, then the cost per further tick. */
    private static final int STALL_GRACE = 3, STALL_COST = 400;

    private Route route(InfernoSnapshot f, InfernoCollision grid, List<Npc> enemies, Npc target, int range)
    {
        Tile here = new Tile(f.x, f.y);
        InfernoThreats.Choice standing = threats.choose(f, enemies, grid, Collections.singletonList(here), wave);
        if (!grid.available() || target == null || manualMovement) return new Route(here, standing, standing, 0);
        InfernoCollision.Paths paths = grid.paths(here, 16);
        List<Tile> candidates = new ArrayList<>(paths.reachable);
        Tile hint = movementHint != null && movementHint.length >= 2 ? new Tile(movementHint[0], movementHint[1]) : null;
        candidates.sort(Comparator.<Tile>comparingInt(t -> target.distance(t.x, t.y) <= range && grid.playerSight(t, target) ? 0 : 1)
            .thenComparingInt(t -> Math.max(0, target.distance(t.x, t.y) - range))
            .thenComparingInt(t -> hint != null ? t.distance(hint) : paths.distance(t)));
        if (candidates.size() > 24) candidates = new ArrayList<>(candidates.subList(0, 24));
        candidates.add(here);
        if (hint != null && paths.distance(hint) >= 0 && !candidates.contains(hint)) candidates.add(hint);
        // Include nearby escape tiles even when they cannot immediately attack the chosen target.
        for (Tile tile : paths.reachable) if (paths.distance(tile) <= 2 && !candidates.contains(tile)) candidates.add(tile);
        boolean walking = f.moving && moveTarget != null && !moveTarget.equals(here) && paths.distance(moveTarget) >= 0;
        if (walking && !candidates.contains(moveTarget)) candidates.add(moveTarget);
        // Unknown attack phases are modelled as due every tick, so NPCs parked behind a pillar make every exposed
        // tile look worse than cover forever (2026-11-01 wave 14: blob and two bats, 80+ idle ticks). Waiting
        // in cover gets steadily more expensive until stepping out to tag them wins.
        long stalled = stalledSince < 0 ? 0 : Math.max(0, f.tick - stalledSince - STALL_GRACE);
        double noShot = 250 + stalled * STALL_COST;
        Route best = null, kept = null, stay = null;
        for (Tile tile : candidates)
        {
            List<Tile> path = paths.to(tile); if (path.isEmpty()) continue;
            InfernoThreats.Choice prayer = threats.choose(f, enemies, grid, path, wave);
            boolean shot = target.distance(tile.x, tile.y) <= range && grid.playerSight(tile, target);
            double score = prayer.immediate * 1_000_000.0 + prayer.total * 10.0
                + (shot ? 0 : noShot) + Math.max(0, target.distance(tile.x, tile.y) - range) * 25
                + paths.distance(tile) * 2.0;
            if (hint != null) score += Math.min(40, tile.distance(hint)) * 4.0;
            for (Npc n : enemies)
            {
                if (n.role == Role.JAD && n.distance(tile.x, tile.y) <= 1) score += 100_000;
                if (threats.digging(n, f.tick) && tile.distance(here) < 2) score += 25;
            }
            Route option = new Route(tile, prayer, standing, score);
            if (best == null || score < best.score) best = option;
            if (walking && tile.equals(moveTarget)) kept = option;
            if (tile.equals(here)) stay = option;
        }
        if (best == null) return new Route(here, standing, standing, 0);
        if (kept != null && kept.prayer.immediate <= best.prayer.immediate
            && kept.score <= best.score + KEEP_DESTINATION_MARGIN) return kept;
        if (!f.moving && stay != null && stay.prayer.immediate <= best.prayer.immediate
            && stay.score <= best.score + STAY_MARGIN) return stay;
        return best;
    }

    private List<JsonObject> zuk(InfernoSnapshot f, InfernoCollision grid, List<Npc> enemies, Npc zuk)
    {
        List<JsonObject> out = new ArrayList<>(); Tile here = new Tile(f.x, f.y);
        Npc shield = first(f, Role.SHIELD);
        InfernoThreats.Choice pray = threats.choose(f, enemies, grid, Collections.singletonList(here), 69);
        protection(f, pray.guard, out); camera(f, 2025, out);
        // Gold session08 keeps offense off until healers, then holds it through travel.
        // The two-restore-dose cutoff reproduces its one observed late conservation transition.
        // Shield phase: two of three recordings raise offense with the first shot at Zuk when 25+ prayer is left
        // (26 and 29 raised it, 23 did not) and hold it until prayer falls to 15; it then stays off until healers.
        if (zukEarlyOffense && (f.prayer <= 15 || sawJadAtZuk)) zukEarlyOffense = false;
        boolean early = zukEarlyOffense && !zukFinal && (f.npc(f.interacting) == zuk || lastTarget == zuk.index);
        offense(f, zukFinal && InfernoSupplies.doses(f, InfernoSupplies.Kind.RESTORE) > 2 || early ? f.offensive(false) : "", out);
        boolean topoff = sawJadAtZuk && !zukFinal && health(zuk) <= 0.25;
        JsonObject supply = supplies.next(f, 69, topoff, first(f, Role.ZUK_HEALER) != null);
        if (supply != null) out.add(supply);
        if (shield == null || !grid.available())
        {
            status = shield == null ? "Zuk: shield unavailable; protecting while observing" : "Zuk: awaiting collision";
            return out;
        }
        InfernoThreats.Track clock = threats.track(zuk);
        boolean fire = clock != null && clock.lastAttack == f.tick;
        if (fire) lastZukAttack = f.tick;
        int period = zukFinal ? 7 : 10;
        int untilFire = lastZukAttack < 0 ? 1 : (int) Math.max(1, lastZukAttack + period - f.tick);
        int expectedShieldX = predictShield(shield.x, untilFire);
        Tile attackPosition = moveTarget != null && safeAtLaunch(moveTarget, shield, expectedShieldX)
            ? moveTarget : new Tile(clamp(f.x, expectedShieldX - 1, expectedShieldX + 3),
                clamp(f.y, shield.y - 4, shield.y - 2));
        Npc target = zukTarget(f, grid, enemies, zuk, attackPosition);
        boolean ruby = target.role == Role.ZUK ? health(zuk) > 0.20
            : f.equipment.stream().anyMatch(i -> i.slot == 13 && i.lower.contains("ruby"));
        Mode mode = target.role == Role.ZUK || target.role == Role.JAD
            || target.role == Role.MAGE && !tagged.containsKey(target.index) ? Mode.LONG_RANGE
            : target.distance(f.x, f.y) <= 5 && loadout.available(Mode.BLOWPIPE) ? Mode.BLOWPIPE : Mode.LONG_RANGE;
        if (target.role == Role.ZUK_HEALER && f.moving && f.weapon() != null
            && InfernoLoadout.weaponType(f.weapon()) == Mode.LONG_RANGE) mode = Mode.LONG_RANGE;
        if (mode == Mode.BLOWPIPE && fullShieldSwap(f) && !inventorySpace.canDropVial(f)
            && loadout.available(Mode.LONG_RANGE) && !loadout.twoHanded(Mode.LONG_RANGE)) mode = Mode.LONG_RANGE;
        if (!loadout.available(mode)) mode = loadout.available(Mode.BLOWPIPE) ? Mode.BLOWPIPE : Mode.LONG_RANGE;

        Tile next = null;
        boolean recordedWait = false;
        if (fire && zukReference != null)
        {
            InfernoZukReference.Hint hint = zukReference.suggest(f.raw, period, shieldDirection, shieldDwell);
            int[] choice = hint.movement;
            recordedWait = hint.matched && choice == null && safeAtLaunch(here, shield, expectedShieldX);
            if (choice != null && choice.length >= 2)
            {
                Tile candidate = new Tile(choice[0], choice[1]);
                if (safeAtLaunch(candidate, shield, expectedShieldX) && reachableBy(f, grid, here, candidate, untilFire)) next = candidate;
            }
        }
        if (next == null && !recordedWait && movementHint != null && movementHint.length >= 2)
        {
            Tile candidate = new Tile(movementHint[0], movementHint[1]);
            if (safeAtLaunch(candidate, shield, expectedShieldX) && reachableBy(f, grid, here, candidate, untilFire)) next = candidate;
        }
        // A walk already heading to a tile that is safe at the next launch keeps it (the recordings click once
        // per launch; re-planning a tick later moved the click 34+140 times on the success run).
        if (zukDest != null && (here.equals(zukDest) || !safeAtLaunch(zukDest, shield, expectedShieldX))) zukDest = null;
        if (next == null && !fire && zukDest != null) next = zukDest;
        if (next == null && !recordedWait && (fire || !safeAtLaunch(here, shield, expectedShieldX)))
            next = shieldDestination(f, grid, shield, target, expectedShieldX, untilFire);
        int range = loadout.range(mode);
        boolean inReach = target.distance(f.x, f.y) <= range && grid.playerSight(here, target);
        // Zuk recordings: walk on the launch tick, then shoot as soon as the weapon is ready again (never before
        // launch + 3) even mid-walk, and click the destination again once that attack has stopped the walk.
        boolean shootOnTheWay = next != null && !fire && next.equals(zukDest) && inReach && f.interacting != target.index
            && target.distance(next.x, next.y) <= range && grid.playerSight(next, target)
            && lastZukAttack >= 0 && f.tick - lastZukAttack >= 3 && offenseClock.readyNext(f) && lastAttackRequest < zukDestTick;

        if (next != null && !next.equals(here) && !manualMovement && !shootOnTheWay)
        {
            equip(f, mode, ruby, out);
            if (fire || !next.equals(zukDest)) { zukDest = next; zukDestTick = f.tick; }
            if (lastAttackRequest == f.tick - 1 && lastAttackRequest > zukDestTick) { moveTarget = null; }
            move(f, next, out);
            status = "Zuk: move after launch to " + next;
            return out;
        }
        boolean ready = equip(f, mode, ruby, out);
        if (!ready) { status = "Zuk: confirming " + mode + " equipment"; return out; }
        // Standing, the recordings ask for the next shot only on the tick before the weapon is ready.
        if (f.interacting != target.index && !offenseClock.readyNext(f) && !shootOnTheWay)
        {
            status = "Zuk: waiting for the weapon"; return out;
        }
        if (!shootOnTheWay && (!safeAtLaunch(here, shield, expectedShieldX) || !inReach))
        {
            status = "Zuk: holding the next shield launch position";
            return out;
        }
        if (f.spellSelected)
        {
            clearSpellSelection(f, out); return out;
        }
        if (topoff && supplies.recovering() && target.role == Role.ZUK)
        { offense(f, "", out); status = "Zuk: restoring before healer threshold"; return out; }
        special(f, target, mode, out);
        if (attack(f, target, false, out) && target.role == Role.ZUK && !zukEarlyDecided)
        {
            zukEarlyDecided = true; zukEarlyOffense = f.prayer >= ZUK_EARLY_OFFENSE_PRAYER;
            if (zukEarlyOffense) offense(f, f.offensive(false), out);
        }
        status = "Zuk " + (zukFinal ? first(f, Role.ZUK_HEALER) != null ? "healer phase" : "final phase"
            : sawJadAtZuk ? "Jad phase" : "shield phase") + ": " + target.name;
        return out;
    }

    private Npc zukTarget(InfernoSnapshot f, InfernoCollision grid, List<Npc> enemies, Npc zuk, Tile attackPosition)
    {
        List<Npc> adds = enemies.stream().filter(n -> n.role == Role.MAGE || n.role == Role.RANGER).collect(Collectors.toList());
        // Tags and kills are constrained by the current shield-side attack window.
        // The reference works on a nearby healer while a ranger is out of reach on the far side.
        Npc untagged = adds.stream().filter(n -> !tagged.containsKey(n.index))
            .filter(n -> zukReachable(f, grid, n, attackPosition))
            .min(Comparator.<Npc>comparingInt(n -> n.role == Role.MAGE ? 0 : 1)
                .thenComparingInt(n -> n.distance(f.x, f.y))).orElse(null);
        if (untagged != null) return untagged;
        Npc ranger = adds.stream().filter(n -> n.role == Role.RANGER)
            .filter(n -> zukReachable(f, grid, n, attackPosition))
            .min(Comparator.comparingInt(n -> n.distance(f.x, f.y))).orElse(null);
        if (ranger != null) return ranger;
        if (!sawJadAtZuk && health(zuk) <= 0.50)
        {
            Npc mage = adds.stream().filter(n -> n.role == Role.MAGE)
                .min(Comparator.<Npc>comparingLong(n -> { InfernoThreats.Track t = threats.track(n); return t == null ? 0 : t.spawn; })
                    .thenComparingInt(n -> n.distance(f.x, f.y))).orElse(null);
            if (mage != null) return mage;
        }
        Npc jad = first(f, Role.JAD);
        if (jad != null && !tagged.containsKey(jad.index) && zukReachable(f, grid, jad, attackPosition)) return jad;
        List<Npc> healers = enemies.stream().filter(n -> n.role == Role.ZUK_HEALER).collect(Collectors.toList());
        if (!healers.isEmpty())
        {
            Npc observed = f.npc(f.interacting);
            if (observed != null && observed.alive() && observed.role == Role.ZUK_HEALER)
                activeZukHealer = observed.index;
            Npc active = f.npc(activeZukHealer);
            long aggro = healers.stream().filter(n -> tagged.containsKey(n.index)).count();
            if (healersAtOnce > 1 && aggro < healersAtOnce)
            {
                Npc next = healers.stream().filter(n -> !tagged.containsKey(n.index))
                    .filter(n -> zukReachable(f, grid, n, attackPosition))
                    .min(Comparator.comparingInt(n -> n.distance(f.x, f.y))).orElse(null);
                if (next != null) return next;
            }
            if (active != null && active.alive() && active.role == Role.ZUK_HEALER
                && zukReachable(f, grid, active, attackPosition)) return active;
            Npc nearest = healers.stream().min(Comparator.<Npc>comparingInt(n -> zukReachable(f, grid, n, attackPosition) ? 0 : 1)
                .thenComparingInt(n -> n.distance(f.x, f.y))
                .thenComparingInt(n -> n.x)).orElse(null);
            activeZukHealer = nearest.index; return nearest;
        }
        activeZukHealer = -1;
        return zuk;
    }

    private boolean zukReachable(InfernoSnapshot f, InfernoCollision grid, Npc target, Tile attackPosition)
    {
        int range = Math.max(loadout.range(Mode.LONG_RANGE), loadout.range(Mode.BLOWPIPE));
        return target.distance(f.x, f.y) <= range && target.distance(attackPosition.x, attackPosition.y) <= range
            && grid.playerSight(new Tile(f.x, f.y), target) && grid.playerSight(attackPosition, target);
    }

    private int predictShield(int x, int ticks)
    {
        int direction = shieldDirection;
        if (direction == 0) return x;
        int dwell = x == 2257 && direction < 0 || x == 2283 && direction > 0 ? Math.max(0, 4 - shieldDwell) : 0;
        for (int i = 0; i < Math.min(ticks, 60); i++)
        {
            if (x == 2257 && direction < 0 || x == 2283 && direction > 0)
            {
                if (dwell > 0) { dwell--; continue; }
                direction = -direction;
            }
            x += direction;
            if (x == 2257 || x == 2283) dwell = 4;
        }
        return x;
    }

    private static boolean safeAtLaunch(Tile tile, Npc shield, int predictedX)
    {
        return tile.x >= predictedX - 1 && tile.x <= predictedX + 3
            && tile.y >= shield.y - 4 && tile.y <= shield.y - 2;
    }

    private static boolean reachableBy(InfernoSnapshot f, InfernoCollision grid, Tile start, Tile target, int ticks)
    {
        if (!grid.free(target.x, target.y)) return false;
        int distance = grid.paths(start, Math.min(60, ticks * 2)).distance(target);
        return distance >= 0 && distance <= ticks * (f.runOn && f.runEnergy > 0 ? 2 : 1);
    }

    private Tile shieldDestination(InfernoSnapshot f, InfernoCollision grid, Npc shield, Npc target, int predictedX, int ticks)
    {
        Tile here = new Tile(f.x, f.y); InfernoCollision.Paths paths = grid.paths(here, Math.min(60, ticks * 2));
        Tile best = null; double bestScore = Double.POSITIVE_INFINITY;
        for (int x = predictedX - 1; x <= predictedX + 3; x++)
            for (int y = shield.y - 4; y <= shield.y - 2; y++)
            {
                Tile candidate = new Tile(x, y); int distance = paths.distance(candidate);
                if (distance < 0 || distance > ticks * (f.runOn && f.runEnergy > 0 ? 2 : 1)) continue;
                int range = loadout.range(Mode.LONG_RANGE);
                double score = Math.max(0, target.distance(x, y) - range) * 100
                    + (grid.playerSight(candidate, target) ? 0 : 100)
                    + Math.abs(y - (shield.y - 2)) * 4 + distance * 0.2;
                if (score < bestScore) { bestScore = score; best = candidate; }
            }
        return best;
    }

    private boolean equip(InfernoSnapshot f, Mode mode, boolean ruby, List<JsonObject> out)
    {
        if (loadout.ready(f, mode, ruby)) return true;
        if (loadout.twoHanded(mode) && fullShieldSwap(f))
        {
            JsonObject drop = inventorySpace.drop(f, false);
            if (drop != null) out.add(drop);
            return false;
        }
        if (mode != lastMode || f.tick - lastEquip >= 2)
        {
            JsonObject gear = loadout.switchTo(f, mode, ruby);
            if (gear != null) { out.add(gear); lastMode = mode; lastEquip = f.tick; }
        }
        return false;
    }

    private static boolean fullShieldSwap(InfernoSnapshot f)
    { return f.inventory.size() >= 28 && f.weapon() != null && f.equipment.stream().anyMatch(i -> i.slot == 5); }

    private boolean canShoot(InfernoSnapshot f, int period)
    { return threats.lastPlayerShot < 0 || f.tick + 1 >= threats.lastPlayerShot + period; }

    /**
     * The offensive prayer for the shot that fires next tick at what the player is already attacking, or "".
     * The success run raises it only against Jad, mages, meleers and Zuk (never bats, blobs, nibblers or
     * rangers), only for the tick before each shot, and never for the first shot after an attack request.
     */
    private String offensive(InfernoSnapshot f, boolean requesting)
    {
        if (requesting) return "";
        Npc shot = f.npc(f.interacting);
        if (shot == null || !shot.alive()) return "";
        boolean worthwhile = shot.role == Role.MELEE || shot.role == Role.MAGE || shot.role == Role.JAD || shot.role == Role.ZUK;
        if (!worthwhile || !offenseClock.needed(f, false)) return "";
        if (shot.role == Role.MAGE && otherAttacker(f, shot)) return "";
        Mode style = InfernoLoadout.weaponType(f.weapon());
        return f.offensive(style == Mode.BLOOD || style == Mode.ICE);
    }

    /**
     * Another enemy attacked the player within two of its attack cycles. The recordings boost only a fight the
     * target has to itself: success run mage episodes boost 34/34 times when only Magic was flicked and 0/40 times
     * when a ranger or blob forced Range flicks too (or a second mage was the one attacking).
     */
    private boolean otherAttacker(InfernoSnapshot f, Npc shot)
    {
        for (Npc n : f.npcs)
        {
            if (!n.enemy() || n.index == shot.index || n.role == Role.ZUK_HEALER || n.role == Role.SHIELD) continue;
            InfernoThreats.Track t = threats.track(n);
            if (t != null && t.lastAttack >= 0 && f.tick - t.lastAttack <= 2L * InfernoThreats.period(n.role, wave, zukFinal))
                return true;
        }
        return false;
    }

    /**
     * Blowpipe specials in three recordings: against anything but a fresh meleer only once 7+ HP are missing
     * (1-6 missing: 6 of 2,029 eligible ticks; 7+: 67 of 105), and on a meleer whose health bar is not yet shown.
     */
    static final int SPEC_DEFICIT = 7;

    private void special(InfernoSnapshot f, Npc target, Mode mode, List<JsonObject> out)
    {
        if (mode == Mode.BLOWPIPE && f.weapon() != null && f.weapon().lower.contains("blowpipe")
            && f.specEnergy >= 500 && !f.specOn && f.tick - lastSpec >= 1
            && (f.maxHp - f.hp >= SPEC_DEFICIT || target.role == Role.MELEE && target.hpRatio < 0 || target.role == Role.ZUK_HEALER))
        { out.add(command("combat.spec")); lastSpec = f.tick; }
    }

    /** Returns whether an attack request was added this tick. */
    private boolean attack(InfernoSnapshot f, Npc target, boolean ice, List<JsonObject> out)
    {
        if (!target.alive()) return false;
        InfernoThreats.Track track = threats.track(target);
        long spawn = track == null ? -1 : track.spawn;
        boolean sameTarget = lastTarget == target.index && lastTargetId == target.id && lastTargetSpawn == spawn;
        // Moving while already interacting with the target is usually the game pathing into range; the attack is
        // repeated only if that lasts 4+ ticks (a 2-tick repeat clicked over the recordings' offensive flicks).
        boolean newTarget = f.interacting != target.index || f.moving && f.tick - lastAttackRequest >= 4 || ice
            || lastTarget == target.index && !sameTarget;
        if (newTarget && (!sameTarget || f.tick - lastAttackRequest >= (ice ? 5 : 2)))
        {
            JsonObject request = command("npc.attack", "index", target.index);
            // Both Zuk reference runs use 430 only for the first request to a
            // live add which is not yet attacking the player. Subsequent requests
            // remain immediate even if the first request has no game receipt.
            if (sawZuk && track != null && !target.targetsPlayer()
                && (target.role == Role.MAGE || target.role == Role.RANGER || target.role == Role.JAD)
                && tagRequested.getOrDefault(target.index, -1L) != spawn)
                request.addProperty("deadlineMs", 430);
            out.add(request); lastTarget = target.index; lastTargetId = target.id;
            lastTargetSpawn = spawn; lastAttackRequest = f.tick;
            if (target.role == Role.MAGE || target.role == Role.RANGER || target.role == Role.JAD
                || target.role == Role.JAD_HEALER || target.role == Role.ZUK_HEALER) tagRequested.put(target.index, spawn);
            return true;
        }
        return false;
    }

    /**
     * Selection and cast travel as one command, like the reference's same-tick armIce + npc.attack pair: the
     * runner presses both in one gesture so a prayer click cannot clear the spell in between.
     */
    private void castIce(InfernoSnapshot f, Npc target, List<JsonObject> out)
    {
        if (f.tick - lastArmIce < 2) return;
        // A protection release clicked this tick would clear the selection before the cast lands.
        out.removeIf(c -> "prayer.set".equals(InfernoSnapshot.string(c, "op", ""))
            && !InfernoSnapshot.bool(c.getAsJsonObject("args"), "on", true)
            && Arrays.stream(Guard.values()).anyMatch(g -> g != Guard.NONE
                && g.prayer.equals(InfernoSnapshot.string(c.getAsJsonObject("args"), "prayer", ""))));
        out.add(command("magic.armIce", "index", target.index)); lastArmIce = f.tick;
        InfernoThreats.Track track = threats.track(target);
        lastTarget = target.index; lastTargetId = target.id; lastTargetSpawn = track == null ? -1 : track.spawn;
        lastAttackRequest = f.tick;
    }

    private void clearSpellSelection(InfernoSnapshot f, List<JsonObject> out)
    {
        // equipSet is idempotent: repeating already-worn IDs cannot clear a
        // selected widget. Require a genuine carried alternative weapon switch.
        Item alternative = f.inventory.stream().filter(i -> InfernoLoadout.weaponType(i) != null && !f.worn(i.id))
            .filter(i -> !(InfernoLoadout.weaponType(i) == Mode.BLOWPIPE && fullShieldSwap(f)))
            .findFirst().orElse(null);
        if (alternative != null)
        {
            out.add(command("equipment.equip", "itemId", alternative.id));
            status = "Clearing previous Ice selection with a carried weapon switch";
        }
        else status = "Selected spell remains active; clear the selection to resume ranged attacks";
    }

    /** One click per destination; it is repeated only once the player has stopped short of it. */
    private void move(InfernoSnapshot f, Tile tile, List<JsonObject> out)
    {
        if (manualMovement || tile.x == f.x && tile.y == f.y) return;
        if (!tile.equals(moveTarget) || !f.moving && f.tick - lastMove >= 2)
        {
            out.add(command("movement.tile", "x", tile.x, "y", tile.y, "plane", f.plane));
            moveTarget = tile; lastMove = f.tick;
        }
    }

    /**
     * The route settled here while the last walk click still carries the player on. Clicking the player's own
     * tile ends it; an attack click already would, so this is only for turns that do not attack.
     */
    private void stopWalk(InfernoSnapshot f, Tile here, List<JsonObject> out)
    {
        if (manualMovement || !f.moving || moveTarget == null || moveTarget.equals(here)) return;
        out.add(command("movement.tile", "x", f.x, "y", f.y, "plane", f.plane));
        moveTarget = here; lastMove = f.tick;
    }

    private void camera(InfernoSnapshot f, int yaw, List<JsonObject> out)
    {
        JsonObject view = InfernoSnapshot.object(f.raw, "camera");
        int current = InfernoSnapshot.integer(view, "yaw", yaw);
        int delta = Math.min(Math.abs(current - yaw), 2048 - Math.abs(current - yaw));
        boolean turn = delta > 192 && f.tick - lastCamera >= 10;
        // One camera.set replaces a pending one, so a turn and the framing travel together.
        if (framing(f, view))
        {
            out.add(turn ? command("camera.set", "yaw", yaw, "pitch", CAMERA_PITCH, "zoom", CAMERA_ZOOM)
                : command("camera.set", "pitch", CAMERA_PITCH, "zoom", CAMERA_ZOOM));
            if (turn) lastCamera = f.tick;
        }
        else if (turn) { out.add(command("camera.set", "yaw", yaw)); lastCamera = f.tick; }
    }

    /** Off-screen walk tiles are never clicked, so a zoomed-in or tilted view stalls movement. */
    private boolean framing(InfernoSnapshot f, JsonObject view)
    {
        if (!view.has("zoom") || !view.has("pitch")) return false;
        if (InfernoSnapshot.integer(view, "zoom", CAMERA_ZOOM) <= CAMERA_ZOOM_LIMIT
            && InfernoSnapshot.integer(view, "pitch", CAMERA_PITCH) >= CAMERA_PITCH_LIMIT)
        { frameAttempts = 0; return false; }
        if (frameAttempts >= FRAME_ATTEMPTS || f.tick - lastFrame < 3) return false;
        lastFrame = f.tick; frameAttempts++;
        return true;
    }

    private void loot(InfernoSnapshot f, List<JsonObject> out)
    {
        if (!canTakeSupply(f)) return;
        for (JsonElement element : f.ground) if (element.isJsonObject())
        {
            JsonObject item = element.getAsJsonObject(); String name = InfernoSnapshot.string(item, "name", "");
            String lower = name.toLowerCase(java.util.Locale.ROOT);
            int x = InfernoSnapshot.integer(item, "tx", -1), y = InfernoSnapshot.integer(item, "ty", -1);
            // The reserve is reclaimed early only when the player is standing on it (older runs: wave 26-28).
            if (wave < 30 && x == RESERVE.x && y == RESERVE.y && lower.startsWith("super restore")
                && !(f.x == RESERVE.x && f.y == RESERVE.y)) continue;
            // Every recording only ever picks up a dropped Super restore (w52 leaves a dropped brew all run).
            if (lower.startsWith("super restore") && Math.max(Math.abs(x - f.x), Math.abs(y - f.y)) <= 3)
            { out.add(command("ground.take", "name", name, "radius", 3)); return; }
        }
    }

    private boolean opening(InfernoSnapshot f, InfernoCollision grid, List<JsonObject> out)
    {
        boolean inventoryKnown = f.raw.has("inventory") && f.raw.has("equipment");
        // Ground presence proves an earlier reserve, never an available slot.
        // Readiness follows the current inventory/equipment receipt even after
        // a failed drop, a restart, or an unexpected inventory refill.
        initialReserveDone = inventoryKnown && !needsSwapSpace(f);
        boolean first = !openingStarted;
        if (!first && framing(f, InfernoSnapshot.object(f.raw, "camera")))
            out.add(command("camera.set", "pitch", CAMERA_PITCH, "zoom", CAMERA_ZOOM));
        if (first)
        {
            openingStarted = true;
            boolean wornTwoHanded = f.weapon() != null && InfernoLoadout.weaponType(f.weapon()) == Mode.BLOWPIPE;
            openingViaReserve = !initialReserveDone && !wornTwoHanded;
            if (grid.available() && entryObserved && f.y <= 5346)
            {
                Tile initial = openingViaReserve ? RESERVE : new Tile(2273, 5354);
                if (grid.paths(new Tile(f.x, f.y), 40).distance(initial) >= 0) openingMove(f, initial, out);
            }
            camera(f, 1039, out);
            if (f.animation == 4367 || entryObserved && f.y <= 5346)
            { status = "Entering: moving immediately toward the north pillar"; return true; }
        }
        if (!grid.available()) { status = "Opening: waiting for the arena collision snapshot"; return true; }
        Mode preparation = !rangeOnly ? Mode.ICE : loadout.available(Mode.CHINS) ? Mode.CHINS
            : loadout.available(Mode.BLOWPIPE) ? Mode.BLOWPIPE : Mode.LONG_RANGE;
        boolean ready = loadout.ready(f, preparation, false);
        boolean needsReserveBeforeSwitch = rangeOnly && loadout.twoHanded(preparation) && fullShieldSwap(f);
        if (f.animation != 4367 && !ready && !needsReserveBeforeSwitch)
        {
            if (rangeOnly) equip(f, preparation, false, out);
            else if (f.tick - lastEquip >= 2)
            {
                JsonObject gear = loadout.openingMage(f);
                if (gear != null) { out.add(gear); lastEquip = f.tick; lastMode = Mode.ICE; }
            }
        }
        // Equipping a carried staff and book from a two-handed weapon frees a
        // slot itself; sessions02/09 therefore never issue a reserve drop.
        if (ready && inventoryKnown && !needsSwapSpace(f)) initialReserveDone = true;
        Tile here = new Tile(f.x, f.y);
        if (!initialReserveDone && (ready || needsReserveBeforeSwitch) && inventoryKnown && here.equals(RESERVE))
        {
            JsonObject drop = inventorySpace.drop(f, true);
            if (drop != null) out.add(drop);
            status = "Opening: " + inventorySpace.status();
            return true;
        }
        JsonObject supply = f.animation == 4367 ? null : supplies.next(f, wave);
        if (supply != null) out.add(supply);
        if (f.animation != 4367 && ready && !rangeOnly && !f.spellSelected && f.iceLevel() > 0)
            prearmIce(f, 2265, 5345, out);
        Tile destination;
        if (initialReserveDone && f.y >= 5353) destination = OPENING_READY;
        else if (f.y < 5348) destination = new Tile(openingViaReserve ? 2270 : 2272, 5353);
        else if (f.y < 5350) destination = new Tile(openingViaReserve ? 2271 : 2273, 5354);
        else if (f.y < 5352 && openingViaReserve && !initialReserveDone) destination = new Tile(2273, 5356);
        else destination = initialReserveDone ? OPENING_READY : RESERVE;
        if (!here.equals(destination) && grid.paths(here, 40).distance(destination) >= 0) openingMove(f, destination, out);
        status = initialReserveDone ? "Opening: ready behind the north pillar" : "Opening: preparing the north-pillar restore reserve";
        return true;
    }

    private void openingMove(InfernoSnapshot f, Tile tile, List<JsonObject> out)
    {
        if (manualMovement || tile.x == f.x && tile.y == f.y) return;
        if (!tile.equals(moveTarget) || !f.moving && f.tick - lastMove >= 2)
        {
            out.add(command("movement.tile", "x", tile.x, "y", tile.y, "plane", f.plane));
            moveTarget = tile; lastMove = f.tick;
        }
    }

    private void prearmIce(InfernoSnapshot f, int x, int y, List<JsonObject> out)
    {
        if (f.tick - lastArmIce >= 1)
        { out.add(command("magic.armIce", "hoverX", x, "hoverY", y, "holdTicks", 5)); lastArmIce = f.tick; }
    }

    private static boolean reservePresent(InfernoSnapshot f)
    {
        for (JsonElement element : f.ground) if (element.isJsonObject())
        {
            JsonObject item = element.getAsJsonObject();
            if (InfernoSnapshot.integer(item, "tx", -1) == RESERVE.x
                && InfernoSnapshot.integer(item, "ty", -1) == RESERVE.y
                && InfernoSnapshot.string(item, "name", "").toLowerCase(java.util.Locale.ROOT).startsWith("super restore")) return true;
        }
        return false;
    }

    private boolean reclaimReserve(InfernoSnapshot f, InfernoCollision grid, List<JsonObject> out)
    {
        if (wave < 30 || !canTakeSupply(f) || !grid.available()) return false;
        if (!reservePresent(f)) return false;
        Tile here = new Tile(f.x, f.y);
        // Manual movement: no walk to the reserve, so it is taken only once the player stands on it.
        if (manualMovement && !here.equals(RESERVE)) return false;
        if (grid.paths(here, 40).distance(RESERVE) < 0) return false;
        status = "Returning for the initial restore reserve";
        if (!here.equals(RESERVE)) move(f, RESERVE, out);
        else if (f.tick - lastLoot >= 3)
        { out.add(command("ground.take", "name", "Super restore", "radius", 2)); lastLoot = f.tick; }
        return true;
    }

    /** A clear scene also permits recovery when the local activity attaches mid-run. */
    private boolean recoverSwapSpace(InfernoSnapshot f, InfernoCollision grid, List<JsonObject> out)
    {
        if (!needsSwapSpace(f) || !f.raw.has("inventory") || !f.raw.has("equipment")) return false;
        if (inventorySpace.canDropVial(f))
        {
            JsonObject drop = inventorySpace.drop(f, false);
            if (drop != null) out.add(drop);
            status = "Preparing weapon space: " + inventorySpace.status();
            return true;
        }
        if (!grid.available())
        { status = "Preparing weapon space: waiting for arena collision"; return true; }
        Tile here = new Tile(f.x, f.y);
        if (here.equals(RESERVE))
        {
            JsonObject drop = inventorySpace.drop(f, true);
            if (drop != null) out.add(drop);
            status = "Preparing weapon space: " + inventorySpace.status();
            return true;
        }
        // Manual movement: the restore is dropped only once the player stands on the reserve tile.
        if (manualMovement) return false;
        if (inventorySpace.reserveBlocked())
        { status = "Preparing weapon space: " + inventorySpace.status(); return true; }
        List<Tile> path = grid.paths(here, 40).to(RESERVE);
        if (path.isEmpty())
        { status = "Preparing weapon space: safe restore reserve is unreachable"; return true; }
        move(f, path.get(Math.min(path.size() - 1, 8)), out);
        status = "Preparing weapon space at the north-pillar restore reserve";
        return true;
    }

    private boolean hasTwoHandedSwitch()
    {
        return loadout.available(Mode.BLOWPIPE)
            || loadout.available(Mode.LONG_RANGE) && loadout.twoHanded(Mode.LONG_RANGE);
    }

    private boolean needsSwapSpace(InfernoSnapshot f)
    { return hasTwoHandedSwitch() && fullShieldSwap(f); }

    /** Taking loot must leave the extra slot needed to put away a worn shield. */
    private boolean canTakeSupply(InfernoSnapshot f)
    {
        int keepFree = hasTwoHandedSwitch() && f.weapon() != null && f.equipment.stream().anyMatch(i -> i.slot == 5) ? 1 : 0;
        return f.inventory.size() + keepFree < 28;
    }

    private static void protection(InfernoSnapshot f, Guard desired, List<JsonObject> out)
    {
        // Protection prayers are mutually exclusive in game. Enabling the new guard also
        // removes the old one; a second toggle can race the first varbit receipt.
        if (desired != Guard.NONE)
        {
            if (!f.prayers.contains(desired.prayer)) out.add(command("prayer.set", "prayer", desired.prayer, "on", true));
            else out.add(command("prayer.flick", "prayer", desired.prayer));
            return;
        }
        for (Guard guard : Guard.values()) if (guard != Guard.NONE && f.prayers.contains(guard.prayer))
        {
            JsonObject release = command("prayer.set", "prayer", guard.prayer, "on", false);
            release.addProperty("deadlineMs", 350); out.add(release);
        }
    }

    private static void offense(InfernoSnapshot f, String desired, List<JsonObject> out)
    {
        if (desired != null && !desired.isEmpty() && !f.prayers.contains(desired))
            out.add(command("prayer.set", "prayer", desired, "on", true));
        for (String prayer : OFFENSE) if (!prayer.equals(desired) && f.prayers.contains(prayer))
            out.add(command("prayer.set", "prayer", prayer, "on", false));
    }

    private static Npc first(InfernoSnapshot f, Role role)
    { for (Npc n : f.npcs) if (n.alive() && n.role == role) return n; return null; }

    private double health(Npc n)
    {
        // An unrendered bar is unknown, not a reset to full HP. Zuk's bar is
        // absent for hundreds of gold cleanup frames while another NPC is targeted.
        if (n.role == Role.ZUK && n.index == knownZukIndex) return knownZukHealth;
        return n.hpRatio < 0 || n.hpScale <= 0 ? 1.0 : (double) n.hpRatio / n.hpScale;
    }
    private static int clamp(int n, int min, int max) { return Math.min(max, Math.max(min, n)); }
    private static int number(Object value, int fallback)
    {
        try { return value instanceof Number ? ((Number) value).intValue() : value == null ? fallback : Integer.parseInt(value.toString()); }
        catch (RuntimeException ignored) { return fallback; }
    }

    static JsonObject command(String op, Object... pairs)
    {
        JsonObject message = new JsonObject(), args = new JsonObject(); message.addProperty("op", op);
        message.addProperty("tick", 0); message.addProperty("deadlineMs", 0);
        for (int i = 0; i + 1 < pairs.length; i += 2)
        {
            String key = pairs[i].toString(); Object value = pairs[i + 1];
            if (value instanceof Number) args.addProperty(key, (Number) value);
            else if (value instanceof Boolean) args.addProperty(key, (Boolean) value);
            else args.addProperty(key, String.valueOf(value));
        }
        message.add("args", args); return message;
    }
}
