package net.runelite.client.plugins.microbot.autofarmer;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.MouseMotionNature;
/** Temporary movement tuning; restores the previous client values when released. */
final class MouseFeel {
    private MouseMotionNature nature;
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
    }
    void restore() {
        if (nature == null) return;
        if (nature.getReactionTimeBaseMs() == Math.max(reaction,220)) nature.setReactionTimeBaseMs(reaction);
        if (nature.getReactionTimeVariationMs() == Math.max(variation,180)) nature.setReactionTimeVariationMs(variation);
        if (nature.getMinSteps() == Math.max(steps,16)) nature.setMinSteps(steps);
        nature = null;
    }
}
