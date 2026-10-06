package net.runelite.client.plugins.microbot.bankseller;

import static net.runelite.client.plugins.microbot.bankseller.BankSellerRecoveryPolicy.Action;
import static net.runelite.client.plugins.microbot.bankseller.BankSellerOriginalOffers.Result;

/** Pure recovery handoff regressions; never starts or controls a game client. */
public final class BankSellerRecoveryPolicyTest {
    private static int assertions;

    private BankSellerRecoveryPolicyTest() {
    }

    public static void main(String[] args) {
        coversEveryResultAndGuardCombination();
        recoveryHandsOffOnlyOnceToTheFreshSellingController();
        System.out.println("BankSellerRecoveryPolicyTest passed (" + assertions + " assertions)");
    }

    private static void coversEveryResultAndGuardCombination() {
        // Bits: recovery-only, restoration pending, protection snapshotted,
        // account matches. READY can finish/continue only at masks 12 and 13.
        Action[] readyExpected = {
                Action.STOP, Action.STOP, Action.STOP, Action.STOP,
                Action.STOP, Action.STOP, Action.STOP, Action.STOP,
                Action.STOP, Action.STOP, Action.STOP, Action.STOP,
                Action.FINISH, Action.CONTINUE_BANK_SELLING, Action.STOP, Action.STOP
        };
        for (int mask = 0; mask < readyExpected.length; mask++) {
            boolean recoveryOnly = (mask & 1) != 0;
            boolean pendingRestoration = (mask & 2) != 0;
            boolean protectionSnapshotted = (mask & 4) != 0;
            boolean accountMatches = (mask & 8) != 0;
            String context = "Recovery guard combination " + mask;
            expect(Action.STOP, null, recoveryOnly, pendingRestoration,
                    protectionSnapshotted, accountMatches, context + " with unknown result");
            expect(Action.STOP, Result.ERROR, recoveryOnly, pendingRestoration,
                    protectionSnapshotted, accountMatches, context + " with error");
            expect(Action.WAIT, Result.WAITING, recoveryOnly, pendingRestoration,
                    protectionSnapshotted, accountMatches, context + " still waiting");
            expect(readyExpected[mask], Result.READY, recoveryOnly, pendingRestoration,
                    protectionSnapshotted, accountMatches, context + " ready");
        }
    }

    private static void recoveryHandsOffOnlyOnceToTheFreshSellingController() {
        expect(Action.WAIT, Result.WAITING, true, true, true, true,
                "An old journal still restoring never starts liquidation");
        expect(Action.CONTINUE_BANK_SELLING, Result.READY, true, false, true, true,
                "A safely completed old journal hands off to bank selling");

        // The replacement is a fresh controller, not another recovery-only
        // completion. The same protected startup snapshot/account are retained.
        boolean freshControllerRecoveryOnly = false;
        expect(Action.WAIT, Result.WAITING, freshControllerRecoveryOnly, true, true, true,
                "Fresh parking must finish before bank selling proceeds");
        expect(Action.FINISH, Result.READY, freshControllerRecoveryOnly, false, true, true,
                "Normal selling followed by restoration finishes the run");
        expect(Action.FINISH, Result.READY, freshControllerRecoveryOnly, false, true, true,
                "A fresh-controller completion cannot create another selling handoff");
    }

    private static void expect(Action expected, Result result, boolean recoveryOnly,
                               boolean pendingRestoration, boolean protectionSnapshotted,
                               boolean accountMatches, String message) {
        Action actual = BankSellerRecoveryPolicy.nextAction(result, recoveryOnly,
                pendingRestoration, protectionSnapshotted, accountMatches);
        if (actual != expected) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
        assertions++;
    }
}
