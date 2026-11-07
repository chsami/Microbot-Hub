package net.runelite.client.plugins.microbot.roguesden;

import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectQueryable;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoguesDenClickObstacleTest {

    private static final Obstacles.Obstacle ENTRANCE = new Obstacles.Obstacle(3048, 4997, 7251);
    private static final Obstacles.Obstacle NEXT = new Obstacles.Obstacle(3039, 4999, "Stand");
    private static final WorldPoint OUTSIDE_ENTRANCE = new WorldPoint(3050, 4997, 0);
    private static final Obstacles.Obstacle WALL_STAND = new Obstacles.Obstacle(2992, 5088, "Stand");
    private static final Obstacles.Obstacle WALL_SEARCH = new Obstacles.Obstacle(2992, 5088, "Search", 7249, 750);
    private static final Obstacles.Obstacle AFTER_WALL = new Obstacles.Obstacle(2993, 5088, "Stand");

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

    @Test
    void flashPowderNullPlayerLocationSkipsTick() throws Exception {
        RoguesDenScript script = new RoguesDenScript();

        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class);
             MockedStatic<Rs2Npc> npc = mockStatic(Rs2Npc.class)) {
            player.when(Rs2Player::getWorldLocation).thenReturn(null);
            inventory.when(() -> Rs2Inventory.hasItem(ItemID.ROGUESDEN_FLASH_POWDER)).thenReturn(true);

            assertTrue(invoke(script, "useFlashPowder"));

            microbot.verifyNoInteractions();
            npc.verifyNoInteractions();
        }
    }

    @Test
    void searchesTrappedWallFromAdjacentTile() throws Exception {
        Obstacles.OBSTACLES = new Obstacles.Obstacle[]{ENTRANCE, WALL_STAND, WALL_SEARCH, AFTER_WALL};
        RoguesDenScript script = new RoguesDenScript();
        Rs2TileObjectQueryable query = mock(Rs2TileObjectQueryable.class);
        Rs2TileObjectCache cache = mock(Rs2TileObjectCache.class);
        when(cache.query()).thenReturn(query);

        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Random> random = mockStatic(Rs2Random.class);
             MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class);
             MockedStatic<Global> global = mockStatic(Global.class)) {
            microbot.when(Microbot::getRs2TileObjectCache).thenReturn(cache);
            player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(2991, 5087, 1));
            random.when(() -> Rs2Random.between(1, 10)).thenReturn(1);

            assertFalse(clickObstacle(script));

            verify(query).interact(WALL_SEARCH.getObjectId(), "search");
            walker.verifyNoInteractions();
        }
    }

    @Test
    void walksToWallStandTileWhenFarFromWall() throws Exception {
        Obstacles.OBSTACLES = new Obstacles.Obstacle[]{ENTRANCE, WALL_STAND, WALL_SEARCH, AFTER_WALL};
        RoguesDenScript script = new RoguesDenScript();

        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Random> random = mockStatic(Rs2Random.class);
             MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class);
             MockedStatic<Global> global = mockStatic(Global.class)) {
            player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(2988, 5087, 1));
            random.when(() -> Rs2Random.between(1, 10)).thenReturn(1);
            walker.when(() -> Rs2Walker.walkFastCanvas(WALL_STAND.getTile())).thenReturn(true);

            assertFalse(clickObstacle(script));

            walker.verify(() -> Rs2Walker.walkFastCanvas(WALL_STAND.getTile()));
            microbot.verify(Microbot::getRs2TileObjectCache, never());
        }
    }

    @Test
    void turnsCameraWhenStandStepIsNotReached() throws Exception {
        Obstacles.OBSTACLES = new Obstacles.Obstacle[]{ENTRANCE, WALL_STAND, WALL_SEARCH, AFTER_WALL};
        RoguesDenScript script = new RoguesDenScript();

        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Random> random = mockStatic(Rs2Random.class);
             MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class);
             MockedStatic<Rs2Camera> camera = mockStatic(Rs2Camera.class);
             MockedStatic<Global> global = mockStatic(Global.class)) {
            player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(2988, 5087, 1));
            random.when(() -> Rs2Random.between(1, 10)).thenReturn(1);
            walker.when(() -> Rs2Walker.walkFastCanvas(WALL_STAND.getTile())).thenReturn(true);

            for (int i = 0; i < RoguesDenScript.STUCK_WALK_ATTEMPTS; i++) {
                clickObstacle(script);
            }
            camera.verifyNoInteractions();

            clickObstacle(script);
            camera.verify(() -> Rs2Camera.angleToTile(WALL_STAND.getTile()));
            microbot.verify(() -> Microbot.log(anyString()));
            walker.verify(() -> Rs2Walker.walkFastCanvas(WALL_STAND.getTile()), times(RoguesDenScript.STUCK_WALK_ATTEMPTS + 1));
        }
    }

    @Test
    void logsStuckStandStepOnlyTwicePerTile() throws Exception {
        Obstacles.OBSTACLES = new Obstacles.Obstacle[]{ENTRANCE, WALL_STAND, WALL_SEARCH, AFTER_WALL};
        RoguesDenScript script = new RoguesDenScript();
        int firings = 4;

        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Random> random = mockStatic(Rs2Random.class);
             MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class);
             MockedStatic<Rs2Camera> camera = mockStatic(Rs2Camera.class);
             MockedStatic<Global> global = mockStatic(Global.class)) {
            player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(2988, 5087, 1));
            random.when(() -> Rs2Random.between(1, 10)).thenReturn(1);
            walker.when(() -> Rs2Walker.walkFastCanvas(WALL_STAND.getTile())).thenReturn(true);

            for (int i = 0; i < (RoguesDenScript.STUCK_WALK_ATTEMPTS + 1) * firings; i++) {
                clickObstacle(script);
            }

            camera.verify(() -> Rs2Camera.angleToTile(WALL_STAND.getTile()), times(firings));
            microbot.verify(() -> Microbot.log(startsWith("Can't reach")), times(1));
            microbot.verify(() -> Microbot.log(startsWith("Still can't reach")), times(1));

            player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(2989, 5087, 1));
            for (int i = 0; i <= RoguesDenScript.STUCK_WALK_ATTEMPTS; i++) {
                clickObstacle(script);
            }

            microbot.verify(() -> Microbot.log(startsWith("Can't reach")), times(2));
        }
    }

    @Test
    void doesNotTurnCameraWhilePlayerKeepsMoving() throws Exception {
        Obstacles.OBSTACLES = new Obstacles.Obstacle[]{ENTRANCE, WALL_STAND, WALL_SEARCH, AFTER_WALL};
        RoguesDenScript script = new RoguesDenScript();
        AtomicInteger calls = new AtomicInteger();

        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Random> random = mockStatic(Rs2Random.class);
             MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class);
             MockedStatic<Rs2Camera> camera = mockStatic(Rs2Camera.class);
             MockedStatic<Global> global = mockStatic(Global.class)) {
            player.when(Rs2Player::getWorldLocation)
                    .thenAnswer(invocation -> new WorldPoint(2980 + calls.getAndIncrement() % 8, 5087, 1));
            random.when(() -> Rs2Random.between(1, 10)).thenReturn(1);
            walker.when(() -> Rs2Walker.walkFastCanvas(WALL_STAND.getTile())).thenReturn(true);

            for (int i = 0; i < RoguesDenScript.STUCK_WALK_ATTEMPTS * 3; i++) {
                clickObstacle(script);
            }

            camera.verifyNoInteractions();
            microbot.verify(() -> Microbot.log(anyString()), never());
        }
    }

    private static boolean clickObstacle(RoguesDenScript script) throws Exception {
        return invoke(script, "clickObstacle");
    }

    private static boolean invoke(RoguesDenScript script, String name) throws Exception {
        Method method = RoguesDenScript.class.getDeclaredMethod(name);
        method.setAccessible(true);
        return (boolean) method.invoke(script);
    }
}
