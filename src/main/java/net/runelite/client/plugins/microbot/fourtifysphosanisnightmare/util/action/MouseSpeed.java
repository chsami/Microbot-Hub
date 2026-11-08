package net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.util.action;

import java.util.List;
import java.util.function.Supplier;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.NaturalMouse;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api.OvershootManager;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api.SpeedManager;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.DefaultOvershootManager;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.DefaultSpeedManager;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.Flow;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.MouseMotionNature;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.util.FlowTemplates;

/**
 * Shortens the cursor's glide to each click while the fight needs several clicks in one tick, and puts the
 * mouse settings back afterwards. The cursor still travels a curved path to each target.
 */
public final class MouseSpeed
{
    /** The glide takes this long, up to twice it, whatever the distance. */
    static final long GLIDE_MILLIS = 25;
    static final int REACTION_VARIATION_MILLIS = 10;
    private final Supplier<NaturalMouse> mouse;
    private final SpeedManager fast = new DefaultSpeedManager(List.of(new Flow(FlowTemplates.variatingFlow()),
        new Flow(FlowTemplates.slowStartupFlow()), new Flow(FlowTemplates.adjustingFlow()), new Flow(FlowTemplates.jaggedFlow())));
    private MouseMotionNature nature;
    private SpeedManager speed;
    private int reactionBase;
    private int reactionVariation;
    private int overshoots = -1;

    public MouseSpeed(Supplier<NaturalMouse> mouse)
    {
        this.mouse = mouse;
        ((DefaultSpeedManager) fast).setMouseMovementBaseTimeMs(GLIDE_MILLIS);
    }

    /** Uses the short glide, or puts back the settings it replaced. */
    public void fast(boolean on)
    {
        NaturalMouse current = mouse.get();
        if (on && current != null)
        {
            // The first glide builds the mouse's settings, so they are built before being replaced.
            current.getFactory();
            MouseMotionNature target = current.nature;
            if (target.getSpeedManager() == fast)
            {
                return;
            }
            nature = target;
            speed = target.getSpeedManager();
            reactionBase = target.getReactionTimeBaseMs();
            reactionVariation = target.getReactionTimeVariationMs();
            OvershootManager overshoot = target.getOvershootManager();
            overshoots = overshoot instanceof DefaultOvershootManager ? ((DefaultOvershootManager) overshoot).getOvershoots() : -1;
            target.setSpeedManager(fast);
            target.setReactionTimeBaseMs(0);
            target.setReactionTimeVariationMs(REACTION_VARIATION_MILLIS);
            if (overshoots >= 0)
            {
                ((DefaultOvershootManager) overshoot).setOvershoots(0);
            }
        }
        else if (!on && nature != null)
        {
            // A change of mouse speed in Microbot rebuilds its settings; those are left as they are.
            if (nature.getSpeedManager() == fast)
            {
                nature.setSpeedManager(speed);
                nature.setReactionTimeBaseMs(reactionBase);
                nature.setReactionTimeVariationMs(reactionVariation);
                if (overshoots >= 0 && nature.getOvershootManager() instanceof DefaultOvershootManager)
                {
                    ((DefaultOvershootManager) nature.getOvershootManager()).setOvershoots(overshoots);
                }
            }
            nature = null;
            speed = null;
        }
    }

    public boolean isFast()
    {
        return nature != null;
    }
}
