package net.runelite.client.plugins.microbot.droslayer;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;

/** Wilderness supplies have the normal menu verbs, but cannot be consumed on normal travel legs. */
public final class DroSlayerWildernessSupplies {
    public static final String ICE_SACK_NAME = "Blighted ancient ice sack";
    public static final int ICE_SACK_ID = 24607;
    private static final Pattern DOSES = Pattern.compile("\\(([1-4])\\)$");
    private static final List<String> FOOD_ORDER = Arrays.asList(
            "manta ray", "dark crab", "tuna potato", "anglerfish", "shark",
            "sea turtle", "cooked karambwan", "monkfish", "swordfish", "lobster");
    private static final List<String> BLIGHTED_FOOD_ORDER = Arrays.asList(
            "blighted manta ray", "blighted anglerfish", "blighted karambwan");

    private DroSlayerWildernessSupplies() {}

    public static boolean isBlighted(String name) {
        return normalize(name).startsWith("blighted ");
    }

    /** The caller supplies live Wilderness status when consuming, and destination status when banking. */
    public static boolean canConsume(String name, boolean inWilderness) {
        return !isBlighted(name) || inWilderness;
    }

    public static boolean isFood(Rs2ItemModel item, boolean allowBlighted) {
        return item != null && item.getQuantity() > 0 && item.isFood()
                && canConsume(item.getName(), allowBlighted);
    }

    /** Prefer Wilderness supplies on Wilderness trips; ordinary supplies remain valid fallbacks. */
    public static int foodPreference(String name, boolean wildernessTask) {
        String normalized = normalize(name);
        if (wildernessTask) {
            int blightedIndex = BLIGHTED_FOOD_ORDER.indexOf(normalized);
            if (blightedIndex >= 0) return blightedIndex;
        }
        int ordinaryIndex = FOOD_ORDER.indexOf(normalized);
        return (wildernessTask ? BLIGHTED_FOOD_ORDER.size() : 0)
                + (ordinaryIndex < 0 ? FOOD_ORDER.size() : ordinaryIndex);
    }

    public static boolean isPrayerPotion(Rs2ItemModel item, boolean allowBlighted) {
        return item != null && item.getQuantity() > 0 && !item.isNoted()
                && isPrayerPotion(item.getName(), allowBlighted);
    }

    public static boolean isPrayerPotion(String name, boolean allowBlighted) {
        return prayerDoses(name, allowBlighted) > 0;
    }

    /** A blighted potion outside the Wilderness must never satisfy a usable-dose check. */
    public static int prayerDoses(String name, boolean allowBlighted) {
        String normalized = normalize(name);
        if (!canConsume(normalized, allowBlighted)) return 0;
        Matcher matcher = DOSES.matcher(normalized);
        if (!matcher.find()) return 0;
        String base = normalized.substring(0, matcher.start()).trim();
        if (!base.equals("prayer potion") && !base.equals("super restore")
                && !base.equals("sanfew serum") && !base.equals("blighted super restore")) return 0;
        return Integer.parseInt(matcher.group(1));
    }

    public static boolean isIceSpell(String spellName) {
        String normalized = normalize(spellName).replace('_', ' ');
        return normalized.equals("ice rush") || normalized.equals("ice burst")
                || normalized.equals("ice blitz") || normalized.equals("ice barrage");
    }

    public static DroSlayerTaskRequirements.Requirement prayerRequirement(int potionCount) {
        return DroSlayerTaskRequirements.Requirement.inventory(
                "Wilderness prayer supplies", "Blighted super restore(4)", potionCount,
                "Blighted super restore", "Super restore", "Prayer potion", "Sanfew serum");
    }

    /** Add only for an explicitly selected ice spell; sacks do not substitute for other spells. */
    public static DroSlayerTaskRequirements.Requirement iceSackRequirement(int casts) {
        return DroSlayerTaskRequirements.Requirement.inventory(
                "Wilderness ice spell sacks", ICE_SACK_NAME, casts, ICE_SACK_NAME);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
