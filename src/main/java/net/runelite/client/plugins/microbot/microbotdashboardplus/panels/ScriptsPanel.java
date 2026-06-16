package net.runelite.client.plugins.microbot.microbotdashboardplus.panels;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.microbotdashboardplus.data.PollSnapshot;
import net.runelite.client.plugins.microbot.microbotdashboardplus.poller.GameStatePoller;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.AbstractCellEditor;
import javax.swing.JButton;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Active Scripts section. JTable: Plugin / Status / Runtime / Action (Stop).
 *
 * <p>Stop button calls {@link Microbot#stopPlugin(Plugin)} for the row's
 * plugin. Refresh happens implicitly on the next poll.
 */
@Slf4j
public class ScriptsPanel extends DashboardSection {

    private static final String[] COLUMNS = {"Plugin", "Status", "Runtime", "Action"};

    private final ScriptsTableModel model = new ScriptsTableModel();
    private final JTable table;

    public ScriptsPanel(GameStatePoller poller) {
        super("Active Scripts", poller);

        table = new JTable(model);
        table.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        table.setForeground(Color.WHITE);
        table.setFont(FontManager.getRunescapeSmallFont());
        table.setRowHeight(22);
        table.setGridColor(ColorScheme.MEDIUM_GRAY_COLOR);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(false);
        table.setFillsViewportHeight(true);
        table.setOpaque(false);

        JTableHeader header = table.getTableHeader();
        header.setBackground(ColorScheme.DARK_GRAY_COLOR);
        header.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        header.setFont(FontManager.getRunescapeSmallFont());
        header.setReorderingAllowed(false);

        table.getColumnModel().getColumn(0).setPreferredWidth(180);
        table.getColumnModel().getColumn(1).setPreferredWidth(80);
        table.getColumnModel().getColumn(2).setPreferredWidth(80);
        table.getColumnModel().getColumn(3).setPreferredWidth(70);

        TableCellRenderer textRenderer = new TextRenderer();
        table.getColumnModel().getColumn(0).setCellRenderer(textRenderer);
        table.getColumnModel().getColumn(1).setCellRenderer(textRenderer);
        table.getColumnModel().getColumn(2).setCellRenderer(textRenderer);
        table.getColumnModel().getColumn(3).setCellRenderer(new StopButtonRenderer());
        table.getColumnModel().getColumn(3).setCellEditor(new StopButtonEditor());

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(ColorScheme.DARKER_GRAY_COLOR);
        scroll.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        scroll.setPreferredSize(new Dimension(380, 200));
        add(scroll, BorderLayout.CENTER);
    }

    @Override
    protected void applySnapshot(PollSnapshot snapshot) {
        if (snapshot == null) return;
        List<PollSnapshot.ScriptStatus> rows = snapshot.getActiveScripts();
        model.update(rows == null ? Collections.emptyList() : rows);
        setSubtitle("(" + model.getRowCount() + ")");
    }

    private void stopPlugin(String pluginClassName) {
        if (pluginClassName == null) return;
        try {
            Plugin target = null;
            for (Plugin p : Microbot.getPluginManager().getPlugins()) {
                if (p != null && p.getClass().getName().equals(pluginClassName)) {
                    target = p;
                    break;
                }
            }
            if (target == null) {
                log.warn("Stop requested but plugin {} not found", pluginClassName);
                return;
            }
            Microbot.stopPlugin(target);
            poller.refreshNow();
        } catch (Throwable t) {
            log.warn("Stop plugin failed for {}: {}", pluginClassName, t.getMessage(), t);
        }
    }

    // ------------------------------------------------------------------
    // Table model
    // ------------------------------------------------------------------

    private static final class ScriptsTableModel extends AbstractTableModel {
        private List<PollSnapshot.ScriptStatus> rows = new ArrayList<>();

        void update(List<PollSnapshot.ScriptStatus> newRows) {
            this.rows = new ArrayList<>(newRows);
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }
        @Override public boolean isCellEditable(int row, int col) { return col == 3; }
        @Override public Class<?> getColumnClass(int col) { return String.class; }

        @Override
        public Object getValueAt(int row, int col) {
            PollSnapshot.ScriptStatus s = rows.get(row);
            switch (col) {
                case 0: return s.getDisplayName();
                case 1: return s.getStatus() == null ? "--" : s.getStatus();
                case 2: return formatRuntime(s.getRuntimeMillis());
                case 3: return s.getPluginClassName();
                default: return "";
            }
        }
    }

    private static String formatRuntime(long ms) {
        if (ms <= 0) return "--";
        long secs = ms / 1000;
        long h = secs / 3600;
        long m = (secs % 3600) / 60;
        long s = secs % 60;
        if (h > 0) return String.format("%dh %02dm", h, m);
        if (m > 0) return String.format("%dm %02ds", m, s);
        return String.format("%ds", s);
    }

    // ------------------------------------------------------------------
    // Renderers + editors
    // ------------------------------------------------------------------

    private static class TextRenderer extends DefaultTableCellRenderer {
        TextRenderer() { setFont(FontManager.getRunescapeSmallFont()); }
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int r, int c) {
            Component cmp = super.getTableCellRendererComponent(t, v, sel, focus, r, c);
            cmp.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            cmp.setForeground(Color.WHITE);
            return cmp;
        }
    }

    private static class StopButtonRenderer extends JButton implements TableCellRenderer {
        StopButtonRenderer() {
            setText("Stop");
            setFont(FontManager.getRunescapeSmallFont());
            setBackground(new Color(0x5c2929));
            setForeground(Color.WHITE);
            setFocusPainted(false);
            setBorderPainted(false);
            setHorizontalAlignment(SwingConstants.CENTER);
        }
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int r, int c) {
            return this;
        }
    }

    private class StopButtonEditor extends AbstractCellEditor implements TableCellEditor {
        private final JButton button;
        private String activePluginClass;

        StopButtonEditor() {
            button = new JButton("Stop");
            button.setFont(FontManager.getRunescapeSmallFont());
            button.setBackground(new Color(0x7c3939));
            button.setForeground(Color.WHITE);
            button.setFocusPainted(false);
            button.setBorderPainted(false);
            button.addActionListener(e -> {
                String target = activePluginClass;
                fireEditingStopped();
                if (target != null) stopPlugin(target);
            });
        }
        @Override
        public Component getTableCellEditorComponent(JTable t, Object v, boolean sel, int r, int c) {
            activePluginClass = (v == null) ? null : v.toString();
            return button;
        }
        @Override
        public Object getCellEditorValue() {
            return activePluginClass;
        }
    }
}
