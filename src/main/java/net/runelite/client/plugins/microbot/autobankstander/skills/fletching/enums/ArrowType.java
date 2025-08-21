package net.runelite.client.plugins.microbot.autobankstander.skills.fletching.enums;

import net.runelite.api.gameval.ItemID;

public enum ArrowType {
    HEADLESS("Headless arrows", 1, ItemID.ARROW_SHAFT, ItemID.FEATHER, ItemID.HEADLESS_ARROW),
    BRONZE("Bronze arrows", 1, ItemID.HEADLESS_ARROW, ItemID.BRONZE_ARROWHEADS, ItemID.BRONZE_ARROW),
    IRON("Iron arrows", 15, ItemID.HEADLESS_ARROW, ItemID.IRON_ARROWHEADS, ItemID.IRON_ARROW),
    STEEL("Steel arrows", 30, ItemID.HEADLESS_ARROW, ItemID.STEEL_ARROWHEADS, ItemID.STEEL_ARROW),
    MITHRIL("Mithril arrows", 45, ItemID.HEADLESS_ARROW, ItemID.MITHRIL_ARROWHEADS, ItemID.MITHRIL_ARROW),
    BROAD("Broad arrows", 52, ItemID.HEADLESS_ARROW, ItemID.SLAYER_BROAD_ARROWHEAD, ItemID.SLAYER_BROAD_ARROWS),
    ADAMANT("Adamant arrows", 60, ItemID.HEADLESS_ARROW, ItemID.ADAMANT_ARROWHEADS, ItemID.ADAMANT_ARROW),
    RUNE("Rune arrows", 75, ItemID.HEADLESS_ARROW, ItemID.RUNE_ARROWHEADS, ItemID.RUNE_ARROW),
    AMETHYST("Amethyst arrows", 82, ItemID.HEADLESS_ARROW, ItemID.AMETHYST_ARROWHEADS, ItemID.AMETHYST_ARROW),
    DRAGON("Dragon arrows", 90, ItemID.HEADLESS_ARROW, ItemID.DRAGON_ARROWHEADS, ItemID.DRAGON_ARROW);

    private final String name;
    private final int levelRequired;
    private final int materialOneId;
    private final int materialTwoId;
    private final int productId;

    ArrowType(String name, int levelRequired, int materialOneId, int materialTwoId, int productId) {
        this.name = name;
        this.levelRequired = levelRequired;
        this.materialOneId = materialOneId;
        this.materialTwoId = materialTwoId;
        this.productId = productId;
    }

    public String getName() {
        return name;
    }

    public int getLevelRequired() {
        return levelRequired;
    }

    public int getMaterialOneId() {
        return materialOneId;
    }

    public int getMaterialTwoId() {
        return materialTwoId;
    }

    public int getProductId() {
        return productId;
    }


    @Override
    public String toString() {
        return name;
    }
}