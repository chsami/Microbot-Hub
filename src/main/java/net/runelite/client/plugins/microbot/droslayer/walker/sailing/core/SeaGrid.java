package net.runelite.client.plugins.microbot.droslayer.walker.sailing.core;

/** Immutable for the lifetime of a path search. Unknown is NOT open water. */
public interface SeaGrid {
    byte UNKNOWN = 0, OPEN = 1, BLOCKED = 2;
    byte cell(int x, int y);
    boolean live(int x, int y);
    default boolean open(int x, int y) { return cell(x, y) == OPEN; }
}
