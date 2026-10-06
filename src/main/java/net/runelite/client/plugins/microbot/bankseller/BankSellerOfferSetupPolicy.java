package net.runelite.client.plugins.microbot.bankseller;

/** Strict readback of the GE setup's full, unabridged unit-price label. */
final class BankSellerOfferSetupPolicy {
    private BankSellerOfferSetupPolicy() {
    }

    static long unitPrice(String text) {
        if (text == null) {
            return -1;
        }
        String value = text.replaceAll("<[^>]*>", "").trim();
        if (!value.matches("(?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+) coins?")) {
            return -1;
        }
        try {
            long price = Long.parseLong(value.substring(0, value.indexOf(' ')).replace(",", ""));
            return price > 0 && price <= Integer.MAX_VALUE ? price : -1;
        } catch (NumberFormatException invalid) {
            return -1;
        }
    }
}
