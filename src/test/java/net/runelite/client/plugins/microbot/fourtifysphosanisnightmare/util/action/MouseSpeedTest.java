package net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.util.action;

import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.NaturalMouse;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api.SpeedManager;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.DefaultOvershootManager;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.DefaultSpeedManager;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.MouseMotionNature;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MouseSpeedTest
{
    private final NaturalMouse natural = new NaturalMouse();
    private final MouseSpeed speed = new MouseSpeed(() -> natural);

    @Test
    public void theFightGlidesFastAndTheSettingsComeBackAfter()
    {
        natural.getFactory();
        MouseMotionNature nature = natural.nature;
        SpeedManager before = nature.getSpeedManager();
        int variation = nature.getReactionTimeVariationMs();
        int overshoots = ((DefaultOvershootManager) nature.getOvershootManager()).getOvershoots();

        speed.fast(true);
        assertTrue(speed.isFast());
        assertNotSame(before, nature.getSpeedManager());
        long glide = nature.getSpeedManager().getFlowWithTime(100).y;
        assertTrue(glide <= 4 * MouseSpeed.GLIDE_MILLIS, "glide " + glide);
        assertEquals(0, nature.getReactionTimeBaseMs());
        assertEquals(MouseSpeed.REACTION_VARIATION_MILLIS, nature.getReactionTimeVariationMs());
        assertEquals(0, ((DefaultOvershootManager) nature.getOvershootManager()).getOvershoots());

        speed.fast(false);
        assertFalse(speed.isFast());
        assertSame(before, nature.getSpeedManager());
        assertEquals(variation, nature.getReactionTimeVariationMs());
        assertEquals(overshoots, ((DefaultOvershootManager) nature.getOvershootManager()).getOvershoots());
    }

    @Test
    public void settingsMicrobotRebuiltDuringTheFightAreLeftAsTheyAre()
    {
        speed.fast(true);
        SpeedManager rebuilt = new DefaultSpeedManager();
        natural.nature.setSpeedManager(rebuilt);
        speed.fast(false);
        assertSame(rebuilt, natural.nature.getSpeedManager());
    }

    @Test
    public void withoutAMouseNothingChanges()
    {
        MouseSpeed none = new MouseSpeed(() -> null);
        none.fast(true);
        assertFalse(none.isFast());
        none.fast(false);
    }
}
