package net.runelite.client.plugins.microbot.prometheuslogin;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.TextAttribute;
import java.util.HashMap;
import java.util.Map;

/** Colours and helpers shared by the Prometheus Login window (navy panels, purple and cyan accents). */
final class Theme
{
    private Theme() { }

    static final Color BG = new Color(12, 17, 26);
    static final Color CARD = new Color(21, 29, 42);
    static final Color CARD_DEEP = new Color(18, 25, 37);
    static final Color CARD_HOVER = new Color(28, 38, 56);
    static final Color FIELD = new Color(9, 13, 20);
    static final Color LINE = new Color(39, 52, 70);
    static final Color TEXT = new Color(234, 240, 249);
    static final Color MUTED = new Color(142, 158, 179);
    static final Color PURPLE = new Color(161, 143, 255);
    static final Color CYAN = new Color(85, 215, 197);
    static final Color BAD = new Color(240, 106, 118);
    static final Color ON_ACCENT = new Color(17, 19, 35);

    static Font font(int style, float size)
    {
        return new Font("Segoe UI", style, 12).deriveFont(style, size);
    }

    /** Small capitals-style label font with extra letter spacing. */
    static Font tracked(int style, float size, float tracking)
    {
        Map<TextAttribute, Object> attrs = new HashMap<>();
        attrs.put(TextAttribute.TRACKING, tracking);
        return font(style, size).deriveFont(attrs);
    }

    static void quality(Graphics2D g)
    {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    static Color blend(Color a, Color b, float t)
    {
        return new Color((int) (a.getRed() + (b.getRed() - a.getRed()) * t),
            (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
            (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    static Color alpha(Color c, int a)
    {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }
}
