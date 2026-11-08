package net.runelite.client.plugins.microbot.fourtifysgemmining;

import java.util.Random;
import net.runelite.api.Item;
import net.runelite.client.plugins.microbot.fourtifysgemmining.config.GemMiningConfig;
import net.runelite.client.plugins.microbot.Microbot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TickDelayTest
{
    @Test
    public void unweightedGaussianRoundsAndClampsToTheConfiguredBounds() throws Exception
    {
        TimingConfig config = new TimingConfig();
        config.minimum = 1;
        config.maximum = 8;
        config.target = 3;
        config.deviation = 2;
        Fixture f = new Fixture(config);
        f.gaussian(.75);
        assertEquals(5, f.plugin.rollTickDelay());
        f.gaussian(-10);
        assertEquals(1, f.plugin.rollTickDelay());
        f.gaussian(10);
        assertEquals(8, f.plugin.rollTickDelay());
    }

    @Test
    public void weightedDistributionUsesMagnitudeAndTruncatesWithinBounds() throws Exception
    {
        TimingConfig config = new TimingConfig();
        config.minimum = 1;
        config.maximum = 8;
        config.target = 3;
        config.deviation = 2;
        config.weighted = true;
        Fixture f = new Fixture(config);
        f.gaussian(.1353352832366127);
        assertEquals(7, f.plugin.rollTickDelay());
        f.gaussian(-.1353352832366127);
        assertEquals(7, f.plugin.rollTickDelay());
        f.gaussian(2);
        assertEquals(1, f.plugin.rollTickDelay());
        f.gaussian(0);
        assertEquals(8, f.plugin.rollTickDelay());
    }

    @Test
    public void twoTickDelaySkipsBothIdleTicksBeforeAnotherRequest() throws Exception
    {
        Fixture f = new Fixture(TimingConfig.fixed(2));
        f.tick();
        assertEquals(1, f.world.requests.size());
        f.gainGem();
        f.tick();
        assertEquals(1, f.world.requests.size());
        f.tick();
        assertEquals(1, f.world.requests.size());
        f.tick();
        assertEquals(2, f.world.requests.size());
    }

    @Test
    public void animationDoesNotConsumeTheIdleTickCountdown() throws Exception
    {
        Fixture f = new Fixture(TimingConfig.fixed(2));
        f.tick();
        f.gainGem();
        f.world.animation = 625;
        for (int i = 0; i < 4; i++)
        {
            f.tick();
        }
        assertEquals(1, f.world.requests.size());
        f.world.animation = -1;
        f.tick();
        assertEquals("Short pause (2 ticks)", f.plugin.getStatus());
        f.tick();
        assertEquals("Short pause (1 tick)", f.plugin.getStatus());
        assertEquals(1, f.world.requests.size());
        f.tick();
        assertEquals(2, f.world.requests.size());
    }

    @Test
    public void aPauseEndingDuringMiningRestoresTheMiningStatus() throws Exception
    {
        Fixture f = new Fixture(TimingConfig.fixed(2));
        f.tick();
        f.gainGem();
        f.world.animation = 625;
        f.tick();
        String mining = f.plugin.getStatus();
        Microbot.pauseAllScripts.set(true);
        try
        {
            f.tick();
            assertEquals("Paused by Microbot (pause button or break). Resumes when unpaused", f.plugin.getStatus());
        }
        finally
        {
            Microbot.pauseAllScripts.set(false);
        }
        f.tick();
        assertEquals(mining, f.plugin.getStatus());
    }

    @Test
    public void zeroTickDelayAllowsTheNextEligibleGameTick() throws Exception
    {
        Fixture f = new Fixture(TimingConfig.fixed(0));
        f.tick();
        f.gainGem();
        f.tick();
        assertEquals(2, f.world.requests.size());
    }

    @Test
    public void longAnimationKeepsThePendingMiningAttemptAliveAcrossTheDelayGate() throws Exception
    {
        Fixture f = new Fixture(TimingConfig.fixed(2));
        f.tick();
        f.world.animation = 625;
        for (int i = 0; i < 20; i++)
        {
            f.tick();
        }
        assertEquals(1, f.world.requests.size());
        f.world.animation = -1;
        f.tick();
        f.tick();
        f.tick();
        assertEquals(1, f.world.requests.size());
        f.gainGem();
        f.tick();
        assertEquals(1, f.world.requests.size());
        f.tick();
        f.tick();
        assertEquals(2, f.world.requests.size());
    }

    @Test
    public void pendingAttemptPollDoesNotSampleOrRearmTheDelay() throws Exception
    {
        Fixture f = new Fixture(TimingConfig.fixed(2));
        FixedGaussian gaussian = new FixedGaussian(0);
        f.inject("random", gaussian);
        f.tick();
        assertEquals(1, gaussian.samples);
        f.tick();
        f.tick();
        f.tick();
        assertEquals(1, f.world.requests.size());
        assertEquals(1, gaussian.samples);
        f.gainGem();
        f.tick();
        assertEquals(1, f.world.requests.size());
        assertEquals(2, gaussian.samples);
        f.tick();
        f.tick();
        assertEquals(2, f.world.requests.size());
        assertEquals(3, gaussian.samples);
    }

    @Test
    public void admittedOrRejectedRequestDoesNotSampleDelayBeforeDispatch() throws Exception
    {
        Fixture fixture = new Fixture(TimingConfig.fixed(2));
        FixedGaussian gaussian = new FixedGaussian(0);
        fixture.inject("random", gaussian);
        fixture.session.decisionOnly();
        assertEquals(0, gaussian.samples);
        assertTrue(fixture.world.requests.isEmpty());
        fixture.world.objects.clear();
        fixture.session.clientTick();
        assertEquals(0, gaussian.samples);
        assertTrue(fixture.world.requests.isEmpty());
        fixture.world.rock(2825, 3000);
        fixture.tick();
        assertEquals(1, gaussian.samples);
        assertEquals(1, fixture.world.requests.size());
    }

    @Test
    public void samePositionPreconditionRejectionDoesNotSampleDelayOrArmAnAttempt() throws Exception
    {
        Fixture fixture = new Fixture(TimingConfig.fixed(2));
        FixedGaussian gaussian = new FixedGaussian(0);
        fixture.inject("random", gaussian);
        for (int i = 0; i < 40; i++)
        {
            fixture.session.decisionOnly();
            fixture.world.animation = 625;
            fixture.session.clientTick();
            fixture.world.animation = -1;
        }
        assertEquals(0, gaussian.samples);
        assertTrue(fixture.world.requests.isEmpty());
        fixture.tick();
        assertEquals(1, gaussian.samples);
        assertEquals(1, fixture.world.requests.size());
    }

    @Test
    public void finalIdleDecrementStillReportsAWait() throws Exception
    {
        Fixture f = new Fixture(TimingConfig.fixed(0));
        f.plugin.setTickDelay(1);
        f.world.animation = 625;
        assertTrue(f.plugin.waitForDelay(f.world.player));
        assertTrue(f.plugin.waitForDelay(f.world.player));
        f.world.animation = -1;
        assertTrue(f.plugin.waitForDelay(f.world.player));
        assertFalse(f.plugin.waitForDelay(f.world.player));
    }

    private static final class TimingConfig implements GemMiningConfig
    {
        private int minimum;
        private int maximum = 1;
        private int target;
        private int deviation = 1;
        private boolean weighted;

        @Override
        public int tickDelayMin()
        {
            return minimum;
        }

        @Override
        public int tickDelayMax()
        {
            return maximum;
        }

        @Override
        public int tickDelayTarget()
        {
            return target;
        }

        @Override
        public int tickDelayDeviation()
        {
            return deviation;
        }

        @Override
        public boolean tickDelayWeightedDistribution()
        {
            return weighted;
        }

        private static TimingConfig fixed(int ticks)
        {
            TimingConfig config = new TimingConfig();
            config.minimum = ticks;
            config.maximum = ticks;
            return config;
        }
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
        private final TestWorld world;
        private final FourtifysGemMiningPlugin plugin;

        private Fixture(TimingConfig config) throws Exception
        {
            session = new PluginTestFixture(config);
            world = session.world;
            plugin = session.plugin;
            session.enableAndDrain();
        }

        private void gaussian(double value) throws Exception
        {
            inject("random", new FixedGaussian(value));
        }

        private void inject(String name, Object value) throws Exception
        {
            session.inject(name, value);
        }

        private void gainGem()
        {
            world.inventory = new Item[] {new Item(1265, 1), new Item(1625, 1)};
        }

        private void tick()
        {
            session.tick();
        }
    }
}
