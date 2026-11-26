package net.runelite.client.plugins.microbot.accountstarter.run;

import org.junit.jupiter.api.Test;

import static net.runelite.client.plugins.microbot.accountstarter.run.SessionSetup.Login.LOGIN;
import static net.runelite.client.plugins.microbot.accountstarter.run.SessionSetup.Login.NO_PROFILE;
import static net.runelite.client.plugins.microbot.accountstarter.run.SessionSetup.Login.WAIT;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SessionSetupLoginTest {
    @Test
    void logsInOnlyWhenNothingElseWill() {
        assertEquals(LOGIN, SessionSetup.login(true, false, false, false, false, true, true));
        assertEquals(WAIT, SessionSetup.login(false, false, false, false, false, true, true), "not at the login screen");
        assertEquals(WAIT, SessionSetup.login(true, true, false, false, false, true, true), "a break handler logs back in");
        assertEquals(WAIT, SessionSetup.login(true, false, true, false, false, true, true), "Auto Login logs back in");
        assertEquals(WAIT, SessionSetup.login(true, false, false, true, false, true, true), "a login is under way");
        assertEquals(WAIT, SessionSetup.login(true, false, false, false, true, true, true), "the player has the mouse");
        assertEquals(WAIT, SessionSetup.login(true, false, false, false, false, true, false), "not due yet");
    }

    @Test
    void noProfileStopsInsteadOfTryingThirtyTimes() {
        assertEquals(NO_PROFILE, SessionSetup.login(true, false, false, false, false, false, true));
    }
}
