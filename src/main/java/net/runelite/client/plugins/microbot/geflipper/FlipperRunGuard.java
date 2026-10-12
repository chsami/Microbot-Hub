package net.runelite.client.plugins.microbot.geflipper;

/** Preserve Microbot's input/pause guards without Script.run's shared preference writes. */
final class FlipperRunGuard {
    interface Context {
        void heartbeat();
        boolean loggedIn();
        boolean fatigueActive();
        void startFatigueSession();
        boolean tutorialComplete();
        boolean blockingEvent();
        boolean humanInput();
        void releaseHeldKeys();
        boolean paused();
        boolean interrupted();
    }

    static boolean canRun(Context context) {
        context.heartbeat();
        if (!context.loggedIn()) return false;
        if (!context.fatigueActive()) context.startFatigueSession();
        if (!context.tutorialComplete() || context.blockingEvent()) return false;
        boolean human = context.humanInput();
        if (human) context.releaseHeldKeys();
        return !context.paused() && !human && !context.interrupted();
    }
}
