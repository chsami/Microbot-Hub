package net.runelite.client.plugins.microbot.geflipper;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class WaitingMouseTest {
    @Test
    void virtualCursorPositionAndExitFlagDetermineWhetherMovementIsNeeded() {
        assertTrue(WaitingMouse.insideCanvas(new java.awt.Point(20, 30), false, 765, 503));
        assertFalse(WaitingMouse.insideCanvas(new java.awt.Point(20, 30), true, 765, 503));
        assertFalse(WaitingMouse.insideCanvas(new java.awt.Point(-1, -1), false, 765, 503));
        assertFalse(WaitingMouse.insideCanvas(new java.awt.Point(765, 30), false, 765, 503));
        assertFalse(WaitingMouse.insideCanvas(null, false, 765, 503));
    }

    @Test
    void defaultOffAndZeroChanceNeverReadOrMoveTheClient() {
        WaitingMouse timer = new WaitingMouse((low, high) -> low);
        Context context = new Context();
        assertFalse(new FlipperConfig() {}.waitingMouseOffScreen());
        assertFalse(timer.tick(false, 100, 0, context));
        assertFalse(timer.tick(true, 0, 10000, context));
        assertEquals(0, context.reads);
        assertEquals(0, context.moves);
    }

    @Test
    void continuousWaitUsesRandomDeadlineAndMovesOnlyOnce() {
        WaitingMouse timer = new WaitingMouse((low, high) -> low == 1 ? 1 : high);
        Context context = new Context();
        assertTrue(timer.tick(true, 30, 0, context));
        timer.tick(true, 30, 64499, context);
        assertEquals(0, context.moves, "The automatically chosen random delay must fully elapse");
        timer.tick(true, 30, 64500, context);
        timer.tick(true, 30, 300000, context);
        assertEquals(1, context.moves, "Refreshed waiting observations must not cause repeated parking");
    }

    @Test
    void failedChanceCheckGetsAnotherRandomDelay() {
        int[] rolls = {100, 1};
        int[] rollIndex = {0};
        WaitingMouse timer = new WaitingMouse((low, high) -> low == 1 ? rolls[rollIndex[0]++] : low);
        Context context = new Context();
        timer.tick(true, 30, 0, context);
        timer.tick(true, 30, 14600, context);
        timer.tick(true, 30, 29199, context);
        assertEquals(0, context.moves);
        timer.tick(true, 30, 29200, context);
        assertEquals(1, context.moves);
    }

    @Test
    void actionPauseOrUnavailableUiCancelsTheOldDeadline() {
        WaitingMouse timer = new WaitingMouse((low, high) -> low);
        Context context = new Context();
        timer.tick(true, 100, 0, context);
        context.waiting = false;
        assertFalse(timer.tick(true, 100, 2000, context));
        context.waiting = true;
        timer.tick(true, 100, 2001, context);
        timer.tick(true, 100, 4000, context);
        assertEquals(0, context.moves);
        timer.tick(true, 100, 4001, context);
        assertEquals(1, context.moves);
    }

    @Test
    void changedSuggestionAtFinalRecheckPreventsMovement() {
        WaitingMouse timer = new WaitingMouse((low, high) -> low);
        Context context = new Context();
        timer.tick(true, 100, 0, context);
        context.falseAtRead = 3;
        assertFalse(timer.tick(true, 100, 2000, context));
        assertEquals(0, context.moves);
    }

    @Test
    void logoutOrConfigResetDuringFinalReadInvalidatesMovement() {
        WaitingMouse timer = new WaitingMouse((low, high) -> low);
        Context context = new Context();
        timer.tick(true, 100, 0, context);
        context.onRead = () -> { if (context.reads == 3) timer.reset(); };
        assertFalse(timer.tick(true, 100, 2000, context));
        assertEquals(0, context.moves);
    }

    @Test
    void alreadyOutsideIsLeftAloneUntilANewWaitingEpisode() {
        WaitingMouse timer = new WaitingMouse((low, high) -> low);
        Context context = new Context();
        context.inside = false;
        timer.tick(true, 100, 0, context);
        context.inside = true;
        timer.tick(true, 100, 2000, context);
        assertEquals(0, context.moves, "Another plugin's mouse return must not start a parking loop");
        timer.reset();
        timer.tick(true, 100, 2001, context);
        timer.tick(true, 100, 4001, context);
        assertEquals(1, context.moves);
    }

    @Test
    void explicitResetAndDisableRequireAFreshDelay() {
        WaitingMouse timer = new WaitingMouse((low, high) -> low);
        Context context = new Context();
        timer.tick(true, 100, 0, context);
        timer.reset();
        timer.tick(true, 100, 1000, context);
        assertEquals(0, context.moves);
        timer.tick(false, 100, 2000, context);
        timer.tick(true, 100, 3000, context);
        assertEquals(0, context.moves);
        timer.tick(true, 100, 5000, context);
        assertEquals(1, context.moves);
    }

    @Test
    void percentageAloneDerivesSafeAutomaticDelayBounds() {
        WaitingMouse timer = new WaitingMouse((low, high) -> {
            assertTrue(low >= 1000 || low == 1);
            assertTrue(high >= low);
            assertTrue(high <= 90000);
            return low;
        });
        Context context = new Context();
        timer.tick(true, 500, 0, context);
        timer.tick(true, 500, 2000, context);
        assertEquals(1, context.moves);
        assertEquals(2000, WaitingMouse.minimumDelay(100));
        assertEquals(5000, WaitingMouse.maximumDelay(100));
        for (int frequency = 1; frequency <= 100; frequency++) {
            assertTrue(WaitingMouse.maximumDelay(frequency) >= WaitingMouse.minimumDelay(frequency));
            assertTrue(WaitingMouse.minimumDelay(frequency) <= WaitingMouse.minimumDelay(frequency - 1));
            assertTrue(WaitingMouse.maximumDelay(frequency) <= WaitingMouse.maximumDelay(frequency - 1));
        }
    }

    private static class Context implements WaitingMouse.Context {
        boolean waiting = true;
        boolean inside = true;
        int reads;
        int falseAtRead = -1;
        int moves;
        Runnable onRead = () -> {};
        public boolean waiting() {
            reads++;
            onRead.run();
            return waiting && reads != falseAtRead;
        }
        public boolean insideCanvas() { return inside; }
        public void moveOffScreen() { moves++; }
    }
}
