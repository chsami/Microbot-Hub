package net.runelite.client.plugins.microbot.colosseumprayer;

import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

/**
 * Resolved demand for one protection overhead from a single threat class.
 */
final class ColosseumPrayerDemand {
    private final ColosseumPrayerThreat threat;
    private final Rs2PrayerEnum protection;

    ColosseumPrayerDemand(ColosseumPrayerThreat threat, Rs2PrayerEnum protection) {
        this.threat = threat;
        this.protection = protection;
    }

    ColosseumPrayerThreat threat() {
        return threat;
    }

    Rs2PrayerEnum protection() {
        return protection;
    }
}
