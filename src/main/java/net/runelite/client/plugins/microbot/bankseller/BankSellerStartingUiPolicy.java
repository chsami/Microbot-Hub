package net.runelite.client.plugins.microbot.bankseller;

/** Only reveal missing startup slots. Never substitutes hidden widgets for a verified snapshot. */
final class BankSellerStartingUiPolicy {
    enum Action { NONE, CLOSE_EXCHANGE, CLOSE_BANK, OPEN_INVENTORY, OPEN_EQUIPMENT }

    private BankSellerStartingUiPolicy() {
    }

    static Action nextAction(boolean inventoryMissing, boolean equipmentMissing,
                             boolean exchangeOpen, boolean bankOpen) {
        if (!inventoryMissing && !equipmentMissing) {
            return Action.NONE;
        }
        if (exchangeOpen) {
            return Action.CLOSE_EXCHANGE;
        }
        if (bankOpen) {
            return Action.CLOSE_BANK;
        }
        return inventoryMissing ? Action.OPEN_INVENTORY : Action.OPEN_EQUIPMENT;
    }
}
