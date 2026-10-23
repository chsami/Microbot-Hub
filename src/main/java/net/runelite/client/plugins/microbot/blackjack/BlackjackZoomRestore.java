package net.runelite.client.plugins.microbot.blackjack;

final class BlackjackZoomRestore
{
    private long matchedSince = -1;

    void reset()
    {
        matchedSince = -1;
    }

    boolean confirm(int current, int target, int tolerance, long now)
    {
        if (Math.abs(current - target) > tolerance)
        {
            reset();
            return false;
        }
        if (matchedSince < 0)
        {
            matchedSince = now;
        }
        return now - matchedSince >= 3_000;
    }
}
