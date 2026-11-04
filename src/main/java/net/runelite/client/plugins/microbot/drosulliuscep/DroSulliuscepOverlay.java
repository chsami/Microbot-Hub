/*
 * DroSulliuscep - Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Created by DRO (droplugins). Free and open source.
 * Reuse is permitted only under LICENSE.txt: every copy, fork or excerpt of this
 * file must keep this notice, the author credit and the license text. See CREDITS.txt.
 */
package net.runelite.client.plugins.microbot.drosulliuscep;

import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

import javax.inject.Inject;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Locale;

/** Compact Fishing-style Dro overlay with a Sulliuscep red palette. */
public class DroSulliuscepOverlay extends OverlayPanel
{
    private final DroOverlayDropdown dropdown = new DroOverlayDropdown(this);
    public void enableDropDownInput() { dropdown.enable(); }
    public void disableDropDownInput() { dropdown.disable(); }
    @Override public Dimension render(Graphics2D graphics) { return dropdown.render(graphics, this::renderExpanded); }

    private static final int WIDTH = 280;
    private static final int PADDING = 8;
    private static final int ROW_HEIGHT = 16;
    private static final int ROWS = 6;

    // Recorded run loot contained the Sulliuscep cap (21626); presentation only.
    private static final int SULLIUSCEP_CAP_ID = 21626;

    private static final Color BACKGROUND = new Color(42,42,42,156);
    private static final Color BORDER = new Color(205,211,218,225);
    private static final Color RED = new Color(195,202,211);
    private static final Color TEXT = new Color(218,222,228);
    private static final Color MUTED = new Color(195,202,211);
    private static final Color LIVE = new Color(91, 224, 158);
    private static final Color SHADOW = new Color(0, 0, 0, 190);

    private static final Font TITLE_FONT = FontManager.getRunescapeFont();
    private static final Font LABEL_FONT = FontManager.getRunescapeSmallFont();
    private static final Font VALUE_FONT = FontManager.getRunescapeFont();

    private final DroSulliuscepScript script;
    private final ItemManager itemManager;

    @Inject
    DroSulliuscepOverlay(
            DroSulliuscepPlugin plugin,
            DroSulliuscepScript script,
            ItemManager itemManager)
    {
        super(plugin);
        this.script = script;
        this.itemManager = itemManager;

        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(OverlayPriority.HIGH);
    }

    
    private Dimension renderExpanded(Graphics2D graphics)
    {
        int height = 58 + (ROWS * ROW_HEIGHT) + 5;

        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        graphics.setColor(BACKGROUND);
        graphics.fillRoundRect(0, 0, WIDTH, height, 9, 9);
        graphics.setStroke(new BasicStroke(1.1f));
        graphics.setColor(BORDER);
        graphics.drawRoundRect(0, 0, WIDTH - 1, height - 1, 9, 9);

        renderHeader(graphics);

        graphics.setColor(new Color(205,211,218,76));
        graphics.drawLine(PADDING, 45, WIDTH - PADDING, 45);

        int y = 64;
        row(graphics, "Active", value(script.getActiveTreeDisplay()), y, TEXT);
        y += ROW_HEIGHT;
        row(graphics, "Runtime", duration(script.getRuntimeMs()), y, TEXT);
        y += ROW_HEIGHT;
        row(graphics, "WC XP", number(script.getXpGained()), y, TEXT);
        y += ROW_HEIGHT;
        row(graphics, "WC XP/hr", number(script.getXpPerHour()), y, LIVE);
        y += ROW_HEIGHT;
        row(graphics, "Trees / Cycles", number(script.getTreesCompleted()) + " / "
                + number(script.getCyclesCompleted()), y, TEXT);
        y += ROW_HEIGHT;
        row(graphics, "Next break", value(script.getNextBreak()), y, MUTED);

        return new Dimension(WIDTH, height);
    }

    private void renderHeader(Graphics2D graphics)
    {
        int iconX = PADDING;
        int iconY = 3;
        int iconSize = 28;

        graphics.setColor(new Color(195,202,211,30));
        graphics.fillOval(iconX, iconY, iconSize, iconSize);
        graphics.setColor(BORDER);
        graphics.drawOval(iconX, iconY, iconSize, iconSize);

        try
        {
            BufferedImage icon = itemManager.getImage(SULLIUSCEP_CAP_ID, 1, false);
            if (icon != null)
            {
                graphics.drawImage(icon, iconX + 2, iconY + 2, 24, 24, null);
            }
        }
        catch (Exception ignored)
        {
            // Keep overlay rendering even while the item image cache warms up.
        }

        int textX = 43;
        drawText(graphics, TITLE_FONT, RED, "Dro Sulliuscep Run", textX, 19);

        String status = value(script.getStatus());
        DroSulliuscepState state = script.getState();
        boolean active = state != DroSulliuscepState.ERROR
                && state != DroSulliuscepState.PAUSED
                && !"-".equals(status)
                && !"Starting".equalsIgnoreCase(status)
                && !"Idle".equalsIgnoreCase(status)
                && !"Stopped".equalsIgnoreCase(status);

        graphics.setColor(active ? LIVE : MUTED);
        graphics.fillOval(textX, 23, 6, 6);
        drawText(graphics, LABEL_FONT, MUTED,
                fit(graphics, LABEL_FONT, status, WIDTH - (textX + 12) - PADDING),
                textX + 12, 35);
    }

    private void row(Graphics2D graphics, String left, String right, int y, Color rightColor)
    {
        drawText(graphics, LABEL_FONT, MUTED, left, PADDING, y);

        String fitted = fit(graphics, VALUE_FONT, right, 112);
        graphics.setFont(VALUE_FONT);
        FontMetrics fm = graphics.getFontMetrics();
        int x = WIDTH - PADDING - fm.stringWidth(fitted);
        drawText(graphics, VALUE_FONT, rightColor, fitted, x, y);
    }

    private static String fit(Graphics2D graphics, Font font, String value, int maxWidth)
    {
        String text = value(value);
        graphics.setFont(font);
        FontMetrics fm = graphics.getFontMetrics();
        if (fm.stringWidth(text) <= maxWidth)
        {
            return text;
        }

        String suffix = "...";
        int limit = Math.max(0, maxWidth - fm.stringWidth(suffix));
        int end = text.length();
        while (end > 0 && fm.stringWidth(text.substring(0, end)) > limit)
        {
            end--;
        }
        return end <= 0 ? suffix : text.substring(0, end) + suffix;
    }

    private static void drawText(Graphics2D graphics, Font font, Color color, String text, int x, int y)
    {
        graphics.setFont(font);
        graphics.setColor(SHADOW);
        graphics.drawString(text, x + 1, y + 1);
        graphics.setColor(color);
        graphics.drawString(text, x, y);
    }

    private static String value(String value)
    {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static String number(long value)
    {
        return String.format(Locale.US, "%,d", Math.max(0L, value));
    }

    private static String duration(long ms)
    {
        long total = Math.max(0L, ms) / 1_000L;
        long hours = total / 3_600L;
        long minutes = (total % 3_600L) / 60L;
        long seconds = total % 60L;
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
    }
}
