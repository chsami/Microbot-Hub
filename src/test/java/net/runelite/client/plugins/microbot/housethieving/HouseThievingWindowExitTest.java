package net.runelite.client.plugins.microbot.housethieving;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectQueryable;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HouseThievingWindowExitTest {

    private static final int WINDOW_ID = 52998;

    @Test
    void exitsThroughWindowOfferingExit() {
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class)) {
            Rs2TileObjectQueryable query = stubWindow(microbot, Set.of("Exit"));

            assertTrue(HouseThievingScript.clickWindowExit(WINDOW_ID));
            verify(query).interact("Exit");
            verify(query, never()).interact("Exit-window");
        }
    }

    @Test
    void fallsBackToLegacyExitWindowAction() {
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class)) {
            Rs2TileObjectQueryable query = stubWindow(microbot, Set.of("Exit-window"));

            assertTrue(HouseThievingScript.clickWindowExit(WINDOW_ID));
            verify(query).interact("Exit");
            verify(query).interact("Exit-window");
        }
    }

    @Test
    void reportsFailureWhenWindowOffersNoExit() {
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class)) {
            stubWindow(microbot, Set.of("Examine"));

            assertFalse(HouseThievingScript.clickWindowExit(WINDOW_ID));
        }
    }

    private static Rs2TileObjectQueryable stubWindow(MockedStatic<Microbot> microbot, Set<String> actions) {
        Rs2TileObjectQueryable query = mock(Rs2TileObjectQueryable.class);
        when(query.withId(anyInt())).thenReturn(query);
        when(query.interact(anyString())).thenAnswer(invocation -> actions.contains(invocation.<String>getArgument(0)));
        Rs2TileObjectCache cache = mock(Rs2TileObjectCache.class);
        when(cache.query()).thenReturn(query);
        microbot.when(Microbot::getRs2TileObjectCache).thenReturn(cache);
        return query;
    }
}
