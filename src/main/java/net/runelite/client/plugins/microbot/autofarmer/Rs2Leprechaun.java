package net.runelite.client.plugins.microbot.autofarmer;
import net.runelite.client.plugins.microbot.Microbot;

import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;

/**
 * Shared utility for interacting with the Tool Leprechaun's exchange interface.
 * Included in every plugin JAR via the build system, alongside PluginConstants.
 */
public final class Rs2Leprechaun {

    private static final int EXCHANGE_GROUP = 125;
    private static final int EXCHANGE_ROOT_CHILD = 0;
    private static final int REMOVE_ONE_MODE = 0;
    private static final Map<Integer, Integer> COMPOST_WIDGET_CHILDREN = new HashMap<>();

    static {
        COMPOST_WIDGET_CHILDREN.put(ItemID.BUCKET_COMPOST, 17);
        COMPOST_WIDGET_CHILDREN.put(ItemID.BUCKET_SUPERCOMPOST, 18);
        COMPOST_WIDGET_CHILDREN.put(ItemID.BUCKET_ULTRACOMPOST, 19);
    }

    private Rs2Leprechaun() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static boolean isExchangeOpen() {
        return Rs2Widget.isWidgetVisible(EXCHANGE_GROUP, EXCHANGE_ROOT_CHILD);
    }

    public static boolean openExchange() {
        Rs2NpcModel leprechaun = Rs2Npc.getNpc("Tool Leprechaun");
        if (leprechaun == null) {
            Microbot.log("Tool Leprechaun not found nearby.");
            return false;
        }
        Rs2Npc.interact(leprechaun, "Exchange");
        Global.sleepUntil(Rs2Leprechaun::isExchangeOpen, 5000);
        return isExchangeOpen();
    }

    public static void closeExchange() {
        if (isExchangeOpen()) {
            Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
            Global.sleepUntil(() -> !isExchangeOpen(), 2000);
        }
    }

    /**
     * Withdraws one compost of the given type from the nearest Tool Leprechaun.
     * Opens the exchange interface, clicks the compost widget (Remove-1), then closes.
     *
     * @param compostItemId one of ItemID.BUCKET_COMPOST, BUCKET_SUPERCOMPOST, or BUCKET_ULTRACOMPOST
     * @return true if the compost appeared in inventory
     */
    public static boolean depositNotedCompost(int compostItemId) {
        int noted = FarmingSupplies.notedId(compostItemId);
        if (noted < 0 || !Rs2Inventory.hasItem(noted)) return true;
        int before = Rs2Inventory.itemQuantity(noted);
        int storedBefore = FarmingSupplies.stored(compostItemId);
        if (!isExchangeOpen() && !openExchange()) return false;
        int sideWidget = compostItemId == ItemID.BUCKET_COMPOST ? 8257546
                : compostItemId == ItemID.BUCKET_SUPERCOMPOST ? 8257547 : 8257548;
        try {
            if (!Rs2Widget.clickWidget(InterfaceID.FarmingTools.QUANTITY_ALL)) return false;
            Global.sleep(350, 600);
            if (!Rs2Widget.clickWidget(sideWidget)) return false;
            return Global.sleepUntil(() -> Rs2Inventory.itemQuantity(noted) < before
                    && FarmingSupplies.stored(compostItemId) > storedBefore, 6000);
        } finally {
            ensureRemoveOne();
            closeExchange();
        }
    }

    public static boolean withdrawCompost(int compostItemId) {
        Integer childId = COMPOST_WIDGET_CHILDREN.get(compostItemId);
        if (childId == null) {
            Microbot.log("Unsupported compost item ID for leprechaun withdrawal: " + compostItemId);
            return false;
        }

        if (!isExchangeOpen() && !openExchange()) {
            return false;
        }
        try {
            if (!ensureRemoveOne()) {
                Microbot.log("Could not confirm quantity 1 in the Tool Leprechaun exchange.");
                return false;
            }

            Widget compostWidget = Rs2Widget.getWidget(EXCHANGE_GROUP, childId);
            if (compostWidget == null) {
                Microbot.log("Compost widget not found in leprechaun interface.");
                return false;
            }

            int before = Rs2Inventory.itemQuantity(compostItemId);
            return Rs2Widget.clickWidget(compostWidget)
                    && Global.sleepUntil(() -> Rs2Inventory.itemQuantity(compostItemId) > before, 3000);
        } finally {
            closeExchange();
        }
    }

    private static boolean ensureRemoveOne() {
        if (!isExchangeOpen()) return false;
        if (isRemoveOneSelected()) return true;
        return Rs2Widget.clickWidget(InterfaceID.FarmingTools.QUANTITY_1)
                && Global.sleepUntil(Rs2Leprechaun::isRemoveOneSelected, 2000);
    }

    private static boolean isRemoveOneSelected() {
        return Microbot.getVarbitValue(VarbitID.FARMING_TOOLS_SELECTEDQUANTITY) == REMOVE_ONE_MODE;
    }
}
