package net.runelite.client.plugins.microbot.aiomagic;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Humanised click delays: mostly fast, with quick bursts and rare hesitations, never the same value twice in a row.
 */
public class ClickDelay {
    private static final double BURST_CHANCE = 0.12;
    private static final double HESITATION_CHANCE = 0.03;

    private final Random random; // null = use the calling thread's ThreadLocalRandom
    private int last = -1;

    public ClickDelay() {
        this(null);
    }

    ClickDelay(Random random) {
        this.random = random;
    }

    /**
     * @return the next delay in ms within [min, max]; min and max are swapped if given the wrong way round
     */
    public int next(int min, int max) {
        Random random = this.random != null ? this.random : ThreadLocalRandom.current();
        int low = Math.max(0, Math.min(min, max));
        int high = Math.max(low, Math.max(min, max));
        double fraction = Math.pow(random.nextDouble(), 3);
        double roll = random.nextDouble();
        if (roll < HESITATION_CHANCE) {
            fraction = 0.7 + random.nextDouble() * 0.3;
        } else if (roll < HESITATION_CHANCE + BURST_CHANCE) {
            fraction *= 0.25;
        }
        int delay = low + (int) Math.round(fraction * (high - low));
        if (delay == last && high > low) {
            delay = delay == high ? delay - 1 : delay + 1;
        }
        last = delay;
        return delay;
    }
}
