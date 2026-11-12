package net.runelite.client.plugins.microbot.droefficientwalker;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ClientTick;
import net.runelite.client.plugins.microbot.droefficientwalker.compat.DroLoginReadiness;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WildernessPresenceWatcherTest {
    static void tick(EfficientWalkerPlugin plugin) throws Exception {set(plugin,"nextWildernessThreatCheckAt",0L);plugin.onClientTick(new ClientTick());}
    static void set(Object o,String name,Object value) throws Exception {Field f=WalkerTestReflection.declaredField(o.getClass(), name);f.setAccessible(true);f.set(o,value);}
}
