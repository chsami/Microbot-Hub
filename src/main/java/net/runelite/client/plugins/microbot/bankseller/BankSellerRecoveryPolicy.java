package net.runelite.client.plugins.microbot.bankseller;

/** A recovered interruption may hand off once; final restoration must still finish normally. */
final class BankSellerRecoveryPolicy {
    enum Action { WAIT, CONTINUE_BANK_SELLING, FINISH, STOP }

    private BankSellerRecoveryPolicy() {
    }

    static Action nextAction(BankSellerOriginalOffers.Result result, boolean recoveryOnly,
                             boolean pendingRestoration, boolean protectionSnapshotted, boolean accountMatches) {
        if (result == null || result == BankSellerOriginalOffers.Result.ERROR) {
            return Action.STOP;
        }
        if (result == BankSellerOriginalOffers.Result.WAITING) {
            return Action.WAIT;
        }
        if (pendingRestoration || !protectionSnapshotted || !accountMatches) {
            return Action.STOP;
        }
        return recoveryOnly ? Action.CONTINUE_BANK_SELLING : Action.FINISH;
    }
}
