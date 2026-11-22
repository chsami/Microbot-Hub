package net.runelite.client.plugins.microbot.chaosaltar;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Pvp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Collections;
import java.util.List;

import static net.runelite.api.ItemID.DRAGON_BONES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

class ChaosAltarBankingTest
{
    @AfterEach
    void resetDeathFlag()
    {
        ChaosAltarScript.didWeDie = false;
    }

    @Test
    void wornAmuletWithNoSpareInBankKeepsBanking()
    {
        ChaosAltarScript script = new ChaosAltarScript();
        try (MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class);
             MockedStatic<Rs2Equipment> equipment = mockStatic(Rs2Equipment.class);
             MockedStatic<Microbot> microbot = mockStatic(Microbot.class))
        {
            bank.when(Rs2Bank::isOpen).thenReturn(true);
            bank.when(() -> Rs2Bank.hasItem(DRAGON_BONES)).thenReturn(true);
            equipment.when(() -> Rs2Equipment.isWearing("Burning amulet", false)).thenReturn(true);

            script.handleBanking();

            bank.verify(() -> Rs2Bank.withdrawAll(DRAGON_BONES));
            bank.verify(() -> Rs2Bank.withdrawAndEquip(anyString()), never());
            microbot.verify(() -> Microbot.stopPlugin(ChaosAltarPlugin.class), never());
            microbot.verify(() -> Microbot.showMessage(anyString()), never());
        }
    }

    @Test
    void noAmuletAnywhereClosesBankAndTellsThePlayer()
    {
        ChaosAltarScript script = new ChaosAltarScript();
        try (MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class);
             MockedStatic<Rs2Equipment> equipment = mockStatic(Rs2Equipment.class);
             MockedStatic<Microbot> microbot = mockStatic(Microbot.class))
        {
            bank.when(Rs2Bank::isOpen).thenReturn(true);
            bank.when(() -> Rs2Bank.hasItem(DRAGON_BONES)).thenReturn(true);

            script.handleBanking();

            bank.verify(Rs2Bank::closeBank);
            bank.verify(() -> Rs2Bank.withdrawAll(DRAGON_BONES), never());
            microbot.verify(() -> Microbot.stopPlugin(ChaosAltarPlugin.class));
            microbot.verify(() -> Microbot.showMessage(ChaosAltarScript.OUT_OF_AMULETS_MESSAGE));
            assertEquals(ChaosAltarScript.OUT_OF_AMULETS_MESSAGE, Microbot.status);
        }
    }

    @Test
    void noBonesInBankClosesBankAndTellsThePlayer()
    {
        ChaosAltarScript script = new ChaosAltarScript();
        try (MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class);
             MockedStatic<Rs2Equipment> equipment = mockStatic(Rs2Equipment.class);
             MockedStatic<Microbot> microbot = mockStatic(Microbot.class))
        {
            bank.when(Rs2Bank::isOpen).thenReturn(true);
            equipment.when(() -> Rs2Equipment.isWearing("Burning amulet", false)).thenReturn(true);

            script.handleBanking();

            bank.verify(Rs2Bank::closeBank);
            microbot.verify(() -> Microbot.stopPlugin(ChaosAltarPlugin.class));
            microbot.verify(() -> Microbot.showMessage(ChaosAltarScript.OUT_OF_BONES_MESSAGE));
        }
    }

    @Test
    void deathAfterBankingWithBonesAndAmuletSkipsSecondBankTrip()
    {
        ChaosAltarScript script = new ChaosAltarScript();
        ChaosAltarScript.didWeDie = true;
        try (MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class);
             MockedStatic<Rs2Equipment> equipment = mockStatic(Rs2Equipment.class);
             MockedStatic<Rs2Pvp> pvp = mockStatic(Rs2Pvp.class);
             MockedStatic<Microbot> microbot = mockStatic(Microbot.class, RETURNS_DEEP_STUBS))
        {
            inventory.when(Rs2Inventory::getBones).thenReturn(bones(28));
            equipment.when(() -> Rs2Equipment.isWearing("Burning amulet", false)).thenReturn(true);

            assertEquals(State.TELEPORT_TO_WILDERNESS, script.determineState());
            assertFalse(ChaosAltarScript.didWeDie);
        }
    }

    @Test
    void deathWithoutAmuletStillBanks()
    {
        ChaosAltarScript script = new ChaosAltarScript();
        ChaosAltarScript.didWeDie = true;
        try (MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class);
             MockedStatic<Rs2Equipment> equipment = mockStatic(Rs2Equipment.class);
             MockedStatic<Rs2Pvp> pvp = mockStatic(Rs2Pvp.class);
             MockedStatic<Microbot> microbot = mockStatic(Microbot.class, RETURNS_DEEP_STUBS))
        {
            inventory.when(Rs2Inventory::getBones).thenReturn(bones(28));

            assertEquals(State.BANK, script.determineState());
            assertFalse(ChaosAltarScript.didWeDie);
        }
    }

    private static List<Rs2ItemModel> bones(int count)
    {
        return Collections.nCopies(count, mock(Rs2ItemModel.class));
    }
}
