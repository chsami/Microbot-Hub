package net.runelite.client.plugins.microbot.accountstarter.logic;

import lombok.Data;

@Data
public class ResumeState {
    private String currentTask;
    private int coinsSpent;
    private boolean starterKitClaimed;
    private String lastJonReply;
    private int mudfootStep;
    /** Captain Harlan refuses for 30 minutes, across restarts too. */
    private long harlanClaimMs;
}
