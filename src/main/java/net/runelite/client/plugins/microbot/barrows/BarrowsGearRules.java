package net.runelite.client.plugins.microbot.barrows;

import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsItem;

import java.util.Comparator;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.ToIntFunction;

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

    static <T> Comparator<T> closestToSetupRow(int setupItemId, String setupItemName,
                                              ToIntFunction<T> idOf, Function<T, String> nameOf) {
        return Comparator.<T>comparingInt(candidate -> idOf.applyAsInt(candidate) == setupItemId ? 0 : 1)
                .thenComparingInt(candidate -> setupItemName != null
                        && setupItemName.equalsIgnoreCase(nameOf.apply(candidate)) ? 0 : 1)
                .thenComparingInt(candidate -> {
                    String name = nameOf.apply(candidate);
                    return name == null ? Integer.MAX_VALUE : name.length();
                });
    }
}
