package net.runelite.client.plugins.microbot.dropyramidplunder;

import java.util.Random;

/**
 * The owner's repeated-click rhythm, measured from the 2026-11-09 Pyramid Plunder recording
 * (2,289 consecutive clicks on the same trap, chest, door, urn or sarcophagus).
 *
 * <p>Intervals are drawn from the recorded histogram, not a fixed rate: most fall between 120 and
 * 240 ms, about one in eight is a 300-900 ms hesitation and a few are longer than a second. A run
 * of short intervals is a burst and a long one is the gap between bursts, as recorded. Each
 * plunder run gets its own tempo so no two loops repeat.
 */
final class PyramidPlunderClicks {
    /** Clicks between consecutive menu clicks, in 20 ms bins starting at 60 ms. */
    private static final int BIN_START_MS = 60;
    private static final int BIN_WIDTH_MS = 20;
    private static final int[] RECORDED_INTERVALS = {
            1, 3, 25, 139, 458, 628, 439, 158, 73, 35, 11, 11, 21, 25, 31, 13, 20, 13, 12, 7,
            14, 9, 6, 6, 6, 11, 8, 4, 5, 7, 5, 5, 5, 3, 7, 5, 1, 2, 9, 2,
            1, 1, 4, 5, 1, 3, 0, 4, 3, 0, 0, 0, 1, 1, 0, 2, 2, 0, 3, 2,
            1, 0, 1, 2, 0, 3, 0, 1, 0, 2, 0, 3};
    private static final int RECORDED_TOTAL;
    static {
        int total = 0;
        for (int count : RECORDED_INTERVALS) total += count;
        RECORDED_TOTAL = total;
    }

    /** An interval at least this long ends a burst; the script looks at the game before the next. */
    static final int BURST_GAP_MS = 290;
    static final int MIN_INTERVAL_MS = 55;

    private PyramidPlunderClicks() { }

    static int recordedTotal() { return RECORDED_TOTAL; }

    /** One plunder run's clicking style. */
    static final class Cadence {
        private final Random random;
        private final double tempo;

        Cadence(Random random) {
            this.random = random;
            // The recording's own run-to-run spread: a little quicker or slower, never mechanical.
            this.tempo = clamp(1.0 + random.nextGaussian() * 0.06, 0.88, 1.16);
        }

        double tempo() { return tempo; }

        /** Milliseconds until the next click on the same target. */
        int nextIntervalMs() {
            int pick = random.nextInt(RECORDED_TOTAL);
            int bin = 0;
            while (bin < RECORDED_INTERVALS.length - 1 && pick >= RECORDED_INTERVALS[bin]) {
                pick -= RECORDED_INTERVALS[bin];
                bin++;
            }
            double base = BIN_START_MS + bin * BIN_WIDTH_MS + random.nextDouble() * BIN_WIDTH_MS;
            double jitter = random.nextGaussian() * 5.0;
            return Math.max(MIN_INTERVAL_MS, (int) Math.round(base * tempo + jitter));
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
