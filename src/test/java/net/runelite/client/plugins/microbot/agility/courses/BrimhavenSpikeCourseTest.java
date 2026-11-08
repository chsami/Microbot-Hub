package net.runelite.client.plugins.microbot.agility.courses;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectQueryable;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class BrimhavenSpikeCourseTest {

    private static final WorldPoint ARENA_FAR_FROM_ROPE_SWING = new WorldPoint(2773, 9557, 3);
    private static final WorldPoint ARENA_ENTRY_PLATFORM = new WorldPoint(2792, 9590, 3);
    private static final int MISSING_CHECKS_BEFORE_STOP = 20;

    private MockedStatic<Microbot> microbot;
    private MockedStatic<Rs2Player> player;
    private Rs2TileObjectQueryable query;

    @BeforeEach
    void setUp() {
        microbot = mockStatic(Microbot.class);
        player = mockStatic(Rs2Player.class);

        query = mock(Rs2TileObjectQueryable.class);
        when(query.withId(anyInt())).thenReturn(query);
        when(query.within(any(WorldPoint.class), anyInt())).thenReturn(query);
        Rs2TileObjectCache cache = mock(Rs2TileObjectCache.class);
        when(cache.query()).thenReturn(query);
        microbot.when(Microbot::getRs2TileObjectCache).thenReturn(cache);

        player.when(Rs2Player::getWorldLocation).thenReturn(ARENA_FAR_FROM_ROPE_SWING);
        player.when(Rs2Player::isMoving).thenReturn(false);
        player.when(Rs2Player::isAnimating).thenReturn(false);
    }

    @AfterEach
    void tearDown() {
        player.close();
        microbot.close();
    }

    @Test
    void asksToStopAfterRopeSwingStaysOutOfRangeWhileIdle() {
        BrimhavenSpikeCourse course = new BrimhavenSpikeCourse();

        for (int i = 1; i < MISSING_CHECKS_BEFORE_STOP; i++) {
            assertNull(course.getCurrentObstacle());
            assertNull(course.getObstacleNotFoundStopMessage());
        }

        assertNull(course.getCurrentObstacle());
        assertNotNull(course.getObstacleNotFoundStopMessage());
    }

    @Test
    void foundObstacleClearsMissingCount() {
        BrimhavenSpikeCourse course = new BrimhavenSpikeCourse();
        for (int i = 1; i < MISSING_CHECKS_BEFORE_STOP; i++) {
            course.getCurrentObstacle();
        }

        Rs2TileObjectModel ropeSwing = mock(Rs2TileObjectModel.class);
        when(query.nearest()).thenReturn(ropeSwing);
        assertSame(ropeSwing, course.getCurrentObstacle());

        when(query.nearest()).thenReturn(null);
        course.getCurrentObstacle();
        assertNull(course.getObstacleNotFoundStopMessage());
    }

    @Test
    void missesWhileMovingDoNotCount() {
        player.when(Rs2Player::isMoving).thenReturn(true);
        BrimhavenSpikeCourse course = new BrimhavenSpikeCourse();

        for (int i = 0; i < MISSING_CHECKS_BEFORE_STOP * 2; i++) {
            course.getCurrentObstacle();
        }

        assertNull(course.getObstacleNotFoundStopMessage());
    }

    @Test
    void resetClearsMissingCount() {
        BrimhavenSpikeCourse course = new BrimhavenSpikeCourse();
        for (int i = 0; i < MISSING_CHECKS_BEFORE_STOP; i++) {
            course.getCurrentObstacle();
        }

        course.reset();

        assertNull(course.getObstacleNotFoundStopMessage());
    }

    @Test
    void spikeStepNeverAsksToStop() {
        BrimhavenSpikeCourse course = new BrimhavenSpikeCourse();
        course.isObstacleComplete(1, 0, 0, 0);
        course.isObstacleComplete(2, 1, 0, 0);

        for (int i = 0; i < MISSING_CHECKS_BEFORE_STOP * 2; i++) {
            assertNull(course.getCurrentObstacle());
        }

        assertNull(course.getObstacleNotFoundStopMessage());
    }

    @Test
    void arenaTilesOutsideTheOldBoxCountAsPaid() {
        player.when(Rs2Player::getWorldLocation).thenReturn(ARENA_FAR_FROM_ROPE_SWING);
        assertTrue(new BrimhavenSpikeCourse().hasPaid());

        player.when(Rs2Player::getWorldLocation).thenReturn(ARENA_ENTRY_PLATFORM);
        assertTrue(new BrimhavenSpikeCourse().hasPaid());

        player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(2809, 3192, 0));
        assertFalse(new BrimhavenSpikeCourse().hasPaid());
    }
}
