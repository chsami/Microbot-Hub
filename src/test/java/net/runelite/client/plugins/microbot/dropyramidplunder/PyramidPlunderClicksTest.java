package net.runelite.client.plugins.microbot.dropyramidplunder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Random;
import net.runelite.client.plugins.microbot.dropyramidplunder.PyramidPlunderClicks.Cadence;
import org.junit.jupiter.api.Test;

class PyramidPlunderClicksTest {
    @Test void theHistogramHoldsEveryRecordedRepeatClick() {
        // trap 487 + chest 139 + door 845 + urn 753 + sarcophagus 65 in the 2026-11-09 recording.
        assertEquals(2289, PyramidPlunderClicks.recordedTotal());
    }

    @Test void intervalsFollowTheRecordingAndAreNeverAFixedRate() {
        Cadence cadence = new Cadence(new Random(7));
        int[] samples = new int[4000];
        for (int i = 0; i < samples.length; i++) samples[i] = cadence.nextIntervalMs();
        int[] sorted = samples.clone();
        Arrays.sort(sorted);
        assertTrue(sorted[0] >= PyramidPlunderClicks.MIN_INTERVAL_MS);
        int median = sorted[sorted.length / 2];
        assertTrue(median >= 140 && median <= 220, "median " + median);
        long gaps = Arrays.stream(samples).filter(ms -> ms >= PyramidPlunderClicks.BURST_GAP_MS).count();
        double share = gaps / (double) samples.length;
        assertTrue(share > 0.08 && share < 0.25, "burst gap share " + share);
        assertTrue(Arrays.stream(samples).distinct().count() > 150, "intervals must vary");
        assertTrue(sorted[sorted.length - 1] > 900, "the recorded long hesitations are kept");
    }

    @Test void eachRunGetsItsOwnTempo() {
        Random random = new Random(11);
        double first = new Cadence(random).tempo();
        double second = new Cadence(random).tempo();
        double third = new Cadence(random).tempo();
        assertNotEquals(first, second);
        assertNotEquals(second, third);
        for (double tempo : new double[] {first, second, third}) assertTrue(tempo >= 0.88 && tempo <= 1.16);
    }
}
