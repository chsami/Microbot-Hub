package net.runelite.client.plugins.microbot.droslayer.walker.sailing.core;

import java.util.HashMap;
import java.util.Map;

/** Distinguishes hull movement, real voyage progress, and oscillation. Uses monotonic milliseconds. */
public final class ProgressWatch {
    private SeaPoint last;
    private long lastMoved, lastUseful, lastTick = Long.MIN_VALUE, known;
    private double bestDistance = Double.POSITIVE_INFINITY;
    private final Map<Long, Integer> visits = new HashMap<>();
    public void reset(long now) {
        last = null; lastMoved = now; lastUseful = now; lastTick = Long.MIN_VALUE;
        bestDistance = Double.POSITIVE_INFINITY; known = 0; visits.clear();
    }
    public void observe(SeaPoint p, SeaPoint destination, long tick, long knownTiles, long now) {
        if (p == null || destination == null || tick == lastTick) return;
        lastTick = tick;
        if (last == null || p.distance(last) >= 1) { lastMoved = now; last = p; }
        double distance = p.distance(destination);
        if (distance + 3 < bestDistance || knownTiles >= known + 64) {
            lastUseful = now; bestDistance = Math.min(bestDistance, distance); known = knownTiles;
        }
    }
    public long stalledFor(long now) { return Math.max(0, now - lastMoved); }
    public long noUsefulProgressFor(long now) { return Math.max(0, now - lastUseful); }
    public void grace(long now) { lastMoved = now; }
    public void visitedFrontier(SeaPoint point) {
        // Cluster neighbouring frontier tiles so a one-tile change does not reset avoidance.
        long key = SeaPoint.key(point.x >> 3, point.y >> 3);
        visits.merge(key, 1, Integer::sum);
    }
    public int visits(SeaPoint point) { return visits.getOrDefault(SeaPoint.key(point.x >> 3, point.y >> 3), 0); }
}
