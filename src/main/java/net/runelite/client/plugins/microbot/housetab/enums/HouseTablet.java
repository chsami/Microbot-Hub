package net.runelite.client.plugins.microbot.housetab.enums;

import net.runelite.api.widgets.WidgetUtil;

public enum HouseTablet {
    ENCHANT_SAPPHIRE("Enchant Sapphire", 19),
    VARROCK_TELEPORT("Varrock Teleport", 20),
    LUMBRIDGE_TELEPORT("Lumbridge Teleport", 21),
    FALADOR_TELEPORT("Falador Teleport", 22),
    TELEPORT_TO_HOUSE("Teleport to House", 23),
    CAMELOT_TELEPORT("Camelot Teleport", 24),
    KOUREND_CASTLE_TELEPORT("Kourend Castle Teleport", 25),
    ARDOUGNE_TELEPORT("Ardougne Teleport", 26),
    CIVITAS_ILLA_FORTIS_TELEPORT("Civitas illa Fortis Teleport", 27),
    SUMMON_BOAT("Summon Boat", 28),
    WATCHTOWER_TELEPORT("Watchtower Teleport", 29),
    TELEPORT_TO_BOAT("Teleport to Boat", 30);

    private static final int SPELL_TABLET_GROUP_ID = 403;

    private final String name;
    private final int childId;

    HouseTablet(String name, int childId) {
        this.name = name;
        this.childId = childId;
    }

    public int getWidgetId() {
        return WidgetUtil.packComponentId(SPELL_TABLET_GROUP_ID, childId);
    }

    @Override
    public String toString() {
        return name;
    }
}
