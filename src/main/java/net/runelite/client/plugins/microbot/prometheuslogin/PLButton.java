package net.runelite.client.plugins.microbot.prometheuslogin;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.JButton;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

/** Rounded button with a short hover fade. Primary = purple gradient; secondary = dark card. */
final class PLButton extends JButton
{
    private final boolean primary;
    private float amount;
    private final Timer fade;

    PLButton(String text, boolean primary)
    {
        super(text);
        this.primary = primary;
        fade = new Timer(16, e -> {
            float target = getModel().isRollover() && isEnabled() ? 1 : 0;
            amount += (target - amount) * .27f;
            if (Math.abs(target - amount) < .015f)
            {
                amount = target;
                ((Timer) e.getSource()).stop();
            }
            repaint();
        });
        getModel().addChangeListener(e -> {
            if (isShowing()) fade.start();
            else
            {
                amount = getModel().isRollover() ? 1 : 0;
                repaint();
            }
        });
        setFont(Theme.font(java.awt.Font.BOLD, 12f));
        setOpaque(false);
        setContentAreaFilled(false);
        setFocusPainted(false);
        setRolloverEnabled(true);
        setBorder(new EmptyBorder(9, 16, 9, 16));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    @Override public void removeNotify()
    {
        fade.stop();
        super.removeNotify();
    }

    @Override protected void paintComponent(Graphics raw)
    {
        Graphics2D g = (Graphics2D) raw.create();
        Theme.quality(g);
        int shift = getModel().isPressed() ? 1 : 0;
        Color normal = primary ? Theme.PURPLE : Theme.CARD;
        Color hover = primary ? new Color(190, 173, 255) : new Color(44, 53, 76);
        Color fill = Theme.blend(normal, hover, amount);
        g.setPaint(new GradientPaint(0, 0, fill, getWidth(), getHeight(), primary ? new Color(123, 107, 217) : Theme.CARD));
        g.fillRoundRect(1, 1 + shift, getWidth() - 2, getHeight() - 3, 10, 10);
        g.setColor(Theme.blend(Theme.LINE, Theme.PURPLE, amount));
        g.drawRoundRect(1, 1 + shift, getWidth() - 3, getHeight() - 4, 10, 10);
        g.setFont(getFont());
        g.setColor(primary ? Theme.ON_ACCENT : Theme.TEXT);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(getText(), (getWidth() - fm.stringWidth(getText())) / 2, (getHeight() - fm.getHeight()) / 2 + fm.getAscent() + shift);
        g.dispose();
    }
}
