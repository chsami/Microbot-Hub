package net.runelite.client.plugins.microbot.agility;

import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.Perspective;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.agility.courses.AgilityCourseHandler;
import net.runelite.client.plugins.microbot.api.tileitem.Rs2TileItemCache;
import net.runelite.client.plugins.microbot.api.tileitem.Rs2TileItemQueryable;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.reflection.Rs2Reflection;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgilityScriptMarkOfGraceTest {

    private static final WorldPoint PLAYER_LOCATION = new WorldPoint(3269, 3166, 3);
    private static final WorldPoint MARK_LOCATION = new WorldPoint(3272, 3166, 3);
    private static final LocalPoint MARK_LOCAL_LOCATION = new LocalPoint(6464, 6464, WorldView.TOPLEVEL);
    private static final String SKIP_MESSAGE = "Could not reach the mark of grace, continuing the course";

    private MockedStatic<Microbot> microbot;
    private MockedStatic<Rs2Inventory> inventory;
    private MockedStatic<Rs2Player> player;
    private MockedStatic<Rs2Walker> walker;
    private MockedStatic<Rs2Camera> camera;
    private Rs2TileItemCache cache;
    private Rs2TileItemQueryable query;
    private AgilityCourseHandler courseHandler;
    private AgilityScript script;

    @BeforeEach
    void setUp() {
        microbot = mockStatic(Microbot.class);
        inventory = mockStatic(Rs2Inventory.class);
        player = mockStatic(Rs2Player.class);
        walker = mockStatic(Rs2Walker.class);
        camera = mockStatic(Rs2Camera.class);

        query = mock(Rs2TileItemQueryable.class);
        when(query.fromWorldView()).thenReturn(query);
        when(query.withId(anyInt())).thenReturn(query);
        when(query.where(any())).thenReturn(query);
        cache = mock(Rs2TileItemCache.class);
        when(cache.query()).thenReturn(query);
        microbot.when(Microbot::getRs2TileItemCache).thenReturn(cache);

        Client client = mock(Client.class);
        when(client.getCanvasWidth()).thenReturn(765);
        when(client.getCanvasHeight()).thenReturn(503);
        microbot.when(Microbot::getClient).thenReturn(client);

        inventory.when(Rs2Inventory::isFull).thenReturn(false);

        courseHandler = mock(AgilityCourseHandler.class);
        when(courseHandler.getPlayerWorldLocation()).thenReturn(PLAYER_LOCATION);

        script = new AgilityScript(mock(MicroAgilityPlugin.class), mock(MicroAgilityConfig.class));
    }

    @AfterEach
    void tearDown() {
        camera.close();
        walker.close();
        player.close();
        inventory.close();
        microbot.close();
    }

    private Rs2TileItemModel markOnTheRoof() {
        Rs2TileItemModel mark = mock(Rs2TileItemModel.class);
        when(mark.getWorldLocation()).thenReturn(MARK_LOCATION);
        when(mark.getLocalLocation()).thenReturn(MARK_LOCAL_LOCATION);
        when(query.toList()).thenReturn(List.of(mark));
        return mark;
    }

    @Test
    void markFoundWhileAnimatingStaysTargetedOnNextLoop() {
        markOnTheRoof();
        player.when(Rs2Player::isAnimating).thenReturn(true);

        assertTrue(script.lootMarksOfGrace(courseHandler));
        assertTrue(script.lootMarksOfGrace(courseHandler));

        verify(cache, times(2)).query();
        microbot.verify(() -> Microbot.doInvoke(any(), any()), never());
        microbot.verify(() -> Microbot.log("Mark of grace spotted, picking it up after this obstacle"), times(1));
    }

    @Test
    void targetedMarkThatIsGoneReleasesTheLoop() {
        Rs2TileItemModel mark = mock(Rs2TileItemModel.class);
        when(mark.getWorldLocation()).thenReturn(MARK_LOCATION);
        when(query.toList()).thenReturn(List.of(mark), Collections.emptyList());
        player.when(Rs2Player::isAnimating).thenReturn(true);

        assertTrue(script.lootMarksOfGrace(courseHandler));
        assertFalse(script.lootMarksOfGrace(courseHandler));
        assertFalse(script.lootMarksOfGrace(courseHandler));

        verify(cache, times(2)).query();
    }

    @Test
    void scanStaysThrottledWithoutATarget() {
        when(query.toList()).thenReturn(Collections.emptyList());

        assertFalse(script.lootMarksOfGrace(courseHandler));
        assertFalse(script.lootMarksOfGrace(courseHandler));

        verify(cache, times(1)).query();
    }

    @Test
    void markOnScreenIsTakenOnceThePlayerIsIdle() {
        markOnTheRoof();
        camera.when(() -> Rs2Camera.isTileOnScreen(any(LocalPoint.class))).thenReturn(true);
        ItemComposition item = mock(ItemComposition.class);
        when(item.getName()).thenReturn("Mark of grace");
        ClientThread clientThread = mock(ClientThread.class);
        when(clientThread.runOnClientThreadOptional(any())).thenReturn(Optional.of(item));
        microbot.when(Microbot::getClientThread).thenReturn(clientThread);

        try (MockedStatic<Rs2Reflection> reflection = mockStatic(Rs2Reflection.class);
             MockedStatic<Perspective> perspective = mockStatic(Perspective.class)) {
            reflection.when(() -> Rs2Reflection.getGroundItemActions(item)).thenReturn(new String[]{null, null, "Take", null, null});

            assertTrue(script.lootMarksOfGrace(courseHandler));
        }

        microbot.verify(() -> Microbot.doInvoke(any(), any()), times(1));
        camera.verify(() -> Rs2Camera.turnTo(any(LocalPoint.class)), never());
    }

    @Test
    void markOffScreenTurnsTheCameraOnceThenWalksToIt() {
        markOnTheRoof();
        camera.when(() -> Rs2Camera.isTileOnScreen(any(LocalPoint.class))).thenReturn(false);
        player.when(Rs2Player::isMoving).thenReturn(false, false, true);
        walker.when(() -> Rs2Walker.walkMiniMap(MARK_LOCATION)).thenReturn(true);

        assertTrue(script.lootMarksOfGrace(courseHandler));
        assertTrue(script.lootMarksOfGrace(courseHandler));

        camera.verify(() -> Rs2Camera.turnTo(any(LocalPoint.class)), times(1));
        walker.verify(() -> Rs2Walker.walkMiniMap(MARK_LOCATION), times(1));
        microbot.verify(() -> Microbot.doInvoke(any(), any()), never());
    }

    @Test
    void markThatCannotBeWalkedToIsSkippedAndTheCourseContinues() throws InterruptedException {
        markOnTheRoof();
        camera.when(() -> Rs2Camera.isTileOnScreen(any(LocalPoint.class))).thenReturn(false);
        walker.when(() -> Rs2Walker.walkMiniMap(MARK_LOCATION)).thenReturn(false);

        assertTrue(script.lootMarksOfGrace(courseHandler));
        assertFalse(script.lootMarksOfGrace(courseHandler));
        assertFalse(script.lootMarksOfGrace(courseHandler));
        verify(cache, times(2)).query();
        microbot.verify(() -> Microbot.log(SKIP_MESSAGE), times(1));

        Thread.sleep(800);
        assertFalse(script.lootMarksOfGrace(courseHandler));

        verify(cache, times(3)).query();
        camera.verify(() -> Rs2Camera.turnTo(any(LocalPoint.class)), times(1));
        walker.verify(() -> Rs2Walker.walkMiniMap(MARK_LOCATION), times(1));
        microbot.verify(() -> Microbot.log(SKIP_MESSAGE), times(1));
    }
}
