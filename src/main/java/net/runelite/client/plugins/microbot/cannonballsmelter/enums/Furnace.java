package net.runelite.client.plugins.microbot.cannonballsmelter.enums;

import net.runelite.api.gameval.ObjectID;

public enum Furnace {
    EDGEVILLE(ObjectID.VARROCK_DIARY_FURNACE),
    SHILO_VILLAGE(ObjectID.ZQFURNACE_LIT),
    PRIFDDINAS(ObjectID.PRIF_FURNACE),
    PORT_PHASMATYS(ObjectID.FAI_FALADOR_FURNACE);

    public final int furnaceID;

    private Furnace(int furnaceID) {
        this.furnaceID = furnaceID;
    }
}
