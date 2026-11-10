package net.runelite.client.plugins.microbot.droinfernoprayers;

import java.awt.*;

/** The overlay title: the plugin name in the overlay's gold. */
final class InfernoOverlayBranding {
    private static final String TITLE = "Inferno Prayers";
    private static final Color GOLD = new Color(205, 157, 83);

    static void draw(Graphics2D g, Rectangle box) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        FontMetrics metrics = g.getFontMetrics();
        int x = box.x + Math.max(0, (box.width - metrics.stringWidth(TITLE)) / 2);
        int y = box.y + (box.height - metrics.getHeight()) / 2 + metrics.getAscent();
        g.setColor(GOLD);
        g.drawString(TITLE, x, y);
    }

    private InfernoOverlayBranding() {}
}
