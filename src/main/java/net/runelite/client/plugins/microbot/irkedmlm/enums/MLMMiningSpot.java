package net.runelite.client.plugins.microbot.irkedmlm.enums;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.Arrays;
import java.util.HashSet;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;

/**
 * Static spatial identity for a Motherlode Mine area.
 * <p>
 * <b>Allowed:</b> one or more {@link WorldArea}s (union defines containment), anchor {@link WorldPoint}s,
 * upstairs/downstairs flag, optional preferred stand tiles (position hints only).
 * <p>
 * Upper spots use a single {@link WorldArea} each (tight chamber bounds).
 * <p>
 * <b>Forbidden:</b> vein selection, rockfall/path logic, scoring, blacklists, recovery, or any
 * gameplay decision-making. Intelligence lives in {@code MiningSession} and other sessions.
 */
@Getter
@RequiredArgsConstructor
public enum MLMMiningSpot {

    WEST_LOWER(Arrays.asList(
            new WorldPoint(3731, 5659, 0),
            new WorldPoint(3731, 5663, 0)
    ), false, Arrays.asList(
            new WorldArea(3725, 5655, 12, 10, 0)
    ), null),

    WEST_MID(Arrays.asList(
            new WorldPoint(3730, 5666, 0),
            new WorldPoint(3731, 5669, 0)
    ), false, Arrays.asList(
            new WorldArea(3725, 5665, 12, 10, 0)
    ), null),

    SOUTH_EAST(Arrays.asList(
            new WorldPoint(3753, 5650, 0),
            new WorldPoint(3756, 5653, 0)
    ), false, Arrays.asList(
            new WorldArea(3748, 5645, 12, 12, 0)
    ), null),

    SOUTH_WEST(Arrays.asList(
            new WorldPoint(3740, 5648, 0)
    ), false, Arrays.asList(
            new WorldArea(3732, 5645, 15, 10, 0)
    ), null),

    WEST_UPPER(Arrays.asList(
            new WorldPoint(3750, 5681, 0),
            new WorldPoint(3751, 5680, 0)
    ), true, Arrays.asList(
            new WorldArea(3747, 5679, 7, 6, 0)
    ), westUpperPreferredOperatingTiles()),

    // Height 7, not 6: measured against live vein positions, the chamber's northern row is y5675 and
    // holds three of its eight veins — (3758,5675), (3761,5675), (3762,5675). At height 6 the spot saw
    // only five and waited on respawns it did not need to. y5676 stays out: that is the corridor side,
    // behind the rockfall.
    EAST_UPPER(Arrays.asList(
            new WorldPoint(3761, 5672, 0),
            new WorldPoint(3760, 5673, 0)
    ), true, Arrays.asList(
            new WorldArea(3756, 5669, 7, 7, 0)
    ), eastUpperPreferredOperatingTiles()),

    /** Config-only choice: the script resolves it to a random lower area and never mines "ANY" itself. */
    ANY(Collections.emptyList(), false, Collections.emptyList(), null);

    /** The real lower-floor areas ANY picks from. */
    public static final List<MLMMiningSpot> LOWER_AREAS =
            Collections.unmodifiableList(Arrays.asList(WEST_LOWER, WEST_MID, SOUTH_EAST, SOUTH_WEST));

    /** Navigation anchors (return-to-area targets). */
    private final List<WorldPoint> worldPoint;
    @Getter(AccessLevel.NONE)
    private final boolean upstairs;
    /** Union of areas: navigation return + vein candidate bounds on upper floors. */
    private final List<WorldArea> worldAreas;
    /** Optional stand positions; not used for containment. */
    private final Set<WorldPoint> preferredOperatingTiles;

    private static Set<WorldPoint> tiles(WorldPoint... points) {
        if (points == null || points.length == 0) {
            return null;
        }
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(points)));
    }

    private static Set<WorldPoint> westUpperPreferredOperatingTiles() {
        return tiles(
                new WorldPoint(3748, 5680, 0),
                new WorldPoint(3749, 5680, 0),
                new WorldPoint(3750, 5680, 0),
                new WorldPoint(3751, 5680, 0),
                new WorldPoint(3752, 5680, 0),
                new WorldPoint(3749, 5681, 0),
                new WorldPoint(3750, 5681, 0),
                new WorldPoint(3751, 5681, 0),
                new WorldPoint(3750, 5682, 0),
                new WorldPoint(3751, 5682, 0),
                new WorldPoint(3752, 5681, 0),
                new WorldPoint(3750, 5683, 0)
        );
    }

    private static Set<WorldPoint> eastUpperPreferredOperatingTiles() {
        return tiles(
                new WorldPoint(3759, 5673, 0),
                new WorldPoint(3761, 5672, 0),
                new WorldPoint(3760, 5673, 0),
                new WorldPoint(3761, 5671, 0),
                new WorldPoint(3761, 5670, 0),
                new WorldPoint(3761, 5673, 0)
        );
    }

    public boolean isUpstairs() {
        return upstairs;
    }

    public boolean isDownstairs() {
        return !upstairs;
    }

    /** Position hints for upper chambers; empty when not configured. */
    public Set<WorldPoint> getPreferredOperatingTiles() {
        return preferredOperatingTiles != null ? preferredOperatingTiles : Collections.emptySet();
    }

    /** Pure geometry: point inside any configured {@link WorldArea}. */
    public boolean contains(WorldPoint point) {
        if (point == null || worldAreas == null) {
            return false;
        }
        for (WorldArea area : worldAreas) {
            if (area.contains(point)) {
                return true;
            }
        }
        return false;
    }
}
