package net.runelite.client.plugins.microbot.accountstarter.logic;

import java.util.Random;
import lombok.Value;

@Value
public class Habits {
    double eatAtFraction;
    int bankAtFreeSlots;
    int claimBatch;
    long blockMinMs;
    long blockMaxMs;
    double plannerNoise;
    boolean buryInBatches;
    double glanceAfterLevel;
    double lootSkip;
    double gePremium;
    long stuckAfterMs;
    double buyBuffer;
    double checkPathsChance;
    int foodStock;
    double restMargin;
    int voucherStart;

    public static Habits draw(long seed) {
        Random r = new Random(seed);
        double eat = 0.35 + r.nextDouble() * 0.25;
        int bank = r.nextInt(4);
        int batch = 1 + r.nextInt(3);
        long min = 15 * 60_000L + (long) (r.nextDouble() * 15 * 60_000L);
        long max = min + 60_000L + (long) (r.nextDouble() * (45 * 60_000L - min - 60_000L));
        double noise = 0.2 + r.nextDouble() * 0.4;
        boolean bury = r.nextBoolean();
        double glance = 0.2 + r.nextDouble() * 0.5;
        double skip = 0.05 + r.nextDouble() * 0.20;
        double premium = 1.05 + r.nextDouble() * 0.10;
        long stuck = 3 * 60_000L + (long) (r.nextDouble() * 3 * 60_000L);
        r.nextInt(8); // unused draw: removing it shifts every later habit for existing accounts
        double buffer = 1.10 + r.nextDouble() * 0.30;
        double check = 0.15 + r.nextDouble() * 0.45;
        int food = 6 + r.nextInt(5);
        double margin = 0.10 + r.nextDouble() * 0.15;
        int voucher = r.nextInt(4);
        return new Habits(eat, bank, batch, min, max, noise, bury, glance, skip, premium, stuck, buffer, check, food, margin, voucher);
    }

    public PlannerTuning tuning() {
        return new PlannerTuning(plannerNoise, claimBatch, blockMinMs, blockMaxMs, buyBuffer, voucherStart);
    }
}
