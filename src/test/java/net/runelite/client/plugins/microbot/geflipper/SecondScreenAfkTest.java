package net.runelite.client.plugins.microbot.geflipper;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SecondScreenAfkTest {

    static final class MockPort implements SecondScreenAfk.Port {
        boolean loggedIn = true;
        int currentWorld = 302;
        boolean exchangeOrBankOpen = true;
        boolean closedInterfaces;
        boolean movedMouseOffScreen;
        boolean loggedOut;
        int loginWorld = -1;

        @Override public boolean loggedIn() { return loggedIn; }
        @Override public int currentWorld() { return currentWorld; }
        @Override public boolean exchangeOrBankOpen() { return exchangeOrBankOpen; }
        @Override public void closeInterfaces() { closedInterfaces = true; exchangeOrBankOpen = false; }
        @Override public void moveMouseOffScreen() { movedMouseOffScreen = true; }
        @Override public void logout() { loggedOut = true; loggedIn = false; }
        @Override public void login(int world) { loginWorld = world; loggedIn = true; }
    }

    @Test
    void finishCannotCreateALoginWhenAfkNeverOwnedOne() {
        MockPort port = new MockPort();
        port.loggedIn = false;
        SecondScreenAfk afk = new SecondScreenAfk(port, (min, max) -> min);
        assertFalse(afk.resumeForFinish());
        assertFalse(afk.tickLoggedOut(FlipperConfig.SecondScreenAfkMode.LOGOUT_BETWEEN_CHECKS,
            10, false, 1_000_000));
        assertEquals(-1, port.loginWorld);
        assertEquals(SecondScreenAfk.Phase.WORKING, afk.phase());
    }

    @Test
    void disabledModeDoesNothing() {
        MockPort port = new MockPort();
        SecondScreenAfk afk = new SecondScreenAfk(port, (min, max) -> min);

        assertFalse(afk.tickBeforeTrading(FlipperConfig.SecondScreenAfkMode.OFF, 10, false, 1000));
        assertFalse(afk.onSettledWaitTick(FlipperConfig.SecondScreenAfkMode.OFF, 10, false, true, 1000));
        assertFalse(afk.tickLoggedOut(FlipperConfig.SecondScreenAfkMode.OFF, 10, false, 1000));
        assertEquals(SecondScreenAfk.Phase.WORKING, afk.phase());
    }

    @Test
    void settledWaitTransitionsToSnoozeAfterDelay() {
        MockPort port = new MockPort();
        SecondScreenAfk afk = new SecondScreenAfk(port, (min, max) -> min);

        long now = 10_000L;
        // First settled tick arms the settle window
        assertTrue(afk.onSettledWaitTick(FlipperConfig.SecondScreenAfkMode.STAY_LOGGED_IN, 10, false, true, now));
        assertEquals(SecondScreenAfk.Phase.WORKING, afk.phase());
        assertTrue(afk.status().contains("finishing check-in"));

        // Tick before settle delay expires stays in WORKING
        assertTrue(afk.onSettledWaitTick(FlipperConfig.SecondScreenAfkMode.STAY_LOGGED_IN, 10, false, true, now + 1000L));
        assertEquals(SecondScreenAfk.Phase.WORKING, afk.phase());

        // Tick after settle delay expires transitions to SNOOZING and moves mouse off screen
        assertTrue(afk.onSettledWaitTick(FlipperConfig.SecondScreenAfkMode.STAY_LOGGED_IN, 10, false, true, now + SecondScreenAfk.SETTLE_MIN_MS + 10L));
        assertEquals(SecondScreenAfk.Phase.SNOOZING, afk.phase());
        assertTrue(port.movedMouseOffScreen);
        assertTrue(afk.status().contains("next check-in in"));
    }

    @Test
    void stayLoggedInSnoozingBlocksTradingUntilTimerExpires() {
        MockPort port = new MockPort();
        SecondScreenAfk afk = new SecondScreenAfk(port, (min, max) -> min);

        long now = 10_000L;
        afk.onSettledWaitTick(FlipperConfig.SecondScreenAfkMode.STAY_LOGGED_IN, 1, false, true, now);
        long settledAt = now + SecondScreenAfk.SETTLE_MIN_MS + 10L;
        afk.onSettledWaitTick(FlipperConfig.SecondScreenAfkMode.STAY_LOGGED_IN, 1, false, true, settledAt);
        assertEquals(SecondScreenAfk.Phase.SNOOZING, afk.phase());

        // While snoozing, tickBeforeTrading returns true to pause Copilot actions
        assertTrue(afk.tickBeforeTrading(FlipperConfig.SecondScreenAfkMode.STAY_LOGGED_IN, 1, false, settledAt + 10_000L));
        assertTrue(afk.status().contains("next check-in in"));

        // After snooze expires, resumes WORKING
        assertFalse(afk.tickBeforeTrading(FlipperConfig.SecondScreenAfkMode.STAY_LOGGED_IN, 1, false, settledAt + 60_000L));
        assertEquals(SecondScreenAfk.Phase.WORKING, afk.phase());
    }

    @Test
    void logoutModeClosesInterfacesLogsOutAndLogsBackInWhenTimerExpires() {
        MockPort port = new MockPort();
        SecondScreenAfk afk = new SecondScreenAfk(port, (min, max) -> min);

        long now = 10_000L;
        afk.onSettledWaitTick(FlipperConfig.SecondScreenAfkMode.LOGOUT_BETWEEN_CHECKS, 1, false, true, now);
        long settledAt = now + SecondScreenAfk.SETTLE_MIN_MS + 10L;
        afk.onSettledWaitTick(FlipperConfig.SecondScreenAfkMode.LOGOUT_BETWEEN_CHECKS, 1, false, true, settledAt);
        assertEquals(SecondScreenAfk.Phase.SNOOZING, afk.phase());
        assertTrue(afk.holdingLogout());

        // Interface close on first tick
        long t1 = settledAt + 10L;
        assertTrue(afk.tickBeforeTrading(FlipperConfig.SecondScreenAfkMode.LOGOUT_BETWEEN_CHECKS, 1, false, t1));
        assertTrue(port.closedInterfaces);
        assertFalse(port.loggedOut);

        // Logout after retry delay
        long t2 = t1 + SecondScreenAfk.LOGOUT_RETRY_MS + 10L;
        assertTrue(afk.tickBeforeTrading(FlipperConfig.SecondScreenAfkMode.LOGOUT_BETWEEN_CHECKS, 1, false, t2));
        assertTrue(port.loggedOut);

        // While logged out during snooze, tickLoggedOut returns true
        long t3 = t2 + 5_000L;
        assertTrue(afk.tickLoggedOut(FlipperConfig.SecondScreenAfkMode.LOGOUT_BETWEEN_CHECKS, 1, false, t3));
        assertTrue(afk.status().contains("logged out"));

        // When snooze expires, transitions to LOGGING_IN and calls port.login(world)
        long t4 = settledAt + 60_000L;
        assertTrue(afk.tickLoggedOut(FlipperConfig.SecondScreenAfkMode.LOGOUT_BETWEEN_CHECKS, 1, false, t4));
        assertEquals(302, port.loginWorld);
        assertEquals(SecondScreenAfk.Phase.LOGGING_IN, afk.phase());
    }
}
