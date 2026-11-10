package net.runelite.client.plugins.microbot.droslayer;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class DroSlayerWalkerCancellationTest {
    public static class PendingWalker implements DroSlayerWalkerBridge.Engine {
        DroSlayerWalkerBridge bridge;
        int requests;
        int cancellations;
        public boolean available() { return true; }
        public WorldPoint resolveDestination(WorldPoint target) { return target; }
        public boolean walkTo(WorldPoint point) { requests++; bridge.shutdown(); return true; }
        public String status() { return "IDLE"; }
        public String planningFailure() { return "NONE"; }
        public void cancel() { cancellations++; }
        public DroSlayerWalkerBridge.Lease acquireWildernessContext(String owner) { throw new UnsupportedOperationException(); }
        public boolean isWildernessPlayerAttackActive() { return false; }
        public String wildernessEscapeState() { return "IDLE"; }
        public void acknowledgeWildernessEscape() { }
    }

    @Test public void shutdownCancelsPendingCallAndItsLateReturnCannotReclaimRoute() throws Exception {
        PendingWalker walker = new PendingWalker();
        DroSlayerWalkerBridge bridge = new DroSlayerWalkerBridge(walker);
        walker.bridge = bridge;
        try (var dro = mockStatic(Microbot.class); var player = mockStatic(Rs2Player.class)) {
            player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(3100,3500,0));
            assertFalse(bridge.tickTo(new WorldPoint(3237,3622,0), 2, DroSlayerTravelMode.FULL_TRAVEL, 15));
            assertEquals(1,walker.cancellations);
            assertFalse(bridge.isRouteActive());
            assertFalse(bridge.tickTo(new WorldPoint(3237,3622,0), 2, DroSlayerTravelMode.FULL_TRAVEL, 15));
            assertEquals(1,walker.requests);
        }
    }
}
