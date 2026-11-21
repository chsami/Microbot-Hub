package net.runelite.client.plugins.microbot.droinfernoprayers;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ArenaRearmTest
{
    @Test public void aStopThePlayerMustFixNeverRestartsByItself() {
        ArenaRearm rearm = new ArenaRearm();
        for (int tick = 0; tick < 10; tick++) assertFalse(rearm.due(true, false));
        assertFalse(rearm.due(true, true));
    }

    @Test public void aLostConnectionRestartsOnTheFirstReliableTickEvenInsideTheArena() {
        ArenaRearm rearm = new ArenaRearm();
        rearm.stopped(ArenaRearm.After.LOGGED_IN);
        assertFalse(rearm.due(false, false), "login screen and loading are not reliable");
        assertTrue(rearm.due(true, true), "reconnected mid-wave");
        rearm.stopped(ArenaRearm.After.LOGGED_IN);
        assertTrue(rearm.due(true, false), "logged in outside with the plugin already ticked");
    }

    @Test public void aFinishedRunWaitsUntilThePlayerHasLeft() {
        ArenaRearm rearm = new ArenaRearm();
        rearm.stopped(ArenaRearm.After.OUTSIDE);
        for (int tick = 0; tick < 20; tick++) assertFalse(rearm.due(true, true), "still inside after the last kill");
        assertFalse(rearm.due(true, false));
        assertFalse(rearm.due(true, false));
        assertTrue(rearm.due(true, false));
    }

    @Test public void oneOutsideReadingDuringARegionReloadDoesNotCount() {
        ArenaRearm rearm = new ArenaRearm();
        rearm.stopped(ArenaRearm.After.OUTSIDE);
        assertFalse(rearm.due(true, false));
        assertFalse(rearm.due(true, false));
        assertFalse(rearm.due(true, true), "back inside resets the count");
        assertFalse(rearm.due(false, false), "unreliable ticks are ignored");
        assertFalse(rearm.due(true, false));
        assertFalse(rearm.due(true, false));
        assertTrue(rearm.due(true, false));
    }

    @Test public void aLostArenaReadingResumesAsSoonAsTheArenaIsSeenAgain() {
        ArenaRearm rearm = new ArenaRearm();
        rearm.stopped(ArenaRearm.After.ARENA_SEEN_OR_OUTSIDE);
        assertFalse(rearm.due(true, false));
        assertTrue(rearm.due(true, true), "the player never left: resume in the fight");
        rearm.stopped(ArenaRearm.After.ARENA_SEEN_OR_OUTSIDE);
        assertFalse(rearm.due(true, false));
        assertFalse(rearm.due(true, false));
        assertTrue(rearm.due(true, false), "the player really left: ready for the next entry");
    }

    @Test public void clearingForgetsThePendingRestart() {
        ArenaRearm rearm = new ArenaRearm();
        rearm.stopped(ArenaRearm.After.LOGGED_IN);
        rearm.clear();
        assertEquals(ArenaRearm.After.NEVER, rearm.pending());
        assertFalse(rearm.due(true, true));
    }
}
