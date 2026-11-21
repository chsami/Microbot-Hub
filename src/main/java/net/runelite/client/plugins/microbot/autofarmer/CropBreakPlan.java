package net.runelite.client.plugins.microbot.autofarmer;
import java.util.*;
/** Select the earliest enabled patch; the saved crop deadline is never extended for travel. */
final class CropBreakPlan {
    static HerbPatch next(Collection<HerbPatch> patches,PatchLedger ledger) {
        return patches.stream().filter(HerbPatch::isEnabled)
            .min(Comparator.comparingLong((HerbPatch p)->ledger.checkAt(p.getRegionName())).thenComparing(HerbPatch::getRegionName)).orElse(null);
    }
    static boolean shouldLogout(long now,long deadline,boolean enabled) {return enabled && deadline-now>30000L;}
}
