package net.runelite.client.plugins.microbot.varrockanvil;

import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.varrockanvil.enums.Bars;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class VarrockAnvilBankingTest {

    private static final int BAR = Bars.RUNITE.getId();
    private static final String WITHDRAW_STOP = "Could not withdraw Runite bars after 3 tries. Clear the bank search, make sure the bars are visible in the bank, then start the script again.";

    @Test
    void slowWithdrawWithBarsStillInTheBankDoesNotStop() throws Exception {
        VarrockAnvilScript script = stubbedScript();
        try (MockedStatic<Global> global = mockStatic(Global.class);
             MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class)) {
            openBankWithBars(bank, inventory, 27);
            inventory.when(() -> Rs2Inventory.count(BAR)).thenReturn(0);

            bank(script);

            bank.verify(() -> Rs2Bank.withdrawAll(BAR));
            bank.verify(Rs2Bank::closeBank, never());
            verify(script, never()).stop(anyString());
        }
    }

    @Test
    void failedWithdrawWithNoBarsLeftInTheBankStops() throws Exception {
        VarrockAnvilScript script = stubbedScript();
        try (MockedStatic<Global> global = mockStatic(Global.class);
             MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class)) {
            openBankWithBars(bank, inventory, 27);
            bank.when(() -> Rs2Bank.count(BAR)).thenReturn(27, 0);
            inventory.when(() -> Rs2Inventory.count(BAR)).thenReturn(0);

            bank(script);

            verify(script).stop("Out of Runite bars in the bank. Stopping.");
        }
    }

    @Test
    void barsWithdrawnAsNotesStopsWithTheNoteHint() throws Exception {
        VarrockAnvilScript script = stubbedScript();
        try (MockedStatic<Global> global = mockStatic(Global.class);
             MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class)) {
            openBankWithBars(bank, inventory, 27);
            bank.when(() -> Rs2Bank.count(BAR)).thenReturn(27, 0);
            inventory.when(() -> Rs2Inventory.count(BAR)).thenReturn(0);
            inventory.when(() -> Rs2Inventory.hasNotedItem("Runite bar", true)).thenReturn(true);

            bank(script);

            verify(script).stop("Runite bars were withdrawn as notes. Set the bank to 'Withdraw as: Item', deposit the noted bars, and start the script again.");
        }
    }

    @Test
    void withdrawAsItemIsSetBeforeWithdrawing() throws Exception {
        VarrockAnvilScript script = stubbedScript();
        try (MockedStatic<Global> global = mockStatic(Global.class);
             MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class)) {
            openBankWithBars(bank, inventory, 27);
            bank.when(Rs2Bank::setWithdrawAsItem).thenReturn(false);

            bank(script);

            bank.verify(Rs2Bank::setWithdrawAsItem);
            bank.verify(() -> Rs2Bank.withdrawAll(anyInt()), never());
            verify(script, never()).stop(anyString());
        }
    }

    @Test
    void withdrawThatLandsAfterTheWaitDoesNotStop() throws Exception {
        VarrockAnvilScript script = stubbedScript();
        try (MockedStatic<Global> global = mockStatic(Global.class);
             MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class)) {
            openBankWithBars(bank, inventory, 27);
            bank.when(() -> Rs2Bank.count(BAR)).thenReturn(27, 0);
            inventory.when(() -> Rs2Inventory.count(BAR)).thenReturn(0, 27);

            bank(script);

            verify(script, never()).stop(anyString());
        }
    }

    @Test
    void repeatedFailedWithdrawsStopAfterThreeTries() throws Exception {
        VarrockAnvilScript script = stubbedScript();
        try (MockedStatic<Global> global = mockStatic(Global.class);
             MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class)) {
            openBankWithBars(bank, inventory, 27);
            inventory.when(() -> Rs2Inventory.count(BAR)).thenReturn(0);

            bank(script);
            bank(script);
            verify(script, never()).stop(anyString());
            bank(script);

            verify(script, times(1)).stop(WITHDRAW_STOP);
            bank.verify(() -> Rs2Bank.withdrawAll(BAR), times(3));
        }
    }

    @Test
    void successfulWithdrawClosesTheBank() throws Exception {
        VarrockAnvilScript script = stubbedScript();
        try (MockedStatic<Global> global = mockStatic(Global.class);
             MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class)) {
            openBankWithBars(bank, inventory, 27);
            inventory.when(() -> Rs2Inventory.count(BAR)).thenReturn(0, 27);
            global.when(() -> Global.sleepUntil(any(BooleanSupplier.class), anyInt()))
                    .thenAnswer(invocation -> ((BooleanSupplier) invocation.getArgument(0)).getAsBoolean());

            bank(script);

            bank.verify(Rs2Bank::closeBank);
            verify(script, never()).stop(anyString());
        }
    }

    @Test
    void outOfBarsStopsWithoutWithdrawing() throws Exception {
        VarrockAnvilScript script = stubbedScript();
        try (MockedStatic<Global> global = mockStatic(Global.class);
             MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class)) {
            openBankWithBars(bank, inventory, 0);

            bank(script);

            verify(script).stop("Out of Runite bars in the bank. Stopping.");
            bank.verify(() -> Rs2Bank.withdrawAll(anyInt()), never());
            bank.verify(() -> Rs2Bank.withdrawAll(anyString()), never());
        }
    }

    @Test
    void unloadedBankMirrorDoesNotStopForOutOfBars() throws Exception {
        VarrockAnvilScript script = stubbedScript();
        try (MockedStatic<Global> global = mockStatic(Global.class);
             MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class)) {
            openBankWithBars(bank, inventory, 0);
            bank.when(Rs2Bank::getBankLiveEpoch).thenReturn(0);
            bank.when(() -> Rs2Bank.verifyBankMirrorAfterOpen(anyBoolean(), anyInt())).thenReturn(false);

            bank(script);

            verify(script, never()).stop(anyString());
            bank.verify(() -> Rs2Bank.withdrawAll(anyInt()), never());
            bank.verify(Rs2Bank::closeBank);
        }
    }

    @Test
    void missingHammerStopsWithoutWithdrawingBars() throws Exception {
        VarrockAnvilScript script = stubbedScript();
        try (MockedStatic<Global> global = mockStatic(Global.class);
             MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class)) {
            openBankWithBars(bank, inventory, 27);
            inventory.when(() -> Rs2Inventory.hasItem(ItemID.HAMMER)).thenReturn(false);

            bank(script);

            verify(script).stop("No hammer in your inventory or bank. Put a hammer in your bank and start again.");
            bank.verify(() -> Rs2Bank.withdrawOne(ItemID.HAMMER), never());
            bank.verify(() -> Rs2Bank.withdrawAll(anyInt()), never());
            bank.verify(() -> Rs2Bank.withdrawAll(anyString()), never());
        }
    }

    @Test
    void slowHammerWithdrawWithHammerInTheBankDoesNotStop() throws Exception {
        VarrockAnvilScript script = stubbedScript();
        try (MockedStatic<Global> global = mockStatic(Global.class);
             MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class)) {
            openBankWithBars(bank, inventory, 27);
            inventory.when(() -> Rs2Inventory.hasItem(ItemID.HAMMER)).thenReturn(false);
            bank.when(() -> Rs2Bank.count(ItemID.HAMMER)).thenReturn(1);

            bank(script);

            bank.verify(() -> Rs2Bank.withdrawOne(ItemID.HAMMER));
            bank.verify(() -> Rs2Bank.withdrawAll(anyInt()), never());
            verify(script, never()).stop(anyString());
        }
    }

    private static VarrockAnvilScript stubbedScript() {
        VarrockAnvilScript script = spy(new VarrockAnvilScript());
        doNothing().when(script).stop(anyString());
        return script;
    }

    private static void openBankWithBars(MockedStatic<Rs2Bank> bank, MockedStatic<Rs2Inventory> inventory, int bars) {
        bank.when(Rs2Bank::openBank).thenReturn(true);
        bank.when(Rs2Bank::isOpen).thenReturn(true);
        bank.when(() -> Rs2Bank.verifyBankMirrorAfterOpen(anyBoolean(), anyInt())).thenReturn(true);
        bank.when(Rs2Bank::setWithdrawAsItem).thenReturn(true);
        bank.when(() -> Rs2Bank.count(BAR)).thenReturn(bars);
        inventory.when(() -> Rs2Inventory.hasItem(ItemID.HAMMER)).thenReturn(true);
    }

    private static void bank(VarrockAnvilScript script) throws Exception {
        Method method = VarrockAnvilScript.class.getDeclaredMethod("bank", Bars.class);
        method.setAccessible(true);
        method.invoke(script, Bars.RUNITE);
    }
}
