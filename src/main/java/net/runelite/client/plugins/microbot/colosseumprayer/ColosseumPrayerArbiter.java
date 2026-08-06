package net.runelite.client.plugins.microbot.colosseumprayer;

import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

import javax.annotation.Nullable;
import java.util.List;

final class ColosseumPrayerArbiter {

    private ColosseumPrayerArbiter() {
    }

    /**
     * If multiple prayers are demanded the same tick, the threat with the lowest {@link ColosseumPrayerThreat#arbiterRank()}
     * wins. Javelin + serpent shaman pairs are resolved in {@link net.runelite.client.plugins.microbot.colosseumprayer.ColosseumPrayerScript}
     * by tick alternation, not here. If tied on rank, earliest entry in {@code demands} wins.
     */
    @Nullable
    static Rs2PrayerEnum resolve(List<ColosseumPrayerDemand> demands) {
        if (demands.isEmpty()) {
            return null;
        }
        ColosseumPrayerDemand best = null;
        for (ColosseumPrayerDemand candidate : demands) {
            if (best == null) {
                best = candidate;
                continue;
            }
            if (beats(candidate, best)) {
                best = candidate;
            }
        }
        if (best == null) {
            return null;
        }
        return best.protection();
    }

    private static boolean beats(ColosseumPrayerDemand candidate, ColosseumPrayerDemand incumbent) {
        int c = candidate.threat().arbiterRank();
        int i = incumbent.threat().arbiterRank();
        if (c < i) {
            return true;
        }
        return false;
    }
}
