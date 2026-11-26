package net.runelite.client.plugins.microbot.accountstarter.logic;

import lombok.Value;

@Value
public class PlannerTuning {
    double noise;
    int claimBatch;
    long blockMinMs;
    long blockMaxMs;
    double buyBuffer;
    int voucherStart;
}
