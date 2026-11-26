package net.runelite.client.plugins.microbot.bankseller;

final class BankSellerPassPolicy {
    enum Action { WAIT_FOR_SLOT, SELL_INVENTORY, BANK }

    private BankSellerPassPolicy() {
    }

    static Action nextAction(boolean waitingForOfferSlot, int availableSlots, boolean sellableInventory) {
        if (waitingForOfferSlot && availableSlots == 0) {
            return Action.WAIT_FOR_SLOT;
        }
        return sellableInventory ? Action.SELL_INVENTORY : Action.BANK;
    }
}
