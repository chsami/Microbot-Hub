package net.runelite.client.plugins.microbot.droefficientwalker.sailing.core;

import java.util.Arrays;

/** A copied, edge-trimmed top-level sailing collision scene. No live client references. */
public final class SceneChart {
    public final long tick;
    public final int baseX, baseY, width, height;
    private final byte[] cells;
    public SceneChart(long tick, int baseX, int baseY, int width, int height, byte[] cells) {
        if (width <= 0 || height <= 0 || width > 512 || height > 512
                || cells.length != width * height) throw new IllegalArgumentException("Invalid scene dimensions");
        for (byte cell : cells) if (cell < SeaGrid.UNKNOWN || cell > SeaGrid.BLOCKED) {
            throw new IllegalArgumentException("Invalid scene cell");
        }
        this.tick = tick; this.baseX = baseX; this.baseY = baseY;
        this.width = width; this.height = height; this.cells = cells.clone();
    }
    public boolean contains(int x, int y) { return x >= baseX && y >= baseY && x < baseX + width && y < baseY + height; }
    public byte cell(int x, int y) {
        return contains(x, y) ? cells[(x - baseX) * height + y - baseY] : SeaGrid.UNKNOWN;
    }
    public byte[] copyCells() { return cells.clone(); }
    public boolean sameGeometry(SceneChart other) {
        return other != null && baseX == other.baseX && baseY == other.baseY
                && width == other.width && height == other.height && Arrays.equals(cells, other.cells);
    }
}
