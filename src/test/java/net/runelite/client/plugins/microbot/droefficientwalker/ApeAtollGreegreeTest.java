package net.runelite.client.plugins.microbot.droefficientwalker;

import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.QuestState;
import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ApeAtollGreegreeTest {
    static final WorldPoint ISLAND = new WorldPoint(2773,2699,0);

    @Test void everyOwnedAlternativeIsRecognized() {
        for (int id : ApeAtollGreegree.IDS) assertEquals(id, ApeAtollGreegree.first(i -> i == id));
        assertEquals(0, ApeAtollGreegree.first(i -> false));
    }

    @Test void holdIsSentOnceAndInventoryRefreshCannotPrematurelyFailIt() {
        ApeAtollGreegree policy = new ApeAtollGreegree(); AtomicInteger clicks = new AtomicInteger();
        assertTrue(policy.tick(ApeAtollGreegree.TABLET_LANDING,QuestState.IN_PROGRESS,0,4031,1,id -> {clicks.incrementAndGet(); return true;}));
        for (int tick = 2; tick < 16; tick++) {
            assertTrue(policy.tick(ApeAtollGreegree.TABLET_LANDING,QuestState.IN_PROGRESS,0,0,tick,id -> {clicks.incrementAndGet(); return true;}));
            assertNull(policy.failure());
        }
        assertEquals(1, clicks.get());
        assertFalse(policy.tick(ApeAtollGreegree.TABLET_LANDING,QuestState.IN_PROGRESS,4031,0,16,id -> {fail("Already equipped"); return true;}));
    }

    @Test void preMm2ProtectionIncludesTheActualTabletLanding() {
        assertTrue(ApeAtollGreegree.required(ISLAND,QuestState.NOT_STARTED));
        assertTrue(ApeAtollGreegree.required(ApeAtollGreegree.TABLET_LANDING,QuestState.IN_PROGRESS));
        assertFalse(ApeAtollGreegree.required(ISLAND,QuestState.FINISHED));
        assertFalse(ApeAtollGreegree.required(new WorldPoint(3164,3487,0),QuestState.IN_PROGRESS));
        assertFalse(ApeAtollGreegree.surface(new WorldPoint(2773,2699,-1)));
    }

    @Test void failedOrUnacknowledgedEquipmentStopsWithoutAnotherHold() {
        ApeAtollGreegree policy = new ApeAtollGreegree(); AtomicInteger clicks = new AtomicInteger();
        policy.tick(ISLAND,QuestState.IN_PROGRESS,0,4024,1,id -> {clicks.incrementAndGet(); return true;});
        policy.tick(ISLAND,QuestState.IN_PROGRESS,0,4024,17,id -> {clicks.incrementAndGet(); return true;});
        assertNotNull(policy.failure()); assertEquals(1, clicks.get());
        policy.reset(); policy.tick(ISLAND,QuestState.IN_PROGRESS,0,0,1,id -> true); assertNotNull(policy.failure());
        policy.reset(); policy.tick(ISLAND,QuestState.IN_PROGRESS,0,4024,1,id -> false); assertNotNull(policy.failure());
    }

    @Test void pausedInputAndPostMm2LeaveEquipmentAlone() {
        ApeAtollGreegree policy = new ApeAtollGreegree();
        policy.tick(ISLAND,QuestState.IN_PROGRESS,0,19525,1,id -> true); policy.pause(1000);
        policy.tick(ISLAND,QuestState.IN_PROGRESS,0,19525,1001,id -> {fail("No repeated Hold"); return true;});
        assertNull(policy.failure());
        assertFalse(policy.tick(ISLAND,QuestState.FINISHED,0,0,1002,id -> {fail("Post MM2"); return true;}));
    }
}
