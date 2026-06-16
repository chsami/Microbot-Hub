package net.runelite.client.plugins.microbot.microbotdashboardplus.panels;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.microbotdashboardplus.data.PollSnapshot;
import net.runelite.client.plugins.microbot.microbotdashboardplus.data.XpHistory;
import net.runelite.client.plugins.microbot.microbotdashboardplus.poller.GameStatePoller;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.util.List;

/**
 * XP-over-time chart. Custom Java2D paint, no dependency on external charting
 * libraries.
 *
 * <p>Controls (in section header): skill JComboBox + window JComboBox.
 * Default selection: Mining @ 30 minute window. Y axis: XP gained relative
 * to the earliest sample in the visible window. X axis: time.
 *
 * <p>Spans full width of the dashboard grid.
 */
public class XpChartPanel extends DashboardSection {

    private static final WindowChoice[] WINDOWS = {
            new WindowChoice("5 min", 5L * 60_000),
            new WindowChoice("15 min", 15L * 60_000),
            new WindowChoice("30 min", 30L * 60_000),
            new WindowChoice("1 hour", 60L * 60_000),
            new WindowChoice("4 hours", 4L * 60 * 60_000),
            new WindowChoice("24 hours", 24L * 60 * 60_000),
    };
    private static final int DEFAULT_WINDOW_INDEX = 2; // 30 min

    private static final String CONFIG_GROUP = "MicrobotDashboardPlus";
    private static final String K_CHART_SKILL = "chartSkill";
    private static final String K_CHART_WINDOW_INDEX = "chartWindowIndex";

    private final Skill[] selectableSkills;
    private final JComboBox<Skill> skillCombo;
    private final JComboBox<WindowChoice> windowCombo;
    private final ChartCanvas canvas;

    public XpChartPanel(GameStatePoller poller) {
        super("XP Over Time", poller);

        selectableSkills = buildSelectableSkills();

        // Canvas first; lambdas below capture it.
        canvas = new ChartCanvas();
        canvas.setPreferredSize(new Dimension(780, 240));

        // Header controls.
        JLabel skillLbl = new JLabel("Skill");
        skillLbl.setForeground(Color.LIGHT_GRAY);
        skillLbl.setFont(FontManager.getRunescapeSmallFont());
        addHeaderControl(skillLbl);

        skillCombo = new JComboBox<>(selectableSkills);
        skillCombo.setPreferredSize(new Dimension(110, 20));
        skillCombo.setRenderer(new SkillRenderer());
        Skill defaultSkill = restoreSkill(findDefault(selectableSkills, Skill.MINING));
        skillCombo.setSelectedItem(defaultSkill);
        skillCombo.addActionListener(e -> {
            Object sel = skillCombo.getSelectedItem();
            if (sel instanceof Skill) persistString(K_CHART_SKILL, ((Skill) sel).name());
            canvas.repaint();
        });
        addHeaderControl(skillCombo);

        JLabel windowLbl = new JLabel("Window");
        windowLbl.setForeground(Color.LIGHT_GRAY);
        windowLbl.setFont(FontManager.getRunescapeSmallFont());
        addHeaderControl(windowLbl);

        windowCombo = new JComboBox<>(WINDOWS);
        windowCombo.setPreferredSize(new Dimension(90, 20));
        windowCombo.setSelectedIndex(restoreWindowIndex());
        windowCombo.addActionListener(e -> {
            persistString(K_CHART_WINDOW_INDEX, Integer.toString(windowCombo.getSelectedIndex()));
            canvas.repaint();
        });
        addHeaderControl(windowCombo);

        // Body.
        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.add(canvas, BorderLayout.CENTER);
        add(body, BorderLayout.CENTER);
    }

    private static Skill[] buildSelectableSkills() {
        java.util.List<Skill> list = new java.util.ArrayList<>();
        for (Skill s : Skill.values()) {
            if (s == Skill.OVERALL) continue;
            list.add(s);
        }
        return list.toArray(new Skill[0]);
    }

    private static Skill findDefault(Skill[] skills, Skill preferred) {
        for (Skill s : skills) if (s == preferred) return s;
        return skills.length == 0 ? null : skills[0];
    }

    // -----------------------------------------------------------------
    // Persistence
    // -----------------------------------------------------------------

    private Skill restoreSkill(Skill fallback) {
        try {
            String raw = Microbot.getConfigManager().getConfiguration(CONFIG_GROUP, K_CHART_SKILL);
            if (raw == null || raw.isEmpty()) return fallback;
            Skill found = Skill.valueOf(raw);
            for (Skill s : selectableSkills) if (s == found) return s;
            return fallback;
        } catch (Throwable t) {
            return fallback;
        }
    }

    private int restoreWindowIndex() {
        try {
            String raw = Microbot.getConfigManager().getConfiguration(CONFIG_GROUP, K_CHART_WINDOW_INDEX);
            if (raw == null || raw.isEmpty()) return DEFAULT_WINDOW_INDEX;
            int idx = Integer.parseInt(raw);
            if (idx < 0 || idx >= WINDOWS.length) return DEFAULT_WINDOW_INDEX;
            return idx;
        } catch (Throwable t) {
            return DEFAULT_WINDOW_INDEX;
        }
    }

    private static void persistString(String key, String value) {
        try {
            Microbot.getConfigManager().setConfiguration(CONFIG_GROUP, key, value);
        } catch (Throwable ignored) { /* config not ready -- swallow */ }
    }

    @Override
    protected void applySnapshot(PollSnapshot snapshot) {
        // The chart redraws on its own data view; just trigger a repaint.
        canvas.repaint();
    }

    // ---------------------------------------------------------------------
    // Chart canvas
    // ---------------------------------------------------------------------

    private final class ChartCanvas extends JPanel {

        ChartCanvas() {
            setBackground(ColorScheme.DARKER_GRAY_COLOR);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();

                // Background.
                g2.setColor(ColorScheme.DARKER_GRAY_COLOR);
                g2.fillRect(0, 0, w, h);

                Skill skill = (Skill) skillCombo.getSelectedItem();
                WindowChoice window = (WindowChoice) windowCombo.getSelectedItem();
                if (skill == null || window == null) return;

                XpHistory history = poller.getXpHistory();
                List<XpHistory.SamplePoint> all = history.getSamples(skill);
                long now = System.currentTimeMillis();
                long cutoff = now - window.windowMs;

                // Filter samples within the window.
                java.util.List<XpHistory.SamplePoint> samples = new java.util.ArrayList<>();
                for (XpHistory.SamplePoint p : all) {
                    if (p.timestampMillis >= cutoff) samples.add(p);
                }

                // Chart bounds.
                int padLeft = 60;
                int padRight = 16;
                int padTop = 14;
                int padBottom = 28;
                int plotW = Math.max(10, w - padLeft - padRight);
                int plotH = Math.max(10, h - padTop - padBottom);

                // Axes.
                g2.setColor(ColorScheme.MEDIUM_GRAY_COLOR);
                g2.drawLine(padLeft, padTop, padLeft, padTop + plotH);          // Y axis
                g2.drawLine(padLeft, padTop + plotH, padLeft + plotW, padTop + plotH); // X axis

                if (samples.size() < 2) {
                    drawCenteredString(g2, "Waiting for XP samples in selected window",
                            padLeft, padTop, plotW, plotH, Color.GRAY);
                    return;
                }

                int baseline = samples.get(0).xp;
                int maxDelta = 0;
                for (XpHistory.SamplePoint p : samples) {
                    maxDelta = Math.max(maxDelta, p.xp - baseline);
                }
                if (maxDelta <= 0) {
                    drawCenteredString(g2, "No XP gained in window",
                            padLeft, padTop, plotW, plotH, Color.GRAY);
                    return;
                }

                // Y ticks: 4 horizontal grid lines.
                g2.setFont(FontManager.getRunescapeSmallFont());
                g2.setColor(Color.GRAY);
                for (int i = 1; i <= 4; i++) {
                    int yVal = (int) ((maxDelta * (long) i) / 4);
                    int y = padTop + plotH - (int) ((yVal * (long) plotH) / maxDelta);
                    g2.setColor(new Color(80, 80, 80));
                    g2.drawLine(padLeft + 1, y, padLeft + plotW, y);
                    g2.setColor(Color.LIGHT_GRAY);
                    String lbl = formatXpShort(yVal);
                    int lblWidth = g2.getFontMetrics().stringWidth(lbl);
                    g2.drawString(lbl, padLeft - lblWidth - 4, y + 4);
                }

                // X ticks: 4 vertical positions (~quarter through, half, etc.).
                long windowMs = window.windowMs;
                for (int i = 0; i <= 4; i++) {
                    long t = now - windowMs + (windowMs * i / 4);
                    int x = padLeft + (int) ((plotW * (long) i) / 4);
                    g2.setColor(new Color(80, 80, 80));
                    g2.drawLine(x, padTop, x, padTop + plotH);
                    g2.setColor(Color.LIGHT_GRAY);
                    String lbl = (i == 4) ? "now" : (humanAgo(now - t));
                    int lblWidth = g2.getFontMetrics().stringWidth(lbl);
                    g2.drawString(lbl, x - lblWidth / 2, padTop + plotH + 16);
                }

                // Line path.
                Path2D.Double path = new Path2D.Double();
                boolean first = true;
                for (XpHistory.SamplePoint p : samples) {
                    double xf = (double) (p.timestampMillis - cutoff) / (double) windowMs;
                    int x = padLeft + (int) (xf * plotW);
                    int yVal = p.xp - baseline;
                    int y = padTop + plotH - (int) ((yVal * (long) plotH) / maxDelta);
                    if (first) { path.moveTo(x, y); first = false; }
                    else { path.lineTo(x, y); }
                }

                g2.setStroke(new BasicStroke(2f));
                g2.setColor(ColorScheme.PROGRESS_COMPLETE_COLOR);
                g2.draw(path);

                // Summary text top-right.
                String summary = String.format("Δ %s XP · %s window",
                        formatXpFull(maxDelta), window.label);
                g2.setColor(Color.LIGHT_GRAY);
                g2.setFont(FontManager.getRunescapeSmallFont());
                int sumWidth = g2.getFontMetrics().stringWidth(summary);
                g2.drawString(summary, padLeft + plotW - sumWidth, padTop - 2);

            } finally {
                g2.dispose();
            }
        }

        private void drawCenteredString(Graphics2D g2, String s, int x, int y, int w, int h, Color color) {
            g2.setColor(color);
            g2.setFont(FontManager.getRunescapeSmallFont());
            int sw = g2.getFontMetrics().stringWidth(s);
            int sh = g2.getFontMetrics().getHeight();
            g2.drawString(s, x + (w - sw) / 2, y + (h - sh) / 2 + g2.getFontMetrics().getAscent());
        }
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private static String formatXpShort(int xp) {
        if (xp >= 1_000_000) return String.format("%.1fM", xp / 1_000_000.0);
        if (xp >= 1_000) return String.format("%.1fk", xp / 1_000.0);
        return Integer.toString(xp);
    }

    private static String formatXpFull(int xp) {
        return java.text.NumberFormat.getIntegerInstance().format(xp);
    }

    private static String humanAgo(long ms) {
        long sec = ms / 1000;
        if (sec < 60) return sec + "s";
        long min = sec / 60;
        if (min < 60) return min + "m";
        long hr = min / 60;
        return hr + "h";
    }

    private static final class WindowChoice {
        final String label;
        final long windowMs;

        WindowChoice(String label, long windowMs) {
            this.label = label;
            this.windowMs = windowMs;
        }
        @Override public String toString() { return label; }
    }

    private static class SkillRenderer extends javax.swing.DefaultListCellRenderer {
        @Override
        public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value,
                                                               int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof Skill) {
                String n = ((Skill) value).getName();
                setText(Character.toUpperCase(n.charAt(0)) + n.substring(1).toLowerCase());
            }
            setHorizontalAlignment(SwingConstants.LEFT);
            return this;
        }
    }
}
