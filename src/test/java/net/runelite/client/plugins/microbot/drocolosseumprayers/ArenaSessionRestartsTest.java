package net.runelite.client.plugins.microbot.drocolosseumprayers;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ArenaSessionRestartsTest
{
    @Test public void decisionLoopFailuresAreRetriedAndDeliberateStopsAreNot() {
        assertTrue(ArenaSessionRestarts.recoverable("Local Colosseum: Decision exceeded its 600 ms frame deadline"));
        assertTrue(ArenaSessionRestarts.recoverable("Local Colosseum: Decision missed its observed game tick"));
        assertTrue(ArenaSessionRestarts.recoverable("Local Colosseum: Decision backlog would discard an observed game frame"));
        assertTrue(ArenaSessionRestarts.recoverable("Local Colosseum: Decision failed: IllegalStateException"));
        assertTrue(ArenaSessionRestarts.recoverable("Local Colosseum: snapshot capture failed: NullPointerException"));
        assertFalse(ArenaSessionRestarts.recoverable("Local Colosseum: blocked unapproved action"));
        assertFalse(ArenaSessionRestarts.recoverable(null));
    }

    @Test public void aLagSpikeIsRetriedButAClientThatCannotKeepUpIsNot() {
        ArenaSessionRestarts restarts = new ArenaSessionRestarts();
        assertTrue(restarts.allow(0));
        assertTrue(restarts.allow(1_000));
        assertTrue(restarts.allow(2_000));
        assertFalse(restarts.allow(3_000), "a fourth restart inside a minute gives up");
        assertFalse(restarts.allow(59_999));
        assertTrue(restarts.allow(60_000), "the first restart has aged out");
    }

    @Test public void clearingStartsTheCountAgain() {
        ArenaSessionRestarts restarts = new ArenaSessionRestarts();
        for (int i = 0; i < ArenaSessionRestarts.LIMIT; i++) assertTrue(restarts.allow(i));
        assertFalse(restarts.allow(10));
        restarts.clear();
        assertTrue(restarts.allow(11));
    }
}
