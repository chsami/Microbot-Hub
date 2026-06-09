package net.runelite.client.plugins.microbot.microbotdashboardplus;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.microbotdashboardplus.data.PollSnapshot;
import net.runelite.client.plugins.microbot.microbotdashboardplus.poller.GameStatePoller;
import net.runelite.client.plugins.microbot.microbotdashboardplus.window.DashboardWindow;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.function.Consumer;

/**
 * Right-sidebar plugin panel: compact at-a-glance summary + launcher button
 * for the full {@link DashboardWindow}. Mirrors the Hub convention used by
 * ShootingStar / AutoBankStander / ActionReplay.
 *
 * <p>Subscribes to the {@link GameStatePoller}; updates labels on each
 * snapshot. The "Open Dashboard" button calls into the window owner (the
 * plugin) to show or focus the floating window.
 */
@Slf4j
public class DashboardPanel extends PluginPanel {

    private final GameStatePoller poller;
    private final Runnable openDashboard;
    private final Consumer<PollSnapshot> snapshotListener;

    private final JLabel statusValue = new JLabel("--");
    private final JLabel playerValue = new JLabel("--");
    private final JLabel worldValue = new JLabel("--");
    private final JLabel scriptsValue = new JLabel("--");

    public DashboardPanel(GameStatePoller poller, Runnable openDashboard) {
        super();
        this.poller = poller;
        this.openDashboard = openDashboard;

        setBorder(new EmptyBorder(10, 10, 10, 10));
        setLayout(new BorderLayout());

        add(buildTopArea(), BorderLayout.NORTH);
        add(buildStatsArea(), BorderLayout.CENTER);
        add(buildButtonArea(), BorderLayout.SOUTH);

        snapshotListener = this::applySnapshot;
        poller.addListener(snapshotListener);
    }

    private JPanel buildTopArea() {
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.setBorder(new EmptyBorder(0, 0, 10, 0));

        JLabel title = new JLabel("Dashboard");
        title.setForeground(Color.WHITE);
        title.setFont(FontManager.getRunescapeBoldFont());
        top.add(title, BorderLayout.NORTH);

        JLabel sub = new JLabel("MicrobotDashboardPlus v" + MicrobotDashboardPlusPlugin.version);
        sub.setForeground(Color.GRAY);
        sub.setFont(FontManager.getRunescapeSmallFont());
        top.add(sub, BorderLayout.SOUTH);

        return top;
    }

    private JPanel buildStatsArea() {
        JPanel stats = new JPanel(new GridLayout(0, 1, 0, 4));
        stats.setOpaque(false);

        stats.add(makeRow("Status", statusValue));
        stats.add(makeRow("Player", playerValue));
        stats.add(makeRow("World", worldValue));
        stats.add(makeRow("Active", scriptsValue));

        return stats;
    }

    private JPanel makeRow(String label, JLabel valueLabel) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);

        JLabel lbl = new JLabel(label);
        lbl.setForeground(Color.LIGHT_GRAY);
        lbl.setFont(FontManager.getRunescapeSmallFont());
        lbl.setPreferredSize(new Dimension(60, 16));
        row.add(lbl, BorderLayout.WEST);

        valueLabel.setForeground(Color.WHITE);
        valueLabel.setFont(FontManager.getRunescapeSmallFont());
        row.add(valueLabel, BorderLayout.CENTER);

        return row;
    }

    private JPanel buildButtonArea() {
        JPanel buttons = new JPanel(new GridLayout(0, 1, 0, 6));
        buttons.setOpaque(false);
        buttons.setBorder(new EmptyBorder(12, 0, 0, 0));

        JButton openBtn = new JButton("Open Dashboard");
        openBtn.setBackground(ColorScheme.BRAND_ORANGE);
        openBtn.setForeground(Color.WHITE);
        openBtn.setFocusPainted(false);
        openBtn.setFont(FontManager.getRunescapeBoldFont());
        openBtn.addActionListener(e -> {
            if (openDashboard != null) openDashboard.run();
        });
        buttons.add(openBtn);

        JButton refreshBtn = new JButton("Refresh now");
        refreshBtn.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
        refreshBtn.setForeground(Color.WHITE);
        refreshBtn.setFocusPainted(false);
        refreshBtn.setFont(FontManager.getRunescapeSmallFont());
        refreshBtn.addActionListener(e -> poller.refreshNow());
        buttons.add(refreshBtn);

        return buttons;
    }

    /** Plugin shutDown calls this. Removes listener so the poller stops feeding us after disposal. */
    public void detach() {
        poller.removeListener(snapshotListener);
    }

    private void applySnapshot(PollSnapshot snapshot) {
        if (snapshot == null) return;
        if (snapshot.isLoggedIn()) {
            statusValue.setText("Connected");
            statusValue.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
        } else {
            statusValue.setText("Disconnected");
            statusValue.setForeground(ColorScheme.PROGRESS_ERROR_COLOR);
        }
        playerValue.setText(snapshot.getPlayerName());
        worldValue.setText(String.valueOf(snapshot.getWorldId()));
        scriptsValue.setText(snapshot.getActiveScripts() == null ? "0"
                : Integer.toString(snapshot.getActiveScripts().size()));

        SwingUtilities.invokeLater(() -> {
            revalidate();
            repaint();
        });
    }
}
