package net.runelite.client.plugins.microbot.droslayer;

import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;

import java.util.Locale;

/**
 * Item rules Dro Slayer shares with Efficient Walker. They are copied here because the two are
 * separate Hub plugins, so Slayer cannot call Efficient Walker's classes directly. Keep them in step
 * with {@code WildernessCombatEscape} and {@code WebTravel} in Efficient Walker.
 */
final class DroSlayerWalkerItems {
    private DroSlayerWalkerItems() {
    }

    /** True for a teleport that works from level-30 Wilderness, matching Efficient Walker's escape. */
    static boolean isLevel30Escape(String name) {
        return escapePreference(name) != Integer.MAX_VALUE;
    }

    /** Lower is preferred; {@link Integer#MAX_VALUE} when the item is not a level-30 escape. */
    static int escapePreference(String name) {
        if (name == null) return Integer.MAX_VALUE;
        String lower = name.trim().toLowerCase(Locale.ROOT);
        if (lower.equals("royal seed pod")) return 0;
        if (lower.equals("amulet of eternal glory") || lower.matches("amulet of glory(?:\\(t\\))?\\s*\\([1-6]\\)")) return 1;
        if (lower.matches("ring of wealth\\s*\\([1-5]\\)")) return 2;
        if (lower.matches("combat bracelet\\s*\\([1-6]\\)")) return 3;
        if (lower.matches("skills necklace\\s*\\([1-6]\\)")) return 4;
        return Integer.MAX_VALUE;
    }

    /** The teleport option an escape item uses. */
    static String destinationAction(String name) {
        switch (escapePreference(name)) {
            case 0: return "Commune";
            case 1: return "Edgeville";
            case 2: return "Grand Exchange";
            case 3: return "Monastery";
            case 4: return "Fishing Guild";
            default: return "";
        }
    }

    /** True when the equipped weapon has a slash bonus, so webs can be cut. */
    static boolean hasSlashWeapon() {
        if (Microbot.getClientThread() == null || Microbot.getItemManager() == null) return false;
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            var weapon = Rs2Equipment.get(EquipmentInventorySlot.WEAPON);
            if (weapon == null) return false;
            var stats = Microbot.getItemManager().getItemStats(weapon.getId());
            return stats != null && stats.getEquipment() != null && stats.getEquipment().getAslash() > 0;
        }).orElse(false);
    }
}
