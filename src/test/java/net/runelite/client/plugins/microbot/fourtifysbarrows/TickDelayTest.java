package net.runelite.client.plugins.microbot.fourtifysbarrows;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Random;
import net.runelite.api.Player;
import net.runelite.client.plugins.microbot.fourtifysbarrows.config.BarrowsConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TickDelayTest
{
    @Test
    public void idleTicksConsumeTheCountdownAndTheFinalDecrementStillWaits()
    {
        FourtifysBarrowsPlugin plugin = new FourtifysBarrowsPlugin();
        plugin.setTickDelay(2);
        assertTrue(plugin.waitForDelay(player(-1)));
        assertTrue(plugin.waitForDelay(player(-1)));
        assertFalse(plugin.waitForDelay(player(-1)));
    }

    @Test
    public void actionAnimationConsumesTheCountdownWithoutPostAnimationDelay()
    {
        FourtifysBarrowsPlugin plugin = new FourtifysBarrowsPlugin();
        plugin.setTickDelay(1);
        assertTrue(plugin.waitForDelay(player(625)));
        assertFalse(plugin.waitForDelay(player(625)));
        assertFalse(plugin.waitForDelay(player(-1)));
    }

    @Test
    public void defaultTimingAddsOneTickDelay() throws Exception
    {
        BarrowsConfig config = new BarrowsConfig() {};
        assertEquals(0, config.tickDelayMin());
        assertEquals(1, config.tickDelayMax());
        assertEquals(1, config.tickDelayTarget());
        assertEquals(0, config.tickDelayDeviation());
        FourtifysBarrowsPlugin plugin = new FourtifysBarrowsPlugin();
        inject(plugin, "config", config);
        for (double gaussian : new double[] {-10, 0, 10})
        {
            inject(plugin, "random", new FixedGaussian(gaussian));
            assertEquals(1, plugin.rollTickDelay());
        }
    }

    @Test
    public void explicitDelayRetainsItsFullTickBudgetDuringContinuousAnimation()
    {
        FourtifysBarrowsPlugin plugin = new FourtifysBarrowsPlugin();
        plugin.setTickDelay(3);
        assertTrue(plugin.waitForDelay(player(625)));
        assertTrue(plugin.waitForDelay(player(625)));
        assertTrue(plugin.waitForDelay(player(625)));
        assertFalse(plugin.waitForDelay(player(625)));
    }

    @Test
    public void unweightedDistributionRoundsThenClamps() throws Exception
    {
        FourtifysBarrowsPlugin plugin = plugin(false, .75);
        assertEquals(5, plugin.rollTickDelay());
        inject(plugin, "random", new FixedGaussian(-10));
        assertEquals(1, plugin.rollTickDelay());
        inject(plugin, "random", new FixedGaussian(10));
        assertEquals(8, plugin.rollTickDelay());
    }

    @Test
    public void weightedDistributionUsesAbsoluteGaussianLogarithmAndTruncates() throws Exception
    {
        FourtifysBarrowsPlugin plugin = plugin(true, -.1353352832366127);
        assertEquals(7, plugin.rollTickDelay());
        inject(plugin, "random", new FixedGaussian(2));
        assertEquals(1, plugin.rollTickDelay());
        inject(plugin, "random", new FixedGaussian(0));
        assertEquals(8, plugin.rollTickDelay());
    }

    private static FourtifysBarrowsPlugin plugin(boolean weighted, double gaussian) throws Exception
    {
        FourtifysBarrowsPlugin plugin = new FourtifysBarrowsPlugin();
        BarrowsConfig config = new BarrowsConfig()
        {
            @Override
            public int tickDelayMin()
            {
                return 1;
            }

            @Override
            public int tickDelayMax()
            {
                return 8;
            }

            @Override
            public int tickDelayTarget()
            {
                return 3;
            }

            @Override
            public int tickDelayDeviation()
            {
                return 2;
            }

            @Override
            public boolean tickDelayWeightedDistribution()
            {
                return weighted;
            }
        };
        inject(plugin, "config", config);
        inject(plugin, "random", new FixedGaussian(gaussian));
        return plugin;
    }

    private static Player player(int animation)
    {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
            (proxy, method, args) -> method.getName().equals("getAnimation") ? animation : null);
    }

    private static void inject(Object instance, String name, Object value) throws Exception
    {
        Field field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(instance, value);
    }

    private static final class FixedGaussian extends Random
    {
        private final double value;

        FixedGaussian(double value)
        {
            this.value = value;
        }

        @Override
        public double nextGaussian()
        {
            return value;
        }
    }
}
