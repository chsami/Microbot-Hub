package net.runelite.client.plugins.microbot.colosseumprayer;

/**
 * Threat sources for arbitration. Lower rank number wins same-tick conflicts.
 * Baseline priority: manticore → javelin → serpent shaman → jaguar → shockwave.
 * When javelin and serpent shaman both demand different prayers, the script alternates per tick instead of arbiter ranking.
 */
enum ColosseumPrayerThreat {
    MANTICORE(1),
    JAVELIN_COLOSSUS(2),
    SERPENT_SHAMAN(3),
    JAGUAR_WARRIOR(4),
    SHOCKWAVE_COLOSSUS(5);

    private final int arbiterRank;

    ColosseumPrayerThreat(int arbiterRank) {
        this.arbiterRank = arbiterRank;
    }

    int arbiterRank() {
        return arbiterRank;
    }
}
