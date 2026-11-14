package net.runelite.client.plugins.microbot.droslayer;

import java.awt.*;
import net.runelite.client.ui.overlay.components.BackgroundComponent;

/** Plugin-local Slayer-style translucent grey panel with a black outer border. */
public final class DroOverlayFrame {
    public static final Color BACKGROUND = new Color(42, 42, 42, 156);
    public static final Color ACCENT = new Color(205, 211, 218, 225);
    private DroOverlayFrame() { }
    public static void render(Graphics2D graphics, int width, int height) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setStroke(new BasicStroke(1f));
            new BackgroundComponent(BACKGROUND, new Rectangle(0, 0, width, height), true).render(g);
            border(g, width, height);
        } finally { g.dispose(); }
    }
    public static void border(Graphics2D graphics, int width, int height) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setStroke(new BasicStroke(1f));
            g.setColor(new Color(0, 0, 0, 225));
            g.drawRect(0, 0, width - 1, height - 1);
        } finally { g.dispose(); }
    }
}
