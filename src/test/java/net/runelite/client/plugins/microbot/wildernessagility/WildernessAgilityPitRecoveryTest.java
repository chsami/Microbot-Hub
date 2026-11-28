package net.runelite.client.plugins.microbot.wildernessagility;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectQueryable;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WildernessAgilityPitRecoveryTest {

    private static final WorldPoint PIT_TILE = new WorldPoint(3004, 10362, 0);

    @Test
    void emptyLadderRequeryFallsBackToFirstLookup() {
        Rs2TileObjectModel ladder = ladder(true);
        Rs2TileObjectQueryable query = pitQuery(List.of(ladder), Collections.emptyList());

        runRecovery(query, 1);

        verify(ladder).click("Climb-up");
    }

    @Test
    void failedClickRetriesWithoutWaitingForThrottle() {
        Rs2TileObjectModel ladder = ladder(false);
        Rs2TileObjectQueryable query = pitQuery(List.of(ladder));

        runRecovery(query, 2);

        verify(ladder, times(2)).click("Climb-up");
    }

    @Test
    void successfulClickIsThrottled() {
        Rs2TileObjectModel ladder = ladder(true);
        Rs2TileObjectQueryable query = pitQuery(List.of(ladder));

        runRecovery(query, 2);

        verify(ladder, times(1)).click("Climb-up");
    }

    @Test
    void nullPlayerLocationInPitSkipsLadderTick() {
        Rs2TileObjectModel ladder = ladder(true);
        Rs2TileObjectQueryable query = pitQuery(List.of(ladder));

        runRecovery(query, 1, null);

        verify(ladder, never()).click("Climb-up");
    }

    @Test
    void nullPlayerLocationAfterClimbKeepsRopeRecovery() throws Exception {
        WildernessAgilityScript script = new WildernessAgilityScript();
        set(script, "currentState", state("PIT_RECOVERY"));
        set(script, "pitRecoveryTarget", state("ROPE"));

        Rs2TileObjectQueryable query = mock(Rs2TileObjectQueryable.class);
        when(query.withId(anyInt())).thenReturn(query);
        Rs2TileObjectCache cache = mock(Rs2TileObjectCache.class);
        when(cache.query()).thenReturn(query);

        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class)) {
            microbot.when(Microbot::getRs2TileObjectCache).thenReturn(cache);
            player.when(Rs2Player::getWorldLocation).thenReturn(null);

            assertDoesNotThrow(script::recoverFromPit);

            walker.verifyNoInteractions();
        }

        assertEquals(state("PIT_RECOVERY"), get(script, "currentState"));
        assertEquals(state("ROPE"), get(script, "pitRecoveryTarget"));
        assertFalse((boolean) get(script, "ropeRecoveryWalked"));
    }

    @Test
    void nullPlayerLocationSkipsPipeTick() throws Exception {
        WildernessAgilityScript script = new WildernessAgilityScript();
        Method handlePipe = WildernessAgilityScript.class.getDeclaredMethod("handlePipe");
        handlePipe.setAccessible(true);

        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class)) {
            player.when(Rs2Player::getWorldLocation).thenReturn(null);
            player.when(Rs2Player::isAnimating).thenReturn(false);
            player.when(Rs2Player::isMoving).thenReturn(false);

            assertDoesNotThrow(() -> handlePipe.invoke(script));

            walker.verifyNoInteractions();
            microbot.verifyNoInteractions();
        }

        assertFalse((boolean) get(script, "isWaitingForPipe"));
    }

    private static Object state(String name) throws Exception {
        Field field = WildernessAgilityScript.class.getDeclaredField("currentState");
        for (Object constant : field.getType().getEnumConstants()) {
            if (((Enum<?>) constant).name().equals(name)) {
                return constant;
            }
        }
        throw new IllegalArgumentException(name);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = WildernessAgilityScript.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object get(Object target, String name) throws Exception {
        Field field = WildernessAgilityScript.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static Rs2TileObjectModel ladder(boolean clickSucceeds) {
        Rs2TileObjectModel ladder = mock(Rs2TileObjectModel.class);
        when(ladder.getWorldLocation()).thenReturn(PIT_TILE);
        when(ladder.click("Climb-up")).thenReturn(clickSucceeds);
        return ladder;
    }

    @SafeVarargs
    private static Rs2TileObjectQueryable pitQuery(List<Rs2TileObjectModel> first, List<Rs2TileObjectModel>... rest) {
        Rs2TileObjectQueryable query = mock(Rs2TileObjectQueryable.class);
        when(query.withId(anyInt())).thenReturn(query);
        when(query.nearest()).thenReturn(mock(Rs2TileObjectModel.class));
        when(query.toList()).thenReturn(first, rest);
        return query;
    }

    private static void runRecovery(Rs2TileObjectQueryable query, int ticks) {
        runRecovery(query, ticks, PIT_TILE);
    }

    private static void runRecovery(Rs2TileObjectQueryable query, int ticks, WorldPoint playerLocation) {
        Rs2TileObjectCache cache = mock(Rs2TileObjectCache.class);
        when(cache.query()).thenReturn(query);
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class)) {
            microbot.when(Microbot::getRs2TileObjectCache).thenReturn(cache);
            player.when(Rs2Player::getWorldLocation).thenReturn(playerLocation);

            WildernessAgilityScript script = new WildernessAgilityScript();
            for (int i = 0; i < ticks; i++) {
                assertDoesNotThrow(script::recoverFromPit);
            }
        }
    }
}
