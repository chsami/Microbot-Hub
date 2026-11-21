package net.runelite.client.plugins.microbot.droelf;

import net.runelite.client.plugins.microbot.util.math.Rs2Random;

public enum AfkParkSide {
    NONE(0, 0), LEFT(-1, 0), RIGHT(1, 0), TOP(0, -1), BOTTOM(0, 1), RANDOM(0, 0);
    private static final AfkParkSide[] EDGES = {LEFT, RIGHT, TOP, BOTTOM};
    private final int dx;
    private final int dy;
    AfkParkSide(int dx, int dy) { this.dx = dx; this.dy = dy; }
    public int getDx() { return dx; }
    public int getDy() { return dy; }
    public boolean parksOffScreen() { return this != NONE; }
    public boolean isHorizontal() { return dx != 0; }
    public static AfkParkSide randomEdge() { return EDGES[Rs2Random.between(0, EDGES.length)]; }
    @Override public String toString() { return name().charAt(0) + name().substring(1).toLowerCase(); }
}
