package net.runelite.client.plugins.microbot.scurrius;

import net.runelite.client.plugins.microbot.scurrius.SetupSupplies.Item;
import net.runelite.client.plugins.microbot.scurrius.SetupSupplies.Needs;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SetupSuppliesTest {
    private static final int TAB = 8007;
    private static final int LOBSTER = 379;
    private static final int POTION4 = 2434;

    private static List<Item> setup() {
        List<Item> items = new ArrayList<>();
        items.add(new Item(TAB, "Varrock teleport", 5));         // one stack of 5
        for (int i = 0; i < 20; i++) items.add(new Item(LOBSTER, "Lobster", 1));
        for (int i = 0; i < 2; i++) items.add(new Item(POTION4, "Prayer potion(4)", 1));
        items.add(new Item(1275, "Rune pickaxe", 1));            // not a supply
        return items;
    }

    @Test
    void derivesFoodDosesAndTabQuantityFromTheItemList() {
        Needs needs = SetupSupplies.derive(setup(), id -> id == LOBSTER, TAB);
        assertEquals(20, needs.food);
        assertEquals(8, needs.prayerDoses);
        assertEquals(5, needs.tabs); // the stack quantity, not 1 slot
    }

    @Test
    void foodStacksAndMixedPrayerItemsAreCounted() {
        List<Item> items = List.of(new Item(LOBSTER, "Lobster", 1), new Item(LOBSTER, "Lobster", 1),
                new Item(3024, "Super restore(3)", 1), new Item(2434, "Prayer potion(2)", 2));
        Needs needs = SetupSupplies.derive(items, id -> id == LOBSTER, TAB);
        assertEquals(2, needs.food);
        assertEquals(3 + 4, needs.prayerDoses);
        assertEquals(0, needs.tabs);
    }

    @Test
    void dosesComeFromTheNameAndDefaultToOne() {
        assertEquals(4, SetupSupplies.doses("Prayer potion(4)"));
        assertEquals(1, SetupSupplies.doses("Prayer potion(1)"));
        assertEquals(1, SetupSupplies.doses("Super restore"));
        assertEquals(1, SetupSupplies.doses(null));
        assertTrue(SetupSupplies.isPrayerRestore("Super restore(2)"));
        assertFalse(SetupSupplies.isPrayerRestore("Super attack(4)"));
    }

    @Test
    void theSetupIsMetOnlyWhenNothingIsShort() {
        Needs needs = SetupSupplies.derive(setup(), id -> id == LOBSTER, TAB);
        assertTrue(SetupSupplies.isMet(needs, 20, 8, 5));
        assertTrue(SetupSupplies.isMet(needs, 25, 12, 6));
        assertFalse(SetupSupplies.isMet(needs, 19, 8, 5));
        assertFalse(SetupSupplies.isMet(needs, 20, 7, 5));
        assertFalse(SetupSupplies.isMet(needs, 20, 8, 1));
    }

    @Test
    void missingNamesWhatTheBankCouldNotSupplyWithStackQuantities() {
        List<String> missing = SetupSupplies.missing(setup(), id -> {
            if (id == TAB) return 5;       // the tab stack is complete
            if (id == LOBSTER) return 18;  // two lobsters short
            if (id == POTION4) return 2;
            return 0;                      // no pickaxe
        });
        assertEquals(List.of("Lobster 18/20", "Rune pickaxe 0/1"), missing);
        assertTrue(SetupSupplies.missing(setup(), id -> 99).isEmpty());
    }
}
