package net.runelite.client.plugins.microbot.anvilsmither.enums;

import lombok.Getter;
import net.runelite.api.Quest;

/**
 * Items selectable in the anvil interface (widget group 312).
 *
 * childId is the widget child to click. Children 9-27, 29-31, 34 and 35 are stable, named slots
 * (dagger..kiteshield, darttips, arrowheads, knives, bolts, limbs). Children 28, 32, 33, 36, 37 and
 * 38 are generic "other_1..other_6" slots that the game repopulates per metal, so their index is NOT
 * stable across bar types. Anything in those slots, and every Sailing keel item, uses childId 0 and
 * is selected by matching the interface text instead.
 *
 * levelOffset is added to the base level of the metal (bronze 0, iron 15, steel 30, mithril 50,
 * adamant 70, rune 85) and the result is held between 1 and 99: a rune scimitar is 85 + 5 = 90.
 */
public enum AnvilItem {
    //     name, child, bars, level offset
    DAGGER("Dagger", 9, 1, 0),
    SWORD("Sword", 10, 1, 4),
    SCIMITAR("Scimitar", 11, 2, 5),
    LONG_SWORD("Long sword", 12, 2, 6),
    TWO_HAND_SWORD("2-hand sword", 13, 3, 14),
    AXE("Axe", 14, 1, 1),
    MACE("Mace", 15, 1, 2),
    WARHAMMER("Warhammer", 16, 3, 9),
    BATTLE_AXE("Battle axe", 17, 3, 10),
    CLAWS("Claws", 18, 2, 13),
    CHAIN_BODY("Chain body", 19, 3, 11),
    PLATE_LEGS("Plate legs", 20, 3, 16),
    PLATE_SKIRT("Plate skirt", 21, 3, 16),
    PLATE_BODY("Plate body", 22, 5, 18),
    NAILS("Nails", 23, 1, 4),
    MEDIUM_HELM("Medium helm", 24, 1, 3),
    FULL_HELM("Full helm", 25, 2, 7),
    SQUARE_SHIELD("Square shield", 26, 2, 8),
    KITE_SHIELD("Kite shield", 27, 3, 12),
    DART_TIPS("Dart tips", 29, 1, 4),
    ARROWTIPS("Arrowtips", 30, 1, 5),
    KNIVES("Knives", 31, 1, 7),
    BOLTS("Bolts (unf)", 34, 1, 3),
    LIMBS("Crossbow limbs", 35, 1, 6),

    // One metal each, in the generic other_N slots. The search word is the part of the name the
    // interface label and the item name share ("Lantern frame" / "Oil lantern frame").
    //     name, search, metal, level
    BRONZE_WIRE("Bronze wire", "wire", Bars.BRONZE, 4),
    OIL_LAMP("Oil lantern frame", "lantern frame", Bars.IRON, 26),
    IRON_SPIT("Iron spit", "spit", Bars.IRON, 17),
    BULLSEYE_LAMP("Bullseye lantern (unf)", "bullseye lantern", Bars.STEEL, 49),
    STEEL_STUDS("Steel studs", "studs", Bars.STEEL, 36),

    // Sailing. Both are made at a normal anvil; the metal word comes from Bars.getKeelMetal().
    // Large keel parts take 5 regular keel parts of the same metal, not bars.
    KEEL_PARTS("Keel parts", KeelKind.REGULAR),
    LARGE_KEEL_PARTS("Large keel parts", KeelKind.LARGE);

    public enum KeelKind { NONE, REGULAR, LARGE }

    private static final int IRON_LIMBS_LEVEL = 23; // the one item off the base + offset rule

    private final String itemName;
    @Getter
    private final int childId;
    @Getter
    private final int requiredBars;
    @Getter
    private final KeelKind keelKind;
    private final int levelOffset;
    private final int fixedLevel;
    /** The interface text to look for when there is no stable child index. */
    @Getter
    private final String search;
    /** The only metal this item exists in, or null when every metal makes it. */
    @Getter
    private final Bars onlyBar;

    AnvilItem(String itemName, int childId, int requiredBars, int levelOffset) {
        this(itemName, childId, requiredBars, KeelKind.NONE, levelOffset, 0, itemName, null);
    }

    AnvilItem(String itemName, String search, Bars onlyBar, int fixedLevel) {
        this(itemName, 0, 1, KeelKind.NONE, 0, fixedLevel, search, onlyBar);
    }

    AnvilItem(String itemName, KeelKind keelKind) {
        this(itemName, 0, 5, keelKind, 0, 0, itemName, null);
    }

    AnvilItem(String itemName, int childId, int requiredBars, KeelKind keelKind, int levelOffset,
              int fixedLevel, String search, Bars onlyBar) {
        this.itemName = itemName;
        this.childId = childId;
        this.requiredBars = requiredBars;
        this.keelKind = keelKind;
        this.levelOffset = levelOffset;
        this.fixedLevel = fixedLevel;
        this.search = search;
        this.onlyBar = onlyBar;
    }

    public String getName() {
        return itemName;
    }

    @Override
    public String toString() {
        return itemName;
    }

    /** The Smithing level this item needs when made from the given metal. */
    public int levelFor(Bars bar) {
        if (isKeelItem()) return bar.getKeelLevel();
        if (fixedLevel > 0) return fixedLevel;
        if (this == LIMBS && bar == Bars.IRON) return IRON_LIMBS_LEVEL;
        return Math.max(1, Math.min(99, bar.getBaseLevel() + levelOffset));
    }

    /** Items with no stable child index must be found by matching the interface text. */
    public boolean hasValidChildId() {
        return childId > 0;
    }

    /** A quest the item itself needs, beyond the anvil's place. Dart tips: The Tourist Trap (OSRS wiki). */
    public Quest getRequiredQuest() {
        return this == DART_TIPS ? Quest.THE_TOURIST_TRAP : null;
    }

    /** Items one smithing action makes (OSRS wiki): 15 nails or arrowtips, 10 dart tips or bolts, 5 knives. */
    public int getOutputPerAction() {
        switch (this) {
            case NAILS:
            case ARROWTIPS: return 15;
            case DART_TIPS:
            case BOLTS:     return 10;
            case KNIVES:    return 5;
            default:        return 1;
        }
    }

    public boolean isKeelItem() {
        return keelKind != KeelKind.NONE;
    }

    public boolean isLargeKeel() {
        return keelKind == KeelKind.LARGE;
    }
}
