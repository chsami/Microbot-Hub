package net.runelite.client.plugins.microbot.autofishing;

import net.runelite.client.plugins.microbot.autofishing.enums.Fish;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

class AutoFishingScriptTest {
    @Test
    void karambwanjiBaitIsRequiredItemNotCatch() {
        assertTrue(AutoFishingScript.isRequiredItem(Fish.KARAMBWAN, "Raw karambwanji"));
        assertFalse(AutoFishingScript.isRequiredItem(Fish.KARAMBWAN, "Raw karambwan"));
    }

    @Test
    void caughtFishAreNeverRequiredItems() {
        for (Fish fish : Fish.values()) {
            for (String item : fish.getItemNames()) {
                assertFalse(AutoFishingScript.isRequiredItem(fish, item), fish + " " + item);
            }
        }
    }

    @Test
    void consumableGearIsWithdrawnAsWholeStackWithoutWielding() {
        for (String consumable : List.of("Feather", "Fishing bait", "Dark fishing bait", "Sandworms", "Raw karambwanji")) {
            try (MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class);
                 MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class);
                 MockedStatic<Global> global = mockStatic(Global.class)) {
                bank.when(() -> Rs2Bank.hasItem(anyString())).thenReturn(true);
                global.when(() -> Global.sleepUntil(any())).thenReturn(true);

                new AutoFishingScript().withdrawAndEquipItem(consumable);

                bank.verify(() -> Rs2Bank.withdrawAll(consumable));
                bank.verify(() -> Rs2Bank.withdrawOne(anyString()), never());
                inventory.verifyNoInteractions();
            }
        }
    }
}
