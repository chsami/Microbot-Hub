package net.runelite.client.plugins.microbot.bankseller;

import org.junit.jupiter.api.Test;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Live-probe-derived widget fixtures; never starts or controls a game client. */
public final class BankSellerStartingWidgetsTest {
    // Independent values recorded in the live 2.6.26 probe, in 387:15..25 order.
    private static final int[] EMPTY_SPRITES = {156, 157, 158, 159, 161, 162, 163, 164, 165, 160, 166};
    private static final int TILE_SPRITE = 170;

    @Test
    void realContainerReadbackPreservesKnownAndUnknownData() {
        check(BankSellerStartingWidgets.containerIds(null) == null, "Absent container remains unknown");
        check(BankSellerStartingWidgets.containerIds(container(null)) == null, "Malformed item array remains unknown");
        List<Integer> empty = BankSellerStartingWidgets.containerIds(container(new Item[0]));
        check(empty != null && empty.isEmpty(), "A real explicitly empty container is valid");
        List<Integer> ids = BankSellerStartingWidgets.containerIds(container(new Item[]{
                new Item(-1, 0), new Item(4151, 1), new Item(5319, 100), new Item(565, 2_000)}));
        check(ids.size() == 4, "Container readback preserves all positions");
        check(ids.get(0) == -1, "Container explicit empty ID is preserved");
        check(ids.get(1) == 4151, "Container gear ID is preserved");
        check(ids.get(2) == 5319, "Container noted-item ID is preserved for canonical protection");
        check(ids.get(3) == 565, "Container stackable-item ID is preserved");
        check(BankSellerStartingWidgets.containerIds(container(new Item[]{null})).get(0) == null,
                "Unknown container entries are not silently converted to empty slots");
    }

    @Test
    void inventorySlotsRecognizeOnlyVerifiedEmptyGraphics() {
        check(BankSellerStartingWidgets.inventorySlotItemId(null) == null, "Missing inventory slot is unknown");
        MutableWidget wrongType = emptyInventorySlot();
        wrongType.type = WidgetType.LAYER;
        check(BankSellerStartingWidgets.inventorySlotItemId(wrongType.widget) == null,
                "A non-graphic inventory slot is unknown");
        MutableWidget slot = emptyInventorySlot();
        check(Objects.equals(BankSellerStartingWidgets.inventorySlotItemId(slot.widget), -1),
                "Hidden BLANKOBJECT with quantity one is the verified empty slot");
        check(ItemID.BLANKOBJECT == 6512, "Fixture BLANKOBJECT matches the probed item ID");
        slot.quantity = 0;
        check(BankSellerStartingWidgets.inventorySlotItemId(slot.widget) == null,
                "Hidden blank graphic with quantity zero is not verified empty");
        slot.quantity = 2;
        check(BankSellerStartingWidgets.inventorySlotItemId(slot.widget) == null,
                "Hidden blank graphic with quantity two is not verified empty");
        slot.quantity = 1;
        slot.hidden = false;
        check(BankSellerStartingWidgets.inventorySlotItemId(slot.widget) == null,
                "A visible dummy item is not accepted as empty or real inventory");
        slot.itemId = 4151;
        slot.hidden = true;
        check(BankSellerStartingWidgets.inventorySlotItemId(slot.widget) == null,
                "A hidden real item must never be interpreted as empty");
        slot.hidden = false;
        check(Objects.equals(BankSellerStartingWidgets.inventorySlotItemId(slot.widget), 4151),
                "A visible real inventory item is preserved");
        slot.itemId = 5319;
        slot.quantity = 500;
        check(Objects.equals(BankSellerStartingWidgets.inventorySlotItemId(slot.widget), 5319),
                "Visible noted stack identity is preserved");
        slot.itemId = 995;
        check(Objects.equals(BankSellerStartingWidgets.inventorySlotItemId(slot.widget), 995),
                "Visible starting coins remain represented in the protection snapshot");
        slot.itemId = 4151;
        slot.quantity = 0;
        check(BankSellerStartingWidgets.inventorySlotItemId(slot.widget) == null,
                "A real item with missing quantity is unknown");
        slot.quantity = -1;
        check(BankSellerStartingWidgets.inventorySlotItemId(slot.widget) == null,
                "A real item with negative quantity is unknown");
        slot.itemId = -1;
        slot.quantity = 0;
        check(BankSellerStartingWidgets.inventorySlotItemId(slot.widget) == null,
                "A generic visible -1 graphic is not the verified inventory empty layout");
        slot.hidden = true;
        check(BankSellerStartingWidgets.inventorySlotItemId(slot.widget) == null,
                "A generic hidden -1 graphic is not the verified blank-object layout");
        slot.hidden = false;
        slot.itemId = 0;
        slot.quantity = 1;
        check(BankSellerStartingWidgets.inventorySlotItemId(slot.widget) == null,
                "Zero inventory item ID cannot prove a loaded slot");
    }

    @Test
    void fullInventoryRequiresEverySlotAndVisibleRoot() {
        MutableWidget inventory = emptyInventory();
        Map<Integer, Widget> widgets = new HashMap<>();
        widgets.put(InterfaceID.Inventory.ITEMS, inventory.widget);
        Client client = client(widgets);
        List<Integer> ids = BankSellerStartingWidgets.inventoryIds(client);
        check(ids != null && ids.size() == 28, "The naked inventory fixture supplies all 28 slots");
        check(ids.stream().allMatch(id -> id == -1), "All verified blank-object graphics become explicit empty slots");
        inventory.hidden = true;
        check(BankSellerStartingWidgets.inventoryIds(client) == null, "Hidden inventory root is unknown");
        inventory.hidden = false;
        inventory.type = WidgetType.GRAPHIC;
        check(BankSellerStartingWidgets.inventoryIds(client) == null, "Wrong inventory-root type is unknown");
        inventory.type = WidgetType.LAYER;
        MutableWidget finalSlot = inventory.children[27];
        inventory.children[27] = null;
        check(BankSellerStartingWidgets.inventoryIds(client) == null, "One missing final inventory slot rejects the whole fallback");
        inventory.children[27] = finalSlot;
        inventory.children[10].quantity = 2;
        check(BankSellerStartingWidgets.inventoryIds(client) == null, "One malformed dummy quantity rejects the whole fallback");
        inventory.children[10].quantity = 1;
        inventory.children[0].itemId = 4151;
        check(BankSellerStartingWidgets.inventoryIds(client) == null, "One hidden real item rejects the whole inventory fallback");
        inventory.children[0].hidden = false;
        inventory.children[27].itemId = 5319;
        inventory.children[27].quantity = 50;
        inventory.children[27].hidden = false;
        ids = BankSellerStartingWidgets.inventoryIds(client);
        check(ids != null && ids.size() == 28, "Mixed real-item and empty-slot fallback remains complete");
        check(ids.get(0) == 4151, "First real inventory item is protected");
        check(ids.get(27) == 5319, "Last noted stack is protected");
        check(ids.get(1) == -1, "Other slots remain explicitly empty");
        widgets.clear();
        check(BankSellerStartingWidgets.inventoryIds(client) == null, "Missing inventory root remains unknown");
    }

    @Test
    void equipmentSlotsRequireTheirCompleteVerifiedLayout() {
        int emptySprite = EMPTY_SPRITES[0];
        check(BankSellerStartingWidgets.equipmentSlotItemId(null, emptySprite) == null,
                "Missing equipment tile is unknown");
        MutableWidget slot = emptyEquipmentSlot(emptySprite);
        check(Objects.equals(BankSellerStartingWidgets.equipmentSlotItemId(slot.widget, emptySprite), -1),
                "Complete background/item/placeholder layout proves the equipment slot empty");
        slot.hidden = true;
        check(BankSellerStartingWidgets.equipmentSlotItemId(slot.widget, emptySprite) == null,
                "Hidden equipment root cannot prove emptiness");
        slot.hidden = false;
        slot.type = WidgetType.GRAPHIC;
        check(BankSellerStartingWidgets.equipmentSlotItemId(slot.widget, emptySprite) == null,
                "Wrong equipment-root type cannot prove emptiness");
        slot.type = WidgetType.LAYER;
        MutableWidget background = slot.children[0];
        MutableWidget item = slot.children[1];
        MutableWidget placeholder = slot.children[2];
        slot.children[0] = null;
        check(BankSellerStartingWidgets.equipmentSlotItemId(slot.widget, emptySprite) == null,
                "Missing equipment background is unknown");
        slot.children[0] = background;
        slot.children[1] = null;
        check(BankSellerStartingWidgets.equipmentSlotItemId(slot.widget, emptySprite) == null,
                "Missing equipment item graphic is unknown");
        slot.children[1] = item;
        slot.children[2] = null;
        check(BankSellerStartingWidgets.equipmentSlotItemId(slot.widget, emptySprite) == null,
                "A background and item alone cannot prove a loaded equipment slot");
        slot.children[2] = placeholder;
        background.hidden = true;
        check(BankSellerStartingWidgets.equipmentSlotItemId(slot.widget, emptySprite) == null,
                "Hidden decorative equipment background is unknown");
        background.hidden = false;
        background.sprite = TILE_SPRITE + 1;
        check(BankSellerStartingWidgets.equipmentSlotItemId(slot.widget, emptySprite) == null,
                "Wrong decorative background sprite is unknown");
        background.sprite = TILE_SPRITE;
        background.type = WidgetType.LAYER;
        check(BankSellerStartingWidgets.equipmentSlotItemId(slot.widget, emptySprite) == null,
                "Non-graphic equipment background is unknown");
        background.type = WidgetType.GRAPHIC;
        placeholder.sprite = emptySprite + 1;
        check(BankSellerStartingWidgets.equipmentSlotItemId(slot.widget, emptySprite) == null,
                "A different slot's placeholder cannot prove this slot empty");
        placeholder.sprite = emptySprite;
        placeholder.type = WidgetType.LAYER;
        check(BankSellerStartingWidgets.equipmentSlotItemId(slot.widget, emptySprite) == null,
                "Non-graphic placeholder is unknown");
        placeholder.type = WidgetType.GRAPHIC;
        placeholder.itemId = 4151;
        check(BankSellerStartingWidgets.equipmentSlotItemId(slot.widget, emptySprite) == null,
                "A placeholder carrying an item ID is conflicting evidence");
        placeholder.itemId = -1;
        item.type = WidgetType.LAYER;
        check(BankSellerStartingWidgets.equipmentSlotItemId(slot.widget, emptySprite) == null,
                "Non-graphic equipment item child is unknown");
        item.type = WidgetType.GRAPHIC;
        item.quantity = 1;
        check(BankSellerStartingWidgets.equipmentSlotItemId(slot.widget, emptySprite) == null,
                "Empty equipment item with nonzero quantity is unknown");
        item.quantity = 0;
        item.itemId = 0;
        check(BankSellerStartingWidgets.equipmentSlotItemId(slot.widget, emptySprite) == null,
                "Zero item ID is not the verified empty equipment marker");
        item.itemId = -1;
        placeholder.hidden = true;
        check(BankSellerStartingWidgets.equipmentSlotItemId(slot.widget, emptySprite) == null,
                "An empty item with a hidden placeholder is incomplete evidence");
        MutableWidget bareTile = new MutableWidget(WidgetType.LAYER);
        bareTile.children = new MutableWidget[]{background};
        check(BankSellerStartingWidgets.equipmentSlotItemId(bareTile.widget, emptySprite) == null,
                "A bare decorative tile never proves equipment empty");
    }

    @Test
    void equipmentItemsCannotConflictWithPlaceholders() {
        int emptySprite = EMPTY_SPRITES[9];
        MutableWidget slot = emptyEquipmentSlot(emptySprite);
        MutableWidget item = slot.children[1];
        MutableWidget placeholder = slot.children[2];
        item.itemId = 2552;
        item.quantity = 1;
        check(BankSellerStartingWidgets.equipmentSlotItemId(slot.widget, emptySprite) == null,
                "Real item plus visible empty placeholder is conflicting evidence");
        placeholder.hidden = true;
        check(Objects.equals(BankSellerStartingWidgets.equipmentSlotItemId(slot.widget, emptySprite), 2552),
                "Visible ring with hidden placeholder preserves its exact charged variant");
        item.hidden = true;
        check(BankSellerStartingWidgets.equipmentSlotItemId(slot.widget, emptySprite) == null,
                "Hidden real equipment item is unknown rather than empty");
        item.hidden = false;
        item.quantity = 0;
        check(BankSellerStartingWidgets.equipmentSlotItemId(slot.widget, emptySprite) == null,
                "Real equipment item with no quantity is unknown");
        item.quantity = -1;
        check(BankSellerStartingWidgets.equipmentSlotItemId(slot.widget, emptySprite) == null,
                "Real equipment item with negative quantity is unknown");
        item.quantity = 2_000;
        item.itemId = 565;
        check(Objects.equals(BankSellerStartingWidgets.equipmentSlotItemId(slot.widget, emptySprite), 565),
                "A visible stackable equipped item retains its actual item ID");
        placeholder.sprite = -1;
        check(BankSellerStartingWidgets.equipmentSlotItemId(slot.widget, emptySprite) == null,
                "Even occupied equipment needs the expected hidden placeholder layout");
    }

    @Test
    void fullEquipmentIncludesRingAndAmmoAndRequiresEverySlot() {
        Map<Integer, Widget> widgets = new HashMap<>();
        MutableWidget[] slots = new MutableWidget[11];
        for (int index = 0; index < slots.length; index++) {
            slots[index] = emptyEquipmentSlot(EMPTY_SPRITES[index]);
            widgets.put((387 << 16) | (15 + index), slots[index].widget);
        }
        Client client = client(widgets);
        List<Integer> ids = BankSellerStartingWidgets.equipmentIds(client);
        check(ids != null && ids.size() == 11, "Naked equipment requires only the 11 real wearable tiles");
        check(ids.stream().allMatch(id -> id == -1), "All verified naked tiles become explicit empty equipment slots");
        check(!widgets.containsKey((387 << 16) | 6), "Fixture has no irrelevant body-kit tile dependency");
        equip(slots[3], 4151, 1);
        equip(slots[9], 2552, 1);
        equip(slots[10], 565, 2_000);
        ids = BankSellerStartingWidgets.equipmentIds(client);
        check(ids != null && ids.size() == 11, "Mixed real equipment and empty tiles produce a complete fallback");
        check(ids.get(3) == 4151, "Worn weapon is represented");
        check(ids.get(9) == 2552, "Worn ring is represented despite absence from appearance kit slots");
        check(ids.get(10) == 565, "Worn ammunition is represented despite absence from appearance kit slots");
        check(ids.get(0) == -1, "Unoccupied wearable tiles remain explicitly empty");
        widgets.remove((387 << 16) | 25);
        check(BankSellerStartingWidgets.equipmentIds(client) == null, "A missing ammo tile rejects the whole equipment fallback");
        widgets.put((387 << 16) | 25, slots[10].widget);
        slots[0].hidden = true;
        check(BankSellerStartingWidgets.equipmentIds(client) == null, "One hidden equipment root rejects the whole fallback");
        slots[0].hidden = false;
        slots[1].children[2].sprite = EMPTY_SPRITES[0];
        check(BankSellerStartingWidgets.equipmentIds(client) == null, "One wrong per-slot placeholder rejects the whole fallback");
        slots[1].children[2].sprite = EMPTY_SPRITES[1];
        MutableWidget originalItem = slots[5].children[1];
        slots[5].children[1] = null;
        check(BankSellerStartingWidgets.equipmentIds(client) == null, "One incomplete equipment layout rejects the whole fallback");
        slots[5].children[1] = originalItem;
        slots[9].children[1].hidden = true;
        check(BankSellerStartingWidgets.equipmentIds(client) == null, "A hidden real ring rejects the whole fallback");
        slots[9].children[1].hidden = false;
        slots[10].children[2].hidden = false;
        check(BankSellerStartingWidgets.equipmentIds(client) == null, "A real ammo stack plus empty placeholder rejects the whole fallback");
        widgets.clear();
        check(BankSellerStartingWidgets.equipmentIds(client) == null, "Completely missing equipment widgets remain unknown");
    }

    private static void equip(MutableWidget slot, int itemId, int quantity) {
        slot.children[1].itemId = itemId;
        slot.children[1].quantity = quantity;
        slot.children[1].hidden = false;
        slot.children[2].hidden = true;
    }

    private static MutableWidget emptyInventory() {
        MutableWidget inventory = new MutableWidget(WidgetType.LAYER);
        inventory.children = new MutableWidget[28];
        for (int index = 0; index < inventory.children.length; index++) {
            inventory.children[index] = emptyInventorySlot();
        }
        return inventory;
    }

    private static MutableWidget emptyInventorySlot() {
        MutableWidget slot = new MutableWidget(WidgetType.GRAPHIC);
        slot.hidden = true;
        slot.itemId = ItemID.BLANKOBJECT;
        slot.quantity = 1;
        return slot;
    }

    private static MutableWidget emptyEquipmentSlot(int emptySprite) {
        MutableWidget root = new MutableWidget(WidgetType.LAYER);
        MutableWidget background = new MutableWidget(WidgetType.GRAPHIC);
        background.sprite = TILE_SPRITE;
        MutableWidget item = new MutableWidget(WidgetType.GRAPHIC);
        MutableWidget placeholder = new MutableWidget(WidgetType.GRAPHIC);
        placeholder.sprite = emptySprite;
        root.children = new MutableWidget[]{background, item, placeholder};
        return root;
    }

    private static ItemContainer container(Item[] items) {
        return (ItemContainer) Proxy.newProxyInstance(ItemContainer.class.getClassLoader(),
                new Class<?>[]{ItemContainer.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getItems")) {
                        return items;
                    }
                    throw new AssertionError("Unexpected item-container access: " + method.getName());
                });
    }

    private static Client client(Map<Integer, Widget> widgets) {
        return (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getWidget")) {
                        int id = args.length == 1 ? (Integer) args[0]
                                : ((Integer) args[0] << 16) | (Integer) args[1];
                        return widgets.get(id);
                    }
                    throw new AssertionError("Unexpected client access: " + method.getName());
                });
    }

    private static final class MutableWidget {
        private int type;
        private boolean hidden;
        private int itemId = -1;
        private int quantity;
        private int sprite = -1;
        private MutableWidget[] children = new MutableWidget[0];
        private final Widget widget;

        private MutableWidget(int type) {
            this.type = type;
            widget = (Widget) Proxy.newProxyInstance(Widget.class.getClassLoader(), new Class<?>[]{Widget.class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "getType": return this.type;
                            case "isHidden": return hidden;
                            case "getItemId": return itemId;
                            case "getItemQuantity": return quantity;
                            case "getSpriteId": return sprite;
                            case "getChild":
                                int index = (Integer) args[0];
                                return index >= 0 && index < children.length && children[index] != null
                                        ? children[index].widget : null;
                            default: throw new AssertionError("Unexpected widget access: " + method.getName());
                        }
                    });
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
