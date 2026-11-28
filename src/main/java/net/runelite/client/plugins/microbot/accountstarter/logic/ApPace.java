package net.runelite.client.plugins.microbot.accountstarter.logic;

import java.util.Random;

public final class ApPace {
    static final int REACTION_FLOOR_MS = 140;
    static final int READ_CAP_MS = 2_000;
    private static final double LAPSE_CHANCE = 0.02;

    private final double reactionMedian;
    private final double msPerChar;
    /** Share of skilling re-clicks made late, as someone half watching would. Drawn per session. */
    private final double awayChance;

    private ApPace(double reactionMedian, double msPerChar, double awayChance) {
        this.reactionMedian = reactionMedian;
        this.msPerChar = msPerChar;
        this.awayChance = awayChance;
    }

    public static ApPace draw(long seed) {
        Random r = new Random(seed);
        return new ApPace(250 + r.nextDouble() * 60, 6 + r.nextDouble() * 6, 0.10 + r.nextDouble() * 0.15);
    }

    public ApPace forSession(Random r) {
        return new ApPace(reactionMedian * (0.90 + r.nextDouble() * 0.25), msPerChar, 0.10 + r.nextDouble() * 0.15);
    }

    /** The wait before clicking the next tree, rock or spot: mostly a reaction, sometimes 2-20 s, short ones likelier. */
    public long reclickMs(Random r) {
        long ms = reactionMs(r);
        return r.nextDouble() < awayChance ? ms + 2_000 + Math.round(18_000 * r.nextDouble() * r.nextDouble()) : ms;
    }

    public long reactionMs(Random r) {
        long ms = Math.max(REACTION_FLOOR_MS, Math.round(reactionMedian * Math.exp(0.28 * r.nextGaussian())));
        return r.nextDouble() < LAPSE_CHANCE ? ms + 600 + r.nextInt(2401) : ms;
    }

    public long levelUpMs(Random r, double glanceChance) {
        return reactionMs(r) + (r.nextDouble() < glanceChance ? 1000 + r.nextInt(3001) : 0);
    }

    public long readMs(String text, Random r) {
        int chars = text == null ? 0 : text.replaceAll("<[^>]*>", "").trim().length();
        double read = chars * msPerChar * Math.exp(0.20 * r.nextGaussian());
        return Math.min(READ_CAP_MS, reactionMs(r) + Math.round(read));
    }
}
