package net.runelite.client.plugins.microbot.prometheuslogin;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.LineBorder;

/** Themed replacement for the operating system title bar (which can't be coloured): purple gradient, icon, title, minimise
 *  and close, drag to move, and resizing from the thin purple frame. Call before the frame is made displayable. */
final class WindowChrome
{
    private WindowChrome() { }

    private static final Color LEFT = new Color(0x2f2160);
    private static final Color RIGHT = new Color(0x6a49d9);
    private static final Color FRAME = new Color(0x5a3fc0);
    private static final Color HOVER = new Color(255, 255, 255, 45);
    private static final Color CLOSE_HOVER = new Color(0xd23b4b);
    private static final int EDGE = 4;
    private static final int BAR = 32;

    static void install(JFrame frame, Component content, String title, Image icon, Runnable onClose)
    {
        frame.setUndecorated(true);
        JPanel outer = new JPanel(new BorderLayout());
        outer.setBackground(Theme.BG);
        outer.setBorder(new LineBorder(FRAME, EDGE));
        outer.add(bar(frame, title, icon, onClose), BorderLayout.NORTH);
        outer.add(content, BorderLayout.CENTER);
        frame.setContentPane(outer);
        resizer(frame, outer);
    }

    private static JComponent bar(JFrame frame, String title, Image icon, Runnable onClose)
    {
        JPanel bar = new JPanel(new BorderLayout(8, 0))
        {
            @Override protected void paintComponent(Graphics g)
            {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setPaint(new GradientPaint(0, 0, LEFT, getWidth(), 0, RIGHT));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(new Color(255, 255, 255, 40));
                g2.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
                g2.dispose();
            }
        };
        bar.setOpaque(false);
        bar.setPreferredSize(new Dimension(100, BAR));
        bar.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
        JLabel label = new JLabel(title);
        label.setForeground(Color.WHITE);
        label.setFont(Theme.font(java.awt.Font.BOLD, 12.5f));
        if (icon != null) label.setIcon(new ImageIcon(icon.getScaledInstance(18, 18, Image.SCALE_SMOOTH)));
        label.setIconTextGap(8);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttons.setOpaque(false);
        buttons.add(button(false, HOVER, () -> frame.setState(Frame.ICONIFIED)));
        buttons.add(button(true, CLOSE_HOVER, onClose));
        bar.add(label, BorderLayout.CENTER);
        bar.add(buttons, BorderLayout.EAST);
        Point[] start = new Point[1];
        MouseAdapter drag = new MouseAdapter()
        {
            @Override public void mousePressed(MouseEvent e) { start[0] = e.getPoint(); }
            @Override public void mouseDragged(MouseEvent e)
            {
                if (start[0] == null) return;
                Point p = e.getLocationOnScreen();
                frame.setLocation(p.x - start[0].x - EDGE, p.y - start[0].y - EDGE);
            }
        };
        bar.addMouseListener(drag);
        bar.addMouseMotionListener(drag);
        label.addMouseListener(drag);
        label.addMouseMotionListener(drag);
        return bar;
    }

    private static JButton button(boolean close, Color hover, Runnable action)
    {
        JButton b = new JButton()
        {
            @Override protected void paintComponent(Graphics g)
            {
                if (getModel().isRollover())
                {
                    g.setColor(hover);
                    g.fillRect(0, 0, getWidth(), getHeight());
                }
                Graphics2D g2 = (Graphics2D) g.create();
                Theme.quality(g2);
                g2.setColor(Color.WHITE);
                g2.setStroke(new java.awt.BasicStroke(1.4f));
                int cx = getWidth() / 2, cy = getHeight() / 2;
                if (close)
                {
                    g2.drawLine(cx - 5, cy - 5, cx + 5, cy + 5);
                    g2.drawLine(cx - 5, cy + 5, cx + 5, cy - 5);
                }
                else g2.drawLine(cx - 6, cy + 3, cx + 6, cy + 3);
                g2.dispose();
            }
        };
        b.setPreferredSize(new Dimension(46, BAR));
        b.setFocusable(false);
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setOpaque(false);
        b.setForeground(Color.WHITE);
        b.setFont(Theme.font(java.awt.Font.PLAIN, 15f));
        b.setRolloverEnabled(true);
        b.addActionListener(e -> action.run());
        return b;
    }

    /** Dragging the purple frame resizes the window, with the usual edge and corner cursors. */
    private static void resizer(JFrame frame, JPanel outer)
    {
        int[] mode = {0};
        Point[] origin = new Point[1];
        Rectangle[] bounds = new Rectangle[1];
        MouseAdapter m = new MouseAdapter()
        {
            int zone(MouseEvent e)
            {
                int w = outer.getWidth(), h = outer.getHeight(), x = e.getX(), y = e.getY(), z = 0;
                if (x < EDGE + 2) z |= 1;
                if (x >= w - EDGE - 2) z |= 2;
                if (y < EDGE + 2) z |= 4;
                if (y >= h - EDGE - 2) z |= 8;
                boolean inside = x >= EDGE && x < w - EDGE && y >= EDGE && y < h - EDGE;
                return inside && z == 0 ? 0 : z;
            }

            @Override public void mouseMoved(MouseEvent e)
            {
                int c;
                switch (zone(e))
                {
                    case 1: c = Cursor.W_RESIZE_CURSOR; break;
                    case 2: c = Cursor.E_RESIZE_CURSOR; break;
                    case 4: c = Cursor.N_RESIZE_CURSOR; break;
                    case 8: c = Cursor.S_RESIZE_CURSOR; break;
                    case 5: c = Cursor.NW_RESIZE_CURSOR; break;
                    case 6: c = Cursor.NE_RESIZE_CURSOR; break;
                    case 9: c = Cursor.SW_RESIZE_CURSOR; break;
                    case 10: c = Cursor.SE_RESIZE_CURSOR; break;
                    default: c = Cursor.DEFAULT_CURSOR;
                }
                outer.setCursor(Cursor.getPredefinedCursor(c));
            }

            @Override public void mousePressed(MouseEvent e)
            {
                mode[0] = zone(e);
                origin[0] = e.getLocationOnScreen();
                bounds[0] = frame.getBounds();
            }

            @Override public void mouseDragged(MouseEvent e)
            {
                if (mode[0] == 0 || origin[0] == null) return;
                int dx = e.getXOnScreen() - origin[0].x, dy = e.getYOnScreen() - origin[0].y;
                Rectangle r = new Rectangle(bounds[0]);
                Dimension min = frame.getMinimumSize();
                if ((mode[0] & 2) != 0) r.width = Math.max(min.width, bounds[0].width + dx);
                if ((mode[0] & 8) != 0) r.height = Math.max(min.height, bounds[0].height + dy);
                if ((mode[0] & 1) != 0)
                {
                    int w = Math.max(min.width, bounds[0].width - dx);
                    r.x = bounds[0].x + bounds[0].width - w;
                    r.width = w;
                }
                if ((mode[0] & 4) != 0)
                {
                    int h = Math.max(min.height, bounds[0].height - dy);
                    r.y = bounds[0].y + bounds[0].height - h;
                    r.height = h;
                }
                frame.setBounds(r);
            }
        };
        outer.addMouseListener(m);
        outer.addMouseMotionListener(m);
    }
}
