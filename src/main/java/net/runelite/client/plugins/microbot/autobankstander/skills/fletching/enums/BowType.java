package net.runelite.client.plugins.microbot.autobankstander.skills.fletching.enums;

import net.runelite.api.gameval.ItemID;

public enum BowType {
    // Unstrung bows
    SHORTBOW_UNSTRUNG("Shortbow (u)", 5, ItemID.LOGS, ItemID.BRONZE_KNIFE, ItemID.UNSTRUNG_SHORTBOW),
    LONGBOW_UNSTRUNG("Longbow (u)", 10, ItemID.LOGS, ItemID.BRONZE_KNIFE, ItemID.UNSTRUNG_LONGBOW),
    OAK_SHORTBOW_UNSTRUNG("Oak shortbow (u)", 20, ItemID.OAK_LOGS, ItemID.BRONZE_KNIFE, ItemID.UNSTRUNG_OAK_SHORTBOW),
    OAK_LONGBOW_UNSTRUNG("Oak longbow (u)", 25, ItemID.OAK_LOGS, ItemID.BRONZE_KNIFE, ItemID.UNSTRUNG_OAK_LONGBOW),
    WILLOW_SHORTBOW_UNSTRUNG("Willow shortbow (u)", 35, ItemID.WILLOW_LOGS, ItemID.BRONZE_KNIFE, ItemID.UNSTRUNG_WILLOW_SHORTBOW),
    WILLOW_LONGBOW_UNSTRUNG("Willow longbow (u)", 40, ItemID.WILLOW_LOGS, ItemID.BRONZE_KNIFE, ItemID.UNSTRUNG_WILLOW_LONGBOW),
    MAPLE_SHORTBOW_UNSTRUNG("Maple shortbow (u)", 50, ItemID.MAPLE_LOGS, ItemID.BRONZE_KNIFE, ItemID.UNSTRUNG_MAPLE_SHORTBOW),
    MAPLE_LONGBOW_UNSTRUNG("Maple longbow (u)", 55, ItemID.MAPLE_LOGS, ItemID.BRONZE_KNIFE, ItemID.UNSTRUNG_MAPLE_LONGBOW),
    YEW_SHORTBOW_UNSTRUNG("Yew shortbow (u)", 65, ItemID.YEW_LOGS, ItemID.BRONZE_KNIFE, ItemID.UNSTRUNG_YEW_SHORTBOW),
    YEW_LONGBOW_UNSTRUNG("Yew longbow (u)", 70, ItemID.YEW_LOGS, ItemID.BRONZE_KNIFE, ItemID.UNSTRUNG_YEW_LONGBOW),
    MAGIC_SHORTBOW_UNSTRUNG("Magic shortbow (u)", 80, ItemID.MAGIC_LOGS, ItemID.BRONZE_KNIFE, ItemID.UNSTRUNG_MAGIC_SHORTBOW),
    MAGIC_LONGBOW_UNSTRUNG("Magic longbow (u)", 85, ItemID.MAGIC_LOGS, ItemID.BRONZE_KNIFE, ItemID.UNSTRUNG_MAGIC_LONGBOW),
    
    // Strung bows
    SHORTBOW("Shortbow", 5, ItemID.UNSTRUNG_SHORTBOW, ItemID.BOW_STRING, ItemID.SHORTBOW),
    LONGBOW("Longbow", 10, ItemID.UNSTRUNG_LONGBOW, ItemID.BOW_STRING, ItemID.LONGBOW),
    OAK_SHORTBOW("Oak shortbow", 20, ItemID.UNSTRUNG_OAK_SHORTBOW, ItemID.BOW_STRING, ItemID.OAK_SHORTBOW),
    OAK_LONGBOW("Oak longbow", 25, ItemID.UNSTRUNG_OAK_LONGBOW, ItemID.BOW_STRING, ItemID.OAK_LONGBOW),
    WILLOW_SHORTBOW("Willow shortbow", 35, ItemID.UNSTRUNG_WILLOW_SHORTBOW, ItemID.BOW_STRING, ItemID.WILLOW_SHORTBOW),
    WILLOW_LONGBOW("Willow longbow", 40, ItemID.UNSTRUNG_WILLOW_LONGBOW, ItemID.BOW_STRING, ItemID.WILLOW_LONGBOW),
    MAPLE_SHORTBOW("Maple shortbow", 50, ItemID.UNSTRUNG_MAPLE_SHORTBOW, ItemID.BOW_STRING, ItemID.MAPLE_SHORTBOW),
    MAPLE_LONGBOW("Maple longbow", 55, ItemID.UNSTRUNG_MAPLE_LONGBOW, ItemID.BOW_STRING, ItemID.MAPLE_LONGBOW),
    YEW_SHORTBOW("Yew shortbow", 65, ItemID.UNSTRUNG_YEW_SHORTBOW, ItemID.BOW_STRING, ItemID.YEW_SHORTBOW),
    YEW_LONGBOW("Yew longbow", 70, ItemID.UNSTRUNG_YEW_LONGBOW, ItemID.BOW_STRING, ItemID.YEW_LONGBOW),
    MAGIC_SHORTBOW("Magic shortbow", 80, ItemID.UNSTRUNG_MAGIC_SHORTBOW, ItemID.BOW_STRING, ItemID.MAGIC_SHORTBOW),
    MAGIC_LONGBOW("Magic longbow", 85, ItemID.UNSTRUNG_MAGIC_LONGBOW, ItemID.BOW_STRING, ItemID.MAGIC_LONGBOW);

    private final String name;
    private final int levelRequired;
    private final int materialOneId;
    private final int materialTwoId;
    private final int productId;

    BowType(String name, int levelRequired, int materialOneId, int materialTwoId, int productId) {
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