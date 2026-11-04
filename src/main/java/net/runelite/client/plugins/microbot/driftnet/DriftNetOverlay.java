package net.runelite.client.plugins.microbot.driftnet;

import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

import javax.inject.Inject;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.text.NumberFormat;
import java.time.Duration;
import java.util.Locale;

public class DriftNetOverlay extends OverlayPanel
{
    private final DriftNetOverlayDropdown dropdown = new DriftNetOverlayDropdown(this);
    public void enableDropDownInput() { dropdown.enable(); }
    public void disableDropDownInput() { dropdown.disable(); }
    @Override public Dimension render(Graphics2D graphics) { return dropdown.render(graphics, this::renderExpanded); }

    private static final int WIDTH = 280;
    private static final int ROW_HEIGHT = 16;
    private static final int PADDING = 10;
    private static final int STATUS_X = 55;
    private static final int STATUS_WIDTH = WIDTH - STATUS_X - PADDING;
    private static final int STATUS_LINE_HEIGHT = 13;

    private static final Color BACKGROUND = new Color(42, 42, 42, 156);
    private static final Color BORDER = new Color(205, 211, 218, 120);
    private static final Color BLUE = new Color(205, 211, 218, 225);
    private static final Color TEXT = new Color(218, 222, 228);
    private static final Color MUTED = new Color(195, 202, 211);
    private static final Color LIVE = new Color(95, 226, 161);

    private static final Font TITLE_FONT = new Font("RuneScape Bold", Font.BOLD, 14);
    private static final Font LABEL_FONT = new Font("RuneScape", Font.PLAIN, 12);
    private static final Font VALUE_FONT = new Font("RuneScape Bold", Font.BOLD, 12);

    private final DriftNetPlugin plugin;
    private final ItemManager itemManager;

    @Inject
    DriftNetOverlay(DriftNetPlugin plugin, ItemManager itemManager)
    {
        super(plugin);
        this.plugin = plugin;
        this.itemManager = itemManager;

        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(OverlayPriority.HIGH);
    }

    private Dimension renderExpanded(Graphics2D graphics)
    {
        DriftNetScript script = plugin.getScript();
        java.util.List<String> statusLines = wrap(graphics, LABEL_FONT, value(script.getStatus()), STATUS_WIDTH);
        int extra = (statusLines.size() - 1) * STATUS_LINE_HEIGHT;
        int rows = 6;
        int height = 58 + extra + (rows * ROW_HEIGHT) + 5;

        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        graphics.setColor(BACKGROUND);
        graphics.fillRoundRect(0, 0, WIDTH, height, 9, 9);
        graphics.setStroke(new BasicStroke(1.1f));
        graphics.setColor(BORDER);
        graphics.drawRoundRect(0, 0, WIDTH - 1, height - 1, 9, 9);

        renderHeader(graphics, statusLines);

        graphics.setColor(new Color(205, 211, 218, 76));
        graphics.drawLine(PADDING, 45 + extra, WIDTH - PADDING, 45 + extra);

        int y = 64 + extra;
        row(graphics, "Mode", "Drift Net", y, TEXT);
        y += ROW_HEIGHT;
        row(graphics, "Runtime", duration(script.getRuntimeMs()), y, TEXT);
        y += ROW_HEIGHT;
        row(graphics, "Fishing XP", number(script.getFishingXpGained()), y, TEXT);
        y += ROW_HEIGHT;
        row(graphics, "Fishing XP/hr", number(script.getFishingXpPerHour()), y, LIVE);
        y += ROW_HEIGHT;
        row(graphics, "Hunter XP/hr", number(script.getHunterXpPerHour()), y, LIVE);
        y += ROW_HEIGHT;
        row(graphics, "Next break", value(script.getNextBreak()), y, MUTED);
        return new Dimension(WIDTH, height);
    }

    private void renderHeader(Graphics2D graphics, java.util.List<String> statusLines)
    {
        int iconX = PADDING;
        int iconY = 3;
        int iconSize = 28;

        graphics.setColor(new Color(195, 202, 211, 30));
        graphics.fillOval(iconX, iconY, iconSize, iconSize);
        graphics.setColor(BORDER);
        graphics.drawOval(iconX, iconY, iconSize, iconSize);

        try
        {
            BufferedImage icon = itemManager.getImage(ItemID.FOSSIL_DRIFT_NET, 1, false);
            if (icon != null)
            {
                graphics.drawImage(icon, iconX + 2, iconY + 2, 24, 24, null);
            }
        }
        catch (Exception ignored)
        {
        }

        int textX = 43;
        drawText(graphics, TITLE_FONT, BLUE, "Drift Net", textX, 19);

        DriftNetScript script = plugin.getScript();
        String status = value(script.getStatus());
        boolean active = script.isRunning()
                && !"-".equals(status)
                && !"Starting".equalsIgnoreCase(status)
                && !"Idle".equalsIgnoreCase(status);

        graphics.setColor(active ? LIVE : MUTED);
        graphics.fillOval(textX, 23, 6, 6);
        int y = 35;
        for (String line : statusLines)
        {
            drawText(graphics, LABEL_FONT, MUTED, line, textX + 12, y);
            y += STATUS_LINE_HEIGHT;
        }
    }

    /** Word-wraps the status to at most two lines; the second line is cut with an ellipsis. */
    private java.util.List<String> wrap(Graphics2D graphics, Font font, String text, int maxWidth)
    {
        FontMetrics metrics = graphics.getFontMetrics(font);
        if (metrics.stringWidth(text) <= maxWidth) return java.util.List.of(text);
        String[] words = text.split(" ");
        StringBuilder first = new StringBuilder();
        int i = 0;
        for (; i < words.length; i++)
        {
            String candidate = first.length() == 0 ? words[i] : first + " " + words[i];
            if (metrics.stringWidth(candidate) > maxWidth) break;
            first.setLength(0);
            first.append(candidate);
        }
        if (first.length() == 0) return java.util.List.of(fit(graphics, font, text, maxWidth));
        String rest = String.join(" ", java.util.Arrays.copyOfRange(words, i, words.length));
        return java.util.List.of(first.toString(), fit(graphics, font, rest, maxWidth));
    }

    private void row(Graphics2D graphics, String label, String value, int baselineY, Color valueColor)
    {
        drawText(graphics, LABEL_FONT, MUTED, label, PADDING, baselineY);
        drawRightText(graphics, VALUE_FONT, valueColor, value, WIDTH - PADDING, baselineY);
    }

    private void drawText(Graphics2D graphics, Font font, Color color, String text, int x, int y)
    {
        graphics.setFont(font);
        graphics.setColor(color);
        graphics.drawString(text, x, y);
    }

    private void drawRightText(Graphics2D graphics, Font font, Color color, String text, int rightX, int y)
    {
        graphics.setFont(font);
        graphics.setColor(color);
        int textWidth = graphics.getFontMetrics().stringWidth(text);
        graphics.drawString(text, rightX - textWidth, y);
    }

    private String fit(Graphics2D graphics, Font font, String text, int maxWidth)
    {
        if (text == null || text.isEmpty()) return "";
        FontMetrics metrics = graphics.getFontMetrics(font);
        if (metrics.stringWidth(text) <= maxWidth) return text;
        String ellipsis = "...";
        int ellipsisWidth = metrics.stringWidth(ellipsis);
        StringBuilder builder = new StringBuilder();
        for (char ch : text.toCharArray())
        {
            if (metrics.stringWidth(builder.toString() + ch) + ellipsisWidth > maxWidth)
            {
                return builder.append(ellipsis).toString();
            }
            builder.append(ch);
        }
        return builder.toString();
    }

    private String value(String s)
    {
        return s == null || s.trim().isEmpty() ? "-" : s;
    }

    private String number(long n)
    {
        return NumberFormat.getIntegerInstance(Locale.US).format(n);
    }

    private String duration(long millis)
    {
        if (millis <= 0) return "00:00:00";
        Duration d = Duration.ofMillis(millis);
        long hours = d.toHours();
        long minutes = d.toMinutesPart();
        long seconds = d.toSecondsPart();
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}
