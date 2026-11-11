package net.runelite.client.plugins.microbot.blackjack;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlackjackPickpocketLimitTest
{
    @Test
    void pickpocketClicksStopAfterTwoConfirmedPicksOfAKnockout()
    {
        assertTrue(BlackjackScript.pickpocketClickAllowed("Pickpocket", 0));
        assertTrue(BlackjackScript.pickpocketClickAllowed("Pickpocket", 1));
        assertFalse(BlackjackScript.pickpocketClickAllowed("Pickpocket", 2));
        assertFalse(BlackjackScript.pickpocketClickAllowed("Pickpocket", 3));
    }

    @Test
    void knockOutClicksAreNeverHeldBack()
    {
        for (int picks = 0; picks <= 3; picks++)
        {
            assertTrue(BlackjackScript.pickpocketClickAllowed("Knock-Out", picks));
        }
    }
}
