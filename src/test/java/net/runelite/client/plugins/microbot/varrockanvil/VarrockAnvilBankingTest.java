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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class VarrockAnvilBankingTest {

    private static final int BAR = Bars.RUNITE.getId();

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

            verify(script).stop("Could not find bars in bank.");
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

            verify(script).stop("Out of bars.");
            bank.verify(() -> Rs2Bank.withdrawAll(anyInt()), never());
            bank.verify(() -> Rs2Bank.withdrawAll(anyString()), never());
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

            verify(script).stop("Could not find hammer in bank.");
            bank.verify(() -> Rs2Bank.withdrawAll(anyInt()), never());
            bank.verify(() -> Rs2Bank.withdrawAll(anyString()), never());
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
        bank.when(() -> Rs2Bank.count(BAR)).thenReturn(bars);
        inventory.when(() -> Rs2Inventory.hasItem(ItemID.HAMMER)).thenReturn(true);
    }

    private static void bank(VarrockAnvilScript script) throws Exception {
        Method method = VarrockAnvilScript.class.getDeclaredMethod("bank", Bars.class);
        method.setAccessible(true);
        method.invoke(script, Bars.RUNITE);
    }
}
