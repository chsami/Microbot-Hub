package net.runelite.client.plugins.microbot.bankseller;

import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.api.SpriteID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** Complete live slot readbacks for the rare case of an absent empty container. */
final class BankSellerStartingWidgets {
    private static final int[] EMPTY_EQUIPMENT_SPRITES = {
            SpriteID.EQUIPMENT_SLOT_HEAD, SpriteID.EQUIPMENT_SLOT_CAPE, SpriteID.EQUIPMENT_SLOT_NECK,
            SpriteID.EQUIPMENT_SLOT_WEAPON, SpriteID.EQUIPMENT_SLOT_TORSO, SpriteID.EQUIPMENT_SLOT_SHIELD,
            SpriteID.EQUIPMENT_SLOT_LEGS, SpriteID.EQUIPMENT_SLOT_HANDS, SpriteID.EQUIPMENT_SLOT_FEET,
            SpriteID.EQUIPMENT_SLOT_RING, SpriteID.EQUIPMENT_SLOT_AMMUNITION
    };
    private BankSellerStartingWidgets() {
    }

    /** Must be called on the client thread; a malformed container remains unknown. */
    static List<Integer> containerIds(ItemContainer container) {
        if (container == null || container.getItems() == null) {
            return null;
        }
        return Arrays.stream(container.getItems()).map(item -> item == null ? null : item.getId())
                .collect(Collectors.toList());
    }

    static List<Integer> inventoryIds(Client client) {
        Widget inventory = client.getWidget(InterfaceID.Inventory.ITEMS);
        if (inventory == null || inventory.isHidden() || inventory.getType() != WidgetType.LAYER) {
            return null;
        }
        List<Integer> ids = new ArrayList<>(28);
        for (int slot = 0; slot < 28; slot++) {
            Integer id = inventorySlotItemId(inventory.getChild(slot));
            if (id == null) {
                return null;
            }
            ids.add(id);
        }
        return ids;
    }

    static List<Integer> equipmentIds(Client client) {
        List<Integer> ids = new ArrayList<>(11);
        // Actual wearable slots only, including ring and ammo. Body-kit
        // positions 6/8/11 have no equipment tile and must not be required.
        for (int child = 15; child <= 25; child++) {
            Integer id = equipmentSlotItemId(client.getWidget(387, child), EMPTY_EQUIPMENT_SPRITES[child - 15]);
            if (id == null) {
                return null;
            }
            ids.add(id);
        }
        return ids;
    }

    static Integer inventorySlotItemId(Widget slot) {
        if (slot == null || slot.getType() != WidgetType.GRAPHIC) {
            return null;
        }
        // Verified live in 2.6.26: every empty inventory slot has a hidden
        // BLANKOBJECT graphic, not an itemId of -1. Do not protect that dummy
        // item, and do not treat arbitrary hidden/stale real items as empty.
        if (slot.isHidden()) {
            return slot.getItemId() == ItemID.BLANKOBJECT && slot.getItemQuantity() == 1 ? -1 : null;
        }
        return slot.getItemId() > 0 && slot.getItemId() != ItemID.BLANKOBJECT
                && slot.getItemQuantity() > 0 ? slot.getItemId() : null;
    }

    static Integer equipmentSlotItemId(Widget slot, int emptySprite) {
        if (slot == null || slot.isHidden() || slot.getType() != WidgetType.LAYER) {
            return null;
        }
        // Verified live: child 0 is the tile background, 1 the actual item
        // graphic and 2 this particular slot's empty placeholder. A decorative
        // background alone must never prove an uninitialised slot empty.
        Widget background = slot.getChild(0);
        Widget item = slot.getChild(1);
        Widget placeholder = slot.getChild(2);
        if (background == null || item == null || placeholder == null
                || background.isHidden() || background.getType() != WidgetType.GRAPHIC
                || background.getSpriteId() != SpriteID.EQUIPMENT_SLOT_TILE
                || item.getType() != WidgetType.GRAPHIC || placeholder.getType() != WidgetType.GRAPHIC
                || placeholder.getSpriteId() != emptySprite || placeholder.getItemId() > 0) {
            return null;
        }
        int id = item.getItemId();
        if (id > 0) {
            return !item.isHidden() && item.getItemQuantity() > 0 && placeholder.isHidden() ? id : null;
        }
        return id == -1 && item.getItemQuantity() == 0 && !placeholder.isHidden() ? -1 : null;
    }
}
