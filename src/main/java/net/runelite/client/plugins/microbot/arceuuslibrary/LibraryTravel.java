package net.runelite.client.plugins.microbot.arceuuslibrary;

import net.runelite.api.coords.WorldPoint;

/**
 * Pure helpers for the Arceuus Library travel phase: where the library is, and when a walk
 * may be (re)started. Kept free of client state so it can be unit tested.
 */
final class LibraryTravel
{
    static final int MIN_X = 1605;
    static final int MAX_X = 1660;
    static final int MIN_Y = 3780;
    static final int MAX_Y = 3835;
    static final int MAX_PLANE = 2;

    /** Ground-floor tile inside the library, used as the travel destination. */
    static final WorldPoint ENTRANCE = new WorldPoint(1627, 3801, 0);

    static final long BACKOFF_BASE_MS = 20_000L;
    static final long BACKOFF_MAX_MS = 60_000L;

    private LibraryTravel() {}

    static boolean isInsideLibrary(WorldPoint p)
    {
        return p != null
                && p.getX() >= MIN_X && p.getX() <= MAX_X
                && p.getY() >= MIN_Y && p.getY() <= MAX_Y
                && p.getPlane() >= 0 && p.getPlane() <= MAX_PLANE;
    }

    /** 20s, 40s, 60s, 60s... for failure counts 1, 2, 3, 4... (0 means no back-off). */
    static long backoffMillis(int failures)
    {
        if (failures <= 0) return 0;
        long ms = BACKOFF_BASE_MS * (1L << Math.min(failures - 1, 10));
        return Math.min(ms, BACKOFF_MAX_MS);
    }

    /**
     * Decides whether a new walk toward {@code target} may be started.
     *
     * @param inFlightTarget target of the walk currently running, or null when none is running
     * @param backoffUntilMs wall-clock time before which no walk may start (0 = none)
     */
    static boolean shouldStartWalk(WorldPoint inFlightTarget, WorldPoint target, long nowMs, long backoffUntilMs)
    {
        if (target == null) return false;
        if (inFlightTarget != null) return false;
        return nowMs >= backoffUntilMs;
    }

    /** A running walk is replaced only when the new destination is on another plane or this far from it. */
    static final int RETARGET_DISTANCE = 6;

    /**
     * True when a walk is in flight toward {@code inFlightTarget} but {@code target} is a genuinely
     * different place, so the old walk should be cancelled. Small drifts (a roaming customer moving a
     * few tiles) keep the existing walk instead of re-issuing it every tick.
     */
    static boolean shouldRetarget(WorldPoint inFlightTarget, WorldPoint target)
    {
        if (inFlightTarget == null || target == null) return false;
        if (inFlightTarget.getPlane() != target.getPlane()) return true;
        return inFlightTarget.distanceTo(target) > RETARGET_DISTANCE;
    }
}
