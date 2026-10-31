package net.runelite.client.plugins.microbot.barrows;

import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsItem;

import java.util.Locale;

final class BarrowsGearRules {

    private BarrowsGearRules() {
    }

    static boolean isEmptySetupRow(InventorySetupsItem item) {
        return item == null || item.getId() == -1;
    }

    static boolean isBrokenBarrowsPiece(String itemName) {
        if (itemName == null) {
            return false;
        }
        String lower = itemName.toLowerCase(Locale.ROOT);
        return lower.endsWith(" 0")
                && (lower.contains("ahrim's")
                || lower.contains("dharok's")
                || lower.contains("guthan's")
                || lower.contains("karil's")
                || lower.contains("torag's")
                || lower.contains("verac's"));
    }

    static boolean fillsSetupRow(String setupItemName, String itemName) {
        if (setupItemName == null || setupItemName.isEmpty() || itemName == null) {
            return false;
        }
        return !isBrokenBarrowsPiece(itemName)
                && itemName.toLowerCase(Locale.ROOT).contains(setupItemName.toLowerCase(Locale.ROOT));
    }
}
