package net.runelite.client.plugins.microbot.fourtifysagility.tasks;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.NPC;
import net.runelite.client.plugins.microbot.fourtifysagility.FourtifysAgilityPlugin;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TaskStateTest
{
    @Test
    public void pyramidReadinessPreservesEveryMatchingPositiveTimer() throws Exception
    {
        FourtifysAgilityPlugin plugin = new FourtifysAgilityPlugin();
        Map<NPC, Integer> timers = timers(plugin);
        timers.put(block(5788), 2);
        timers.put(block(5788), 1);
        timers.put(block(5788), 0);
        timers.put(block(5788), -1);
        timers.put(block(5787), 3);
        AtomicInteger actions = new AtomicInteger();

        plugin.forEachReadyPyramidBlock(5788, actions::incrementAndGet);
        assertEquals(2, actions.get());
        plugin.forEachReadyPyramidBlock(5787, actions::incrementAndGet);
        assertEquals(3, actions.get());
        plugin.forEachReadyPyramidBlock(9999, actions::incrementAndGet);
        assertEquals(3, actions.get());
    }

    @Test
    public void pyramidReadinessDoesNotRunActionsWithoutTrackedBlocks() throws Exception
    {
        FourtifysAgilityPlugin plugin = new FourtifysAgilityPlugin();
        AtomicInteger actions = new AtomicInteger();
        plugin.forEachReadyPyramidBlock(5788, actions::incrementAndGet);
        assertEquals(0, actions.get());
        timers(plugin).put(block(5788), 0);
        plugin.forEachReadyPyramidBlock(5788, actions::incrementAndGet);
        assertEquals(0, actions.get());
    }

    @SuppressWarnings("unchecked")
    private static Map<NPC, Integer> timers(FourtifysAgilityPlugin plugin) throws Exception
    {
        Field field = FourtifysAgilityPlugin.class.getDeclaredField("pyramidBlockTimers");
        field.setAccessible(true);
        return (Map<NPC, Integer>) field.get(plugin);
    }

    private static NPC block(int id)
    {
        return (NPC) Proxy.newProxyInstance(NPC.class.getClassLoader(), new Class<?>[] {NPC.class},
                (proxy, method, args) -> {
                    switch (method.getName())
                    {
                        case "getId":
                            return id;
                        case "hashCode":
                            return System.identityHashCode(proxy);
                        case "equals":
                            return proxy == args[0];
                        default:
                            return null;
                    }
                });
    }
}
