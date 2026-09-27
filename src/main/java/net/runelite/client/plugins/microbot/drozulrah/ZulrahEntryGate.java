package net.runelite.client.plugins.microbot.drozulrah;

/** Pure scene handoff policy. No sleeps, clicks, NPC references or game-thread calls. */
final class ZulrahEntryGate
{
    enum Step { WAIT, CONTINUE, COMBAT }
    private int firstTick = -1;
    private int continuedTick = -1;

    void reset() { firstTick = -1; continuedTick = -1; }

    Step observe(boolean ready, int tick, boolean dialogue, boolean activeBoss)
    {
        if (!ready) { reset(); return Step.WAIT; }
        if (firstTick < 0) firstTick = tick;
        if (tick - firstTick < 2) return Step.WAIT;
        if (dialogue)
        {
            if (continuedTick >= 0 && tick - continuedTick < 3) return Step.WAIT;
            continuedTick = tick;
            return Step.CONTINUE;
        }
        if (continuedTick >= 0 && tick - continuedTick < 2) return Step.WAIT;
        return activeBoss ? Step.COMBAT : Step.WAIT;
    }
}
