package net.runelite.client.plugins.microbot.varrockanvil.enums;

import lombok.Getter;

public enum AnvilItem {
    DAGGER("Dagger", 9, 1),
    SWORD("Sword", 10, 1),
    SCIMITAR("Scimitar", 11, 2),
    LONG_SWORD("Long sword", 12, 2),
    TWO_HAND_SWORD("2-hand sword", 13, 3),
    AXE("Axe", 14, 1),
    MACE("Mace", 15, 1),
    WARHAMMER("Warhammer", 16, 3),
    BATTLE_AXE("Battle axe", 17, 3),
    CLAWS("Claws", 18, 2),
    CHAIN_BODY("Chain body", 19, 3),
    PLATE_LEGS("Plate legs", 20, 3),
    PLATE_SKIRT("Plate skirt", 21, 3),
    PLATE_BODY("Plate body", 22, 5),
    NAILS("Nails", 23, 1),
    MEDIUM_HELM("Medium helm", 24, 1),
    FULL_HELM("Full helm", 25, 2),
    SQUARE_SHIELD("Square shield", 26, 2),
    KITE_SHIELD("Kite shield", 27, 3),
    DART_TIPS("Dart tips", 29, 1),
    ARROWTIPS("Arrowtips", 30, 1),
    KNIVES("Knives", 31, 1),
    BRONZE_WIRE("Bronze wire", 32, 1),
    OIL_LAMP("Oil lamp", 28, 1),
    IRON_SPIT("Iron spit", 32, 1),
    BULLSEYE_LAMP("Bullseye lamp", 28, 1),
    STEEL_STUDS("Studs", 32, 1),
    BOLTS("Bolts (unf)", 34, 1),
    KEEL_PARTS("Keel parts", 0, 5),
    LARGE_KEEL_PARTS("Large keel parts", 0, 5);  // consumes 5 small keel parts of the chosen tier (not raw bars)

    private final String itemName;
    @Getter
    private final int childId;
    @Getter
    private final int requiredBars;

    AnvilItem(final String _itemName, final int _childId, final int _requiredBars) {
        itemName = _itemName;
        childId = _childId;
        requiredBars = _requiredBars;
    }

    public String getName() {
        return itemName;
    }

    /**
     * Returns true if this item has a valid widget child ID for the anvil interface.
     * Items with childId=0 (keel parts, sailing items) must use name-based search instead.
     */
    public boolean hasValidChildId() {
        return childId > 0;
    }
}
