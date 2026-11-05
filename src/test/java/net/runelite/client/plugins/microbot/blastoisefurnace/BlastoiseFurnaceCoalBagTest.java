package net.runelite.client.plugins.microbot.blastoisefurnace;

import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

class BlastoiseFurnaceCoalBagTest {

    private MockedStatic<Microbot> microbot;
    private MockedStatic<Rs2Inventory> inventory;
    private MockedStatic<Rs2Bank> bank;

    @BeforeEach
    void setUp() {
        microbot = mockStatic(Microbot.class);
        inventory = mockStatic(Rs2Inventory.class);
        bank = mockStatic(Rs2Bank.class);
    }

    @AfterEach
    void tearDown() {
        bank.close();
        inventory.close();
        microbot.close();
    }

    @Test
    void withdrawsTheOpenCoalBagWhenOnlyItIsInTheBank() {
        bank.when(() -> Rs2Bank.hasItem(ItemID.COAL_BAG_OPEN)).thenReturn(true);

        assertTrue(script().ensureCoalBag());

        bank.verify(() -> Rs2Bank.withdrawItem(ItemID.COAL_BAG_OPEN), times(1));
        bank.verify(() -> Rs2Bank.withdrawItem(ItemID.COAL_BAG), never());
        microbot.verify(() -> Microbot.stopPlugin(nullable(Plugin.class)), never());
    }

    @Test
    void acceptsTheOpenCoalBagAlreadyInTheInventory() {
        inventory.when(() -> Rs2Inventory.contains(ItemID.COAL_BAG, ItemID.COAL_BAG_OPEN)).thenReturn(true);

        assertTrue(script().ensureCoalBag());

        bank.verify(() -> Rs2Bank.withdrawItem(anyInt()), never());
        microbot.verify(() -> Microbot.stopPlugin(nullable(Plugin.class)), never());
    }

    @Test
    void stillWithdrawsTheClosedCoalBag() {
        bank.when(() -> Rs2Bank.hasItem(ItemID.COAL_BAG)).thenReturn(true);

        assertTrue(script().ensureCoalBag());

        bank.verify(() -> Rs2Bank.withdrawItem(ItemID.COAL_BAG), times(1));
    }

    @Test
    void stopsWhenNoCoalBagIsInTheInventoryOrBank() {
        assertFalse(script().ensureCoalBag());

        microbot.verify(() -> Microbot.showMessage(anyString()), times(1));
        microbot.verify(() -> Microbot.stopPlugin(nullable(Plugin.class)), times(1));
        bank.verify(() -> Rs2Bank.withdrawItem(anyInt()), never());
    }

    @Test
    void keepsBothCoalBagVariantsWhenDepositing() {
        assertTrue(Arrays.asList(BlastoiseFurnaceScript.KEEP_ITEMS).contains(ItemID.COAL_BAG));
        assertTrue(Arrays.asList(BlastoiseFurnaceScript.KEEP_ITEMS).contains(ItemID.COAL_BAG_OPEN));
    }

    private static BlastoiseFurnaceScript script() {
        return new BlastoiseFurnaceScript(null, null);
    }
}
