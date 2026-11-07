package net.runelite.client.plugins.microbot.geflipper;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Collections;
import java.util.List;
import javax.swing.JPanel;
import net.runelite.client.ui.FontManager;

/**
 * Draws the Mule receiver rows the way the in-game overlay does, for the login-screen window. It paints
 * everything itself (no child components), so its size and look follow only the rows it was given.
 */
final class MuleStatsPanel extends JPanel {
    static final int MIN_WIDTH = 230;
    static final int MAX_WIDTH = 380;
    private static final int PAD_X = 8;
    private static final int PAD_Y = 6;
    private static final int ROW_GAP = 2;
    private static final int COLUMN_GAP = 14;
    private static final Color BACKGROUND = new Color(0x1B1B1B);
    private static final Color BORDER = new Color(0x4A4A4A);
    private static final Color SHADOW = Color.BLACK;

    private volatile String title = "";
    private volatile List<MuleOverlayView.Row> rows = Collections.emptyList();

    MuleStatsPanel() {
        setOpaque(true);
        setBackground(BACKGROUND);
        setFont(overlayFont());
    }

    private static Font overlayFont() {
        try {
            Font font = FontManager.getRunescapeSmallFont();
            if (font != null) return font;
        } catch (Throwable unavailable) {
            // Fall back to a standard font when the client's bundled fonts cannot load.
        }
        return new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    }

    void setContent(String title, List<MuleOverlayView.Row> rows) {
        this.title = title == null ? "" : title;
        this.rows = rows == null ? Collections.<MuleOverlayView.Row>emptyList() : rows;
        revalidate();
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics metrics = getFontMetrics(getFont());
        int widest = metrics.stringWidth(title);
        for (MuleOverlayView.Row row : rows) {
            int width = row.value.isEmpty()
                ? metrics.stringWidth(row.label)
                : metrics.stringWidth(row.label) + COLUMN_GAP + metrics.stringWidth(row.value);
            widest = Math.max(widest, width);
        }
        int width = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, widest + 2 * PAD_X));
        int height = 2 * PAD_Y + metrics.getHeight() * (rows.size() + 1) + ROW_GAP * rows.size();
        return new Dimension(width, height);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            int width = getWidth();
            int height = getHeight();
            g.setColor(BACKGROUND);
            g.fillRect(0, 0, width, height);
            g.setColor(BORDER);
            g.drawRect(0, 0, width - 1, height - 1);
            g.setFont(getFont());
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            FontMetrics metrics = g.getFontMetrics();
            int inner = width - 2 * PAD_X;
            int baseline = PAD_Y + metrics.getAscent();
            String heading = fit(title, inner, metrics);
            draw(g, heading, PAD_X + (inner - metrics.stringWidth(heading)) / 2, baseline, MuleOverlayView.TITLE_COLOR);
            for (MuleOverlayView.Row row : rows) {
                baseline += metrics.getHeight() + ROW_GAP;
                if (row.value.isEmpty()) {
                    draw(g, fit(row.label, inner, metrics), PAD_X, baseline, MuleOverlayView.color(row.tone));
                    continue;
                }
                String value = fit(row.value, inner, metrics);
                int valueWidth = metrics.stringWidth(value);
                draw(g, fit(row.label, inner - valueWidth - COLUMN_GAP, metrics), PAD_X, baseline, Color.WHITE);
                draw(g, value, width - PAD_X - valueWidth, baseline, MuleOverlayView.color(row.tone));
            }
        } finally {
            g.dispose();
        }
    }

    private static void draw(Graphics2D g, String text, int x, int y, Color color) {
        g.setColor(SHADOW);
        g.drawString(text, x + 1, y + 1);
        g.setColor(color);
        g.drawString(text, x, y);
    }

    /** Shortens text with an ellipsis so one long message can never push a row outside the window. */
    static String fit(String text, int width, FontMetrics metrics) {
        if (text == null || text.isEmpty() || width <= 0) return "";
        if (metrics.stringWidth(text) <= width) return text;
        String ellipsis = "...";
        int end = text.length();
        while (end > 0 && metrics.stringWidth(text.substring(0, end) + ellipsis) > width) end--;
        return end == 0 ? "" : text.substring(0, end) + ellipsis;
    }
}
