package net.runelite.client.plugins.microbot.slayer;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Looting style options for the slayer plugin.
 */
@Getter
@RequiredArgsConstructor
public enum LootStyle {
    MIXED("Mixed", "Loot by both item list and GE price"),
    ITEM_LIST("Item List", "Only loot items from the custom list"),
    GE_PRICE_RANGE("GE Price Range", "Only loot items within price range");

    private final String name;
    private final String description;

    @Override
    public String toString() {
        return name;
    }
}
