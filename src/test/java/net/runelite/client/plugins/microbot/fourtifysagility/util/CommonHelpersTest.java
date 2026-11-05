package net.runelite.client.plugins.microbot.fourtifysagility.util;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.Player;
import net.runelite.client.plugins.microbot.fourtifysagility.util.action.ActionQueue;
import net.runelite.client.plugins.microbot.fourtifysagility.util.config.TimingConfig;
import net.runelite.client.plugins.microbot.fourtifysagility.util.inventory.InventoryGainTracker;
import net.runelite.client.plugins.microbot.fourtifysagility.util.lifecycle.SessionLifecycle;
import net.runelite.client.plugins.microbot.fourtifysagility.util.timing.TickDelay;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CommonHelpersTest
{
    @Test
    public void queuesEnforceCapacityFifoAndOneRequestPerDispatch()
    {
        ActionQueue queue = new ActionQueue();
        List<Integer> observed = new ArrayList<>();
        for (int i = 0; i < 50; i++)
        {
            int request = i;
            assertTrue(queue.offer(() -> observed.add(request)));
        }
        assertFalse(queue.offer(() -> observed.add(50)));
        queue.dispatch();
        assertEquals(List.of(0), observed);
        queue.dispatch();
        assertEquals(List.of(0, 1), observed);
        queue.clear();
        queue.dispatch();
        assertEquals(List.of(0, 1), observed);
    }

    @Test
    public void clearingOnePluginsQueueCannotCancelAnotherPluginsRequest()
    {
        ActionQueue agility = new ActionQueue();
        ActionQueue mining = new ActionQueue();
        AtomicInteger count = new AtomicInteger();
        agility.offer(count::incrementAndGet);
        mining.offer(count::incrementAndGet);
        agility.clear();
        agility.dispatch();
        mining.dispatch();
        assertEquals(1, count.get());
    }

    @Test
    public void dispatchWindowIncludesCycleTwentyEightAndExcludesTwentyNine()
    {
        assertTrue(ActionQueue.withinWindow(128, 100));
        assertFalse(ActionQueue.withinWindow(129, 100));
    }

    @Test
    public void countdownStateIsPerPluginAndOnlyConsumesIdleAnimationTicks()
    {
        TickDelay first = new TickDelay();
        TickDelay second = new TickDelay();
        List<String> status = new ArrayList<>();
        first.set(1);
        assertFalse(second.waitForDelay(player(-1), status::add));
        assertTrue(first.waitForDelay(player(625), status::add));
        assertTrue(first.waitForDelay(player(-1), status::add));
        assertFalse(first.waitForDelay(player(-1), status::add));
        // The animating tick neither counts down nor shows the countdown.
        assertEquals(List.of("Short pause (1 tick)"), status);
    }

    @Test
    public void defaultsAndGaussianClampingMatchTheApprovedProfile()
    {
        TimingConfig config = new TimingConfig() { };
        assertEquals(0, config.tickDelayMin());
        assertEquals(1, config.tickDelayMax());
        assertEquals(1, config.tickDelayTarget());
        assertEquals(0, config.tickDelayDeviation());
        assertFalse(config.tickDelayWeightedDistribution());
        assertEquals(1, TickDelay.roll(gaussian(-100), config));
        assertEquals(1, TickDelay.roll(gaussian(0), config));
        assertEquals(1, TickDelay.roll(gaussian(100), config));
        TimingConfig varied = new TimingConfig()
        {
            @Override
            public int tickDelayDeviation()
            {
                return 1;
            }
        };
        assertEquals(0, TickDelay.roll(gaussian(-100), varied));
        assertEquals(1, TickDelay.roll(gaussian(100), varied));
    }

    @Test
    public void oldLifecycleCallbacksCannotAffectANewerEnable()
    {
        SessionLifecycle lifecycle = new SessionLifecycle();
        int original = lifecycle.enable();
        assertTrue(lifecycle.isCurrentStart(original));
        int stopped = lifecycle.disable();
        assertFalse(lifecycle.isCurrentStart(original));
        assertTrue(lifecycle.isCurrentStop(stopped));
        int latest = lifecycle.enable();
        assertFalse(lifecycle.isCurrentStop(stopped));
        assertFalse(lifecycle.isCurrent(original));
        assertTrue(lifecycle.isCurrentStart(latest));
        lifecycle.setStarted(true);
        assertFalse(lifecycle.isCurrentStart(latest));
    }

    @Test
    public void lifecycleStateDoesNotLeakBetweenPlugins()
    {
        SessionLifecycle first = new SessionLifecycle();
        SessionLifecycle second = new SessionLifecycle();
        first.enable();
        first.setStarted(true);
        assertFalse(second.isEnabled());
        assertFalse(second.isStarted());
        second.disable();
        assertTrue(first.isEnabled());
        assertTrue(first.isStarted());
    }

    @Test
    public void gainPoliciesRetainTheirDifferentRemovalBaselines()
    {
        InventoryGainTracker highestObservedCount = new InventoryGainTracker();
        InventoryGainTracker snapshots = new InventoryGainTracker(InventoryGainTracker.Baseline.PREVIOUS_SNAPSHOT);
        for (int quantity : new int[] {10, 12, 11, 12})
        {
            highestObservedCount.observe(quantity, true);
            snapshots.observe(quantity, true);
        }
        assertEquals(2, highestObservedCount.getCollected());
        assertEquals(3, snapshots.getCollected());
    }

    @Test
    public void inventoryTransfersAndUnavailableSnapshotsDoNotCountAsGains()
    {
        for (InventoryGainTracker.Baseline policy : InventoryGainTracker.Baseline.values())
        {
            InventoryGainTracker tracker = new InventoryGainTracker(policy);
            tracker.observe(10, false);
            tracker.observe(50, false);
            tracker.observe(51, true);
            tracker.suspend();
            tracker.observe(100, true);
            assertEquals(1, tracker.getCollected());
            tracker.reset();
            assertEquals(0, tracker.getCollected());
        }
    }

    private static Player player(int animation)
    {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
            (proxy, method, args) -> method.getName().equals("getAnimation") ? animation : null);
    }

    private static Random gaussian(double value)
    {
        return new Random()
        {
            @Override
            public double nextGaussian()
            {
                return value;
            }
        };
    }
}
