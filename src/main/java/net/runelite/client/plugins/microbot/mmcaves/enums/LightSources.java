package net.runelite.client.plugins.microbot.mmcaves.enums;

public enum LightSources {
    TORCH("Torch"),
    CANDLE("Candle"),
    OIL_LAMP("Oil lamp"),
    OIL_LANTERN("Oil lantern"),
    BULLSEYE_LANTERN("Bullseye lantern"),
    SAPPHIRE_LANTERN("Sapphire lantern"),
    EMERALD_LANTERN("Emerald lantern"),
    BRUMA_TORCH("Bruma torch"),
    ABBYSAL_LANTERN("Abbysal lantern"),
    FIREMAKING_CAPE("Firemaking cape"),
    FIREMAKING_CAPE_T("Firemaking cape(t)");

    private final String itemName;

    LightSources(String itemName) {
        this.itemName = itemName;
    }

    public String getItemName() {
        return itemName;
    }
}
