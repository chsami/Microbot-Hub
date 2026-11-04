package net.runelite.client.plugins.microbot.magetrainingarena;

import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.magetrainingarena.enums.Rooms;
import net.runelite.client.plugins.microbot.magetrainingarena.enums.TelekineticRooms;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MageTrainingArenaScriptTest {

    private static Widget item(int itemId) {
        Widget widget = mock(Widget.class);
        when(widget.getItemId()).thenReturn(itemId);
        return widget;
    }

    @Test
    void unloadedRewardListHasNoReward() {
        assertEquals(-1, MageTrainingArenaScript.findRewardIndex(null, 6908));
    }

    @Test
    void missingRewardIsNotClicked() {
        Widget[] widgets = {item(6908), item(6910)};
        assertEquals(-1, MageTrainingArenaScript.findRewardIndex(widgets, 6912));
    }

    @Test
    void matchingRewardReturnsItsIndex() {
        Widget[] widgets = {item(6908), null, item(6910)};
        assertEquals(2, MageTrainingArenaScript.findRewardIndex(widgets, 6910));
    }

    @Test
    void unknownPlayerLocationHasNoTelekineticRoom() {
        assertNull(MageTrainingArenaScript.findTelekineticRoom(null));
    }

    @Test
    void locationOutsideMazesHasNoTelekineticRoom() {
        assertNull(MageTrainingArenaScript.findTelekineticRoom(new WorldPoint(3363, 3318, 0)));
    }

    @Test
    void locationInsideMazeFindsItsTelekineticRoom() {
        assertEquals(TelekineticRooms.A, MageTrainingArenaScript.findTelekineticRoom(TelekineticRooms.A.getMaze()));
    }

    @Test
    void unknownPlayerLocationHasNotLeftTelekineticRoom() {
        assertFalse(MageTrainingArenaScript.hasLeftTelekineticRoom(null, TelekineticRooms.A));
    }

    @Test
    void locationInsideMazeHasNotLeftTelekineticRoom() {
        assertFalse(MageTrainingArenaScript.hasLeftTelekineticRoom(TelekineticRooms.A.getMaze(), TelekineticRooms.A));
    }

    @Test
    void locationInOtherMazeHasLeftTelekineticRoom() {
        assertTrue(MageTrainingArenaScript.hasLeftTelekineticRoom(TelekineticRooms.J.getMaze(), TelekineticRooms.A));
    }

    @Test
    void unknownPlayerLocationIsNotAwayFromTarget() {
        assertFalse(MageTrainingArenaScript.isAwayFrom(null, new WorldPoint(3334, 9718, 0)));
    }

    @Test
    void playerOnTargetIsNotAwayFromTarget() {
        WorldPoint target = new WorldPoint(3334, 9718, 0);
        assertFalse(MageTrainingArenaScript.isAwayFrom(new WorldPoint(3334, 9718, 0), target));
    }

    @Test
    void playerElsewhereIsAwayFromTarget() {
        assertTrue(MageTrainingArenaScript.isAwayFrom(new WorldPoint(3335, 9718, 0), new WorldPoint(3334, 9718, 0)));
    }

    @Test
    void unknownPlayerLocationHasNoRoom() {
        assertNull(MageTrainingArenaScript.findRoom(null));
    }

    @Test
    void locationInsideMazeIsTelekineticRoom() {
        assertEquals(Rooms.TELEKINETIC, MageTrainingArenaScript.findRoom(TelekineticRooms.A.getMaze()));
    }

    @Test
    void locationInsideRoomAreaFindsRoom() {
        assertEquals(Rooms.ALCHEMIST, MageTrainingArenaScript.findRoom(Rooms.ALCHEMIST.getExit()));
    }

    @Test
    void locationOutsideRoomsHasNoRoom() {
        assertNull(MageTrainingArenaScript.findRoom(new WorldPoint(3363, 3318, 0)));
    }

    @Test
    void unknownPlayerLocationIsNotFartherThanDistance() {
        assertFalse(MageTrainingArenaScript.isFartherThan(null, new WorldPoint(3363, 9640, 0), 10));
    }

    @Test
    void distantPlayerIsFartherThanDistance() {
        assertTrue(MageTrainingArenaScript.isFartherThan(new WorldPoint(3350, 9640, 0), new WorldPoint(3363, 9640, 0), 10));
    }

    @Test
    void nearbyPlayerIsNotFartherThanDistance() {
        assertFalse(MageTrainingArenaScript.isFartherThan(new WorldPoint(3360, 9640, 0), new WorldPoint(3363, 9640, 0), 10));
    }
}
