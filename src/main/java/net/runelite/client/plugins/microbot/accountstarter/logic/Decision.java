package net.runelite.client.plugins.microbot.accountstarter.logic;

import java.util.Map;
import lombok.Value;
import net.runelite.client.plugins.microbot.accountstarter.model.ApTask;
import net.runelite.client.plugins.microbot.accountstarter.model.VoucherReward;

@Value
public class Decision {
    public enum Kind { WAIT, DO_TASK, CLAIM_AT_JON, STARTER_KIT, USE_LAMP, REDEEM_VOUCHER, DO_QUEST, DONE, STOP }

    Kind kind;
    ApTask task;
    VoucherReward voucher;
    String reason;
    Map<ApTask, String> parked;
}
