package net.runelite.client.plugins.microbot.bankseller;

/** Dependency-free repricing regression cases; main() never starts or controls a client. */
public final class BankSellerRepricePolicyTest {
    private static final long PLACED_AT = 1_000_000;
    private static int assertions;

    private BankSellerRepricePolicyTest() {
    }

    public static void main(String[] args) {
        waitsForTwoLaterCompletedRounds();
        blockedOffersHaveAnElapsedTimeFallback();
        invalidTrackingFailsClosed();
        roundAndTimestampArithmeticDoesNotOverflow();
        largePriceCutIsPositiveAndBounded();
        oneGpOffersHaveAFiniteWait();
        System.out.println("BankSellerRepricePolicyTest passed (" + assertions + " assertions)");
    }

    private static void waitsForTwoLaterCompletedRounds() {
        check(!due(4, 4, 0, 100), "The placement round must not count as a later selling round");
        check(!due(4, 5, 0, 100), "One later completed round must retain the asking price");
        check(due(4, 6, 0, 100), "The second later completed round must trigger a large price cut");
        check(due(4, 7, 0, 100), "A missed second-round deadline must remain due");
        check(due(0, 2, 0, 2), "Even a 2-GP offer should drop to 1 GP after two later rounds");
        check(!due(0, 2, 90_000, 1), "An already-1-GP offer must never be repriced");
    }

    private static void blockedOffersHaveAnElapsedTimeFallback() {
        check(!due(0, 0, 89_999, 100), "Blocked slots must wait until the full 90-second timeout");
        check(due(0, 0, 90_000, 100), "A blocked offer is due at exactly 90 seconds");
        check(due(0, 0, 90_001, 100), "A blocked offer remains due after 90 seconds");
        check(due(4, 5, 90_000, 100), "Elapsed time also releases an offer with only one later round");
        check(!due(4, 5, 89_999, 100), "Neither one later round nor 89,999 ms alone is enough");
    }

    private static void invalidTrackingFailsClosed() {
        for (long invalidPrice : new long[]{Long.MIN_VALUE, -1, 0, 1}) {
            check(!due(0, 2, 90_000, invalidPrice),
                    "Invalid or floor price " + invalidPrice + " must not authorize repricing");
        }
        check(!BankSellerRepricePolicy.shouldReprice(-1, 2, PLACED_AT, PLACED_AT + 90_000, 100),
                "An invalid placement round must fail closed even after the timeout");
        check(!BankSellerRepricePolicy.shouldReprice(0, -1, PLACED_AT, PLACED_AT + 90_000, 100),
                "An invalid completed round must fail closed");
        check(!BankSellerRepricePolicy.shouldReprice(4, 3, PLACED_AT, PLACED_AT + 90_000, 100),
                "A reversed round counter must fail closed even after the timeout");
        for (long invalidTime : new long[]{Long.MIN_VALUE, -1, 0}) {
            check(!BankSellerRepricePolicy.shouldReprice(0, 2, invalidTime, PLACED_AT, 100),
                    "An invalid placement timestamp " + invalidTime + " must fail closed");
        }
        check(!BankSellerRepricePolicy.shouldReprice(0, 2, PLACED_AT, PLACED_AT - 1, 100),
                "A clock that moved behind placement must fail closed despite two rounds");
        check(!BankSellerRepricePolicy.shouldReprice(0, 2, PLACED_AT, Long.MIN_VALUE, 100),
                "A negative current time must fail closed");
    }

    private static void roundAndTimestampArithmeticDoesNotOverflow() {
        check(due(0, Integer.MAX_VALUE, 0, 100), "A very large valid round distance must remain due");
        check(due(Integer.MAX_VALUE - 2, Integer.MAX_VALUE, 0, 100),
                "The second round near the integer limit must remain due");
        check(!due(Integer.MAX_VALUE - 1, Integer.MAX_VALUE, 0, 100),
                "One round near the integer limit must not become two");
        check(!due(Integer.MAX_VALUE, 0, 90_000, 100),
                "Counter wrap must fail closed instead of masquerading as later rounds");
        check(BankSellerRepricePolicy.shouldReprice(0, 0, Long.MAX_VALUE - 90_000, Long.MAX_VALUE, 100),
                "Elapsed-time subtraction must handle the maximum positive timestamp");
        check(!BankSellerRepricePolicy.shouldReprice(0, 0, Long.MAX_VALUE - 89_999, Long.MAX_VALUE, 100),
                "The elapsed-time boundary near the long limit must remain exact");
        check(BankSellerRepricePolicy.shouldReprice(0, 2, Long.MAX_VALUE, Long.MAX_VALUE, 100),
                "Two completed rounds remain valid at identical maximum timestamps");
        check(!BankSellerRepricePolicy.shouldReprice(0, 2, Long.MIN_VALUE, Long.MAX_VALUE, 100),
                "A negative timestamp must not be allowed to overflow elapsed-time arithmetic");
    }

    private static void largePriceCutIsPositiveAndBounded() {
        long[][] cases = {
                {Long.MIN_VALUE, 1}, {-1, 1}, {0, 1}, {1, 1}, {2, 1}, {9, 1},
                {10, 1}, {19, 1}, {20, 2}, {99, 9}, {100, 10}, {1_000, 100},
                {((long) Integer.MAX_VALUE - 1) * 10, Integer.MAX_VALUE - 1},
                {(long) Integer.MAX_VALUE * 10, Integer.MAX_VALUE},
                {(long) Integer.MAX_VALUE * 10 + 9, Integer.MAX_VALUE},
                {Long.MAX_VALUE, Integer.MAX_VALUE}
        };
        for (long[] entry : cases) {
            check(BankSellerRepricePolicy.reducedPrice(entry[0]) == entry[1],
                    "Price " + entry[0] + " should reduce safely to " + entry[1]);
        }
    }

    private static void oneGpOffersHaveAFiniteWait() {
        check(!BankSellerRepricePolicy.stalledAtOneGp(PLACED_AT, PLACED_AT + 59_999, 1),
                "A 1-GP offer must receive its full 60-second window");
        check(BankSellerRepricePolicy.stalledAtOneGp(PLACED_AT, PLACED_AT + 60_000, 1),
                "A blocked 1-GP offer times out at exactly 60 seconds");
        check(BankSellerRepricePolicy.stalledAtOneGp(PLACED_AT, PLACED_AT + 60_001, 1),
                "A blocked 1-GP offer remains stalled after its timeout");
        for (long otherPrice : new long[]{Long.MIN_VALUE, -1, 0, 2, Long.MAX_VALUE}) {
            check(!BankSellerRepricePolicy.stalledAtOneGp(PLACED_AT, PLACED_AT + 90_000, otherPrice),
                    "Price " + otherPrice + " must not be classified as an exhausted 1-GP sale");
        }
        for (long invalidTime : new long[]{Long.MIN_VALUE, -1, 0}) {
            check(!BankSellerRepricePolicy.stalledAtOneGp(invalidTime, PLACED_AT, 1),
                    "Invalid placement timestamp " + invalidTime + " must not authorize a stop");
        }
        check(!BankSellerRepricePolicy.stalledAtOneGp(PLACED_AT, PLACED_AT - 1, 1),
                "A reversed clock must not authorize a 1-GP timeout");
        check(BankSellerRepricePolicy.stalledAtOneGp(Long.MAX_VALUE - 60_000, Long.MAX_VALUE, 1),
                "The 1-GP timeout must handle maximum positive timestamps without overflow");
        check(!BankSellerRepricePolicy.stalledAtOneGp(Long.MAX_VALUE - 59_999, Long.MAX_VALUE, 1),
                "The 1-GP timeout boundary near the long limit must remain exact");
    }

    private static boolean due(int placedRound, int completedRound, long elapsedMs, long price) {
        return BankSellerRepricePolicy.shouldReprice(placedRound, completedRound,
                PLACED_AT, PLACED_AT + elapsedMs, price);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
