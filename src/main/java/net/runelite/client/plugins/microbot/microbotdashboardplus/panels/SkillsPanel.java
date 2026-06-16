package net.runelite.client.plugins.microbot.microbotdashboardplus.panels;

import net.runelite.api.Experience;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.microbotdashboardplus.data.PollSnapshot;
import net.runelite.client.plugins.microbot.microbotdashboardplus.data.XpHistory;
import net.runelite.client.plugins.microbot.microbotdashboardplus.poller.GameStatePoller;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Skills section: 6-column JTable. Skill / Level / XP / +Δ / XP/hr / ETA.
 *
 * <ul>
 *     <li>Δ is gain since first observation (set when the poller starts).</li>
 *     <li>XP/hr is extrapolated from the 5-minute rolling window in {@link XpHistory}.</li>
 *     <li>ETA is the time to reach a target level, computed from the current
 *         XP/hr and {@link Experience#getXpForLevel(int)}. The target is read
 *         from the per-skill targets you set in config; the skill you are
 *         actively training (the one currently gaining XP) falls back to the
 *         next level when no target is set.</li>
 *     <li>Zero values render muted; positive deltas render green.</li>
 * </ul>
 */
public class SkillsPanel extends DashboardSection {

    private static final NumberFormat NUM = NumberFormat.getIntegerInstance();
    private static final String[] COLUMNS = {"Skill", "Level", "XP", "+Δ", "XP/hr", "ETA"};
    private static final Skill[] SKILL_ORDER = buildSkillOrder();

    private static final String CONFIG_GROUP = "MicrobotDashboardPlus";
    private static final String K_SKILL_TARGETS = "skillTargets";

    private static Skill[] buildSkillOrder() {
        List<Skill> list = new ArrayList<>();
        for (Skill s : Skill.values()) {
            if (s == Skill.OVERALL) continue;
            list.add(s);
        }
        return list.toArray(new Skill[0]);
    }

    private final SkillsTableModel model;
    private final JTable table;

    public SkillsPanel(GameStatePoller poller) {
        super("Skills", poller);
        setSubtitle("(Δ since session start · XP/hr 5-min rolling · ETA to target)");

        model = new SkillsTableModel();
        table = new JTable(model);
        table.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        table.setForeground(Color.WHITE);
        table.setFont(FontManager.getRunescapeSmallFont());
        table.setRowHeight(18);
        table.setGridColor(ColorScheme.MEDIUM_GRAY_COLOR);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(false);
        table.setFillsViewportHeight(true);
        table.setOpaque(false);
        table.setSelectionBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
        table.setSelectionForeground(Color.WHITE);

        JTableHeader header = table.getTableHeader();
        header.setBackground(ColorScheme.DARK_GRAY_COLOR);
        header.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        header.setFont(FontManager.getRunescapeSmallFont());
        header.setReorderingAllowed(false);

        // Column widths.
        table.getColumnModel().getColumn(0).setPreferredWidth(100);
        table.getColumnModel().getColumn(1).setPreferredWidth(45);
        table.getColumnModel().getColumn(2).setPreferredWidth(75);
        table.getColumnModel().getColumn(3).setPreferredWidth(55);
        table.getColumnModel().getColumn(4).setPreferredWidth(65);
        table.getColumnModel().getColumn(5).setPreferredWidth(80);

        // Renderers.
        TableCellRenderer rightAlignMono = new MonoRightRenderer();
        table.getColumnModel().getColumn(1).setCellRenderer(rightAlignMono);
        table.getColumnModel().getColumn(2).setCellRenderer(rightAlignMono);
        table.getColumnModel().getColumn(3).setCellRenderer(new DeltaRenderer());
        table.getColumnModel().getColumn(4).setCellRenderer(new RateRenderer());
        table.getColumnModel().getColumn(5).setCellRenderer(new EtaRenderer());

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(ColorScheme.DARKER_GRAY_COLOR);
        scroll.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        scroll.setPreferredSize(new Dimension(440, 420));
        add(scroll, BorderLayout.CENTER);
    }

    @Override
    protected void applySnapshot(PollSnapshot snapshot) {
        if (snapshot == null) return;
        model.update(snapshot, poller.getXpHistory());
    }

    // ------------------------------------------------------------------
    // Table model
    // ------------------------------------------------------------------

    private static final class SkillsTableModel extends AbstractTableModel {
        private final Object[][] rows = new Object[SKILL_ORDER.length][COLUMNS.length];

        SkillsTableModel() {
            for (int i = 0; i < SKILL_ORDER.length; i++) {
                rows[i][0] = capitalize(SKILL_ORDER[i].getName());
                rows[i][1] = "--";
                rows[i][2] = "--";
                rows[i][3] = 0;
                rows[i][4] = 0;
                rows[i][5] = "--";
            }
        }

        void update(PollSnapshot snapshot, XpHistory history) {
            Map<Skill, Integer> targets = readSkillTargets();
            for (int i = 0; i < SKILL_ORDER.length; i++) {
                Skill s = SKILL_ORDER[i];
                Integer xp = snapshot.getSkillXp() == null ? null : snapshot.getSkillXp().get(s);
                Integer lvl = snapshot.getSkillLevels() == null ? null : snapshot.getSkillLevels().get(s);
                int rate = history.xpPerHour(s);
                rows[i][1] = lvl == null ? "--" : NUM.format(lvl);
                rows[i][2] = xp == null ? "--" : NUM.format(xp);
                rows[i][3] = xp == null ? 0 : history.deltaSinceBaseline(s, xp);
                rows[i][4] = rate;
                rows[i][5] = computeEta(s, xp, lvl, rate, targets);
            }
            fireTableDataChanged();
        }

        /**
         * ETA text to the target level for this skill.
         *
         * <p>Target selection: an explicit per-skill target wins. Otherwise,
         * if the skill is currently gaining XP (rate &gt; 0) we target the next
         * level so the skill being actively trained always shows an estimate.
         * Skills with no target and no XP gain show "--".
         */
        private static String computeEta(Skill s, Integer xp, Integer lvl, int rate, Map<Skill, Integer> targets) {
            if (xp == null || lvl == null) return "--";

            Integer target = targets.get(s);
            if (target == null) {
                // No explicit target: only the actively-training skill gets a
                // next-level estimate.
                if (rate <= 0 || lvl >= Experience.MAX_REAL_LEVEL) return "--";
                target = lvl + 1;
            }
            if (target <= lvl) return "done";
            if (target > Experience.MAX_REAL_LEVEL) target = Experience.MAX_REAL_LEVEL;
            if (rate <= 0) return "--"; // need a rate to estimate

            int targetXp;
            try {
                targetXp = Experience.getXpForLevel(target);
            } catch (IllegalArgumentException ex) {
                return "--";
            }
            int remaining = targetXp - xp;
            if (remaining <= 0) return "done";

            double hours = remaining / (double) rate;
            return "L" + target + " " + formatEta(hours);
        }

        @Override public int getRowCount() { return rows.length; }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }
        @Override public Object getValueAt(int row, int col) { return rows[row][col]; }
        @Override public boolean isCellEditable(int row, int col) { return false; }
        @Override public Class<?> getColumnClass(int col) {
            return (col == 3 || col == 4) ? Integer.class : String.class;
        }
    }

    /** Hours as a compact human ETA: "12m", "3h 25m", "2d 4h". */
    private static String formatEta(double hours) {
        if (hours <= 0 || Double.isNaN(hours) || Double.isInfinite(hours)) return "--";
        long totalMinutes = Math.round(hours * 60.0);
        if (totalMinutes < 1) return "<1m";
        if (totalMinutes < 60) return totalMinutes + "m";
        long h = totalMinutes / 60;
        long m = totalMinutes % 60;
        if (h < 24) return h + "h " + m + "m";
        long d = h / 24;
        long remH = h % 24;
        return d + "d " + remH + "h";
    }

    /**
     * Parse the per-skill targets config (comma-separated SKILL:LEVEL pairs,
     * for example "MINING:70, AGILITY:60"). Unknown skill names and bad levels
     * are skipped. Returns an empty map when nothing is configured.
     */
    private static Map<Skill, Integer> readSkillTargets() {
        Map<Skill, Integer> out = new EnumMap<>(Skill.class);
        String raw;
        try {
            raw = Microbot.getConfigManager().getConfiguration(CONFIG_GROUP, K_SKILL_TARGETS);
        } catch (Throwable t) {
            return out;
        }
        if (raw == null || raw.trim().isEmpty()) return out;

        for (String token : raw.split(",")) {
            String pair = token.trim();
            if (pair.isEmpty()) continue;
            int colon = pair.indexOf(':');
            if (colon <= 0 || colon >= pair.length() - 1) continue;
            String name = pair.substring(0, colon).trim().toUpperCase();
            String levelStr = pair.substring(colon + 1).trim();
            try {
                Skill skill = Skill.valueOf(name);
                int level = Integer.parseInt(levelStr);
                if (level >= 1 && level <= Experience.MAX_REAL_LEVEL) {
                    out.put(skill, level);
                }
            } catch (IllegalArgumentException ignored) {
                // Unknown skill name or non-numeric level: skip silently.
            }
        }
        return out;
    }

    private static String capitalize(String name) {
        if (name == null || name.isEmpty()) return name;
        return Character.toUpperCase(name.charAt(0)) + name.substring(1).toLowerCase();
    }

    // ------------------------------------------------------------------
    // Cell renderers
    // ------------------------------------------------------------------

    private static class MonoRightRenderer extends DefaultTableCellRenderer {
        MonoRightRenderer() {
            setHorizontalAlignment(SwingConstants.RIGHT);
            setFont(FontManager.getRunescapeSmallFont());
        }
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int r, int c) {
            Component cmp = super.getTableCellRendererComponent(t, v, sel, focus, r, c);
            cmp.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            cmp.setForeground(Color.WHITE);
            return cmp;
        }
    }

    private static class DeltaRenderer extends DefaultTableCellRenderer {
        DeltaRenderer() {
            setHorizontalAlignment(SwingConstants.RIGHT);
            setFont(FontManager.getRunescapeSmallFont());
        }
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int r, int c) {
            Component cmp = super.getTableCellRendererComponent(t, v, sel, focus, r, c);
            int delta = (v instanceof Integer) ? (Integer) v : 0;
            setText(delta > 0 ? "+" + NUM.format(delta) : "0");
            cmp.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            cmp.setForeground(delta > 0 ? ColorScheme.PROGRESS_COMPLETE_COLOR : Color.GRAY);
            return cmp;
        }
    }

    private static class RateRenderer extends DefaultTableCellRenderer {
        RateRenderer() {
            setHorizontalAlignment(SwingConstants.RIGHT);
            setFont(FontManager.getRunescapeSmallFont());
        }
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int r, int c) {
            Component cmp = super.getTableCellRendererComponent(t, v, sel, focus, r, c);
            int rate = (v instanceof Integer) ? (Integer) v : 0;
            setText(rate > 0 ? NUM.format(rate) : "--");
            cmp.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            cmp.setForeground(rate > 0 ? Color.WHITE : Color.GRAY);
            return cmp;
        }
    }

    private static class EtaRenderer extends DefaultTableCellRenderer {
        EtaRenderer() {
            setHorizontalAlignment(SwingConstants.RIGHT);
            setFont(FontManager.getRunescapeSmallFont());
        }
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int r, int c) {
            Component cmp = super.getTableCellRendererComponent(t, v, sel, focus, r, c);
            String text = v == null ? "--" : v.toString();
            setText(text);
            cmp.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            boolean muted = "--".equals(text);
            boolean done = "done".equals(text);
            if (done) {
                cmp.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
            } else if (muted) {
                cmp.setForeground(Color.GRAY);
            } else {
                cmp.setForeground(Color.WHITE);
            }
            return cmp;
        }
    }
}
