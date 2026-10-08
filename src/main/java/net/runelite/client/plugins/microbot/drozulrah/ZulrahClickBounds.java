package net.runelite.client.plugins.microbot.drozulrah;

import java.awt.Rectangle;

final class ZulrahClickBounds
{
    static Rectangle visible(Rectangle bounds, int width, int height) {
        if (bounds == null || bounds.width <= 0 || bounds.height <= 0) return null;
        Rectangle clipped = bounds.intersection(new Rectangle(2, 2, Math.max(0, width - 4), Math.max(0, height - 4)));
        if (clipped.width < 4 || clipped.height < 4) return null;
        return new Rectangle(clipped.x + 1, clipped.y + 1, clipped.width - 2, clipped.height - 2);
    }
}
