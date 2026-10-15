package net.runelite.client.plugins.microbot.farmtreerun;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FarmTreeRunBankItemsTest {

    @Test
    void scriptKeepsNoSharedWithdrawalCollection() {
        for (Field field : FarmTreeRunScript.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            assertFalse(Collection.class.isAssignableFrom(field.getType()),
                    "FarmTreeRunScript must not share collection state across threads: " + field.getName());
        }
    }

    @Test
    void mergeSumsSameItemAndNotedFlag() {
        List<FarmingItem> input = new ArrayList<>();
        input.add(new FarmingItem(563, 10));
        input.add(new FarmingItem(554, 30));
        input.add(new FarmingItem(563, 5, false, true));
        input.add(new FarmingItem(995, 100, true));
        input.add(new FarmingItem(995, 50, false));

        List<FarmingItem> merged = FarmTreeRunScript.mergeItems(input);

        assertNotSame(input, merged);
        assertEquals(5, input.size());
        assertEquals(4, merged.size());
        assertEquals(563, merged.get(0).getItemId());
        assertEquals(15, merged.get(0).getQuantity());
        assertFalse(merged.get(0).isOptional());
        assertEquals(554, merged.get(1).getItemId());
        assertTrue(merged.get(2).isNoted());
        assertEquals(100, merged.get(2).getQuantity());
        assertFalse(merged.get(3).isNoted());
        assertEquals(50, merged.get(3).getQuantity());
    }
}
