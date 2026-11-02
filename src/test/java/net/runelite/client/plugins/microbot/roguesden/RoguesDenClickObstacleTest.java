package net.runelite.client.plugins.microbot.roguesden;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectQueryable;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class RoguesDenClickObstacleTest {

    private static final Obstacles.Obstacle ENTRANCE = new Obstacles.Obstacle(3048, 4997, 7251);
    private static final Obstacles.Obstacle NEXT = new Obstacles.Obstacle(3039, 4999, "Stand");
    private static final WorldPoint OUTSIDE_ENTRANCE = new WorldPoint(3050, 4997, 0);

    private Obstacles.Obstacle[] previousObstacles;

    @BeforeEach
    void setUp() {
        previousObstacles = Obstacles.OBSTACLES;
        Obstacles.OBSTACLES = new Obstacles.Obstacle[]{ENTRANCE, NEXT};
    }

    @AfterEach
    void tearDown() {
        Obstacles.OBSTACLES = previousObstacles;
    }

    @Test
    void nullPlayerLocationSkipsTick() throws Exception {
        RoguesDenScript script = new RoguesDenScript();

        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class)) {
            player.when(Rs2Player::getWorldLocation).thenReturn(null);

            assertTrue(clickObstacle(script));

            microbot.verifyNoInteractions();
        }
    }

    @Test
    void entranceWaitToleratesNullPlayerLocation() throws Exception {
        RoguesDenScript script = new RoguesDenScript();
        Rs2TileObjectCache cache = mock(Rs2TileObjectCache.class);
        when(cache.query()).thenReturn(mock(Rs2TileObjectQueryable.class));

        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Global> global = mockStatic(Global.class)) {
            microbot.when(Microbot::getRs2TileObjectCache).thenReturn(cache);
            player.when(Rs2Player::getWorldLocation).thenReturn(OUTSIDE_ENTRANCE);

            assertTrue(clickObstacle(script));

            ArgumentCaptor<BooleanSupplier> predicate = ArgumentCaptor.forClass(BooleanSupplier.class);
            global.verify(() -> Global.sleepUntil(predicate.capture()));

            player.when(Rs2Player::getWorldLocation).thenReturn(null);
            assertDoesNotThrow(() -> assertFalse(predicate.getValue().getAsBoolean()));

            player.when(Rs2Player::getWorldLocation).thenReturn(ENTRANCE.getTile());
            assertTrue(predicate.getValue().getAsBoolean());
        }
    }

    private static boolean clickObstacle(RoguesDenScript script) throws Exception {
        Method method = RoguesDenScript.class.getDeclaredMethod("clickObstacle");
        method.setAccessible(true);
        return (boolean) method.invoke(script);
    }
}
