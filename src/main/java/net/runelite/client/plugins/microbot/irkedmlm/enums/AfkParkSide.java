package net.runelite.client.plugins.microbot.irkedmlm.enums;

import lombok.Getter;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import lombok.RequiredArgsConstructor;

/**
 * Which canvas edge the mouse parks off (and returns from) during off-screen AFK.
 *
 * <p>Models where the player's attention goes while an AFK skill ticks over. Someone watching a
 * second monitor only ever crosses the one edge adjacent to it, so exit and return should be
 * consistent — never a random one of all four, which is the tell the shared
 * {@code naturalMouse.moveOffScreen()} leaves. Someone reading below the client (a browser, chat,
 * the taskbar) crosses the bottom; someone with a monitor stacked above crosses the top.
 *
 * <p>{@link #NONE} opts out entirely: the cursor stays inside the client, which is what a player on
 * a single screen who never flings the mouse out of the window looks like.
 */
@Getter
@RequiredArgsConstructor
public enum AfkParkSide {
    /** Never leave the canvas — the cursor idles on-screen instead of parking off an edge. */
    NONE(0, 0),
    /** Second monitor / attention to the left — the cursor leaves and returns via the left edge. */
    LEFT(-1, 0),
    /** Second monitor / attention to the right. */
    RIGHT(1, 0),
    /** Monitor stacked above, or the cursor flicked up to the browser/taskbar at the top. */
    TOP(0, -1),
    /** Attention below the client — a second window, chat, or the taskbar. */
    BOTTOM(0, 1),
    /** No fixed preference — one real edge is picked per login and kept for the whole session. */
    RANDOM(0, 0);

    /** Horizontal edge direction: -1 left, +1 right, 0 for a vertical edge. */
    private final int dx;
    /** Vertical edge direction: -1 top, +1 bottom, 0 for a horizontal edge. */
    private final int dy;

    /** The four concrete edges {@link #RANDOM} chooses between. */
    private static final AfkParkSide[] REAL_EDGES = { LEFT, RIGHT, TOP, BOTTOM };

    /** True when this setting actually takes the cursor off the canvas. */
    public boolean parksOffScreen() {
        return this != NONE;
    }

    /** True when the exit crosses a left/right edge rather than a top/bottom one. */
    public boolean isHorizontal() {
        return dx != 0;
    }

    /** A concrete edge for {@link #RANDOM}; every other value resolves to itself. */
    public static AfkParkSide randomEdge() {
        return REAL_EDGES[Rs2Random.between(0, REAL_EDGES.length)];
    }

    @Override
    public String toString() {
        // Title-case for the config dropdown (LEFT -> "Left").
        return name().charAt(0) + name().substring(1).toLowerCase();
    }
}
