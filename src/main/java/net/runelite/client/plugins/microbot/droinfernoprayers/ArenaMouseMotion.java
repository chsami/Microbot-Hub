package net.runelite.client.plugins.microbot.droinfernoprayers;

import java.util.Random;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api.MouseMotionFactory;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.DefaultOvershootManager;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.MouseMotionNature;

public final class ArenaMouseMotion {
    private ArenaMouseMotion() {}

    static final long OVERSHOOT_FLOOR_MS = 25;
    static final long FIGHT_BASE_MS = 45;


    public static MouseMotionFactory withoutMistakes(MouseMotionFactory source) {
        var original = source.getNature();
        var copy = new MouseMotionNature();
        copy.setSystemCalls(original.getSystemCalls());
        copy.setMouseInfo(original.getMouseInfo());
        copy.setDeviationProvider(original.getDeviationProvider());
        copy.setNoiseProvider(original.getNoiseProvider());

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
