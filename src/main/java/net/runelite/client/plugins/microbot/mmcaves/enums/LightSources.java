package net.runelite.client.plugins.microbot.mmcaves.enums;

public enum LightSources {
    LIT_TORCH("Lit torch"),
    LIT_CANDLE("Lit candle"),
    OIL_LAMP("Oil lamp"),
    OIL_LANTERN("Oil lantern"),
    BULLSEYE_LANTERN("Bullseye lantern"),
    SAPPHIRE_LANTERN("Sapphire lantern"),
    EMERALD_LANTERN("Emerald lantern"),
    BRUMA_TORCH("Bruma torch"),
    ABYSSAL_LANTERN("Abyssal lantern"),
    KANDARIN_HEADGEAR_1("Kandarin headgear 1"),
    KANDARIN_HEADGEAR_2("Kandarin headgear 2"),
    KANDARIN_HEADGEAR_3("Kandarin headgear 3"),
    KANDARIN_HEADGEAR_4("Kandarin headgear 4"),
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
