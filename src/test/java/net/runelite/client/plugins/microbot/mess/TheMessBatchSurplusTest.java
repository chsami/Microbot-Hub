package net.runelite.client.plugins.microbot.mess;

import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.inventory.InteractOrder;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TheMessBatchSurplusTest {

    private static TheMessScript script(TheMessScript.Dish dish) throws Exception {
        TheMessScript script = mock(TheMessScript.class, CALLS_REAL_METHODS);
        TheMessConfig config = mock(TheMessConfig.class);
        when(config.dish()).thenReturn(dish);
        Field field = TheMessScript.class.getDeclaredField("config");
        field.setAccessible(true);
        field.set(script, config);
        doNothing().when(script).shutdown();
        doReturn(false).when(script).withdrawFromFood(anyInt(), anyInt());
        return script;
    }

    private static void stub(MockedStatic<Rs2Inventory> inventory, Map<Integer, Integer> items) {
        for (Map.Entry<Integer, Integer> item : items.entrySet()) {
            int id = item.getKey();
            int count = item.getValue();
            inventory.when(() -> Rs2Inventory.hasItem(id)).thenReturn(count > 0);
            inventory.when(() -> Rs2Inventory.count(id)).thenReturn(count);
        }
    }

    @Test
    void pizzaLimitIsOneBelowTheOtherDishesAndPastryDoughIsNeverKept() {
        assertEquals(14, TheMessScript.batchLimit(TheMessScript.Dish.STEW, ItemID.BOWL_WATER));
        assertEquals(14, TheMessScript.batchLimit(TheMessScript.Dish.MEAT_PIE, ItemID.HOSIDIUS_SERVERY_PASTRY_DOUGH));
        assertEquals(13, TheMessScript.batchLimit(TheMessScript.Dish.PIZZA, ItemID.BOWL_WATER));
        assertEquals(0, TheMessScript.batchLimit(TheMessScript.Dish.PIZZA, ItemID.HOSIDIUS_SERVERY_PASTRY_DOUGH));
    }

    @Test
    void dropsTheExcessOfAStewBatchWhenTheDishIsPizza() throws Exception {
        TheMessScript script = script(TheMessScript.Dish.PIZZA);

        try (MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class);
             MockedStatic<Global> global = mockStatic(Global.class)) {
            stub(inventory, Map.of(ItemID.BOWL_WATER, 14));

            assertTrue(script.dropBatchSurplus());

            inventory.verify(() -> Rs2Inventory.dropAmount(ItemID.BOWL_WATER, 1, InteractOrder.STANDARD));
        }
        verify(script, never()).shutdown();
    }

    @Test
    void dropsEveryPastryDoughWhenTheDishIsPizza() throws Exception {
        TheMessScript script = script(TheMessScript.Dish.PIZZA);

        try (MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class);
             MockedStatic<Global> global = mockStatic(Global.class)) {
            stub(inventory, Map.of(ItemID.HOSIDIUS_SERVERY_PASTRY_DOUGH, 14));

            assertTrue(script.dropBatchSurplus());

            inventory.verify(() -> Rs2Inventory.dropAmount(ItemID.HOSIDIUS_SERVERY_PASTRY_DOUGH, 14, InteractOrder.STANDARD));
        }
        verify(script, never()).shutdown();
    }

    @Test
    void leavesAFullBatchAlone() throws Exception {
        TheMessScript script = script(TheMessScript.Dish.MEAT_PIE);

        try (MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class);
             MockedStatic<Global> global = mockStatic(Global.class)) {
            stub(inventory, Map.of(
                    ItemID.BOWL_WATER, 14,
                    ItemID.HOSIDIUS_SERVERY_POT_FLOUR, 14));

            assertFalse(script.dropBatchSurplus());

            inventory.verify(() -> Rs2Inventory.dropAmount(anyInt(), anyInt(), any(InteractOrder.class)), never());
        }
        verify(script, never()).shutdown();
    }

    @Test
    void stopsOnlyAfterTheSurplusCouldNotBeDroppedRepeatedly() throws Exception {
        TheMessScript script = script(TheMessScript.Dish.STEW);

        try (MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class);
             MockedStatic<Global> global = mockStatic(Global.class)) {
            stub(inventory, Map.of(ItemID.BOWL_EMPTY, 20));

            for (int attempt = 0; attempt < 5; attempt++) {
                assertTrue(script.dropBatchSurplus());
            }
            verify(script, never()).shutdown();

            assertTrue(script.dropBatchSurplus());

            inventory.verify(() -> Rs2Inventory.dropAmount(ItemID.BOWL_EMPTY, 6, InteractOrder.STANDARD), times(5));
        }
        verify(script).shutdown();
    }

    @Test
    void pizzaWithdrawsFlourForLeftoverBowlsOfWater() throws Exception {
        TheMessScript script = script(TheMessScript.Dish.PIZZA);

        try (MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class)) {
            stub(inventory, Map.of(
                    ItemID.KNIFE, 2,
                    ItemID.BOWL_WATER, 13));

            script.pizzaTick();
        }

        verify(script).withdrawFromFood(ItemID.HOSIDIUS_SERVERY_POT_FLOUR, 13);
    }
}
