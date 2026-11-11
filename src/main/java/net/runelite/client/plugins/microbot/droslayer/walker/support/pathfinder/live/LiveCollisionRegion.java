package net.runelite.client.plugins.microbot.droslayer.walker.support.pathfinder.live;

import java.util.BitSet;

import static net.runelite.api.Constants.REGION_SIZE;

/**
 * Immutable live-collision for a single 64×64 map region, in the same can-north / can-east edge model as
 * {@link LiveCollisionSnapshot} but region-addressed so many of them can accumulate into a
 * {@link LiveCollisionView} covering everywhere the client has ever loaded this session.
 * <p>
 * Each edge carries a validity bit: an edge is only <em>known</em> when a capture actually observed it.
 * Merging two regions ({@link LiveCollisionRegions#merge}) keeps known edges and prefers the newer value,
 * so successive visits fill in edges that an earlier visit left on its untrusted scene rim.
 * <p>
 * Never mutated after construction, so the off-thread pathfinder reads it lock-free.
 */
public final class LiveCollisionRegion {
    static final int SIZE = REGION_SIZE; // 64
    static final int PLANE_STRIDE = SIZE * SIZE;

    private final int planeCount;
    private final BitSet northKnown;
    private final BitSet northValue;
    private final BitSet eastKnown;
    private final BitSet eastValue;

    LiveCollisionRegion(int planeCount, BitSet northKnown, BitSet northValue, BitSet eastKnown, BitSet eastValue) {
        this.planeCount = planeCount;
        this.northKnown = northKnown;
        this.northValue = northValue;
        this.eastKnown = eastKnown;
        this.eastValue = eastValue;
    }

    static int index(int localX, int localY, int z) {
        return z * PLANE_STRIDE + localY * SIZE + localX;
    }

    /**
     * @param localX 0..63 within the region, {@param localY} likewise
     * @return {@link Boolean#TRUE}/{@link Boolean#FALSE} for a known edge, else {@code null}.
     */
    Boolean edgeLocal(int localX, int localY, int z, int flag) {
        if (z < 0 || z >= planeCount) {
            return null;
        }
        final int idx = index(localX, localY, z);
        final BitSet known = flag == LiveCollisionSnapshot.FLAG_EAST ? eastKnown : northKnown;
        if (!known.get(idx)) {
            return null;
        }
        final BitSet value = flag == LiveCollisionSnapshot.FLAG_EAST ? eastValue : northValue;
        return value.get(idx) ? Boolean.TRUE : Boolean.FALSE;
    }

}
