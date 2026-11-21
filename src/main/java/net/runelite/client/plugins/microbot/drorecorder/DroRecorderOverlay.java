package net.runelite.client.plugins.microbot.drorecorder;

import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

import javax.inject.Inject;
import java.awt.*;

/** Compact recording indicator and elapsed time. */
final class DroRecorderOverlay extends OverlayPanel
{
    private static final int WIDTH = 204;
    private static final int HEADER_HEIGHT = 24;
    private static final Color TEXT = new Color(220, 220, 217);
    private static final Color MUTED = new Color(150, 151, 146);
    private static final Color RED = new Color(217, 130, 123);
    private static final Color AMBER = new Color(211, 185, 132);
    private static final Font TITLE_FONT = FontManager.getRunescapeFont();
    private static final Font SMALL_FONT = FontManager.getRunescapeSmallFont();

    private final DroRecorderPlugin plugin;
    private final DroRecorderConfig config;
    @Inject
    private DroRecorderOverlay(DroRecorderPlugin plugin, DroRecorderConfig config)
    {
        super(plugin);
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.BOTTOM_LEFT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(OverlayPriority.LOW);
        setResizable(false);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.showOverlay() || !plugin.hasSession()) return null;
        int height = HEADER_HEIGHT;
        boolean paused = plugin.isPaused();
        long dropped = plugin.getDroppedLines();
        Graphics2D g = (Graphics2D) graphics.create();
        try
        {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(new Color(20, 20, 18, 160));
            g.fillRoundRect(0, 0, WIDTH, height, 7, 7);
            g.setColor(new Color(180, 177, 165, 50));
            g.drawRoundRect(0, 0, WIDTH - 1, height - 1, 7, 7);
            g.setColor(paused || dropped > 0 ? AMBER : RED);
            if (paused)
            {
                g.fillRect(7, 8, 2, 7);
                g.fillRect(11, 8, 2, 7);
            }
            else g.fillOval(8, 9, 5, 5);
            text(g, TITLE_FONT, TEXT, fit(g, TITLE_FONT, DroRecorderPlugin.NAME, 110), 18, headerBaseline(g, TITLE_FONT));
            String runtime = fit(g, SMALL_FONT, plugin.getRuntimeText(), 62);
            g.setFont(SMALL_FONT);
            text(g, SMALL_FONT, paused || dropped > 0 ? AMBER : MUTED,
                    runtime, WIDTH - 8 - g.getFontMetrics().stringWidth(runtime), headerBaseline(g, SMALL_FONT));
        }
        finally
        {
            g.dispose();
        }
        return new Dimension(WIDTH, height);
    }

    private static int headerBaseline(Graphics2D graphics, Font font) {
        FontMetrics metrics = graphics.getFontMetrics(font);
        return (HEADER_HEIGHT - metrics.getAscent() - metrics.getDescent()) / 2 + metrics.getAscent();
    }

    private static String fit(Graphics2D g, Font font, String value, int width)
    {
        String text = value == null || value.isBlank() ? "-" : value;
        g.setFont(font);
        FontMetrics metrics = g.getFontMetrics();
        if (metrics.stringWidth(text) <= width) return text;
        int end = text.length();
        while (end > 0 && metrics.stringWidth(text.substring(0, end) + "...") > width) end--;
        return text.substring(0, end) + "...";
    }

    private static void text(Graphics2D g, Font font, Color color, String value, int x, int y)
    {
        g.setFont(font);
        g.setColor(new Color(0, 0, 0, 130));
        g.drawString(value, x + 1, y + 1);
        g.setColor(color);
        g.drawString(value, x, y);
    }
}
