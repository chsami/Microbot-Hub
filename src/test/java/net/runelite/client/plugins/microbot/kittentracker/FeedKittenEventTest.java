package net.runelite.client.plugins.microbot.kittentracker;

import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
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

    private static boolean validateWithInventory(Set<Integer> inventory) {
        try (MockedStatic<Rs2Inventory> inv = mockStatic(Rs2Inventory.class)) {
            inv.when(() -> Rs2Inventory.contains(any(int[].class)))
                    .thenAnswer(call -> Arrays.stream((int[]) call.getRawArguments()[0]).anyMatch(inventory::contains));
            return hungryKittenEvent().validate();
        }
    }

    @Test
    public void hungryKittenIsFedWithRawSardines() {
        assertTrue(validateWithInventory(Set.of(ItemID.RAW_SARDINE)));
    }

    @Test
    public void hungryKittenIsStillFedWithRawKarambwanji() {
        assertTrue(validateWithInventory(Set.of(ItemID.TBWT_RAW_KARAMBWANJI)));
    }

    @Test
    public void hungryKittenIsNotFedWithoutKittenFood() {
        assertFalse(validateWithInventory(Set.of(ItemID.BALL_OF_WOOL, ItemID.RAW_LOBSTER)));
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
