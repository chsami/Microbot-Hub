package net.runelite.client.plugins.microbot.accountstarter;

import net.runelite.client.plugins.microbot.accountstarter.run.VoucherActivity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoucherOptionTest {
    @Test
    void onlyAnOptionNamingTheVoucherIsPicked() {
        assertEquals(Optional.of("I have a Combat path voucher."),
                VoucherActivity.voucherOption(List.of("I'd like to trade.", "I have a Combat path voucher.", "Yes")));
        assertEquals(Optional.empty(), VoucherActivity.voucherOption(List.of("Yes", "No")));
        assertEquals(Optional.empty(), VoucherActivity.voucherOption(List.of("Can I buy something with this voucher?")));
    }

    @Test
    void theLastClaimBanksOnlyTheRewards() {
        for (int id : new int[]{995, 884, 558, 556, 3010}) assertTrue(VoucherActivity.isReward(id), "reward " + id);
        assertFalse(VoucherActivity.isReward(24131), "the voucher");
        assertFalse(VoucherActivity.isReward(882), "bronze arrows");
    }
}
