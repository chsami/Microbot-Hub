package net.runelite.client.plugins.microbot.wildernessagility;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectQueryable;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
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
        Rs2TileObjectCache cache = mock(Rs2TileObjectCache.class);
        when(cache.query()).thenReturn(query);
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class)) {
            microbot.when(Microbot::getRs2TileObjectCache).thenReturn(cache);
            player.when(Rs2Player::getWorldLocation).thenReturn(PIT_TILE);

            WildernessAgilityScript script = new WildernessAgilityScript();
            for (int i = 0; i < ticks; i++) {
                assertDoesNotThrow(script::recoverFromPit);
            }
        }
    }
}
