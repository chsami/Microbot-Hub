package net.runelite.client.plugins.microbot.droefficientwalker;

import java.lang.reflect.Method;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static net.runelite.client.plugins.microbot.droefficientwalker.WalkerRequestCancellationTest.assertCancelled;
import static org.mockito.Mockito.*;

public class NetworkPlanningCancellationTest {
    @Test public void unrelatedCatalogEdgesDoNotRequestPlayerOrInventorySnapshots() throws Exception {
        NetworkTransportSupport support = mock(NetworkTransportSupport.class, CALLS_REAL_METHODS);
        Method method = WalkerTestReflection.declaredMethod(NetworkTransportSupport.class, "sharedRequirementsSatisfiedNow", Transport.class);
        method.setAccessible(true);
        Transport ladder = new Transport(new WorldPoint(3100,3500,0), new WorldPoint(3100,3500,1),
            "Other ladder", TransportType.TRANSPORT, false, "Climb-up", "Ladder", 17384);
        try (var dro = mockStatic(Microbot.class); var inventory = mockStatic(Rs2Inventory.class)) {
            assertEquals(Boolean.TRUE, method.invoke(support, ladder));
            dro.verifyNoInteractions();
            inventory.verifyNoInteractions();
        }
    }

    @Test public void interruptedGraphRefreshCannotSubmitFallbackWork() {
        NetworkTransportSupport support = mock(NetworkTransportSupport.class, CALLS_REAL_METHODS);
        try {
            Thread.currentThread().interrupt();
            assertCancelled(() -> support.refreshForPlanning(new WorldPoint(3237,3622,0)));
        } finally { Thread.interrupted(); }
    }
}
