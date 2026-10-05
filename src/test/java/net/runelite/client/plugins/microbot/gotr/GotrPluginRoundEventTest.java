package net.runelite.client.plugins.microbot.gotr;

import net.runelite.api.GameState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GotrPluginRoundEventTest
{
    @Test
    void roundEndMessagesAreRecognised()
    {
        assertTrue(GotrPlugin.isRoundEndMessage("The Great Guardian successfully closed the rift!"));
        assertTrue(GotrPlugin.isRoundEndMessage("The Great Guardian was defeated!"));
        assertTrue(GotrPlugin.isRoundEndMessage("<col=ef1020>The Great Guardian was defeated!</col>"));
    }

    @Test
    void otherRoundMessagesDoNotEndTheRound()
    {
        assertFalse(GotrPlugin.isRoundEndMessage("The rift becomes active!"));
        assertFalse(GotrPlugin.isRoundEndMessage("The rift will become active in 30 seconds."));
        assertFalse(GotrPlugin.isRoundEndMessage("The Portal Guardians will keep their rifts open for another 30 seconds."));
    }

    @Test
    void logoutAndHopEndTheSession()
    {
        assertTrue(GotrPlugin.endsSession(GameState.LOGIN_SCREEN));
        assertTrue(GotrPlugin.endsSession(GameState.HOPPING));
        assertFalse(GotrPlugin.endsSession(GameState.LOADING));
        assertFalse(GotrPlugin.endsSession(GameState.LOGGED_IN));
    }
}
