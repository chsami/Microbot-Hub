package net.runelite.client.plugins.microbot.geflipper;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class CopilotWaitingTest {
    @Test
    void onlyAnExplicitActiveWaitIsEligible() {
        Manager manager = new Manager();
        Controller controller = new Controller();
        assertTrue(WaitingMouse.copilotWaiting(manager, controller));
        manager.suggestion.wait = false;
        assertFalse(WaitingMouse.copilotWaiting(manager, controller));
        manager.suggestion = null;
        assertFalse(WaitingMouse.copilotWaiting(manager, controller));
    }

    @Test
    void pausedCopilotWithStaleWaitAndSuggestionErrorsAreRejected() {
        Manager manager = new Manager();
        Controller controller = new Controller();
        controller.paused.paused = true;
        assertFalse(WaitingMouse.copilotWaiting(manager, controller));
        controller.paused.paused = false;
        manager.error = new Object();
        assertFalse(WaitingMouse.copilotWaiting(manager, controller));
        manager.error = null;
        assertTrue(WaitingMouse.copilotWaiting(manager, controller));
    }

    @Test
    void unavailableOrChangedCopilotApisFailClosed() {
        assertFalse(WaitingMouse.copilotWaiting(null, new Controller()));
        assertFalse(WaitingMouse.copilotWaiting(new Manager(), null));
        assertFalse(WaitingMouse.copilotWaiting(new Object(), new Controller()));
        assertFalse(WaitingMouse.copilotWaiting(new Manager(), new Object()));
        assertFalse(WaitingMouse.copilotWaiting(new ThrowingManager(), new Controller()));
    }

    public static class Suggestion {
        boolean wait = true;
        public boolean isWaitSuggestion() { return wait; }
    }
    public static class Manager {
        Suggestion suggestion = new Suggestion();
        Object error;
        public Suggestion getSuggestion() { return suggestion; }
        public Object getSuggestionError() { return error; }
    }
    public static class Controller {
        Paused paused = new Paused();
        public Paused getPausedManager() { return paused; }
    }
    public static class Paused {
        boolean paused;
        public boolean isPaused() { return paused; }
    }
    public static class ThrowingManager extends Manager {
        @Override public Suggestion getSuggestion() { throw new IllegalStateException("Synthetic unavailable state"); }
    }
}
