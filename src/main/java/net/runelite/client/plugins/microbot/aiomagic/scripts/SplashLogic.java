package net.runelite.client.plugins.microbot.aiomagic.scripts;

import net.runelite.client.plugins.microbot.util.magic.Rs2CombatSpells;
import net.runelite.client.plugins.microbot.util.magic.Runes;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Pure (client-free) decision logic for splashing: spell selection, rune maths, AFK timing and NPC filtering.
 * Rune maps use {@link Integer#MAX_VALUE} to mean "unlimited" (provided by a staff).
 */
public final class SplashLogic {
    /** Magic attack bonus at or below which splashing is guaranteed against low-defence targets. */
    public static final int SPLASH_SAFE_MAGIC_BONUS = -65;
    public static final long AFK_MIN_MS = 15L * 60_000L;
    public static final long AFK_MAX_MS = 19L * 60_000L;
    public static final int MAX_TARGET_DISTANCE = 10;
    public static final int MAX_DRIFT_DISTANCE = 8;
    public static final int DEFAULT_NPC_MAX_COMBAT = 10;

    /** Standard-book combat spells, strike -> bolt -> blast -> wave -> surge. */
    public static final List<Rs2CombatSpells> AUTO_ORDER = List.of(
            Rs2CombatSpells.WIND_STRIKE, Rs2CombatSpells.WATER_STRIKE, Rs2CombatSpells.EARTH_STRIKE, Rs2CombatSpells.FIRE_STRIKE,
            Rs2CombatSpells.WIND_BOLT, Rs2CombatSpells.WATER_BOLT, Rs2CombatSpells.EARTH_BOLT, Rs2CombatSpells.FIRE_BOLT,
            Rs2CombatSpells.WIND_BLAST, Rs2CombatSpells.WATER_BLAST, Rs2CombatSpells.EARTH_BLAST, Rs2CombatSpells.FIRE_BLAST,
            Rs2CombatSpells.WIND_WAVE, Rs2CombatSpells.WATER_WAVE, Rs2CombatSpells.EARTH_WAVE, Rs2CombatSpells.FIRE_WAVE,
            Rs2CombatSpells.WIND_SURGE, Rs2CombatSpells.WATER_SURGE, Rs2CombatSpells.EARTH_SURGE, Rs2CombatSpells.FIRE_SURGE);

    /** Names that are safe, non-aggressive splash targets when the user did not configure an NPC. */
    private static final Set<String> SAFE_DEFAULT_NPCS = Set.of(
            "chicken", "rat", "giant rat", "man", "woman", "cow", "cow calf", "goblin", "farmer", "imp");

    private SplashLogic() {
    }

    // ---- Spell selection ----

    /** Number of casts the given supply allows (unlimited runes ignored); Integer.MAX_VALUE if no limiting rune. */
    public static int castsAvailable(Map<Runes, Integer> required, Map<Runes, Integer> available) {
        long casts = Integer.MAX_VALUE;
        for (Map.Entry<Runes, Integer> e : required.entrySet()) {
            if (e.getValue() <= 0) continue;
            int have = available.getOrDefault(e.getKey(), 0);
            if (have == Integer.MAX_VALUE) continue;
            casts = Math.min(casts, have / e.getValue());
        }
        return (int) casts;
    }

    /**
     * Highest spell (by Magic level requirement) in {@link #AUTO_ORDER} whose level is met and for which
     * {@code available} covers at least {@code minCasts} casts. Null if nothing qualifies.
     */
    public static Rs2CombatSpells bestSpell(int magicLevel, Map<Runes, Integer> available, int minCasts) {
        Rs2CombatSpells best = null;
        for (Rs2CombatSpells spell : AUTO_ORDER) {
            if (spell.getRequiredLevel() > magicLevel) continue;
            if (castsAvailable(spell.getRequiredRunes(), available) < Math.max(1, minCasts)) continue;
            if (best == null || spell.getRequiredLevel() >= best.getRequiredLevel()) best = spell;
        }
        return best;
    }

    // ---- Rune maths ----

    /** Runes to withdraw so that {@code casts} casts are covered, after subtracting what is already carried. */
    public static Map<Runes, Integer> runesToWithdraw(Map<Runes, Integer> required, int casts, Map<Runes, Integer> carried) {
        Map<Runes, Integer> out = new EnumMap<>(Runes.class);
        for (Map.Entry<Runes, Integer> e : required.entrySet()) {
            int have = carried.getOrDefault(e.getKey(), 0);
            if (have == Integer.MAX_VALUE) continue;
            long need = (long) e.getValue() * Math.max(0, casts) - have;
            if (need > 0) out.put(e.getKey(), (int) Math.min(need, Integer.MAX_VALUE));
        }
        return out;
    }

    public static long estimateCost(Map<Runes, Integer> runes, Map<Runes, Integer> pricePerRune) {
        long total = 0;
        for (Map.Entry<Runes, Integer> e : runes.entrySet()) {
            total += (long) e.getValue() * pricePerRune.getOrDefault(e.getKey(), 0);
        }
        return total;
    }

    /** Largest cast count (<= wantedCasts) whose missing runes cost at most {@code maxGp}. */
    public static int affordableCasts(Map<Runes, Integer> required, int wantedCasts, Map<Runes, Integer> carried,
                                      Map<Runes, Integer> pricePerRune, long maxGp) {
        int lo = 0, hi = Math.max(0, wantedCasts);
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (estimateCost(runesToWithdraw(required, mid, carried), pricePerRune) <= maxGp) lo = mid;
            else hi = mid - 1;
        }
        return lo;
    }

    /** Runes a staff gives for free, plus their base runes (combo runes count as both). */
    public static Map<Runes, Integer> unlimited(Iterable<Runes> staffRunes) {
        Map<Runes, Integer> out = new HashMap<>();
        for (Runes r : staffRunes) {
            out.put(r, Integer.MAX_VALUE);
            for (Runes base : r.getBaseRunes()) out.put(base, Integer.MAX_VALUE);
        }
        return out;
    }

    /** Merge two supplies, saturating at Integer.MAX_VALUE. */
    public static Map<Runes, Integer> merge(Map<Runes, Integer> a, Map<Runes, Integer> b) {
        Map<Runes, Integer> out = new HashMap<>(a);
        b.forEach((k, v) -> out.merge(k, v, (x, y) -> (int) Math.min((long) x + y, Integer.MAX_VALUE)));
        return out;
    }

    public static String runeItemName(Runes rune) {
        String n = rune.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(n.charAt(0)) + n.substring(1) + " rune";
    }

    // ---- Splash safety ----

    public static boolean isSplashSafe(int totalMagicBonus) {
        return totalMagicBonus <= SPLASH_SAFE_MAGIC_BONUS;
    }

    /** Whether swapping the weapon for a staff keeps the total magic bonus splash-safe. */
    public static boolean staffKeepsSplashSafe(int totalBonus, int currentWeaponBonus, int staffBonus) {
        return isSplashSafe(totalBonus - currentWeaponBonus + staffBonus);
    }

    // ---- AFK timing ----

    public static long nextAfkIntervalMs(Random random) {
        return AFK_MIN_MS + (long) (random.nextDouble() * (AFK_MAX_MS - AFK_MIN_MS));
    }

    public static boolean isAfkClickDue(long nowMs, long lastClickMs, long intervalMs) {
        return nowMs - lastClickMs >= intervalMs;
    }

    // ---- Position / target rules ----

    public static boolean isTargetTooFar(int distance) {
        return distance > MAX_TARGET_DISTANCE;
    }

    public static boolean hasDrifted(int distanceFromStart) {
        return distanceFromStart > MAX_DRIFT_DISTANCE;
    }

    public static boolean isSafeDefaultNpc(String name, int combatLevel) {
        return name != null && combatLevel >= 0 && combatLevel <= DEFAULT_NPC_MAX_COMBAT
                && SAFE_DEFAULT_NPCS.contains(name.trim().toLowerCase(Locale.ROOT));
    }

    public static List<String> describe(Map<Runes, Integer> runes) {
        List<String> out = new ArrayList<>();
        runes.forEach((r, q) -> out.add(q + " " + r.name().toLowerCase(Locale.ROOT)));
        return out;
    }
}
