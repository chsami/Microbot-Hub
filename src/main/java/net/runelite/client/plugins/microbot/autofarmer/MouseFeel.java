package net.runelite.client.plugins.microbot.autofarmer;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api.DeviationProvider;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api.SpeedManager;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.DoublePoint;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.MouseMotionNature;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.util.Pair;
/** Temporary movement tuning; restores the previous client values when released. */
final class MouseFeel {
    private static final double DEVIATION_SCALE = 1.2;
    private static final double TRAVEL_TIME_SCALE = 1.25;

    private MouseMotionNature nature;
    private DeviationProvider originalDeviation;
    private DeviationProvider scaledDeviation;
    private SpeedManager originalSpeed;
    private SpeedManager scaledSpeed;
    private int reaction, variation, steps;
    void enable(MouseMotionNature next) {
        if (next == nature) return;
        restore();
        if (next == null) return;
        nature = next;
        reaction = next.getReactionTimeBaseMs();
        variation = next.getReactionTimeVariationMs();
        steps = next.getMinSteps();
        next.setReactionTimeBaseMs(Math.max(reaction, 220));
        next.setReactionTimeVariationMs(Math.max(variation, 180));
        next.setMinSteps(Math.max(steps, 16));

        originalSpeed = next.getSpeedManager();
        if (originalSpeed != null) {
            SpeedManager baseSpeed = originalSpeed;
            scaledSpeed = distance -> {
                var movement = baseSpeed.getFlowWithTime(distance);
                return new Pair<>(movement.x, Math.round(movement.y * TRAVEL_TIME_SCALE));
            };
            next.setSpeedManager(scaledSpeed);
        }

        originalDeviation = next.getDeviationProvider();
        if (originalDeviation != null) {
            DeviationProvider baseDeviation = originalDeviation;
            scaledDeviation = (distance, progress) -> {
                DoublePoint offset = baseDeviation.getDeviation(distance, progress);
                return new DoublePoint(offset.getX() * DEVIATION_SCALE, offset.getY() * DEVIATION_SCALE);
            };
            next.setDeviationProvider(scaledDeviation);
        }
    }
    void restore() {
        if (nature == null) return;
        if (nature.getReactionTimeBaseMs() == Math.max(reaction,220)) nature.setReactionTimeBaseMs(reaction);
        if (nature.getReactionTimeVariationMs() == Math.max(variation,180)) nature.setReactionTimeVariationMs(variation);
        if (nature.getMinSteps() == Math.max(steps,16)) nature.setMinSteps(steps);
        if (scaledDeviation != null && nature.getDeviationProvider() == scaledDeviation) {
            nature.setDeviationProvider(originalDeviation);
        }
        if (scaledSpeed != null && nature.getSpeedManager() == scaledSpeed) {
            nature.setSpeedManager(originalSpeed);
        }
        originalSpeed = null;
        scaledSpeed = null;
        originalDeviation = null;
        scaledDeviation = null;
        nature = null;
    }
}
