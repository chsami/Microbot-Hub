package net.runelite.client.plugins.microbot.blackjack;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlackjackCameraTurnTest
{
    @Test
    void wrapsAcrossNorthByShortestPath()
    {
        BlackjackCameraTurn turn = new BlackjackCameraTurn();
        turn.start(2000, 48, 0, 1000);
        assertEquals(2000, turn.sample(0));
        assertEquals(0, turn.sample(500));
        assertEquals(48, turn.sample(1000));
        assertNull(turn.sample(1020));
        turn.start(48, 2000, 0, 1000);
        assertEquals(0, turn.sample(500));
        assertEquals(2000, turn.sample(1000));
    }

    @Test
    void easesWithoutRestartingActiveTurn()
    {
        BlackjackCameraTurn turn = new BlackjackCameraTurn();
        turn.start(0, 512, 0, 1000);
        assertEquals(80, turn.sample(250));
        turn.start(80, 1024, 250, 1000);
        assertEquals(256, turn.sample(500));
        assertEquals(432, turn.sample(750));
        assertEquals(512, turn.sample(1000));
        assertFalse(turn.isActive());
    }

    @Test
    void cancellationAndNoopDoNotWriteFurtherFrames()
    {
        BlackjackCameraTurn turn = new BlackjackCameraTurn();
        turn.start(0, 2048, 0, 1000);
        assertNull(turn.sample(0));
        turn.start(0, 512, 0, 1000);
        turn.cancel();
        assertNull(turn.sample(500));
    }
}
