package net.runelite.client.plugins.microbot.bankseller;

public final class BankSellerOfferSetupPolicyTest {
    public static void main(String[] args) {
        String[] labels = {"1 coin", "1 coins", "250 coins", "1,000 coins",
                "<col=ffffff>12,345 coins</col>", "2147483647 coins", "2,147,483,647 coins"};
        long[] prices = {1, 1, 250, 1000, 12345, Integer.MAX_VALUE, Integer.MAX_VALUE};
        int assertions = 0;
        for (int i = 0; i < labels.length; i++) {
            check(BankSellerOfferSetupPolicy.unitPrice(labels[i]) == prices[i], labels[i]);
            assertions++;
        }
        String[] invalid = {null, "", "0 coins", "-1 coins", "2147483648 coins",
                "999999999999999999999999999 coins", "1k coins", "1.5m coins", "1,00 coins",
                "1,0000 coins", "1 000 coins", "100", "100 gp", "total 100 coins", "100 coins each"};
        for (String label : invalid) {
            check(BankSellerOfferSetupPolicy.unitPrice(label) == -1, label);
            assertions++;
        }
        System.out.println("BankSellerOfferSetupPolicyTest passed (" + assertions + " assertions)");
    }

    private static void check(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError("Unexpected GE price readback: " + label);
        }
    }
}
