package net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.util.action;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ClickBurstTest
{
    private final ClickBurst burst = new ClickBurst();
    private final List<Integer> sent = new ArrayList<>();

    @Test
    public void eachClickWaitsForTheGameToTakeTheOneBeforeIt()
    {
        burst.start(List.of(1, 2, 3), item -> sent.add(item));
        burst.advance();
        burst.advance();
        assertEquals(List.of(1), sent, "the second click waits while the first is not taken");
        burst.clicked(1);
        burst.advance();
        burst.clicked(2);
        burst.advance();
        assertEquals(List.of(1, 2, 3), sent);
        assertTrue(burst.isActive(), "the last click is not taken yet");
        burst.clicked(3);
        assertFalse(burst.isActive());
    }

    @Test
    public void aClickOnAnotherItemDoesNotReleaseTheWait()
    {
        burst.start(List.of(1, 2), item -> sent.add(item));
        burst.advance();
        burst.clicked(9);
        burst.advance();
        assertEquals(List.of(1), sent);
    }

    @Test
    public void aClickNeverTakenDropsTheRestOfTheBatch()
    {
        burst.start(List.of(1, 2), item -> sent.add(item));
        for (int tick = 0; tick <= ClickBurst.CLICK_TIMEOUT_TICKS; tick++)
        {
            burst.advance();
        }
        assertFalse(burst.isActive());
        burst.advance();
        assertEquals(List.of(1), sent);
    }

    @Test
    public void anItemThatCouldNotBeClickedDoesNotHoldTheNext()
    {
        burst.start(List.of(1, 2), item -> item != 1 && sent.add(item));
        burst.advance();
        burst.advance();
        assertEquals(List.of(2), sent);
    }
}
