package net.runelite.client.plugins.microbot.droinfernoprayers;

import java.util.Random;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api.MouseMotionFactory;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.DefaultOvershootManager;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.MouseMotionNature;

/** Humanized paths with explicit fast timing, without deliberately missing an arena target. */
public final class ArenaMouseMotion {
    private ArenaMouseMotion() {}

    /** Overshoot corrections stay proportional to the move (move / 1.8) with this floor, so both fit the tick. */
    static final long OVERSHOOT_FLOOR_MS = 25;
    static final long FIGHT_BASE_MS = 45;

    /**
     * Firecape only (Inferno and Colosseum use withoutMistakes, as in 1.0.26). Dro's super-fast-gamer path shape (deviation, noise, flows) with simulated mistakes per
     * Rs2AntibanSettings, on fast arena timing: 45 ms base (half of 1.0.26), no reaction delay. The caller's speed
     * scope still applies. Builds a fresh nature, so the shared NaturalMouse nature is never mutated.
     */
    public static MouseMotionFactory withMistakes(MouseMotionFactory source) {
        var original = source.getNature();
        var copy = new MouseMotionNature();
        copy.setSystemCalls(original.getSystemCalls());
        copy.setMouseInfo(original.getMouseInfo());
        copy.setDeviationProvider(new net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.SinusoidalDeviationProvider(
            net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.SinusoidalDeviationProvider.DEFAULT_SLOPE_DIVIDER));
        copy.setNoiseProvider(new net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.DefaultNoiseProvider(
            net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.DefaultNoiseProvider.DEFAULT_NOISINESS_DIVIDER));
        // Super-fast-gamer flows minus variatingFlow, whose zero buckets stall the cursor mid-move.
        // FIGHT_BASE_MS: a full tick holds several clicks on one pointer lane; 1.0.26's 90 ms left walks a tick late.
        var speed=new net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.DefaultSpeedManager(java.util.List.of(
            new net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.Flow(net.runelite.client.plugins.microbot.util.mouse.naturalmouse.util.FlowTemplates.slowStartupFlow()),
            new net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.Flow(net.runelite.client.plugins.microbot.util.mouse.naturalmouse.util.FlowTemplates.slowStartup2Flow()),
            new net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.Flow(net.runelite.client.plugins.microbot.util.mouse.naturalmouse.util.FlowTemplates.adjustingFlow()),
            new net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.Flow(net.runelite.client.plugins.microbot.util.mouse.naturalmouse.util.FlowTemplates.jaggedFlow())));
        speed.setMouseMovementBaseTimeMs(FIGHT_BASE_MS);
        copy.setSpeedManager(speed);
        copy.setTimeToStepsDivider(original.getTimeToStepsDivider());
        copy.setMinSteps(original.getMinSteps());
        copy.setEffectFadeSteps(original.getEffectFadeSteps());
        copy.setReactionTimeBaseMs(0);
        copy.setReactionTimeVariationMs(0);
        var overshoots = new DefaultOvershootManager(new Random());
        overshoots.setOvershoots(net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings.simulateMistakes ? 2 : 0);
        overshoots.setMinDistanceForOvershoots(3);
        overshoots.setMinOvershootMovementMs(OVERSHOOT_FLOOR_MS);
        copy.setOvershootManager(overshoots);
        return new MouseMotionFactory(copy);
    }

    public static MouseMotionFactory withoutMistakes(MouseMotionFactory source) {
        var original = source.getNature();
        var copy = new MouseMotionNature();
        copy.setSystemCalls(original.getSystemCalls());
        copy.setMouseInfo(original.getMouseInfo());
        copy.setDeviationProvider(original.getDeviationProvider());
        copy.setNoiseProvider(original.getNoiseProvider());
        // Private fast timing; never inherit the mutable raw 500ms manager.
        var speed=new net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.DefaultSpeedManager(java.util.List.of(
            new net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.Flow(net.runelite.client.plugins.microbot.util.mouse.naturalmouse.util.FlowTemplates.slowStartupFlow()),
            new net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.Flow(net.runelite.client.plugins.microbot.util.mouse.naturalmouse.util.FlowTemplates.slowStartup2Flow()),
            new net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.Flow(net.runelite.client.plugins.microbot.util.mouse.naturalmouse.util.FlowTemplates.adjustingFlow())));
        speed.setMouseMovementBaseTimeMs(90);
        copy.setSpeedManager(speed);
        copy.setTimeToStepsDivider(original.getTimeToStepsDivider());
        copy.setMinSteps(original.getMinSteps());
        copy.setEffectFadeSteps(original.getEffectFadeSteps());
        copy.setReactionTimeBaseMs(0);
        copy.setReactionTimeVariationMs(0);
        var overshoots = new DefaultOvershootManager(new Random());
        overshoots.setOvershoots(0);
        copy.setOvershootManager(overshoots);
        return new MouseMotionFactory(copy);
    }
}
