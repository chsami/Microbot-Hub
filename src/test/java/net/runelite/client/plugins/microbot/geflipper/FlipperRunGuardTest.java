package net.runelite.client.plugins.microbot.geflipper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the production guard without a client, settings manager, or gameplay actions. */
public class FlipperRunGuardTest {
    @Test
    public void loggedOutOnlyRecordsHeartbeat() {
        Context context = new Context();
        context.loggedIn = false;
        assertFalse(FlipperRunGuard.canRun(context));
        assertEquals(Arrays.asList("heartbeat", "login"), context.calls);
    }

    @Test
    public void humanInputReleasesHeldKeysAndPreventsTrading() {
        Context context = new Context();
        context.human = true;
        assertFalse(FlipperRunGuard.canRun(context));
        assertTrue(context.calls.contains("release keys"));
        assertFalse(context.calls.contains("interrupted"));
    }

    @Test
    public void globalPausePreventsTrading() {
        Context context = new Context();
        context.paused = true;
        assertFalse(FlipperRunGuard.canRun(context));
        assertFalse(context.calls.contains("release keys"));
    }

    @Test
    public void blockingEventStopsBeforeHumanInputChecks() {
        Context context = new Context();
        context.blocked = true;
        assertFalse(FlipperRunGuard.canRun(context));
        assertFalse(context.calls.contains("human"));
    }

    @Test
    public void incompleteTutorialPreventsTrading() {
        Context context = new Context();
        context.tutorial = false;
        assertFalse(FlipperRunGuard.canRun(context));
        assertFalse(context.calls.contains("blocking"));
    }

    @Test
    public void unavailableClientReadDoesNotBecomePermissionToTrade() {
        Context context = new Context();
        context.unavailable = true;
        assertThrows(GeUiState.UiUnavailable.class, () -> FlipperRunGuard.canRun(context));
        assertFalse(context.calls.contains("blocking"));
    }

    @Test
    public void interruptionPreventsTrading() {
        Context context = new Context();
        context.interrupted = true;
        assertFalse(FlipperRunGuard.canRun(context));
    }

    @Test
    public void eligibleSessionStartsInMemoryFatigueTimerOnlyOnce() {
        Context context = new Context();
        context.fatigueActive = false;
        assertTrue(FlipperRunGuard.canRun(context));
        assertTrue(FlipperRunGuard.canRun(context));
        assertEquals(1, context.calls.stream().filter("start fatigue"::equals).count());
        assertEquals(2, context.calls.stream().filter("heartbeat"::equals).count());
    }

    private static final class Context implements FlipperRunGuard.Context {
        final List<String> calls = new ArrayList<>();
        boolean loggedIn = true, fatigueActive = true, tutorial = true;
        boolean blocked, human, paused, interrupted, unavailable;
        public void heartbeat() { calls.add("heartbeat"); }
        public boolean loggedIn() { calls.add("login"); return loggedIn; }
        public boolean fatigueActive() { calls.add("fatigue"); return fatigueActive; }
        public void startFatigueSession() { calls.add("start fatigue"); fatigueActive = true; }
        public boolean tutorialComplete() {
            calls.add("tutorial");
            if (unavailable) throw new GeUiState.UiUnavailable();
            return tutorial;
        }
        public boolean blockingEvent() { calls.add("blocking"); return blocked; }
        public boolean humanInput() { calls.add("human"); return human; }
        public void releaseHeldKeys() { calls.add("release keys"); }
        public boolean paused() { calls.add("paused"); return paused; }
        public boolean interrupted() { calls.add("interrupted"); return interrupted; }
    }
}
