package net.runelite.client.plugins.microbot.mess;

import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class TheMessPizzaBowlReturnTest {

    private static TheMessScript script() {
        TheMessScript script = mock(TheMessScript.class, CALLS_REAL_METHODS);
        doReturn(true).when(script).returnEmptyBowls();
        doReturn(false).when(script).withdrawFromFood(anyInt(), anyInt());
        return script;
    }

    private static void pizzaTickWith(TheMessScript script, Map<Integer, Integer> items) {
        try (MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class)) {
            int total = 0;
            for (Map.Entry<Integer, Integer> item : items.entrySet()) {
                int id = item.getKey();
                int count = item.getValue();
                total += count;
                inventory.when(() -> Rs2Inventory.hasItem(id)).thenReturn(count > 0);
                inventory.when(() -> Rs2Inventory.count(id)).thenReturn(count);
            }
            int inventoryCount = total;
            inventory.when(Rs2Inventory::count).thenReturn(inventoryCount);
            script.pizzaTick();
        }
    }

    @Test
    void returnsEmptyBowlsBeforeWithdrawingTomatoesWhenInventoryIsFull() {
        TheMessScript script = script();

        pizzaTickWith(script, Map.of(
                ItemID.KNIFE, 2,
                ItemID.HOSIDIUS_SERVERY_PIZZA_BASE, 13,
                ItemID.BOWL_EMPTY, 13));

        verify(script).returnEmptyBowls();
        verify(script, never()).withdrawFromFood(anyInt(), anyInt());
    }

    @Test
    void withdrawsTomatoesForEveryBaseOnceBowlsAreReturned() {
        TheMessScript script = script();

        pizzaTickWith(script, Map.of(
                ItemID.KNIFE, 2,
                ItemID.HOSIDIUS_SERVERY_PIZZA_BASE, 13));

        verify(script).withdrawFromFood(ItemID.HOSIDIUS_SERVERY_TOMATO, 13);
        verify(script, never()).returnEmptyBowls();
    }
}
