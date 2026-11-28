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
            ItemID.TBWT_RAW_KARAMBWANJI,
            ItemID.RAW_SHRIMP,
            ItemID.RAW_ANCHOVIES,
            ItemID.RAW_SARDINE,
            ItemID.RAW_HERRING,
            ItemID.RAW_MACKEREL,
            ItemID.RAW_TROUT,
            ItemID.RAW_COD,
            ItemID.RAW_PIKE,
            ItemID.RAW_SALMON,
            ItemID.RAW_TUNA,
            ItemID.TBWT_COOKED_KARAMBWANJI,
            ItemID.SHRIMP,
            ItemID.ANCHOVIES,
            ItemID.SARDINE,
            ItemID.SEASONED_SARDINE,
            ItemID.HERRING,
            ItemID.MACKEREL,
            ItemID.TROUT,
            ItemID.COD,
            ItemID.PIKE,
            ItemID.SALMON,
            ItemID.TUNA
    };

    private final KittenPlugin kittenPlugin;
    @Inject
    public FeedKittenEvent(KittenPlugin kittenPlugin) {
        this.kittenPlugin = kittenPlugin;
    }

    static Rs2ItemModel findKittenFood() {
        for (int id : KITTEN_FOOD) {
            if (Rs2Inventory.contains(id)) {
                return Rs2Inventory.get(id);
            }
        }
        return null;
    }

    static void feedKitten(Rs2ItemModel food) {
        Microbot.getRs2NpcCache().query().withName("Kitten").toListOnClientThread().stream().findFirst().ifPresent(kitten -> {
            if (Rs2Inventory.useItemOnNpc(food.getId(), kitten.getNpc())) {
                Microbot.log("Kitten Tracker: fed your kitten 1 " + food.getName() + " to stop it running away.");
            }
        });
    }

    @Override
    public boolean validate() {
        return Rs2Inventory.contains(KITTEN_FOOD)
                && (KittenPlugin.HUNGRY_FIRST_WARNING_TIME_LEFT_IN_SECONDS * 1000) >= kittenPlugin.getTimeBeforeHungry() && (kittenPlugin.playerHasFollower() && kittenPlugin.isKitten());

    }

    @Override
    public boolean execute() {
        Rs2ItemModel food = findKittenFood();
        if (food == null) {
            return true;
        }
        feedKitten(food);
        Global.sleepUntil(() -> (KittenPlugin.HUNGRY_FIRST_WARNING_TIME_LEFT_IN_SECONDS * 1000) < kittenPlugin.getTimeBeforeHungry());
        return true;
    }

    @Override
    public BlockingEventPriority priority() {
        return BlockingEventPriority.NORMAL;
    }
}
