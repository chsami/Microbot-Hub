package net.runelite.client.plugins.microbot.anvilsmither.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.gameval.ItemID;

@Getter
@RequiredArgsConstructor
public enum Bars {
    // baseLevel is the dagger level of the metal; each item adds its own offset (see AnvilItem).
    //
    // keelMetal is the word the game uses in Sailing keel item names, which is NOT always the bar
    // name: an "Adamantite bar" makes "Adamant keel parts", a "Runite bar" makes "Rune keel parts".
    // keelLevel is per tier, from the OSRS Wiki keel tables. Dragon keel parts of either size are
    // made at the Dragon Forge, not an anvil, so dragon is not offered.
    //
    //     name                  id                      base  keelMetal   keelLvl
    BRONZE("Bronze bar",         ItemID.BRONZE_BAR,      0,    "bronze",   10),
    IRON("Iron bar",             ItemID.IRON_BAR,        15,   "iron",     22),
    STEEL("Steel bar",           ItemID.STEEL_BAR,       30,   "steel",    38),
    MITHRIL("Mithril bar",       ItemID.MITHRIL_BAR,     50,   "mithril",  56),
    ADAMANTITE("Adamantite bar", ItemID.ADAMANTITE_BAR,  70,   "adamant",  74),
    RUNITE("Runite bar",         ItemID.RUNITE_BAR,      85,   "rune",     86);

    private final String name;
    private final int id;
    private final int baseLevel;
    private final String keelMetal;
    private final int keelLevel;

    @Override
    public String toString() {
        return name;
    }
}
