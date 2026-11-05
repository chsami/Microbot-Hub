package net.runelite.client.plugins.microbot.barrows;

import net.runelite.api.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

class BarrowsDuelingRingStopTest
{
    @Test
    void missingRingInBankStopsThePluginAndTellsThePlayer()
    {
        BarrowsScript script = new BarrowsScript();
        try (MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class);
             MockedStatic<Microbot> microbot = mockStatic(Microbot.class))
        {
            bank.when(Rs2Bank::isOpen).thenReturn(true);

            assertFalse(script.ensureRingOfDuelingFromBank());

            microbot.verify(() -> Microbot.stopPlugin(BarrowsPlugin.class));
            microbot.verify(() -> Microbot.showMessage(argThat((String message) -> message.contains("no Ring of dueling in your bank"))));
        }
    }

    @Test
    void fullInventoryWithRingsInBankSaysInventoryIsFull()
    {
        BarrowsScript script = new BarrowsScript();
        try (MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class);
             MockedStatic<Microbot> microbot = mockStatic(Microbot.class))
        {
            bank.when(Rs2Bank::isOpen).thenReturn(true);
            bank.when(() -> Rs2Bank.count(ItemID.RING_OF_DUELING8)).thenReturn(15);
            inventory.when(Rs2Inventory::isFull).thenReturn(true);

            assertFalse(script.ensureRingOfDuelingFromBank());

            microbot.verify(() -> Microbot.stopPlugin(BarrowsPlugin.class));
            microbot.verify(() -> Microbot.showMessage(argThat((String message) -> message.contains("inventory is full"))));
        }
    }

    @Test
    void failedWithdrawWithRingsInBankDoesNotClaimTheBankIsEmpty()
    {
        BarrowsScript script = new BarrowsScript();
        try (MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class);
             MockedStatic<Microbot> microbot = mockStatic(Microbot.class))
        {
            bank.when(Rs2Bank::isOpen).thenReturn(true);
            bank.when(() -> Rs2Bank.count(ItemID.RING_OF_DUELING8)).thenReturn(15);

            assertFalse(script.ensureRingOfDuelingFromBank());

            microbot.verify(() -> Microbot.showMessage(argThat((String message) -> message.contains("could not withdraw a Ring of dueling"))));
        }
    }

    @Test
    void notEnoughInBankMessageNamesTheSettingAndTheBankCount()
    {
        BarrowsScript script = new BarrowsScript();

        assertEquals("Barrows stopped: only 6 Prayer potion(4) in your bank, but Max Prayer Restore is set to 8. Bank more or lower Max Prayer Restore, then start Barrows again.",
                script.notEnoughInBankMessage(6, "Prayer potion(4)", "Max Prayer Restore", 8));
        assertEquals("Barrows stopped: no Barrows teleports in your bank, but Max Barrows Teleports is set to 5. Bank more or lower Max Barrows Teleports, then start Barrows again.",
                script.notEnoughInBankMessage(0, "Barrows teleports", "Max Barrows Teleports", 5));
    }

    @Test
    void ringAlreadyInInventoryKeepsBanking()
    {
        BarrowsScript script = new BarrowsScript();
        try (MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class);
             MockedStatic<Microbot> microbot = mockStatic(Microbot.class))
        {
            bank.when(Rs2Bank::isOpen).thenReturn(true);
            inventory.when(() -> Rs2Inventory.hasItem(ItemID.RING_OF_DUELING8)).thenReturn(true);

            assertTrue(script.ensureRingOfDuelingFromBank());

            microbot.verify(() -> Microbot.stopPlugin(BarrowsPlugin.class), never());
            microbot.verify(() -> Microbot.showMessage(anyString()), never());
        }
    }

    @Test
    void closedBankIsNotTreatedAsMissingRing()
    {
        BarrowsScript script = new BarrowsScript();
        try (MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class);
             MockedStatic<Microbot> microbot = mockStatic(Microbot.class))
        {
            assertTrue(script.ensureRingOfDuelingFromBank());

            microbot.verify(() -> Microbot.stopPlugin(BarrowsPlugin.class), never());
        }
    }
}
