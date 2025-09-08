package net.runelite.client.plugins.microbot.autosmelt.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.runelite.api.gameval.ItemID;

import java.awt.event.KeyEvent;

@Getter
@AllArgsConstructor
public enum BarType {
    BRONZE("Bronze bar", new int[]{ItemID.COPPER_ORE, ItemID.TIN_ORE}, new int[]{14, 14}, ItemID.BRONZE_BAR, 1, KeyEvent.VK_SPACE),
    IRON("Iron bar", new int[]{ItemID.IRON_ORE}, new int[]{28}, ItemID.IRON_BAR, 15, KeyEvent.VK_1),
    SILVER("Silver bar", new int[]{ItemID.SILVER_ORE}, new int[]{28}, ItemID.SILVER_BAR, 20, KeyEvent.VK_2),
    STEEL("Steel bar", new int[]{ItemID.IRON_ORE, ItemID.COAL}, new int[]{9, 19}, ItemID.STEEL_BAR, 30, KeyEvent.VK_3),
    GOLD("Gold bar", new int[]{ItemID.GOLD_ORE}, new int[]{28}, ItemID.GOLD_BAR, 40, KeyEvent.VK_4),
    MITHRIL("Mithril bar", new int[]{ItemID.MITHRIL_ORE, ItemID.COAL}, new int[]{5, 23}, ItemID.MITHRIL_BAR, 50, KeyEvent.VK_5),
    ADAMANTITE("Adamantite bar", new int[]{ItemID.ADAMANTITE_ORE, ItemID.COAL}, new int[]{3, 25}, ItemID.ADAMANTITE_BAR, 70, KeyEvent.VK_6),
    RUNITE("Runite bar", new int[]{ItemID.RUNITE_ORE, ItemID.COAL}, new int[]{2, 26}, ItemID.RUNITE_BAR, 85, KeyEvent.VK_7);

    private final String displayName;
    private final int[] requiredItems;
    private final int[] requiredQuantities;
    private final int productId;
    private final int requiredLevel;
    private final int keyboardKey;

    @Override
    public String toString() {
        return displayName;
    }

    public int getKey() {
        return keyboardKey;
    }
}
