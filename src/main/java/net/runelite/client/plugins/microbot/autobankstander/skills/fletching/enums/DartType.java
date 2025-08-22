package net.runelite.client.plugins.microbot.autobankstander.skills.fletching.enums;

import net.runelite.api.gameval.ItemID;

public enum DartType {
    BRONZE("Bronze darts", 10, ItemID.BRONZE_DART_TIP, ItemID.FEATHER, ItemID.BRONZE_DART),
    IRON("Iron darts", 22, ItemID.IRON_DART_TIP, ItemID.FEATHER, ItemID.IRON_DART),
    STEEL("Steel darts", 37, ItemID.STEEL_DART_TIP, ItemID.FEATHER, ItemID.STEEL_DART),
    MITHRIL("Mithril darts", 52, ItemID.MITHRIL_DART_TIP, ItemID.FEATHER, ItemID.MITHRIL_DART),
    ADAMANT("Adamant darts", 67, ItemID.ADAMANT_DART_TIP, ItemID.FEATHER, ItemID.ADAMANT_DART),
    RUNE("Rune darts", 81, ItemID.RUNE_DART_TIP, ItemID.FEATHER, ItemID.RUNE_DART),
    AMETHYST("Amethyst darts", 90, ItemID.AMETHYST_DART_TIP, ItemID.FEATHER, ItemID.AMETHYST_DART),
    DRAGON("Dragon darts", 95, ItemID.DRAGON_DART_TIP, ItemID.FEATHER, ItemID.DRAGON_DART);

    private final String name;
    private final int levelRequired;
    private final int tipId;
    private final int featherId;
    private final int productId;

    DartType(String name, int levelRequired, int tipId, int featherId, int productId) {
        this.name = name;
        this.levelRequired = levelRequired;
        this.tipId = tipId;
        this.featherId = featherId;
        this.productId = productId;
    }

    public String getName() {
        return name;
    }

    public int getLevelRequired() {
        return levelRequired;
    }

    public int getTipId() {
        return tipId;
    }

    public int getFeatherId() {
        return featherId;
    }

    public int getProductId() {
        return productId;
    }


    @Override
    public String toString() {
        return name;
    }
}