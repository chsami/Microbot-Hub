package net.runelite.client.plugins.microbot.geflipper;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.awt.Point;

/** Private idle timer; no sleeps, preference writes, or additional scheduled tasks. */
final class WaitingMouse {
    interface Context {
        boolean waiting();
        boolean insideCanvas();
        void moveOffScreen();
    }

    interface RandomInt {
        int inclusive(int minimum, int maximum);
    }

    private final RandomInt random;
    private final AtomicLong generation = new AtomicLong();
    private boolean armed;
    private boolean parked;
    private long nextCheck;

    WaitingMouse() {
        this((minimum, maximum) -> ThreadLocalRandom.current().nextInt(minimum, maximum + 1));
    }

    WaitingMouse(RandomInt random) {
        this.random = random;
    }

    static boolean insideCanvas(Point point, boolean outside, int width, int height) {
        return !outside && point != null && point.x >= 0 && point.y >= 0
            && point.x < width && point.y < height;
    }

    /** Read only ephemeral Copilot state; stale WAIT while paused or errored is not idle. */
    static boolean copilotWaiting(Object manager, Object controller) {
        if (manager == null || controller == null) return false;
        try {
            Object paused = controller.getClass().getMethod("getPausedManager").invoke(controller);
            if (paused == null || !Boolean.FALSE.equals(paused.getClass().getMethod("isPaused").invoke(paused))) return false;
            if (manager.getClass().getMethod("getSuggestionError").invoke(manager) != null) return false;
            Object suggestion = manager.getClass().getMethod("getSuggestion").invoke(manager);
            return suggestion != null && Boolean.TRUE.equals(
                suggestion.getClass().getMethod("isWaitSuggestion").invoke(suggestion));
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return false;
        }
    }

    /** Returns whether this was a confirmed waiting iteration, including checks that do not move. */
    boolean tick(boolean enabled, int frequency, long now, Context context) {
        long currentGeneration = generation.get();
        if (!enabled || frequency <= 0 || !context.waiting()) {
            reset();
            return false;
        }
        boolean move = plan(currentGeneration, context.insideCanvas(), frequency, now);
        if (move) {
            // A logout/config event or changed suggestion must invalidate a pending movement.
            // Never hold the timer lock across a client-thread read or mouse movement.
            if (generation.get() != currentGeneration || !context.waiting()
                || generation.get() != currentGeneration) {
                reset();
                return false;
            }
            context.moveOffScreen();
        }
        return true;
    }

    static int minimumDelay(int frequency) {
        return 2000 + (100 - Math.max(0, Math.min(100, frequency))) * 180;
    }

    static int maximumDelay(int frequency) {
        return 5000 + (100 - Math.max(0, Math.min(100, frequency))) * 850;
    }

    private synchronized boolean plan(long expectedGeneration, boolean insideCanvas, int frequency, long now) {
        if (generation.get() != expectedGeneration || parked) return false;
        if (!insideCanvas) {
            parked = true;
            return false;
        }
        int minimum = minimumDelay(frequency);
        int maximum = maximumDelay(frequency);
        if (!armed) {
            armed = true;
            nextCheck = now + random.inclusive(minimum, maximum);
            return false;
        }
        if (now < nextCheck) return false;
        nextCheck = now + random.inclusive(minimum, maximum);
        if (random.inclusive(1, 100) > Math.min(100, frequency)) return false;
        parked = true;
        return true;
    }

    synchronized void reset() {
        generation.incrementAndGet();
        armed = parked = false;
        nextCheck = 0;
    }
}
