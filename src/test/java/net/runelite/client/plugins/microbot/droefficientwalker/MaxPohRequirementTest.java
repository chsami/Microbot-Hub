package net.runelite.client.plugins.microbot.droefficientwalker;

import java.util.Optional;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.droefficientwalker.support.TransportExecutionRegistry;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MaxPohRequirementTest {
    private final WorldPoint landing = new WorldPoint(3410, 3324, 0);

    private MaxPohSupport.Exit ring() {
        return new MaxPohSupport.Exit("RING", "BIP", landing, null);
    }

    @AfterEach
    void reset() {
        MaxPohSupport.configure(null, null, null);
    }

    @Test
    void capeAndStaffAreTwoSeparateRequirements() {
        try (var micro = mockStatic(Microbot.class)) {
            MaxPohSupport.Leg leg = new MaxPohSupport.Leg(ring(), MaxPohSupport.Entry.CAPE);
            assertNull(leg.getOrigin(), "There is no fictional POH origin");
            assertEquals(landing, leg.getDestination());
            assertEquals(Set.of(9789, 9790), leg.getItemRequirements().get(0).getAllItemIds());
            assertEquals(Set.of(772, 9084), leg.getItemRequirements().get(1).getStaffAlternatives());
            assertFalse(leg.isConsumable());
            assertEquals(Optional.of(TransportExecutionRegistry.Executor.POH),
                TransportExecutionRegistry.executorFor(leg));
        }
    }

    @Test
    void eliteDiaryRemovesOnlyTheStaffRequirement() {
        try (var micro = mockStatic(Microbot.class)) {
            micro.when(() -> Microbot.getVarbitValue(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE)).thenReturn(1);
            MaxPohSupport.Leg leg = new MaxPohSupport.Leg(ring(), MaxPohSupport.Entry.TABLET);
            assertEquals(1, leg.getItemRequirements().size());
            assertEquals(Set.of(8013), leg.getItemRequirements().get(0).getAllItemIds());
            assertTrue(leg.isConsumable());
        }
    }

    @Test
    void houseEntryIsCostedAsATeleportAndCostsMoreThanBeingInside() {
        MaxPohSupport.Exit exit = new MaxPohSupport.Exit("NEXUS", "Catherby", new WorldPoint(2804, 3434, 0), null);
        MaxPohSupport.Leg portal = new MaxPohSupport.Leg(exit, MaxPohSupport.Entry.CAPE);
        MaxPohSupport.Leg inside = new MaxPohSupport.Leg(exit, MaxPohSupport.Entry.INSIDE);
        assertNull(portal.getOrigin(), "Do not store instance or template coordinates");
        assertNull(inside.getOrigin(), "Do not store instance or template coordinates");
        assertEquals(TransportType.TELEPORTATION_ITEM, portal.getType());
        assertTrue(RouteTimeCost.transport(portal) > RouteTimeCost.transport(inside));
    }
}
