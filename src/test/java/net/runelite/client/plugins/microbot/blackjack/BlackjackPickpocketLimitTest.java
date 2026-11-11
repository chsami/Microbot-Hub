package net.runelite.client.plugins.microbot.blackjack;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlackjackPickpocketLimitTest
{
    @Test
    void pickpocketClicksStopAfterTwoConfirmedPicksOfAKnockout()
    {
        assertTrue(BlackjackScript.pickpocketClickAllowed("Pickpocket", 0, 0));
        assertTrue(BlackjackScript.pickpocketClickAllowed("Pickpocket", 1, 1));
        assertFalse(BlackjackScript.pickpocketClickAllowed("Pickpocket", 2, 2));
        assertFalse(BlackjackScript.pickpocketClickAllowed("Pickpocket", 3, 3));
        assertFalse(BlackjackScript.pickpocketClickAllowed("Pickpocket", 2, 0));
    }

    @Test
    void pickpocketClicksStopOnceTheSecondAttemptHasStarted()
    {
        assertTrue(BlackjackScript.pickpocketClickAllowed("Pickpocket", 0, 1));
        assertFalse(BlackjackScript.pickpocketClickAllowed("Pickpocket", 1, 2));
        assertFalse(BlackjackScript.pickpocketClickAllowed("Pickpocket", 0, 2));
    }

    @Test
    void knockOutClicksAreNeverHeldBack()
    {
        for (int count = 0; count <= 3; count++)
        {
            assertTrue(BlackjackScript.pickpocketClickAllowed("Knock-Out", count, count));
        }
    }

    @Test
    void onlyTheAttemptMessageCountsAsAStartedAttempt()
    {
        assertTrue(BlackjackScript.isPickpocketAttemptMessage("you attempt to pick the menaphite's pocket."));
        assertFalse(BlackjackScript.isPickpocketAttemptMessage("you pick the menaphite's pocket."));
        assertFalse(BlackjackScript.isPickpocketAttemptMessage("you fail to pick the menaphite's pocket."));
        assertFalse(BlackjackScript.isPickpocketAttemptMessage("you've been stunned!"));
    }
}
