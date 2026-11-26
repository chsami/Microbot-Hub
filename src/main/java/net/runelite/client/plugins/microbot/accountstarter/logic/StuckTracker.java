package net.runelite.client.plugins.microbot.accountstarter.logic;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.runelite.api.coords.WorldPoint;

public final class StuckTracker {
    private final Map<String, Integer> strikes = new HashMap<>();
    private String activity;
    private WorldPoint where;
    private long totalXp;
    private long sinceMs;
    private boolean paused;

    /** A break or a pause: the time it takes is not time spent stuck. */
    public void pause() {
        paused = true;
    }

    public void observe(long nowMs, WorldPoint where, long totalXp, String activity) {
        if (paused) {
            paused = false;
            sinceMs = nowMs;
        }
        // XP in the same activity is progress: old strikes no longer say anything about it
        if (activity != null && activity.equals(this.activity) && totalXp > this.totalXp) strikes.remove(activity);
        if (!Objects.equals(activity, this.activity) || !Objects.equals(where, this.where) || totalXp != this.totalXp) {
            this.activity = activity;
            this.where = where;
            this.totalXp = totalXp;
            sinceMs = nowMs;
        }
    }

    public boolean stuck(long nowMs, long stuckAfterMs) {
        if (activity == null || nowMs - sinceMs < stuckAfterMs) return false;
        strikes.merge(activity, 1, Integer::sum);
        sinceMs = nowMs;
        return true;
    }

    public int strikes(String activity) {
        return strikes.getOrDefault(activity, 0);
    }

    public void reset() {
        strikes.clear();
        activity = null;
        where = null;
        totalXp = 0;
        sinceMs = 0;
        paused = false;
    }
}
