package net.runelite.client.plugins.microbot.motherloadmine;

import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.api.player.models.Rs2PlayerModel;
import net.runelite.client.plugins.microbot.motherloadmine.enums.MLMMiningSpot;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
    void localPlayerNextToVeinDoesNotCountAsCrowding() {
        WorldPoint vein = new WorldPoint(3730, 5660, 0);
        Player self = mock(Player.class);
        Rs2PlayerModel model = mock(Rs2PlayerModel.class);
        when(model.getPlayer()).thenReturn(self);
        when(model.getWorldLocation()).thenReturn(vein.dx(1));
        assertFalse(MotherloadMineScript.isOtherPlayerNear(model, self, vein));
    }

    @Test
    void otherPlayerNextToVeinCountsAsCrowding() {
        WorldPoint vein = new WorldPoint(3730, 5660, 0);
        Rs2PlayerModel model = mock(Rs2PlayerModel.class);
        when(model.getPlayer()).thenReturn(mock(Player.class));
        when(model.getWorldLocation()).thenReturn(vein.dx(2));
        assertTrue(MotherloadMineScript.isOtherPlayerNear(model, mock(Player.class), vein));
    }

    @Test
    void otherPlayerFarFromVeinOrWithoutLocationIsIgnored() {
        WorldPoint vein = new WorldPoint(3730, 5660, 0);
        Rs2PlayerModel far = mock(Rs2PlayerModel.class);
        when(far.getPlayer()).thenReturn(mock(Player.class));
        when(far.getWorldLocation()).thenReturn(vein.dx(3));
        Rs2PlayerModel unknown = mock(Rs2PlayerModel.class);
        when(unknown.getPlayer()).thenReturn(mock(Player.class));
        Player self = mock(Player.class);
        assertFalse(MotherloadMineScript.isOtherPlayerNear(far, self, vein));
        assertFalse(MotherloadMineScript.isOtherPlayerNear(unknown, self, vein));
        assertFalse(MotherloadMineScript.isOtherPlayerNear(null, self, vein));
    }

    @Test
    void crowdingCheckReadsEachPlayerLocationOnce() {
        WorldPoint vein = new WorldPoint(3730, 5660, 0);
        Rs2PlayerModel model = mock(Rs2PlayerModel.class);
        when(model.getPlayer()).thenReturn(mock(Player.class));
        when(model.getWorldLocation()).thenReturn(vein.dx(1));
        assertTrue(MotherloadMineScript.isOtherPlayerNear(model, mock(Player.class), vein));
        verify(model, times(1)).getWorldLocation();
    }

    @Test
    void playerSnapshotSkipsSelfAndUnknownLocationsWithOneReadPerPlayer() {
        WorldPoint near = new WorldPoint(3731, 5660, 0);
        Player self = mock(Player.class);
        Rs2PlayerModel own = mock(Rs2PlayerModel.class);
        when(own.getPlayer()).thenReturn(self);
        Rs2PlayerModel other = mock(Rs2PlayerModel.class);
        when(other.getPlayer()).thenReturn(mock(Player.class));
        when(other.getWorldLocation()).thenReturn(near);
        Rs2PlayerModel unknown = mock(Rs2PlayerModel.class);
        when(unknown.getPlayer()).thenReturn(mock(Player.class));

        List<WorldPoint> locations = MotherloadMineScript.otherPlayerLocations(Arrays.asList(own, other, null, unknown), self);

        assertEquals(Collections.singletonList(near), locations);
        verify(own, never()).getWorldLocation();
        verify(other, times(1)).getWorldLocation();
        verify(unknown, times(1)).getWorldLocation();
        assertTrue(MotherloadMineScript.otherPlayerLocations(null, self).isEmpty());
    }

    @Test
    void veinIsCrowdedOnlyWhenASnapshotLocationIsWithinTwoTiles() {
        WorldPoint vein = new WorldPoint(3730, 5660, 0);
        assertTrue(MotherloadMineScript.isCrowded(vein, Arrays.asList(vein.dx(5), vein.dy(2))));
        assertFalse(MotherloadMineScript.isCrowded(vein, Collections.singletonList(vein.dx(3))));
        assertFalse(MotherloadMineScript.isCrowded(vein, Collections.emptyList()));
        assertFalse(MotherloadMineScript.isCrowded(vein, null));
        assertFalse(MotherloadMineScript.isCrowded(null, Collections.singletonList(vein)));
    }

    @Test
    void veinsOutsideTheSelectedAreaAreRejected() {
        WorldPoint westLower = new WorldPoint(3730, 5660, 0);
        WorldPoint southLower = new WorldPoint(3750, 5645, 0);
        WorldPoint westUpper = new WorldPoint(3750, 5680, 0);
        WorldPoint eastUpper = new WorldPoint(3760, 5670, 0);

        assertTrue(MotherloadMineScript.isInMiningArea(MLMMiningSpot.WEST_LOWER, westLower));
        assertTrue(MotherloadMineScript.isInMiningArea(MLMMiningSpot.WEST_MID, westLower));
        assertFalse(MotherloadMineScript.isInMiningArea(MLMMiningSpot.WEST_LOWER, southLower));
        assertTrue(MotherloadMineScript.isInMiningArea(MLMMiningSpot.SOUTH_WEST, southLower));
        assertTrue(MotherloadMineScript.isInMiningArea(MLMMiningSpot.SOUTH_EAST, southLower));
        assertFalse(MotherloadMineScript.isInMiningArea(MLMMiningSpot.SOUTH_EAST, westLower));
        assertTrue(MotherloadMineScript.isInMiningArea(MLMMiningSpot.WEST_UPPER, westUpper));
        assertFalse(MotherloadMineScript.isInMiningArea(MLMMiningSpot.WEST_UPPER, eastUpper));
        assertTrue(MotherloadMineScript.isInMiningArea(MLMMiningSpot.EAST_UPPER, eastUpper));
        assertFalse(MotherloadMineScript.isInMiningArea(MLMMiningSpot.EAST_UPPER, westUpper));
        assertFalse(MotherloadMineScript.isInMiningArea(MLMMiningSpot.IDLE, westLower));
        assertFalse(MotherloadMineScript.isInMiningArea(null, westLower));
        assertFalse(MotherloadMineScript.isInMiningArea(MLMMiningSpot.WEST_LOWER, null));
    }

    @Test
    void fullInventoryOfOreWithoutHammerSkipsRepair() {
        assertFalse(MotherloadMineScript.canGetHammer(false, true, 0));
    }

    @Test
    void hammerCanBeObtainedWithRoomPayDirtOrHammer() {
        assertTrue(MotherloadMineScript.canGetHammer(true, true, 0));
        assertTrue(MotherloadMineScript.canGetHammer(false, false, 0));
        assertTrue(MotherloadMineScript.canGetHammer(false, true, 1));
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
