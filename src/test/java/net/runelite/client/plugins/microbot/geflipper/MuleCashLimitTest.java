package net.runelite.client.plugins.microbot.geflipper;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MuleCashLimitTest {
    private static final int CASH_FLOOR = 50_000_000;

    @Test
    void cashBelowOrEqualToTheFloorIsKeptForFlipping() {
        assertEquals(0, transfer(0, CASH_FLOOR));
        assertEquals(0, transfer(CASH_FLOOR - 1L, CASH_FLOOR));
        assertEquals(0, transfer(CASH_FLOOR, CASH_FLOOR));
    }

    @Test
    void excessMustReachTheMinimumTransferBeforeMuling() {
        assertEquals(0, transfer(CASH_FLOOR + 1L, CASH_FLOOR));
        assertEquals(0, transfer(CASH_FLOOR + MulePolicy.MIN_TRANSFER_GP - 1L, CASH_FLOOR));
        assertEquals(MulePolicy.MIN_TRANSFER_GP,
            transfer(CASH_FLOOR + (long) MulePolicy.MIN_TRANSFER_GP, CASH_FLOOR));
    }

    @Test
    void onlyTheExcessIsSentAndTheSelectedFloorRemains() {
        assertEquals(5_000_000, transfer(55_000_000L, CASH_FLOOR));
        assertEquals(25_000_000, transfer(75_000_000L, CASH_FLOOR));
        assertEquals(CASH_FLOOR, 75_000_000L - transfer(75_000_000L, CASH_FLOOR));
    }

    @Test
    void zeroInvalidAndNegativeLimitsCannotTriggerTransfers() {
        assertEquals(0, transfer(50_000_000L, 0));
        assertEquals(0, transfer(50_000_000L, -1));
        assertEquals(0, transfer(50_000_000L, MulePolicy.parseGp("invalid")));
        assertEquals(0, transfer(-1, CASH_FLOOR));
        assertEquals(0, transfer(Long.MIN_VALUE, CASH_FLOOR));
    }

    @Test
    void maximumSupportedFloorParsesWithoutBeingLowered() {
        assertEquals(Integer.MAX_VALUE, MulePolicy.parseGp("2147483647"));
        assertEquals(Integer.MAX_VALUE, MulePolicy.parseGp("2.147483647B"));
        assertEquals(Integer.MAX_VALUE, MulePolicy.parseGp("2,147,483,647 gp"));
    }

    @Test
    void commasAreOnlyThousandsSeparators() {
        assertEquals(1_500_000, MulePolicy.parseGp("1,500,000"));
        assertEquals(50_000_000, MulePolicy.parseGp("50,000,000"));
        assertEquals(1_500_000, MulePolicy.parseGp("1,500k"));
        assertEquals(1_500_000, MulePolicy.parseGp("1.5M"));
        // "1,5M" is a typo for 1.5M; it must not become 15M.
        assertEquals(-1, MulePolicy.parseGp("1,5M"));
        assertEquals(-1, MulePolicy.parseGp("1,50,000"));
        assertEquals(-1, MulePolicy.parseGp(",500"));
        assertEquals(-1, MulePolicy.parseGp("1,000,00"));
    }

    @Test
    void oversizedFloorsAreRejectedInsteadOfSilentlyReducingWorkingCash() {
        assertEquals(-1, MulePolicy.parseGp("2147483648"));
        assertEquals(-1, MulePolicy.parseGp("2.147483648B"));
        assertEquals(-1, MulePolicy.parseGp("2.1474836471B"));
        assertEquals(-1, MulePolicy.parseGp("3B"));
        assertEquals(0, transfer(3_500_000_000L, MulePolicy.parseGp("3B")));
    }

    @Test
    void maximumIntegerFloorDoesNotOverflowItsExcessCalculation() {
        int floor = Integer.MAX_VALUE;
        assertEquals(0, transfer(floor, floor));
        assertEquals(0, transfer((long) floor + MulePolicy.MIN_TRANSFER_GP - 1, floor));
        assertEquals(MulePolicy.MIN_TRANSFER_GP,
            transfer((long) floor + MulePolicy.MIN_TRANSFER_GP, floor));
    }

    @Test
    void inventoryAndBankCashCanBeCombinedWithoutIntegerOverflow() {
        int inventory = 30_000_000;
        int bank = 25_000_000;
        assertEquals(5_000_000, transfer((long) inventory + bank, CASH_FLOOR));

        long twoMaximumStacks = (long) Integer.MAX_VALUE + Integer.MAX_VALUE;
        assertEquals(Integer.MAX_VALUE, transfer(twoMaximumStacks, CASH_FLOOR));
        assertEquals(Integer.MAX_VALUE, transfer(twoMaximumStacks, Integer.MAX_VALUE));
    }

    @Test
    void eachTransferIsCappedToOneStackWhileRetainingAtLeastTheFloor() {
        long cash = (long) Integer.MAX_VALUE * 2;
        int amount = transfer(cash, CASH_FLOOR);
        assertEquals(Integer.MAX_VALUE, amount);
        assertTrue(cash - amount >= CASH_FLOOR);
        assertEquals(Integer.MAX_VALUE, transfer(Long.MAX_VALUE, CASH_FLOOR));
    }

    @Test
    void portfolioPreparationIsNeededOnlyForMuleableExcessThatIsNotLiquid() {
        assertFalse(MulePolicy.shouldLiquidateForMule(CASH_FLOOR + 1L, 10_000_000L,
            true, CASH_FLOOR, CASH_FLOOR));
        assertTrue(MulePolicy.shouldLiquidateForMule(75_000_000L, 10_000_000L,
            true, CASH_FLOOR, CASH_FLOOR));
        assertFalse(MulePolicy.needsPortfolioLiquidity(75_000_000L, 25_000_000L, CASH_FLOOR));
        assertFalse(MulePolicy.needsPortfolioLiquidity(CASH_FLOOR, 0, CASH_FLOOR));
    }

    @Test
    void investedCapitalCountsTowardTheLimitWithoutRequiringThatMuchLooseCash() {
        assertEquals(5_000_000, MulePolicy.calculatePortfolioTransferAmount(55_000_000L, 5_000_000L, CASH_FLOOR));
        assertEquals(15_000_000, MulePolicy.calculatePortfolioTransferAmount(65_000_000L, 20_000_000L, CASH_FLOOR));
        assertEquals(0, MulePolicy.calculatePortfolioTransferAmount(45_000_000L, 10_000_000L, CASH_FLOOR));
        assertEquals(0, MulePolicy.calculatePortfolioTransferAmount(CASH_FLOOR, CASH_FLOOR, CASH_FLOOR));
    }

    @Test
    void transferIsCappedToVerifiedGpEvenWhenThePortfolioIsWorthMore() {
        assertEquals(25_000_000, MulePolicy.requiredPortfolioTransferAmount(75_000_000L, CASH_FLOOR));
        assertEquals(10_000_000, MulePolicy.calculatePortfolioTransferAmount(75_000_000L, 10_000_000L, CASH_FLOOR));
        assertEquals(0, MulePolicy.calculatePortfolioTransferAmount(75_000_000L, 0, CASH_FLOOR));
        assertTrue(MulePolicy.needsPortfolioLiquidity(75_000_000L, 10_000_000L, CASH_FLOOR));
    }

    @Test
    void requiredPortfolioExcessIsBoundedToOneTradeStack() {
        assertEquals(Integer.MAX_VALUE, MulePolicy.requiredPortfolioTransferAmount(Long.MAX_VALUE, CASH_FLOOR));
        assertFalse(MulePolicy.needsPortfolioLiquidity(Long.MAX_VALUE, Integer.MAX_VALUE, CASH_FLOOR));
        assertEquals(0, MulePolicy.requiredPortfolioTransferAmount(Long.MIN_VALUE, CASH_FLOOR));
        assertEquals(0, MulePolicy.requiredPortfolioTransferAmount(75_000_000L, 0));
    }

    @Test
    void offeredGpIsIncludedOnceOnlyWhileTheTradeIsActuallyOpen() {
        MuleSession.Snapshot open = portfolioSnapshot(true);
        MuleSession.Snapshot closed = portfolioSnapshot(false);
        assertEquals(55_000_000L, open.workingCapitalGp());
        assertEquals(50_000_000L, closed.workingCapitalGp());
    }

    @Test
    void legacyInternalThresholdAndReserveRulesRemainValid() {
        assertEquals(20_000_000,
            MulePolicy.calculateTransferAmount(50_000_000L, 50_000_000, 30_000_000));
        assertEquals(0, MulePolicy.calculateTransferAmount(49_999_999L, 50_000_000, 30_000_000));
        assertEquals(0, MulePolicy.calculateTransferAmount(75_000_000L, 30_000_000, 50_000_000));
        assertEquals(0, MulePolicy.calculateTransferAmount(75_000_000L, CASH_FLOOR, -1));
    }

    private static int transfer(long looseCoins, int floor) {
        return MulePolicy.calculatePortfolioTransferAmount(looseCoins, looseCoins, floor);
    }

    private static MuleSession.Snapshot portfolioSnapshot(boolean tradeOpen) {
        return new MuleSession.Snapshot(true, 101L, "FlipperAcc", 330,
            MulePolicy.GE_CENTER_X, MulePolicy.GE_CENTER_Y, MulePolicy.GE_CENTER_PLANE,
            true, false, false, false, 0, 0, 50_000_000L, false,
            tradeOpen, false, "BankMule", 5_000_000, false, 0, true, true);
    }
}
