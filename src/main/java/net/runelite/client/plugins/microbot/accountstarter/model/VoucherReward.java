package net.runelite.client.plugins.microbot.accountstarter.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public enum VoucherReward {
    // F2P amounts paid; the voucher window (interface 643) understates them as 75 arrows and 150 runes
    CLERK(10164, "Grand Exchange Clerk", new RewardItem(995, 2500)),
    LOWE(10165, "Lowe", new RewardItem(884, 100)),
    AUBURY(10166, "Aubury", new RewardItem(558, 200), new RewardItem(556, 200)),
    APOTHECARY(10167, "Apothecary", new RewardItem(3010, 5));

    private final int varbit;
    private final String npcName;
    private final List<RewardItem> rewards;

    VoucherReward(int varbit, String npcName, RewardItem... rewards) {
        this.varbit = varbit;
        this.npcName = npcName;
        this.rewards = Collections.unmodifiableList(Arrays.asList(rewards));
    }

    public int varbit() { return varbit; }
    public String npcName() { return npcName; }
    public List<RewardItem> rewards() { return rewards; }
}
