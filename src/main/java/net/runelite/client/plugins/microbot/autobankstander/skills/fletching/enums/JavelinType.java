package net.runelite.client.plugins.microbot.autobankstander.skills.fletching.enums;

import net.runelite.api.gameval.ItemID;

public enum JavelinType {
    BRONZE("Bronze javelins", 3, ItemID.BRONZE_JAVELIN_HEAD, ItemID.JAVELIN_SHAFT, ItemID.BRONZE_JAVELIN),
    IRON("Iron javelins", 17, ItemID.IRON_JAVELIN_HEAD, ItemID.JAVELIN_SHAFT, ItemID.IRON_JAVELIN),
    STEEL("Steel javelins", 32, ItemID.STEEL_JAVELIN_HEAD, ItemID.JAVELIN_SHAFT, ItemID.STEEL_JAVELIN),
    MITHRIL("Mithril javelins", 47, ItemID.MITHRIL_JAVELIN_HEAD, ItemID.JAVELIN_SHAFT, ItemID.MITHRIL_JAVELIN),
    ADAMANT("Adamant javelins", 62, ItemID.ADAMANT_JAVELIN_HEAD, ItemID.JAVELIN_SHAFT, ItemID.ADAMANT_JAVELIN),
    RUNE("Rune javelins", 77, ItemID.RUNE_JAVELIN_HEAD, ItemID.JAVELIN_SHAFT, ItemID.RUNE_JAVELIN),
    AMETHYST("Amethyst javelins", 84, ItemID.AMETHYST_JAVELIN_HEAD, ItemID.JAVELIN_SHAFT, ItemID.AMETHYST_JAVELIN),
    DRAGON("Dragon javelins", 92, ItemID.DRAGON_JAVELIN_HEAD, ItemID.JAVELIN_SHAFT, ItemID.DRAGON_JAVELIN);

    private final String name;
    private final int levelRequired;
    private final int headId;
    private final int shaftId;
    private final int productId;

    JavelinType(String name, int levelRequired, int headId, int shaftId, int productId) {
        this.name = name;
        this.levelRequired = levelRequired;
        this.headId = headId;
        this.shaftId = shaftId;
        this.productId = productId;
    }

    public String getName() {
        return name;
    }

    public int getLevelRequired() {
        return levelRequired;
    }

    public int getHeadId() {
        return headId;
    }

    public int getShaftId() {
        return shaftId;
    }

    public int getProductId() {
        return productId;
    }

    @Override
    public String toString() {
        return name;
    }
}