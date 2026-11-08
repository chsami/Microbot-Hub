package net.runelite.client.plugins.microbot.aiomagic.scripts;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.List;

final class AlchItemSlot {

    private static final int MOVE_TIMEOUT_MS = 1200;

    private AlchItemSlot() {
    }

    static Rs2ItemModel resolve(List<String> alchItemNames) {
        Rs2ItemModel alchItem = alchItemNames.stream()
                .filter(Rs2Inventory::hasItem)
                .map(Rs2Inventory::get)
                .findFirst()
                .orElse(null);
        if (alchItem == null || !Rs2AntibanSettings.naturalMouse) {
            return alchItem;
        }

        int inventorySlot = Rs2Player.getSkillRequirement(Skill.MAGIC, 55) ? 12 : 4;
        Rs2ItemModel slotItem = alchItemInSlot(alchItemNames, inventorySlot);
        if (slotItem != null) {
            return slotItem;
        }

        if (Rs2Inventory.moveItemToSlot(alchItem, inventorySlot)) {
            Global.sleepUntil(() -> alchItemInSlot(alchItemNames, inventorySlot) != null, MOVE_TIMEOUT_MS);
            slotItem = alchItemInSlot(alchItemNames, inventorySlot);
        }
        return slotItem != null ? slotItem : alchItem;
    }

    private static Rs2ItemModel alchItemInSlot(List<String> alchItemNames, int slot) {
        Rs2ItemModel item = Rs2Inventory.getItemInSlot(slot);
        if (item == null || !Rs2ItemModel.matches(false, alchItemNames.toArray(new String[0])).test(item)) {
            return null;
        }
        return item;
    }
}
