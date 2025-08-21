package net.runelite.client.plugins.microbot.autobankstander.skills.fletching.enums;

import net.runelite.api.gameval.ItemID;

public enum CrossbowType {
    // Crossbow stocks
    WOOD_STOCK("Wood stock", 9, ItemID.LOGS, ItemID.BRONZE_KNIFE, ItemID.XBOWS_CROSSBOW_STOCK_WOOD),
    OAK_STOCK("Oak stock", 24, ItemID.OAK_LOGS, ItemID.BRONZE_KNIFE, ItemID.XBOWS_CROSSBOW_STOCK_OAK),
    WILLOW_STOCK("Willow stock", 39, ItemID.WILLOW_LOGS, ItemID.BRONZE_KNIFE, ItemID.XBOWS_CROSSBOW_STOCK_WILLOW),
    TEAK_STOCK("Teak stock", 46, ItemID.TEAK_LOGS, ItemID.BRONZE_KNIFE, ItemID.XBOWS_CROSSBOW_STOCK_TEAK),
    MAPLE_STOCK("Maple stock", 54, ItemID.MAPLE_LOGS, ItemID.BRONZE_KNIFE, ItemID.XBOWS_CROSSBOW_STOCK_MAPLE),
    MAHOGANY_STOCK("Mahogany stock", 61, ItemID.MAHOGANY_LOGS, ItemID.BRONZE_KNIFE, ItemID.XBOWS_CROSSBOW_STOCK_MAHOGANY),
    YEW_STOCK("Yew stock", 69, ItemID.YEW_LOGS, ItemID.BRONZE_KNIFE, ItemID.XBOWS_CROSSBOW_STOCK_YEW),
    MAGIC_STOCK("Magic stock", 78, ItemID.MAGIC_LOGS, ItemID.BRONZE_KNIFE, ItemID.XBOWS_CROSSBOW_STOCK_MAGIC),
    
    // Unstrung crossbows
    BRONZE_CROSSBOW_UNSTRUNG("Bronze crossbow (u)", 9, ItemID.XBOWS_CROSSBOW_STOCK_WOOD, ItemID.XBOWS_CROSSBOW_LIMBS_BRONZE, ItemID.XBOWS_CROSSBOW_UNSTRUNG_BRONZE),
    BLURITE_CROSSBOW_UNSTRUNG("Blurite crossbow (u)", 24, ItemID.XBOWS_CROSSBOW_STOCK_OAK, ItemID.XBOWS_CROSSBOW_LIMBS_BLURITE, ItemID.XBOWS_CROSSBOW_UNSTRUNG_BLURITE),
    IRON_CROSSBOW_UNSTRUNG("Iron crossbow (u)", 39, ItemID.XBOWS_CROSSBOW_STOCK_WILLOW, ItemID.XBOWS_CROSSBOW_LIMBS_IRON, ItemID.XBOWS_CROSSBOW_UNSTRUNG_IRON),
    STEEL_CROSSBOW_UNSTRUNG("Steel crossbow (u)", 46, ItemID.XBOWS_CROSSBOW_STOCK_TEAK, ItemID.XBOWS_CROSSBOW_LIMBS_STEEL, ItemID.XBOWS_CROSSBOW_UNSTRUNG_STEEL),
    MITHRIL_CROSSBOW_UNSTRUNG("Mithril crossbow (u)", 54, ItemID.XBOWS_CROSSBOW_STOCK_MAPLE, ItemID.XBOWS_CROSSBOW_LIMBS_MITHRIL, ItemID.XBOWS_CROSSBOW_UNSTRUNG_MITHRIL),
    ADAMANT_CROSSBOW_UNSTRUNG("Adamant crossbow (u)", 61, ItemID.XBOWS_CROSSBOW_STOCK_MAHOGANY, ItemID.XBOWS_CROSSBOW_LIMBS_ADAMANTITE, ItemID.XBOWS_CROSSBOW_UNSTRUNG_ADAMANTITE),
    RUNE_CROSSBOW_UNSTRUNG("Rune crossbow (u)", 69, ItemID.XBOWS_CROSSBOW_STOCK_YEW, ItemID.XBOWS_CROSSBOW_LIMBS_RUNITE, ItemID.XBOWS_CROSSBOW_UNSTRUNG_RUNITE),
    DRAGON_CROSSBOW_UNSTRUNG("Dragon crossbow (u)", 78, ItemID.XBOWS_CROSSBOW_STOCK_MAGIC, ItemID.XBOWS_CROSSBOW_LIMBS_DRAGON, ItemID.XBOWS_CROSSBOW_UNSTRUNG_DRAGON),
    
    // Strung crossbows
    BRONZE_CROSSBOW("Bronze crossbow", 9, ItemID.XBOWS_CROSSBOW_UNSTRUNG_BRONZE, ItemID.XBOWS_CROSSBOW_STRING, ItemID.XBOWS_CROSSBOW_BRONZE),
    BLURITE_CROSSBOW("Blurite crossbow", 24, ItemID.XBOWS_CROSSBOW_UNSTRUNG_BLURITE, ItemID.XBOWS_CROSSBOW_STRING, ItemID.XBOWS_CROSSBOW_BLURITE),
    IRON_CROSSBOW("Iron crossbow", 39, ItemID.XBOWS_CROSSBOW_UNSTRUNG_IRON, ItemID.XBOWS_CROSSBOW_STRING, ItemID.XBOWS_CROSSBOW_IRON),
    STEEL_CROSSBOW("Steel crossbow", 46, ItemID.XBOWS_CROSSBOW_UNSTRUNG_STEEL, ItemID.XBOWS_CROSSBOW_STRING, ItemID.XBOWS_CROSSBOW_STEEL),
    MITHRIL_CROSSBOW("Mithril crossbow", 54, ItemID.XBOWS_CROSSBOW_UNSTRUNG_MITHRIL, ItemID.XBOWS_CROSSBOW_STRING, ItemID.XBOWS_CROSSBOW_MITHRIL),
    ADAMANT_CROSSBOW("Adamant crossbow", 61, ItemID.XBOWS_CROSSBOW_UNSTRUNG_ADAMANTITE, ItemID.XBOWS_CROSSBOW_STRING, ItemID.XBOWS_CROSSBOW_ADAMANTITE),
    RUNE_CROSSBOW("Rune crossbow", 69, ItemID.XBOWS_CROSSBOW_UNSTRUNG_RUNITE, ItemID.XBOWS_CROSSBOW_STRING, ItemID.XBOWS_CROSSBOW_RUNITE),
    DRAGON_CROSSBOW("Dragon crossbow", 78, ItemID.XBOWS_CROSSBOW_UNSTRUNG_DRAGON, ItemID.XBOWS_CROSSBOW_STRING, ItemID.XBOWS_CROSSBOW_DRAGON);

    private final String name;
    private final int levelRequired;
    private final int materialOneId;
    private final int materialTwoId;
    private final int productId;

    CrossbowType(String name, int levelRequired, int materialOneId, int materialTwoId, int productId) {
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