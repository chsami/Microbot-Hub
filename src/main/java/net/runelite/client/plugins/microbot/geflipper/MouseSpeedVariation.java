package net.runelite.client.plugins.microbot.geflipper;

import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api.SpeedManager;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.Flow;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.util.Pair;

/** A private duration decorator whose sampled speed remains fixed for one movement. */
final class MouseSpeedVariation implements SpeedManager {
    private static final int MINIMUM_PERCENT = 80;
    private static final int MAXIMUM_PERCENT = 120;
    private static final long MAXIMUM_DURATION_MS = 10_000;

    private final SpeedManager source;
    private final int speedPercent;

    private MouseSpeedVariation(SpeedManager source, int speedPercent) {
        this.source = source;
        this.speedPercent = speedPercent;
    }

    static SpeedManager forMovement(SpeedManager source, boolean enabled, WaitingMouse.RandomInt random) {
        if (source == null) throw new IllegalStateException("Mouse speed source is unavailable.");
        if (!enabled) return source;
        if (random == null) throw new IllegalStateException("Mouse speed random source is unavailable.");
        int sampled = random.inclusive(MINIMUM_PERCENT, MAXIMUM_PERCENT);
        int percent = Math.max(MINIMUM_PERCENT, Math.min(MAXIMUM_PERCENT, sampled));
        return new MouseSpeedVariation(source, percent);
    }

    @Override
    public Pair<Flow, Long> getFlowWithTime(double distance) {
        Pair<Flow, Long> baseline = source.getFlowWithTime(distance);
        if (baseline == null) throw new IllegalStateException("Mouse speed source returned no movement.");
        if (baseline.x == null) throw new IllegalStateException("Mouse speed source returned no flow.");
        if (baseline.y == null || baseline.y <= 0) {
            throw new IllegalStateException("Mouse speed source returned an invalid duration.");
        }
        // Double arithmetic avoids overflowing a valid long duration before it is bounded.
        long duration = Math.round(baseline.y * 100.0 / speedPercent);
        duration = Math.max(1, Math.min(MAXIMUM_DURATION_MS, duration));
        return new Pair<>(baseline.x, duration);
    }
}
