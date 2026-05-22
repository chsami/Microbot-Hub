package net.runelite.client.plugins.microbot.butterflycatcher;

import lombok.Getter;

/**
 * ButterflyType
 *
 * Defines all catchable butterflies and moths from the Hunter skill.
 * Data sourced from: https://oldschool.runescape.wiki/w/Butterfly_(Hunter)
 *
 * Level requirements:
 *   The game requires a Hunter level 10 ABOVE the net level for barehanded catching.
 *   This enum stores the NET level as the baseline; the script derives the
 *   barehanded level by adding 10.
 *
 *   Species              Net   Barehanded
 *   ─────────────────── ────  ──────────
 *   Ruby Harvest          5       15
 *   Sapphire Glacialis   25       35
 *   Snowy Knight         35       45
 *   Black Warlock        45       55
 *   Sunlight Moth        65       75
 *   Moonlight Moth       75       85
 *
 * Item IDs:
 *   Butterfly net      : 10010
 *   Magic butterfly net: 11259
 *   Butterfly jar      : 10012  (same for all species)
 */
@Getter
public enum ButterflyType {

    RUBY_HARVEST(
            "Ruby Harvest",
            new int[]{ 5525 },
            5,
            10012,
            10009
    ),
    SAPPHIRE_GLACIALIS(
            "Sapphire Glacialis",
            new int[]{ 5526 },
            25,
            10012,
            10011
    ),
    SNOWY_KNIGHT(
            "Snowy Knight",
            new int[]{ 5527 },
            35,
            10012,
            10013
    ),
    BLACK_WARLOCK(
            "Black Warlock",
            new int[]{ 5553 },
            45,
            10012,
            10010
    ),

    /**
     * Sunlight Moth — Avium Savannah south of the Hunter Guild.
     * Net: 65 | Barehanded: 75
     */
    SUNLIGHT_MOTH(
            "Sunlight Moth",
            new int[]{ 12770 },
            65,
            10012,
            28890
    ),

    /**
     * Moonlight Moth — Neypotzli / Hunter Guild basement / Tonali Cavern.
     * Net: 75 | Barehanded: 85
     * NPC IDs: 12771, 12772, 12773 (variants per location).
     */
    MOONLIGHT_MOTH(
            "Moonlight Moth",
            new int[]{ 12771, 12772, 12773 },
            75,
            10012,
            28893
    );

    // -------------------------------------------------------------------------

    /** Human-readable name shown in the config dropdown. */
    private final String displayName;

    /**
     * All NPC IDs for this creature.
     * Most species have exactly one. Moonlight Moth has three location variants.
     */
    private final int[] npcIds;

    /**
     * Hunter level required to catch with a butterfly net (or magic butterfly net).
     */
    private final int netLevelRequired;

    /** Item ID of the empty butterfly jar (always 10012). */
    private final int jarItemId;

    /** Item ID placed in the inventory after a successful jar catch. */
    private final int caughtItemId;

    // -------------------------------------------------------------------------

    ButterflyType(String displayName, int[] npcIds, int netLevelRequired,
                  int jarItemId, int caughtItemId) {
        this.displayName      = displayName;
        this.npcIds           = npcIds;
        this.netLevelRequired = netLevelRequired;
        this.jarItemId        = jarItemId;
        this.caughtItemId     = caughtItemId;
    }

    /**
     * Hunter level required to catch bare-handed (always netLevelRequired + 10).
     */
    public int getBarehandedLevelRequired() {
        return netLevelRequired + 10;
    }

    /**
     * Returns the primary NPC ID (first in the array). Used for logging.
     * The script always iterates all IDs in the array when searching.
     */
    public int getNpcId() {
        return npcIds[0];
    }

    @Override
    public String toString() {
        return displayName;
    }
}
