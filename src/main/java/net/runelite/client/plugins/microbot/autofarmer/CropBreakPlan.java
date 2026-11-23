package net.runelite.client.plugins.microbot.autofarmer;
import java.util.*;
/** Select the earliest enabled patch; the saved crop deadline is never extended for travel. */
final class CropBreakPlan {
    static HerbPatch next(Collection<HerbPatch> patches,PatchLedger ledger) {
        return patches.stream().filter(HerbPatch::isEnabled)
            .min(Comparator.comparingLong((HerbPatch p)->ledger.checkAt(p.getRegionName())).thenComparing(HerbPatch::getRegionName)).orElse(null);
    }
    static boolean shouldLogout(long now, long deadline, boolean enabled, int thresholdMinutes) {
        return enabled && deadline - now > Math.max(0, thresholdMinutes) * 60_000L;
    }
}
