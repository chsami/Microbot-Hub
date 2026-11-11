package net.runelite.client.plugins.microbot.droslayer;

import java.lang.reflect.Field;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class DroSlayerWalkerCancellationTest {
    public static class PendingWalker {
        DroSlayerEfficientWalkerBridge bridge;
        int requests;
        int cancellations;
        public boolean walkTo(WorldPoint point) { requests++; bridge.shutdown(); return true; }
        public String getStatus() { return "IDLE"; }
        public void cancel() { cancellations++; }
    }

    @Test public void shutdownCancelsPendingCallAndItsLateReturnCannotReclaimRoute() throws Exception {
        DroSlayerEfficientWalkerBridge bridge = new DroSlayerEfficientWalkerBridge(null);
        PendingWalker walker = new PendingWalker(); walker.bridge = bridge;
        Plugin plugin = mock(Plugin.class);
        set(bridge,"walker",walker); set(bridge,"plugin",plugin);
        set(bridge,"walkTo",PendingWalker.class.getMethod("walkTo",WorldPoint.class));
        set(bridge,"getStatus",PendingWalker.class.getMethod("getStatus"));
        set(bridge,"cancel",PendingWalker.class.getMethod("cancel"));
        try (var dro = mockStatic(Microbot.class); var player = mockStatic(Rs2Player.class)) {
            dro.when(() -> Microbot.isPluginEnabled(plugin.getClass())).thenReturn(true);
            player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(3100,3500,0));
            assertFalse(bridge.tickTo(new WorldPoint(3237,3622,0), 2, DroSlayerTravelMode.FULL_TRAVEL, 15));
            assertEquals(1,walker.cancellations);
            assertFalse(bridge.isRouteActive());
            assertFalse(bridge.tickTo(new WorldPoint(3237,3622,0), 2, DroSlayerTravelMode.FULL_TRAVEL, 15));
            assertEquals(1,walker.requests);
        }
    }

    private static void set(Object object, String name, Object value) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); field.set(object,value);
    }
}
