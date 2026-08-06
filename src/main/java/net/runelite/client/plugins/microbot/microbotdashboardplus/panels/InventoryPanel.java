package net.runelite.client.plugins.microbot.microbotdashboardplus.panels;

import net.runelite.client.plugins.microbot.microbotdashboardplus.data.PollSnapshot;
import net.runelite.client.plugins.microbot.microbotdashboardplus.poller.GameStatePoller;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.border.LineBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.text.NumberFormat;
import java.util.Collections;
import java.util.List;

/**
 * Inventory section. Grid of slot cells with name + quantity, fed from the
 * {@link PollSnapshot} (the poller reads items via Rs2Inventory). Noted items
 * are styled distinctly.
 */
public class InventoryPanel extends DashboardSection {

    private static final NumberFormat NUM = NumberFormat.getIntegerInstance();
    private final JPanel grid;

    public InventoryPanel(GameStatePoller poller) {
        super("Inventory", poller);

        grid = new JPanel(new GridLayout(0, 2, 4, 4));
        grid.setOpaque(false);

        JScrollPane scroll = new JScrollPane(grid);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(ColorScheme.DARKER_GRAY_COLOR);
        scroll.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        scroll.setPreferredSize(new Dimension(380, 360));
        add(scroll, BorderLayout.CENTER);
    }

    @Override
    protected void applySnapshot(PollSnapshot snapshot) {
        if (snapshot == null) return;
        List<PollSnapshot.InventoryItem> items = snapshot.getInventory();
        rebuildGrid(items == null ? Collections.emptyList() : items);
        setSubtitle(items == null ? "" : "(" + items.size() + ")");
    }

    private void rebuildGrid(List<PollSnapshot.InventoryItem> items) {
        grid.removeAll();
        if (items.isEmpty()) {
            JLabel empty = new JLabel("Inventory empty or unavailable");
            empty.setForeground(Color.GRAY);
            empty.setFont(FontManager.getRunescapeSmallFont());
            empty.setHorizontalAlignment(SwingConstants.CENTER);
            grid.add(empty);
        } else {
            for (PollSnapshot.InventoryItem item : items) {
                grid.add(makeCell(item));
            }
        }
        grid.revalidate();
        grid.repaint();
    }

    private JPanel makeCell(PollSnapshot.InventoryItem item) {
        JPanel cell = new JPanel(new BorderLayout(4, 0));
        cell.setBackground(ColorScheme.DARK_GRAY_COLOR);
        cell.setBorder(new LineBorder(ColorScheme.MEDIUM_GRAY_COLOR, 1));

        JLabel name = new JLabel(item.getName() == null ? "?" : item.getName());
        name.setForeground(item.isNoted() ? ColorScheme.PROGRESS_INPROGRESS_COLOR : Color.WHITE);
        name.setFont(FontManager.getRunescapeSmallFont());
        name.setBorder(new javax.swing.border.EmptyBorder(2, 6, 2, 0));
        cell.add(name, BorderLayout.CENTER);

        JLabel qty = new JLabel(NUM.format(item.getQuantity()));
        qty.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
        qty.setFont(FontManager.getRunescapeSmallFont());
        qty.setBorder(new javax.swing.border.EmptyBorder(2, 0, 2, 6));
        qty.setHorizontalAlignment(SwingConstants.RIGHT);
        cell.add(qty, BorderLayout.EAST);

        return cell;
    }
}
