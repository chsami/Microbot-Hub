package net.runelite.client.plugins.microbot.droslayer;

import java.util.List;
import java.util.Locale;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.droslayer.walker.WildernessCombatEscape;

/** Only supplies relevant to the newly selected task survive base setup cleanup. */
final class DroSlayerBankSupplies {
    private DroSlayerBankSupplies() { }

    static boolean isAntifire(String name) {
        return name != null && name.trim().toLowerCase(Locale.ROOT)
                .matches("(?:extended )?(?:super )?antifire(?: mix)?\\([1-4]\\)");
    }

    static boolean needsAntifire(List<DroSlayerTaskRequirements.Requirement> requirements) {
        return requirements.stream().anyMatch(row -> !row.isEquipped()
                && java.util.Arrays.stream(row.getAcceptedNames()).anyMatch(DroSlayerBankSupplies::isAntifire));
    }

    static boolean isGlory(String name) {
        return name != null && name.toLowerCase(Locale.ROOT).trim()
                .matches("amulet of glory(?:\\(t\\))?(?:\\([0-6]\\))?");
    }

    private static int gloryCharges(String name) {
        if (!isGlory(name) || !name.matches(".*\\([1-6]\\)$")) return 0;
        return name.charAt(name.length() - 2) - '0';
    }

    static Rs2ItemModel chargedGlory(List<Rs2ItemModel> items) {
        return items.stream().filter(item -> item != null && !item.isNoted() && item.getQuantity() > 0
                && gloryCharges(item.getName()) > 0)
                .max(Comparator.comparingInt(item -> gloryCharges(item.getName()))).orElse(null);
    }

    /** Deposit all spent/noted glories and all but one of the best carried charged variant. */
    static Map<Integer,Integer> excessGlories(List<Rs2ItemModel> items) {
        Map<Integer,Integer> deposits = new LinkedHashMap<>();
        for (Rs2ItemModel item : items) {
            if (item != null && isGlory(item.getName()) && item.getQuantity() > 0)
                deposits.merge(item.getId(),item.getQuantity(),Integer::sum);
        }
        Rs2ItemModel keep = chargedGlory(items);
        if (keep != null) deposits.computeIfPresent(keep.getId(),(id,total) -> total > 1 ? total - 1 : null);
        return deposits;
    }

    static String chargedJewelryFamily(String name) {
        if (name == null) return null;
        String lower = name.trim().toLowerCase(Locale.ROOT);
        if (!lower.matches("(?:ring of dueling|burning amulet|ring of wealth|amulet of glory(?:\\(t\\))?|skills necklace|combat bracelet|games necklace|necklace of passage|digsite pendant)\\s*\\([1-8]\\)")) return null;
        return lower.substring(0, lower.lastIndexOf('(')).trim();
    }

    static boolean keep(String name, boolean wilderness, boolean cannon, String bracelet,
                        List<DroSlayerTaskRequirements.Requirement> requirements) {
        if (name == null) return false;
        if (DroSlayerCannonPolicy.isSupply(name)) return cannon;
        if (name.equalsIgnoreCase(DroSlayerBracelets.SLAUGHTER)
                || name.equalsIgnoreCase(DroSlayerBracelets.EXPEDITIOUS)) return name.equalsIgnoreCase(bracelet);
        String lower = name.toLowerCase(Locale.ROOT);
        if (wilderness && (WildernessCombatEscape.isLevel30Escape(name)
                || lower.matches("burning amulet\\([1-5]\\)"))) return true;
        if (!wilderness && lower.matches("ring of dueling\\([2-8]\\)")) return true;
        for (DroSlayerTaskRequirements.Requirement requirement : requirements) {
            for (String accepted : requirement.getAcceptedNames()) {
                if (lower.contains(accepted.toLowerCase(Locale.ROOT))) return true;
            }
        }
        return false;
    }
}
