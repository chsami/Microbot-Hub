package net.runelite.client.plugins.microbot.kittentracker;


import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.Microbot;

import javax.inject.Inject;


public class FeedKittenEvent implements BlockingEvent {
    static final int[] KITTEN_FOOD = {
            ItemID.TBWT_RAW_KARAMBWANJI, ItemID.TBWT_COOKED_KARAMBWANJI,
            ItemID.RAW_SHRIMP, ItemID.SHRIMP,
            ItemID.RAW_ANCHOVIES, ItemID.ANCHOVIES,
            ItemID.RAW_SARDINE, ItemID.SARDINE, ItemID.SEASONED_SARDINE,
            ItemID.RAW_HERRING, ItemID.HERRING,
            ItemID.RAW_MACKEREL, ItemID.MACKEREL,
            ItemID.RAW_TROUT, ItemID.TROUT,
            ItemID.RAW_COD, ItemID.COD,
            ItemID.RAW_PIKE, ItemID.PIKE,
            ItemID.RAW_SALMON, ItemID.SALMON,
            ItemID.RAW_TUNA, ItemID.TUNA
    };

    private final KittenPlugin kittenPlugin;
    @Inject
    public FeedKittenEvent(KittenPlugin kittenPlugin) {
        this.kittenPlugin = kittenPlugin;
    }

    @Override
    public boolean validate() {
        return Rs2Inventory.contains(KITTEN_FOOD)
                && (KittenPlugin.HUNGRY_FIRST_WARNING_TIME_LEFT_IN_SECONDS * 1000) >= kittenPlugin.getTimeBeforeHungry() && (kittenPlugin.playerHasFollower() && kittenPlugin.isKitten());

    }

    @Override
    public boolean execute() {
        Rs2ItemModel food = Rs2Inventory.get(KITTEN_FOOD);
        if (food == null) {
            return true;
        }
        Microbot.getRs2NpcCache().query().withName("Kitten").toListOnClientThread().stream().findFirst().ifPresent(kitten -> Rs2Inventory.useItemOnNpc(food.getId(), kitten.getNpc()));
        Global.sleepUntil(() -> (KittenPlugin.HUNGRY_FIRST_WARNING_TIME_LEFT_IN_SECONDS * 1000) < kittenPlugin.getTimeBeforeHungry());
        return true;
    }

    @Override
    public BlockingEventPriority priority() {
        return BlockingEventPriority.NORMAL;
    }
}
