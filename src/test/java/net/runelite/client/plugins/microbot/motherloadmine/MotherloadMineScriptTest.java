package net.runelite.client.plugins.microbot.motherloadmine;

import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.motherloadmine.enums.MLMMiningSpot;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class MotherloadMineScriptTest {
    @Test
    void missingAntibanActivityFallsBackToGeneralMining() {
        assertEquals(Activity.GENERAL_MINING.getActivityIntensity(), MotherloadMineScript.miningIntensity(null));
    }

    @Test
    void usesConfiguredAntibanActivityIntensity() {
        assertEquals(Activity.GENERAL_COMBAT.getActivityIntensity(), MotherloadMineScript.miningIntensity(Activity.GENERAL_COMBAT));
    }

    @Test
    void missingLocalPlayerHasNoWorldLocation() {
        assertNull(MotherloadMineScript.worldLocationOf(null));
    }

    @Test
    void localPlayerWorldLocationIsReturned() {
        WorldPoint location = new WorldPoint(3750, 5670, 0);
        Player player = mock(Player.class);
        when(player.getWorldLocation()).thenReturn(location);
        assertEquals(location, MotherloadMineScript.worldLocationOf(player));
    }

    @Test
    void placeholderMiningSpotsHaveNoTarget() {
        assertNull(MotherloadMineScript.miningTargetOf(null));
        assertNull(MotherloadMineScript.miningTargetOf(MLMMiningSpot.IDLE));
        assertNull(MotherloadMineScript.miningTargetOf(MLMMiningSpot.ANY));
        assertNull(MotherloadMineScript.miningTargetOf(MLMMiningSpot.NORTH));
    }

    @Test
    void realMiningSpotsTargetTheirFirstWorldPoint() {
        for (MLMMiningSpot spot : MLMMiningSpot.values()) {
            if (spot.getWorldPoint() == null) continue;
            assertEquals(spot.getWorldPoint().get(0), MotherloadMineScript.miningTargetOf(spot));
        }
    }

    @Test
    void walkingToPlaceholderSpotIsNotDone() throws Exception {
        assertEquals(Boolean.FALSE, invokeWithSpot(MLMMiningSpot.IDLE, "walkToMiningSpot"));
    }

    @Test
    void repositioningTowardsPlaceholderSpotDoesNothing() throws Exception {
        assertNull(invokeWithSpot(MLMMiningSpot.IDLE, "repositionCameraAndMove"));
    }

    private static Object invokeWithSpot(MLMMiningSpot spot, String methodName) throws Exception {
        MLMMiningSpot previous = MotherloadMineScript.miningSpot;
        MotherloadMineScript script = new MotherloadMineScript(null, null, null, null);
        try {
            MotherloadMineScript.miningSpot = spot;
            Method method = MotherloadMineScript.class.getDeclaredMethod(methodName);
            method.setAccessible(true);
            try {
                return method.invoke(script);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof Exception) throw (Exception) cause;
                throw e;
            }
        } finally {
            MotherloadMineScript.miningSpot = previous;
        }
    }
}
