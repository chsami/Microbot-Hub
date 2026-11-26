package net.runelite.client.plugins.microbot.bankseller;

import org.junit.jupiter.api.Test;

import static net.runelite.client.plugins.microbot.bankseller.BankSellerPassPolicy.Action;
import static org.junit.jupiter.api.Assertions.assertEquals;

public final class BankSellerPassPolicyTest {
    @Test
    void leftoverSellablesGoBackToTheExchangeInsteadOfTheBank() {
        assertEquals(Action.SELL_INVENTORY, BankSellerPassPolicy.nextAction(false, 0, true));
        assertEquals(Action.SELL_INVENTORY, BankSellerPassPolicy.nextAction(false, 1, true));
        assertEquals(Action.SELL_INVENTORY, BankSellerPassPolicy.nextAction(true, 2, true));
    }

    @Test
    void banksOnlyWhenTheInventoryHoldsNothingSellable() {
        assertEquals(Action.BANK, BankSellerPassPolicy.nextAction(false, 0, false));
        assertEquals(Action.BANK, BankSellerPassPolicy.nextAction(false, 3, false));
        assertEquals(Action.BANK, BankSellerPassPolicy.nextAction(true, 1, false));
    }

    @Test
    void keepsWaitingWhileNoSlotIsFree() {
        assertEquals(Action.WAIT_FOR_SLOT, BankSellerPassPolicy.nextAction(true, 0, true));
        assertEquals(Action.WAIT_FOR_SLOT, BankSellerPassPolicy.nextAction(true, 0, false));
    }
}
