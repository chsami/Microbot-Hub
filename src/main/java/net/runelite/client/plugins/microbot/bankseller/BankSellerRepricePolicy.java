package net.runelite.client.plugins.microbot.bankseller;

/** Pure repricing deadlines; the caller must verify that the live offer is owned and still selling. */
final class BankSellerRepricePolicy {
    private static final int LATER_SELLING_ROUNDS = 2;
    private static final long REPRICE_TIMEOUT_MS = 90_000;
    private static final long ONE_GP_GIVE_UP_MS = 60_000;

    private BankSellerRepricePolicy() {
    }

    /** Reprice after two later completed rounds, or after a blocked offer has waited 90 seconds. */
    static boolean shouldReprice(int placedRound, int completedRound, long placedAt,
                                 long now, long currentPrice) {
        if (currentPrice <= 1 || placedRound < 0 || completedRound < placedRound
                || !validElapsedTime(placedAt, now)) {
            return false;
        }
        return (long) completedRound - placedRound >= LATER_SELLING_ROUNDS
                || now - placedAt >= REPRICE_TIMEOUT_MS;
    }

    /** Cut the current asking price by 90%, keeping a valid positive integer GE price. */
    static int reducedPrice(long currentPrice) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, currentPrice / 10));
    }

    /** A blocked 1-GP sale cannot be reduced again; let the caller stop instead of looping forever. */
    static boolean stalledAtOneGp(long placedAt, long now, long price) {
        return price == 1 && validElapsedTime(placedAt, now)
                && now - placedAt >= ONE_GP_GIVE_UP_MS;
    }

    private static boolean validElapsedTime(long placedAt, long now) {
        return placedAt > 0 && now >= placedAt;
    }
}
