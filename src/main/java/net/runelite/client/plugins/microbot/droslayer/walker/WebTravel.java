package net.runelite.client.plugins.microbot.droslayer.walker;

import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

/** Web requirements shared by complete-route planning and execution. */
public final class WebTravel {
    private WebTravel() { }

    public static boolean isWeb(Transport transport) {
        return transport != null && "Slash".equalsIgnoreCase(transport.getAction())
            && "Web".equalsIgnoreCase(transport.getName());
    }

    public static boolean canCut() {
        if (Rs2Inventory.hasItem(946)) return true;
        return hasSlashWeapon();
    }

    static boolean canInteractFrom(WorldPoint player, Transport edge, WorldPoint object) {
        return player != null && edge.getOrigin() != null
            && player.getPlane() == edge.getOrigin().getPlane()
            && player.distanceTo2D(edge.getOrigin()) <= 3
            && (object == null || object.getPlane() == player.getPlane() && object.distanceTo2D(player) <= 1);
    }

    public static boolean hasSlashWeapon() {
        if (Microbot.getClientThread() == null || Microbot.getItemManager() == null) return false;
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            var weapon = Rs2Equipment.get(EquipmentInventorySlot.WEAPON);
            if (weapon == null) return false;
            var stats = Microbot.getItemManager().getItemStats(weapon.getId());
            return stats != null && stats.getEquipment() != null && stats.getEquipment().getAslash() > 0;
        }).orElse(false);
    }
}
