package net.runelite.client.plugins.microbot.agility;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.agility.courses.AgilityCourseHandler;
import net.runelite.client.plugins.microbot.api.tileitem.Rs2TileItemCache;
import net.runelite.client.plugins.microbot.api.tileitem.Rs2TileItemQueryable;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Collections;
import java.util.List;

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

    private MockedStatic<Microbot> microbot;
    private MockedStatic<Rs2Inventory> inventory;
    private MockedStatic<Rs2Player> player;
    private MockedStatic<Rs2Walker> walker;
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

        query = mock(Rs2TileItemQueryable.class);
        when(query.fromWorldView()).thenReturn(query);
        when(query.withId(anyInt())).thenReturn(query);
        when(query.where(any())).thenReturn(query);
        cache = mock(Rs2TileItemCache.class);
        when(cache.query()).thenReturn(query);
        microbot.when(Microbot::getRs2TileItemCache).thenReturn(cache);

        inventory.when(Rs2Inventory::isFull).thenReturn(false);

        courseHandler = mock(AgilityCourseHandler.class);
        when(courseHandler.getPlayerWorldLocation()).thenReturn(PLAYER_LOCATION);

        script = new AgilityScript(mock(MicroAgilityPlugin.class), mock(MicroAgilityConfig.class));
    }

    @AfterEach
    void tearDown() {
        walker.close();
        player.close();
        inventory.close();
        microbot.close();
    }

    @Test
    void markFoundWhileAnimatingStaysTargetedOnNextLoop() {
        Rs2TileItemModel mark = mock(Rs2TileItemModel.class);
        when(mark.getWorldLocation()).thenReturn(MARK_LOCATION);
        when(query.toList()).thenReturn(List.of(mark));
        player.when(Rs2Player::isAnimating).thenReturn(true);

        assertTrue(script.lootMarksOfGrace(courseHandler));
        assertTrue(script.lootMarksOfGrace(courseHandler));

        verify(cache, times(2)).query();
        microbot.verify(() -> Microbot.doInvoke(any(), any()), never());
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
}
