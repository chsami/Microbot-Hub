package net.runelite.client.plugins.microbot.blackjack;

/** A single shortest-path yaw turn, sampled by client ticks rather than sleeps. */
final class BlackjackCameraTurn
{
    private boolean active;
    private int start;
    private int delta;
    private long startedAt;
    private long duration;

    synchronized void start(int from, int to, long now, long durationMs)
    {
        if (active)
        {
            return;
        }
        start = from & 2047;
        delta = ((to - start + 3072) & 2047) - 1024;
        startedAt = now;
        duration = Math.max(1, durationMs);
        active = delta != 0;
    }

    synchronized Integer sample(long now)
    {
        if (!active)
        {
            return null;
        }
        double t = Math.min(1, Math.max(0, (now - startedAt) / (double) duration));
        double eased = t * t * (3 - 2 * t);
        if (t >= 1)
        {
            active = false;
        }
        return (start + (int) Math.round(delta * eased)) & 2047;
    }

    synchronized boolean isActive()
    {
        return active;
    }

    synchronized void cancel()
    {
        active = false;
    }
}
