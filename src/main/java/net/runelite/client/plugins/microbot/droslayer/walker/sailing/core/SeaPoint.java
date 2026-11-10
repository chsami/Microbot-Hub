package net.runelite.client.plugins.microbot.droslayer.walker.sailing.core;

import java.util.Objects;

/** A top-level sea tile, never a tile on the instanced boat deck. */
public final class SeaPoint {
    public final int x;
    public final int y;
    public SeaPoint(int x, int y) { this.x = x; this.y = y; }
    public SeaPoint add(int dx, int dy) { return new SeaPoint(x + dx, y + dy); }
    public double distance(SeaPoint other) { return Math.hypot(x - other.x, y - other.y); }
    public int chebyshev(SeaPoint other) { return Math.max(Math.abs(x - other.x), Math.abs(y - other.y)); }
    public long key() { return key(x, y); }
    public static long key(int x, int y) { return ((long) x << 32) | (y & 0xffffffffL); }
    public static SeaPoint fromKey(long key) { return new SeaPoint((int) (key >> 32), (int) key); }
    @Override public boolean equals(Object o) {
        if (!(o instanceof SeaPoint)) return false;
        SeaPoint p = (SeaPoint) o;
        return x == p.x && y == p.y;
    }
    @Override public int hashCode() { return Objects.hash(x, y); }
    @Override public String toString() { return x + "," + y; }
}
