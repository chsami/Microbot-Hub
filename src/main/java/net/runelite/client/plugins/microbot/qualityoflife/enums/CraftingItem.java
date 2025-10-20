package net.runelite.client.plugins.microbot.qualityoflife.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CraftingItem {
    VAMBRACES("Vambraces", '2', "vambraces", 1),
    CHAPS("Chaps", '3', "chaps", 2),
    BODY("Body", '1', "body", 3),
    SHIELD("Shield", '4', "shield", 2);

    private final String name;
    private final char option;
    private final String containsInventoryName;
    private final int amountRequired;

    @Override
    public String toString() {
        return name;
    }

}