package net.runelite.client.plugins.microbot.fourtifystormenteddemons;

import java.util.Random;
import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TickDelayTest
{
    @Test
    public void unweightedGaussianRoundsAndClampsToTheConfiguredBounds() throws Exception
    {
        Fixture f = new Fixture(1, 8, 3, 2, false);
        f.gaussian(.75);
        assertEquals(5, f.session.plugin.rollTickDelay());
        f.gaussian(-10);
        assertEquals(1, f.session.plugin.rollTickDelay());
        f.gaussian(10);
        assertEquals(8, f.session.plugin.rollTickDelay());
    }

    @Test
    public void weightedDistributionUsesMagnitudeAndTruncatesWithinBounds() throws Exception
    {
        Fixture f = new Fixture(1, 8, 3, 2, true);
        f.gaussian(.1353352832366127);
        assertEquals(7, f.session.plugin.rollTickDelay());
        f.gaussian(-.1353352832366127);
        assertEquals(7, f.session.plugin.rollTickDelay());
        f.gaussian(2);
        assertEquals(1, f.session.plugin.rollTickDelay());
        f.gaussian(0);
        assertEquals(8, f.session.plugin.rollTickDelay());
    }

    @Test
    public void theDelayIsSampledOnceWhenARequestIsDispatchedAndNotBefore() throws Exception
    {
        Fixture f = new Fixture(5, 5, 5, 1, false);
        FixedGaussian gaussian = f.gaussian(0);
        f.session.decisionOnly();
        assertEquals(0, gaussian.samples);
        f.session.clientTick();
        assertEquals(1, gaussian.samples);
        assertEquals(1, f.session.requests());
        f.session.clientTick();
        assertEquals(1, gaussian.samples);
    }

    @Test
    public void aRejectedRequestDoesNotSampleOrArmTheDelay() throws Exception
    {
        Fixture f = new Fixture(5, 5, 5, 1, false);
        FixedGaussian gaussian = f.gaussian(0);
        f.session.decisionOnly();
        f.session.standAt(new WorldPoint(4134, 4382, 0));
        f.session.clientTick();
        assertEquals(0, gaussian.samples);
        assertEquals(0, f.session.requests());
        f.session.tick();
        assertEquals(1, gaussian.samples);
        assertEquals(1, f.session.requests());
    }

    @Test
    public void everyIdleTickOfTheDelayIsSkippedBeforeAnotherRequest() throws Exception
    {
        Fixture f = new Fixture(5, 5, 5, 1, false);
        f.session.tick();
        assertEquals(1, f.session.requests());
        for (int remaining = 5; remaining >= 1; remaining--)
        {
            f.session.tick();
            assertEquals("Short pause (" + remaining + (remaining == 1 ? " tick)" : " ticks)"), f.session.plugin.getStatus());
            assertEquals(1, f.session.requests());
        }
        f.session.tick();
        assertEquals(2, f.session.requests());
    }

    @Test
    public void animationDoesNotConsumeTheIdleTickCountdown() throws Exception
    {
        Fixture f = new Fixture(5, 5, 5, 1, false);
        f.session.tick();
        f.session.world.animation = 808;
        for (int index = 0; index < 4; index++)
        {
            f.session.tick();
            assertEquals("Attacking Tormented Demon", f.session.plugin.getStatus(), "An animation keeps the activity status");
        }
        assertEquals(1, f.session.requests());
        f.session.world.animation = -1;
        f.session.tick();
        assertEquals("Short pause (5 ticks)", f.session.plugin.getStatus());
        f.session.tick();
        assertEquals("Short pause (4 ticks)", f.session.plugin.getStatus());
        assertEquals(1, f.session.requests());
    }

    @Test
    public void zeroTickDelayNeverShowsACountdown() throws Exception
    {
        Fixture f = new Fixture(0, 0, 0, 0, false);
        f.session.tick();
        assertEquals(1, f.session.requests());
        for (int index = 0; index < 3; index++)
        {
            f.session.tick();
            assertFalse(f.session.plugin.getStatus().startsWith("Timeout"));
        }
    }

    @Test
    public void finalIdleDecrementStillReportsAWait() throws Exception
    {
        Fixture f = new Fixture(0, 0, 0, 0, false);
        f.session.plugin.setTickDelay(1);
        f.session.world.animation = 808;
        assertTrue(f.session.plugin.waitForDelay(f.session.world.player));
        assertTrue(f.session.plugin.waitForDelay(f.session.world.player));
        f.session.world.animation = -1;
        assertTrue(f.session.plugin.waitForDelay(f.session.world.player));
        assertFalse(f.session.plugin.waitForDelay(f.session.world.player));
    }

    private static final class FixedGaussian extends Random
    {
        private final double value;
        private int samples;

        private FixedGaussian(double value)
        {
            this.value = value;
        }

        @Override
        public double nextGaussian()
        {
            samples++;
            return value;
        }
    }

    private static final class Fixture
    {
        private final PluginTestFixture session;

        private Fixture(int minimum, int maximum, int target, int deviation, boolean weighted) throws Exception
        {
            session = new PluginTestFixture();
            session.config.delayMin = minimum;
            session.config.delayMax = maximum;
            session.config.delayTarget = target;
            session.config.delayDeviation = deviation;
            session.config.weighted = weighted;
            session.enableAndDrain();
        }

        private FixedGaussian gaussian(double value) throws Exception
        {
            FixedGaussian random = new FixedGaussian(value);
            session.inject("random", random);
            return random;
        }
    }
}
