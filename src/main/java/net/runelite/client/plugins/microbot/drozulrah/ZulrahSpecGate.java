package net.runelite.client.plugins.microbot.drozulrah;

/** Confirmation and failure budget for a spec click; never waits on the game. */
final class ZulrahSpecGate
{
    enum Result { NONE, ARMED, SPENT, RETRY, DISABLED }
    private boolean pending;
    private boolean disabled;
    private long sentAt;
    private int energyBefore;
    private int failures;
    private int cost;

    void reset() { pending = false; disabled = false; failures = 0; }
    boolean canDispatch() { return !pending && !disabled; }
    void dispatched(long now, int energy, int cost) { pending = true; sentAt = now; energyBefore = energy; this.cost = cost; }

    Result observe(long now, int energy, boolean armed)
    {
        if (!pending) return Result.NONE;
        if (energyBefore - energy >= cost)
        {
            pending = false;
            failures = 0;
            return Result.SPENT;
        }
        if (armed)
        {
            pending = false;
            failures = 0;
            return Result.ARMED;
        }
        if (now - sentAt < 5_000L) return Result.NONE;
        pending = false;
        disabled = ++failures >= 2;
        return disabled ? Result.DISABLED : Result.RETRY;
    }
}
