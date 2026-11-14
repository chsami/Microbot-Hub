package net.runelite.client.plugins.microbot.droefficientwalker.support.pathfinder.live;

import net.runelite.client.plugins.microbot.droefficientwalker.support.pathfinder.SplitFlagMap;

import static net.runelite.api.Constants.SCENE_SIZE;

/**
 * Telemetry for the static-vs-live collision question: how much does the freshly captured scene
 * disagree with the shipped map? Feeds the persistent-live-store decision with numbers instead of
 * anecdotes (the MLM rockfall / kebbit "falsely locked" reports were live-vs-static conflicts).
 *
 * <p>Deliberately runs once per CAPTURE against the immutable snapshot — never on the pathfinder
 * hot path, where the same comparison would double every edge lookup. Log-only: nothing here
 * changes routing.
 */
public final class LiveCollisionConflicts {

    /** Disagreement tally for one captured scene. */
    public static final class Tally {
        /** Edges the live scene says are walkable where the shipped map says wall (stale map / open door). */
        public final int liveOpensStatic;
        /** Edges the live scene says are blocked where the shipped map says walkable (new obstacle / closed door). */
        public final int liveBlocksStatic;
        /**
         * Live-walkable edges on tiles the shipped map seals on ALL four sides. Overwhelmingly these are
         * tiles the static map simply has no data for — {@code SplitFlagMap.get} returns false for those,
         * so a floorless upper plane reads as solid wall while the live scene's empty collision flags read
         * as open. Two planes of that noise (~43k edges) drowned the real signal in the first data set, so
         * it gets its own bucket instead of inflating {@link #liveOpensStatic}. A genuinely sealed tile
         * that live says is open — the "falsely locked" rockfall case — also lands here, which is why the
         * bucket is reported rather than discarded.
         */
        public final int liveOpensSealed;

        Tally(int liveOpensStatic, int liveBlocksStatic, int liveOpensSealed) {
            this.liveOpensStatic = liveOpensStatic;
            this.liveBlocksStatic = liveBlocksStatic;
            this.liveOpensSealed = liveOpensSealed;
        }

        /** True when neither regular conflict bucket has entries; sealed openings are tracked separately. */
        public boolean isEmpty() {
            return liveOpensStatic == 0 && liveBlocksStatic == 0;
        }
    }

    /**
     * How much of this scene's disagreement with the shipped map the accumulated overlay ALREADY knew.
     * <p>
     * {@link Tally} answers "how wrong is the static map here", which is the disease, not the treatment —
     * it compares live against STATIC and reads identically whether or not the persistent store is doing
     * its job. This answers the question that actually matters once persistence exists: on arriving
     * somewhere, had we already learned it on a previous visit?
     */
    public static final class Coverage {
        /** Static was wrong and the overlay already had the right answer — a previous visit paid off. */
        public final int alreadyKnown;
        /** Static was wrong and the overlay had nothing — the blind first visit this store exists to end. */
        public final int newInformation;
        /** The overlay had a DIFFERENT value than this capture: world changed, or stale learning. */
        public final int changed;

        Coverage(int alreadyKnown, int newInformation, int changed) {
            this.alreadyKnown = alreadyKnown;
            this.newInformation = newInformation;
            this.changed = changed;
        }

        public int total() {
            return alreadyKnown + newInformation + changed;
        }

        /** Percentage of this scene's static-map errors already covered before arriving. 0 when nothing conflicts. */
        public int alreadyKnownPercent() {
            final int t = total();
            return t == 0 ? 0 : (int) Math.round(100.0 * alreadyKnown / t);
        }
    }

    /**
     * Compares the capture against the overlay as it stood BEFORE this scene was merged in.
     *
     * @param priorView the overlay view pinned before the merge; {@code null} means nothing was learned
     *                  yet, so every disagreement counts as new information
     */
    public static Coverage coverage(LiveCollisionSnapshot snapshot, SplitFlagMap staticMap,
                                    LiveCollisionView priorView) {
        if (snapshot == null || staticMap == null) {
            return new Coverage(0, 0, 0);
        }
        int alreadyKnown = 0;
        int newInformation = 0;
        int changed = 0;
        final int baseX = snapshot.getBaseX();
        final int baseY = snapshot.getBaseY();
        for (int z = 0; z < snapshot.getPlaneCount(); z++) {
            for (int ly = 0; ly < SCENE_SIZE; ly++) {
                for (int lx = 0; lx < SCENE_SIZE; lx++) {
                    final int x = baseX + lx;
                    final int y = baseY + ly;
                    for (int flag = LiveCollisionSnapshot.FLAG_NORTH; flag <= LiveCollisionSnapshot.FLAG_EAST; flag++) {
                        final Boolean live = snapshot.edge(x, y, z, flag);
                        if (live == null || live == staticMap.get(x, y, z, flag)) {
                            continue; // unknown, or static was right — nothing for the store to carry
                        }
                        final Boolean known = priorView == null ? null : priorView.edge(x, y, z, flag);
                        if (known == null) {
                            newInformation++;
                        } else if (known.equals(live)) {
                            alreadyKnown++;
                        } else {
                            changed++;
                        }
                    }
                }
            }
        }
        return new Coverage(alreadyKnown, newInformation, changed);
    }

    private LiveCollisionConflicts() {
    }

    /**
     * Compares every KNOWN edge of {@code snapshot} against {@code staticMap}. Unknown edges (outside
     * the scene, the rim, door-mask exclusions) are skipped — they are exactly the edges the pathfinder
     * would fall back to static for, so they cannot conflict.
     */
    public static Tally tally(LiveCollisionSnapshot snapshot, SplitFlagMap staticMap) {
        if (snapshot == null || staticMap == null) {
            return new Tally(0, 0, 0);
        }
        int liveOpensStatic = 0;
        int liveBlocksStatic = 0;
        int liveOpensSealed = 0;
        final int baseX = snapshot.getBaseX();
        final int baseY = snapshot.getBaseY();
        for (int z = 0; z < snapshot.getPlaneCount(); z++) {
            for (int ly = 0; ly < SCENE_SIZE; ly++) {
                for (int lx = 0; lx < SCENE_SIZE; lx++) {
                    final int x = baseX + lx;
                    final int y = baseY + ly;
                    for (int flag = LiveCollisionSnapshot.FLAG_NORTH; flag <= LiveCollisionSnapshot.FLAG_EAST; flag++) {
                        final Boolean live = snapshot.edge(x, y, z, flag);
                        if (live == null) {
                            continue;
                        }
                        final boolean statik = staticMap.get(x, y, z, flag);
                        if (live == statik) {
                            continue;
                        }
                        if (!live) {
                            liveBlocksStatic++;
                        } else if (staticTileSealed(staticMap, x, y, z)) {
                            liveOpensSealed++;
                        } else {
                            liveOpensStatic++;
                        }
                    }
                }
            }
        }
        return new Tally(liveOpensStatic, liveBlocksStatic, liveOpensSealed);
    }

    /**
     * Whether the shipped map blocks all four sides of {@code (x, y, z)} — i.e. the tile is either solid
     * or (far more often) simply absent from the map data. Checked lazily, only for edges that already
     * disagree, so the extra lookups stay off the common path.
     */
    private static boolean staticTileSealed(SplitFlagMap staticMap, int x, int y, int z) {
        return !staticMap.get(x, y, z, LiveCollisionSnapshot.FLAG_NORTH)          // north
                && !staticMap.get(x, y - 1, z, LiveCollisionSnapshot.FLAG_NORTH)  // south
                && !staticMap.get(x, y, z, LiveCollisionSnapshot.FLAG_EAST)       // east
                && !staticMap.get(x - 1, y, z, LiveCollisionSnapshot.FLAG_EAST);  // west
    }
}
