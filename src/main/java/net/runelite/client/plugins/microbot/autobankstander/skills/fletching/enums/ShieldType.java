package net.runelite.client.plugins.microbot.autobankstander.skills.fletching.enums;

import net.runelite.api.gameval.ItemID;

public enum ShieldType {
    OAK_SHIELD("Oak shield", 27, ItemID.OAK_LOGS, ItemID.KNIFE, ItemID.OAK_SHIELD, 2),
    WILLOW_SHIELD("Willow shield", 42, ItemID.WILLOW_LOGS, ItemID.KNIFE, ItemID.WILLOW_SHIELD, 2),
    MAPLE_SHIELD("Maple shield", 57, ItemID.MAPLE_LOGS, ItemID.KNIFE, ItemID.MAPLE_SHIELD, 2),
    YEW_SHIELD("Yew shield", 72, ItemID.YEW_LOGS, ItemID.KNIFE, ItemID.YEW_SHIELD, 2),
    MAGIC_SHIELD("Magic shield", 87, ItemID.MAGIC_LOGS, ItemID.KNIFE, ItemID.MAGIC_SHIELD, 2),
    REDWOOD_SHIELD("Redwood shield", 92, ItemID.REDWOOD_LOGS, ItemID.KNIFE, ItemID.REDWOOD_SHIELD, 2);

    private final String name;
    private final int levelRequired;
    private final int logId;
    private final int knifeId;
    private final int productId;
    private final int logsRequired;

    ShieldType(String name, int levelRequired, int logId, int knifeId, int productId, int logsRequired) {
        this.name = name;
        this.levelRequired = levelRequired;
        this.logId = logId;
        this.knifeId = knifeId;
        this.productId = productId;
        this.logsRequired = logsRequired;
    }

    public String getName() {
        return name;
    }

    public int getLevelRequired() {
        return levelRequired;
    }

    public int getLogId() {
        return logId;
    }

    public int getKnifeId() {
        return knifeId;
    }

    public int getProductId() {
        return productId;
    }

    public int getLogsRequired() {
        return logsRequired;
    }

    @Override
    public String toString() {
        return name;
    }
}