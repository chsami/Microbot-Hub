package net.runelite.client.plugins.microbot.banksbankstander;

import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.http.api.item.ItemPrice;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class BanksBankStanderItemLookupTest {

    @Test
    void knifeResolvesToExactNameNotFirstSubstringMatch() {
        List<ItemPrice> results = Arrays.asList(price(863, "Iron knife"), price(864, "Bronze knife"), price(946, "Knife"));
        assertEquals(946, lookup("Knife", results));
    }

    @Test
    void nameMatchIgnoresCaseAndSurroundingSpaces() {
        List<ItemPrice> results = Arrays.asList(price(1975, "Chocolate dust"), price(1973, "Chocolate bar"));
        assertEquals(1973, lookup(" chocolate bar ", results));
    }

    @Test
    void untradeableNameWithoutExactPriceMatchFallsBackToName() {
        assertNull(lookup("Incomplete stew", Collections.emptyList()));
    }

    @Test
    void partialNameWithoutExactMatchFallsBackToName() {
        assertNull(lookup("knife", Collections.singletonList(price(863, "Iron knife"))));
    }

    @Test
    void numericIdIsParsedDirectly() {
        assertEquals(946, BanksBankStanderScript.TryParseInt("946"));
    }

    @Test
    void blankIdentifierIsNull() {
        assertNull(BanksBankStanderScript.TryParseInt(""));
    }

    @Test
    void nameOnlyItemIsCheckedByName() {
        try (MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class)) {
            inventory.when(() -> Rs2Inventory.hasItem("Incomplete stew")).thenReturn(true);
            inventory.when(() -> Rs2Inventory.hasItem(anyInt())).thenThrow(new AssertionError("id lookup used for a name-only item"));
            assertTrue(BanksBankStanderScript.hasItem(null, "Incomplete stew"));
        }
    }

    @Test
    void resolvedItemIsCheckedById() {
        try (MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class)) {
            inventory.when(() -> Rs2Inventory.hasItem(946)).thenReturn(false);
            assertFalse(BanksBankStanderScript.hasItem(946, "Knife"));
        }
    }

    private static Integer lookup(String text, List<ItemPrice> results) {
        ItemManager itemManager = mock(ItemManager.class);
        when(itemManager.search(text.trim())).thenReturn(results);
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class)) {
            microbot.when(Microbot::getItemManager).thenReturn(itemManager);
            return BanksBankStanderScript.TryParseInt(text);
        }
    }

    private static ItemPrice price(int id, String name) {
        ItemPrice price = new ItemPrice();
        price.setId(id);
        price.setName(name);
        return price;
    }
}
