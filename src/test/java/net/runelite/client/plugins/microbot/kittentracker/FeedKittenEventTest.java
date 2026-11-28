package net.runelite.client.plugins.microbot.kittentracker;

import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class FeedKittenEventTest {

    private static FeedKittenEvent hungryKittenEvent() {
        KittenPlugin plugin = mock(KittenPlugin.class);
        when(plugin.getTimeBeforeHungry()).thenReturn(60_000L);
        when(plugin.playerHasFollower()).thenReturn(true);
        when(plugin.isKitten()).thenReturn(true);
        return new FeedKittenEvent(plugin);
    }

    private static void stubInventory(MockedStatic<Rs2Inventory> inv, List<Integer> slots) {
        inv.when(() -> Rs2Inventory.contains(any(int[].class)))
                .thenAnswer(call -> Arrays.stream((int[]) call.getRawArguments()[0]).anyMatch(slots::contains));
        inv.when(() -> Rs2Inventory.get(any(int[].class)))
                .thenAnswer(call -> {
                    int[] ids = (int[]) call.getRawArguments()[0];
                    for (int slotId : slots) {
                        if (Arrays.stream(ids).anyMatch(id -> id == slotId)) {
                            Rs2ItemModel item = mock(Rs2ItemModel.class);
                            when(item.getId()).thenReturn(slotId);
                            return item;
                        }
                    }
                    return null;
                });
    }

    private static boolean validateWithInventory(List<Integer> slots) {
        try (MockedStatic<Rs2Inventory> inv = mockStatic(Rs2Inventory.class)) {
            stubInventory(inv, slots);
            return hungryKittenEvent().validate();
        }
    }

    private static Rs2ItemModel foodChosenFrom(List<Integer> slots) {
        try (MockedStatic<Rs2Inventory> inv = mockStatic(Rs2Inventory.class)) {
            stubInventory(inv, slots);
            return FeedKittenEvent.findKittenFood();
        }
    }

    @Test
    public void hungryKittenIsFedWithRawSardines() {
        assertTrue(validateWithInventory(List.of(ItemID.RAW_SARDINE)));
    }

    @Test
    public void hungryKittenIsStillFedWithRawKarambwanji() {
        assertTrue(validateWithInventory(List.of(ItemID.TBWT_RAW_KARAMBWANJI)));
    }

    @Test
    public void hungryKittenIsNotFedWithoutKittenFood() {
        assertFalse(validateWithInventory(List.of(ItemID.BALL_OF_WOOL, ItemID.RAW_LOBSTER)));
    }

    @Test
    public void rawKarambwanjiIsChosenOverCookedFoodInEarlierSlots() {
        Rs2ItemModel food = foodChosenFrom(List.of(ItemID.TUNA, ItemID.SALMON, ItemID.RAW_SARDINE, ItemID.TBWT_RAW_KARAMBWANJI));
        assertEquals(ItemID.TBWT_RAW_KARAMBWANJI, food.getId());
    }

    @Test
    public void rawFishIsChosenOverCookedCombatFood() {
        Rs2ItemModel food = foodChosenFrom(List.of(ItemID.TUNA, ItemID.TROUT, ItemID.RAW_SARDINE));
        assertEquals(ItemID.RAW_SARDINE, food.getId());
    }

    @Test
    public void lowestCookedFishIsUsedWhenNoRawFishIsCarried() {
        Rs2ItemModel food = foodChosenFrom(List.of(ItemID.TUNA, ItemID.SHARK, ItemID.TROUT));
        assertEquals(ItemID.TROUT, food.getId());
    }

    @Test
    public void noFoodIsChosenWithoutKittenFood() {
        assertNull(foodChosenFrom(List.of(ItemID.SHARK, ItemID.RAW_LOBSTER)));
    }

    @Test
    public void everyRawFishIsListedBeforeAnyCookedFish() {
        List<Integer> foods = Arrays.stream(FeedKittenEvent.KITTEN_FOOD).boxed().collect(java.util.stream.Collectors.toList());
        int lastRaw = foods.indexOf(ItemID.RAW_TUNA);
        assertEquals(0, foods.indexOf(ItemID.TBWT_RAW_KARAMBWANJI));
        for (int cooked : new int[]{ItemID.TBWT_COOKED_KARAMBWANJI, ItemID.SHRIMP, ItemID.SARDINE, ItemID.TROUT, ItemID.SALMON, ItemID.TUNA}) {
            assertTrue(foods.indexOf(cooked) > lastRaw, "cooked food listed before raw fish " + cooked);
        }
    }

    @Test
    public void foodListCoversCommonRawAndCookedFish() {
        Set<Integer> foods = Set.of(Arrays.stream(FeedKittenEvent.KITTEN_FOOD).boxed().toArray(Integer[]::new));
        for (int id : new int[]{ItemID.RAW_SHRIMP, ItemID.SHRIMP, ItemID.RAW_SARDINE, ItemID.SARDINE,
                ItemID.RAW_TROUT, ItemID.TROUT, ItemID.RAW_SALMON, ItemID.RAW_TUNA, ItemID.TBWT_COOKED_KARAMBWANJI}) {
            assertTrue(foods.contains(id), "missing kitten food " + id);
        }
    }
}
