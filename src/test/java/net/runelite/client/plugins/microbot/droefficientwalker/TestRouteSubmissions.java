package net.runelite.client.plugins.microbot.droefficientwalker;

/** Existing engine/coordinator fixtures control publication without wall-clock sleeps. */
final class TestRouteSubmissions
{
    private TestRouteSubmissions() { }

    static WalkerRouteSubmission immediate()
    {
        return new WalkerRouteSubmission(new WalkerRouteSubmission.Scheduler()
        {
            @Override public WalkerRouteSubmission.Cancellation schedule(Runnable callback, int delayMillis)
            {
                callback.run();
                return () -> { };
            }
            @Override public void close() { }
        }, () -> WalkerRouteSubmission.MIN_DELAY_MILLIS, Runnable::run);
    }
}
