package net.runelite.client.plugins.microbot.droefficientwalker.sailing.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Explicit no-go areas. Unknown syntax fails closed instead of disabling avoidance. */
public final class HazardZone {
    public final String label;
    public final int x1, y1, x2, y2;
    public final int radius; // -1 means inclusive rectangle.
    private HazardZone(String label, int x1, int y1, int x2, int y2, int radius) {
        this.label = label; this.x1 = x1; this.y1 = y1;
        this.x2 = x2; this.y2 = y2; this.radius = radius;
    }
    public static HazardZone circle(String label, int x, int y, int radius) {
        if (radius < 0 || radius > 1024) throw new IllegalArgumentException("Hazard radius must be 0..1024");
        return new HazardZone(label, x, y, x, y, radius);
    }
    public static HazardZone rectangle(String label, int x1, int y1, int x2, int y2) {
        if (x1 > x2 || y1 > y2) throw new IllegalArgumentException("Hazard rectangle coordinates are reversed: " + label);
        return new HazardZone(label, x1, y1, x2, y2, -1);
    }
    public boolean contains(int x, int y) {
        if (radius >= 0) {
            long dx = (long) x - x1, dy = (long) y - y1;
            return dx * dx + dy * dy <= (long) radius * radius;
        }
        return x >= x1 && x <= x2 && y >= y1 && y <= y2;
    }
    /** One line per zone: circle,label,x,y,radius OR rect,label,x1,y1,x2,y2. */
    public static List<HazardZone> parse(String text) {
        if (text == null || text.trim().isEmpty()) return Collections.emptyList();
        List<HazardZone> result = new ArrayList<>();
        int line = 0;
        for (String raw : text.split("[\\r\\n;]+")) {
            line++;
            String s = raw.trim();
            if (s.isEmpty() || s.startsWith("#")) continue;
            String[] fields = s.split(",", -1);
            for (int i = 0; i < fields.length; i++) fields[i] = fields[i].trim();
            try {
                if (fields.length == 5 && fields[0].equalsIgnoreCase("circle")) {
                    result.add(circle(fields[1], Integer.parseInt(fields[2]), Integer.parseInt(fields[3]), Integer.parseInt(fields[4])));
                } else if (fields.length == 6 && fields[0].equalsIgnoreCase("rect")) {
                    result.add(rectangle(fields[1], Integer.parseInt(fields[2]), Integer.parseInt(fields[3]),
                            Integer.parseInt(fields[4]), Integer.parseInt(fields[5])));
                } else throw new IllegalArgumentException("Expected circle or rect");
            } catch (RuntimeException ex) {
                throw new IllegalArgumentException("Invalid hazard line " + line + ": " + s, ex);
            }
        }
        return Collections.unmodifiableList(result);
    }
    @Override public String toString() {
        return radius < 0 ? "rect," + label + "," + x1 + "," + y1 + "," + x2 + "," + y2
                : "circle," + label + "," + x1 + "," + y1 + "," + radius;
    }
}
