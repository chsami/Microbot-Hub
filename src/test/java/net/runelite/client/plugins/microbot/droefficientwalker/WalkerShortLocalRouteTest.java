package net.runelite.client.plugins.microbot.droefficientwalker;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.poh.data.HouseLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class WalkerShortLocalRouteTest {
    private static final WorldPoint ORIGIN = new WorldPoint(3200, 3200, 0);

    @Test void tenCollisionStepsAreAcceptedButAnEleventhIsNot() {
        List<WorldPoint> path = new ArrayList<>();
        for (int x = 0; x <= 10; x++) path.add(ORIGIN.dx(x));
        assertTrue(EfficientWalker.isShortLocalRoute(path, ORIGIN, ORIGIN.dx(10)));
        path.add(ORIGIN.dx(11));
        assertFalse(EfficientWalker.isShortLocalRoute(path, ORIGIN, ORIGIN.dx(11)));
        assertTrue(EfficientWalker.isShortLocalRoute(List.of(ORIGIN), ORIGIN, ORIGIN));
    }

    @Test void nearbyDestinationDoesNotMakeALongDetourShort() {
        List<WorldPoint> detour = new ArrayList<>();
        for (int y = 0; y <= 6; y++) detour.add(ORIGIN.dy(y));
        detour.add(ORIGIN.dx(1).dy(6));
        for (int y = 6; y >= 0; y--) detour.add(ORIGIN.dx(2).dy(y));
        assertEquals(2, ORIGIN.distanceTo2D(ORIGIN.dx(2)));
        assertFalse(EfficientWalker.isShortLocalRoute(detour, ORIGIN, ORIGIN.dx(2)));
    }

    @Test void missingEndpointsNullTilesPlaneChangesAndJumpsAreRejected() {
        WorldPoint target = ORIGIN.dx(2);
        assertFalse(EfficientWalker.isShortLocalRoute(null, ORIGIN, target));
        assertFalse(EfficientWalker.isShortLocalRoute(List.of(), ORIGIN, target));
        assertFalse(EfficientWalker.isShortLocalRoute(List.of(ORIGIN, target), null, target));
        assertFalse(EfficientWalker.isShortLocalRoute(List.of(ORIGIN, target), ORIGIN, null));
        assertFalse(EfficientWalker.isShortLocalRoute(List.of(ORIGIN.dx(1), target), ORIGIN, target));
        assertFalse(EfficientWalker.isShortLocalRoute(List.of(ORIGIN, ORIGIN.dx(1)), ORIGIN, target));
        assertFalse(EfficientWalker.isShortLocalRoute(List.of(ORIGIN, target), ORIGIN, target));
        assertFalse(EfficientWalker.isShortLocalRoute(Arrays.asList(ORIGIN, null, target), ORIGIN, target));
        WorldPoint upstairs = new WorldPoint(ORIGIN.getX() + 1, ORIGIN.getY(), 1);
        assertFalse(EfficientWalker.isShortLocalRoute(List.of(ORIGIN, upstairs, target), ORIGIN, target));
    }

    @Test void realCollisionRoutePublishesThroughNormalWalkerRouteSubmission() throws Exception {
        try (var fixture = new HousePortalRecoveryTest.Fixture()) {
            WorldPoint origin = outside(fixture);
            assertTrue(startShort(fixture, origin.dx(10)));
            assertEquals(origin.dx(10), fixture.walker.getDestination());
            assertNotEquals(EfficientWalker.Status.IDLE, fixture.walker.getStatus());
        }
    }

    @Test void instanceAndOtherPlaneDoNotStartAShortRoute() throws Exception {
        try (var fixture = new HousePortalRecoveryTest.Fixture()) {
            WorldPoint origin = outside(fixture);
            WorldView world = fixture.client.getLocalPlayer().getWorldView();
            when(world.isInstance()).thenReturn(true);
            assertFalse(startShort(fixture, origin.dx(2)));
            when(world.isInstance()).thenReturn(false);
            assertFalse(startShort(fixture, new WorldPoint(origin.getX() + 2, origin.getY(), 1)));
            assertNull(fixture.walker.getDestination());
        }
    }

    @Test void fullyBlockedNearbyDestinationDoesNotBypassNormalPlanning() throws Exception {
        try (var fixture = new HousePortalRecoveryTest.Fixture()) {
            WorldPoint origin = outside(fixture);
            int[][] flags = fixture.client.getTopLevelWorldView().getCollisionMaps()[0].getFlags();
            flags[52][50] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
            assertFalse(startShort(fixture, origin.dx(2)));
            assertNull(fixture.walker.getDestination());
        }
    }

    @Test void collisionWallForcingLongDetourRejectsNearbyShortRoute() throws Exception {
        try (var fixture = new HousePortalRecoveryTest.Fixture()) {
            WorldPoint origin = outside(fixture);
            int[][] flags = fixture.client.getTopLevelWorldView().getCollisionMaps()[0].getFlags();
            for (int y = 44; y <= 56; y++) flags[51][y] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
            assertFalse(startShort(fixture, origin.dx(2)));
            assertNull(fixture.walker.getDestination());
            // Removing the wall proves this is a collision/detour refusal, not an invalid fixture.
            for (int y = 44; y <= 56; y++) flags[51][y] = 0;
            assertTrue(startShort(fixture, origin.dx(2)));
        }
    }

    private static WorldPoint outside(HousePortalRecoveryTest.Fixture fixture) {
        WorldPoint origin = HouseLocation.PRIFDDINAS.getPortalLocation();
        fixture.inside.set(false); fixture.raw.set(origin); fixture.cached.set(origin);
        WorldView world = fixture.client.getTopLevelWorldView();
        LocalPoint localOrigin = LocalPoint.fromScene(50, 50, world);
        when(fixture.client.getLocalPlayer().getLocalLocation()).thenReturn(localOrigin);
        return origin;
    }

    private static boolean startShort(HousePortalRecoveryTest.Fixture fixture, WorldPoint target) throws Exception {
        Method method = EfficientWalker.class.getDeclaredMethod("startShortLocalRoute", WorldPoint.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(fixture.walker, target);
    }
}
