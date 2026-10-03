package net.runelite.client.plugins.microbot.drozulrah;

/** Immutable phase information shared with the optional rotation helper overlay. */
public final class ZulrahPhaseSnapshot
{
    public static final ZulrahPhaseSnapshot EMPTY = new ZulrahPhaseSnapshot(-1, -1, 0, null, new ZulrahRotation[0]);
    public final int index, startTick, duration;
    public final ZulrahRotation rotation;
    private final ZulrahRotation[] candidates;

    ZulrahPhaseSnapshot(int index, int startTick, int duration, ZulrahRotation rotation, ZulrahRotation[] candidates) {
        this.index = index; this.startTick = startTick; this.rotation = rotation;
        this.duration = duration;
        this.candidates = candidates.clone();
    }
    public ZulrahRotation[] candidates() { return candidates.clone(); }
}
