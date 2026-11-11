package net.runelite.client.plugins.microbot.droslayer.walker.support.pathfinder.live;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Shared, accumulating holder for live collision. One instance is referenced by every {@code CollisionMap}
 * (which is a {@code ThreadLocal} over a shared immutable {@code SplitFlagMap}, so the live data must live
 * outside those per-thread instances).
 * <p>
 * Unlike the original single-scene holder, this accumulates every captured scene into a region-addressed
 * store: the client thread merges each new scene ({@link #set}/{@link #mergeScene}) and previously loaded
 * regions ({@link #putRegion}, from disk) into an immutable {@link LiveCollisionView} that the off-thread
 * pathfinder reads as one volatile load through {@link #current()}. Because each view is immutable, a reader
 * that pins it for the duration of one search sees a consistent view even if the client thread merges a
 * newer scene mid-search. Accumulation means the overlay keeps covering a region after the player leaves it,
 * so routing through already-seen ground reflects the client's real collision instead of the static map's
 * reconstruction.
 * <p>
 * When {@link #isEnabled()} is {@code false} — the default, and whenever the config flag is off —
 * {@link #current()} returns {@code null} and callers behave exactly as the static-only map does.
 */
public final class LiveCollisionOverlay {
    private volatile boolean enabled;
    private volatile LiveCollisionView current;

    /** Mutated only under {@code this}; the immutable {@link #current} view is what readers see. */
    private final Map<Integer, LiveCollisionRegion> regions = new HashMap<>();
    private final Set<Integer> dirty = new HashSet<>();

    /**
     * @return the current accumulated view, or {@code null} when disabled or nothing has been captured yet.
     * Callers doing more than one edge read should pin this into a local so the whole search sees one
     * immutable view.
     */
    public LiveCollisionView current() {
        return enabled ? current : null;
    }

    public synchronized void clear() {
        clearLocked();
    }

    private void clearLocked() {
        regions.clear();
        dirty.clear();
        current = null;
    }
}
