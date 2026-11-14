package net.runelite.client.plugins.microbot.droefficientwalker.sailing.core;

/**
 * Server-observed boat origin and orientation. Coordinates use tile CENTRES as
 * integers: RuneLite fine coordinates / 128 + scene base - 0.5. This preserves
 * sub-tile placement without silently moving an even-length hull half a tile.
 */
public final class SeaPose {
    public final double x, y, heading;
    public SeaPose(double x, double y, double heading) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(heading))
            throw new IllegalArgumentException("Non-finite boat pose");
        this.x = x; this.y = y; this.heading = normalize(heading);
    }
    public SeaPose(SeaPoint tile, int heading) { this(tile.x, tile.y, heading); }
    public SeaPoint tile() { return new SeaPoint((int) Math.floor(x + .5), (int) Math.floor(y + .5)); }
    public int heading16() { return Math.floorMod((int) Math.round(heading), 16); }
    public double distance(SeaPose other) { return Math.hypot(x - other.x, y - other.y); }
    public double distance(SeaPoint point) { return Math.hypot(x - point.x, y - point.y); }
    public SeaPose project(double direction, double distance) {
        double a = direction * Math.PI / 8;
        return new SeaPose(x + Math.cos(a) * distance, y + Math.sin(a) * distance, direction);
    }
    public static double normalize(double h) { return (h % 16 + 16) % 16; }
    public static double delta(double from, double to) { return normalize(to - from + 8) - 8; }
    public static double bearing(double ax, double ay, double bx, double by) {
        return normalize(Math.atan2(by - ay, bx - ax) * 8 / Math.PI);
    }
    @Override public String toString() {
        return String.format(java.util.Locale.ROOT, "%.3f,%.3f h=%.3f", x, y, heading);
    }
}
