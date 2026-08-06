package net.runelite.client.plugins.microbot.microbotdashboardplus.window;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.microbotdashboardplus.MicrobotDashboardPlusConfig;
import net.runelite.client.plugins.microbot.microbotdashboardplus.data.PollSnapshot;
import net.runelite.client.plugins.microbot.microbotdashboardplus.panels.AntibanStatePanel;
import net.runelite.client.plugins.microbot.microbotdashboardplus.panels.DashboardSection;
import net.runelite.client.plugins.microbot.microbotdashboardplus.panels.EventLogPanel;
import net.runelite.client.plugins.microbot.microbotdashboardplus.panels.GuidePanel;
import net.runelite.client.plugins.microbot.microbotdashboardplus.panels.InventoryPanel;
import net.runelite.client.plugins.microbot.microbotdashboardplus.panels.NearbyNpcsPanel;
import net.runelite.client.plugins.microbot.microbotdashboardplus.panels.PlayerPanel;
import net.runelite.client.plugins.microbot.microbotdashboardplus.panels.ScriptsPanel;
import net.runelite.client.plugins.microbot.microbotdashboardplus.panels.SkillsPanel;
import net.runelite.client.plugins.microbot.microbotdashboardplus.panels.XpChartPanel;
import net.runelite.client.plugins.microbot.microbotdashboardplus.poller.GameStatePoller;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import net.runelite.client.plugins.microbot.Microbot;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Floating Swing window that hosts all dashboard sections.
 *
 * <p>Mirrors the RuneLite Var Inspector pattern: a top-level JFrame
 * independent of the client window. Lifecycle is managed by the plugin; the
 * window closes via {@link WindowConstants#HIDE_ON_CLOSE} so the sidebar
 * "Open Dashboard" button can re-show it.
 *
 * <p>Layout: 2-column GridBagLayout for the section grid, plus 3 full-width
 * sections (XP Over Time, Event Log, Guide) that span both columns.
 */
@Slf4j
public class DashboardWindow extends JFrame {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter
            .ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final GameStatePoller poller;
    private final MicrobotDashboardPlusConfig config;
    private final Consumer<PollSnapshot> snapshotListener;
    private final List<DashboardSection> sections = new ArrayList<>();

    /** Map of section -> the predicate that decides if it's currently visible. */
    private final java.util.Map<DashboardSection, java.util.function.BooleanSupplier> visibilityPredicates =
            new java.util.LinkedHashMap<>();

    private final JLabel statusLabel = new JLabel("Connecting...");
    private final JLabel lastPollLabel = new JLabel("Last poll: never");

    /** Alert banner: yellow strip at top, hidden by default, shown when threshold crosses. */
    private JPanel alertBanner;
    private JLabel alertBannerText;

    private static final String CONFIG_GROUP = "MicrobotDashboardPlus";
    private static final String K_WIN_X = "windowX";
    private static final String K_WIN_Y = "windowY";
    private static final String K_WIN_W = "windowWidth";
    private static final String K_WIN_H = "windowHeight";

    public DashboardWindow(GameStatePoller poller, MicrobotDashboardPlusConfig config) {
        super("Microbot Dashboard Plus");
        this.poller = poller;
        this.config = config;

        setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        setMinimumSize(new Dimension(900, 600));

        restoreWindowBounds();
        installBoundsPersistenceListener();

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(ColorScheme.DARK_GRAY_COLOR);
        root.setBorder(new EmptyBorder(8, 12, 8, 12));

        // North = header + (hidden) alert banner stacked vertically.
        JPanel northContainer = new JPanel();
        northContainer.setLayout(new BoxLayout(northContainer, BoxLayout.Y_AXIS));
        northContainer.setOpaque(false);
        northContainer.add(buildHeader());
        northContainer.add(buildAlertBanner());
        root.add(northContainer, BorderLayout.NORTH);

        root.add(buildSectionScroll(), BorderLayout.CENTER);
        root.add(buildFooter(), BorderLayout.SOUTH);

        setContentPane(root);

        // Wire poller banner callback once everything's built.
        poller.setBannerCallback(this::showAlertBanner);

        snapshotListener = this::applySnapshot;
        poller.addListener(snapshotListener);
    }

    /** Re-evaluate each section's visibility predicate. Call when config changes. */
    public void applyVisibility() {
        SwingUtilities.invokeLater(() -> {
            for (java.util.Map.Entry<DashboardSection, java.util.function.BooleanSupplier> e
                    : visibilityPredicates.entrySet()) {
                boolean visible = true;
                try { visible = e.getValue().getAsBoolean(); }
                catch (Throwable t) { /* defensive */ }
                e.getKey().setVisible(visible);
            }
            revalidate();
            repaint();
        });
    }

    public void showOrFocus() {
        SwingUtilities.invokeLater(() -> {
            if (!isVisible()) setVisible(true);
            setState(JFrame.NORMAL);
            toFront();
            requestFocus();
        });
    }

    // ---------------------------------------------------------------------
    // Bounds persistence
    // ---------------------------------------------------------------------

    private void restoreWindowBounds() {
        Integer x = readInt(K_WIN_X);
        Integer y = readInt(K_WIN_Y);
        Integer w = readInt(K_WIN_W);
        Integer h = readInt(K_WIN_H);
        if (w == null || h == null || w < 600 || h < 400) {
            // No saved bounds (or sanity-fail) -- use defaults.
            setSize(1100, 800);
            setLocationRelativeTo(null);
            return;
        }
        setSize(w, h);
        if (x != null && y != null && isOnVisibleScreen(x, y, w, h)) {
            setLocation(x, y);
        } else {
            setLocationRelativeTo(null);
        }
    }

    private static boolean isOnVisibleScreen(int x, int y, int w, int h) {
        try {
            java.awt.Rectangle visible = new java.awt.Rectangle();
            for (java.awt.GraphicsDevice gd : java.awt.GraphicsEnvironment
                    .getLocalGraphicsEnvironment().getScreenDevices()) {
                visible = visible.union(gd.getDefaultConfiguration().getBounds());
            }
            // Require at least 100x100 of the saved window to land inside any monitor.
            java.awt.Rectangle saved = new java.awt.Rectangle(x, y, Math.max(100, w), Math.max(100, h));
            return visible.intersects(saved);
        } catch (Throwable t) {
            return false;
        }
    }

    private void installBoundsPersistenceListener() {
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent e) { saveBounds(); }
            @Override
            public void componentResized(ComponentEvent e) { saveBounds(); }
        });
    }

    private void saveBounds() {
        try {
            writeInt(K_WIN_X, getX());
            writeInt(K_WIN_Y, getY());
            writeInt(K_WIN_W, getWidth());
            writeInt(K_WIN_H, getHeight());
        } catch (Throwable t) {
            log.debug("saveBounds failed: {}", t.getMessage());
        }
    }

    private static Integer readInt(String key) {
        try {
            String raw = Microbot.getConfigManager().getConfiguration(CONFIG_GROUP, key);
            return raw == null || raw.isEmpty() ? null : Integer.parseInt(raw);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void writeInt(String key, int value) {
        try {
            Microbot.getConfigManager().setConfiguration(CONFIG_GROUP, key, Integer.toString(value));
        } catch (Throwable ignored) {
            // ConfigManager not yet ready in some lifecycle edge cases; swallow.
        }
    }

    public void disposeWindow() {
        poller.removeListener(snapshotListener);
        for (DashboardSection s : sections) {
            try { s.detach(); } catch (Throwable ignored) { /* best effort */ }
        }
        SwingUtilities.invokeLater(() -> {
            setVisible(false);
            dispose();
        });
    }

    // ---------------------------------------------------------------------
    // Layout
    // ---------------------------------------------------------------------

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        header.setBorder(new EmptyBorder(6, 10, 6, 10));

        JLabel title = new JLabel("Microbot Dashboard Plus");
        title.setForeground(ColorScheme.BRAND_ORANGE);
        title.setFont(FontManager.getRunescapeBoldFont());
        header.add(title, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.setOpaque(false);
        statusLabel.setForeground(ColorScheme.PROGRESS_INPROGRESS_COLOR);
        statusLabel.setFont(FontManager.getRunescapeSmallFont());
        lastPollLabel.setForeground(Color.LIGHT_GRAY);
        lastPollLabel.setFont(FontManager.getRunescapeSmallFont());
        right.add(statusLabel);
        right.add(lastPollLabel);
        header.add(right, BorderLayout.EAST);

        return header;
    }

    private JScrollPane buildSectionScroll() {
        JPanel sectionGrid = new JPanel(new GridBagLayout());
        sectionGrid.setBackground(ColorScheme.DARK_GRAY_COLOR);
        sectionGrid.setBorder(new EmptyBorder(8, 0, 8, 0));

        // Build real panels.
        PlayerPanel player = new PlayerPanel(poller);
        ScriptsPanel scripts = new ScriptsPanel(poller);
        InventoryPanel inventory = new InventoryPanel(poller);
        SkillsPanel skills = new SkillsPanel(poller);
        NearbyNpcsPanel npcs = new NearbyNpcsPanel(poller);
        AntibanStatePanel antiban = new AntibanStatePanel(poller);
        EventLogPanel eventLog = new EventLogPanel(poller);
        XpChartPanel xpChart = new XpChartPanel(poller);
        GuidePanel guide = new GuidePanel(poller);

        sections.add(player);
        sections.add(scripts);
        sections.add(inventory);
        sections.add(skills);
        sections.add(npcs);
        sections.add(antiban);
        sections.add(xpChart);
        sections.add(eventLog);
        sections.add(guide);

        // Wire each section to its config-driven visibility predicate.
        visibilityPredicates.put(player, config::showPlayer);
        visibilityPredicates.put(scripts, config::showActiveScripts);
        visibilityPredicates.put(inventory, config::showInventory);
        visibilityPredicates.put(skills, config::showSkills);
        visibilityPredicates.put(npcs, config::showNearbyNpcs);
        visibilityPredicates.put(antiban, config::showAntibanState);
        visibilityPredicates.put(xpChart, config::showXpChart);
        visibilityPredicates.put(eventLog, config::showEventLog);
        visibilityPredicates.put(guide, config::showGuide);

        applyVisibility();

        // 2-column grid with 3 full-width spans.
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.insets = new Insets(4, 4, 4, 4);
        c.weightx = 1.0;
        c.weighty = 0;

        addSection(sectionGrid, player, c, 0, 0, 1);
        addSection(sectionGrid, scripts, c, 1, 0, 1);

        addSection(sectionGrid, inventory, c, 0, 1, 1);
        addSection(sectionGrid, skills, c, 1, 1, 1);

        addSection(sectionGrid, npcs, c, 0, 2, 1);
        addSection(sectionGrid, antiban, c, 1, 2, 1);

        addSection(sectionGrid, xpChart, c, 0, 3, 2);
        addSection(sectionGrid, eventLog, c, 0, 4, 2);
        addSection(sectionGrid, guide, c, 0, 5, 2);

        // Push everything to the top.
        c.gridx = 0;
        c.gridy = 6;
        c.gridwidth = 2;
        c.weighty = 1.0;
        JPanel filler = new JPanel();
        filler.setOpaque(false);
        sectionGrid.add(filler, c);

        JScrollPane scroll = new JScrollPane(sectionGrid,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    private static void addSection(JPanel parent, DashboardSection section, GridBagConstraints c,
                                   int col, int row, int span) {
        c.gridx = col;
        c.gridy = row;
        c.gridwidth = span;
        parent.add(section, c);
    }

    private JPanel buildAlertBanner() {
        alertBanner = new JPanel(new BorderLayout(8, 0));
        alertBanner.setBackground(new Color(0xD4, 0xA0, 0x17)); // RuneLite warning gold
        alertBanner.setBorder(new EmptyBorder(6, 12, 6, 8));

        alertBannerText = new JLabel("");
        alertBannerText.setForeground(new Color(0x1E, 0x1E, 0x1E));
        alertBannerText.setFont(FontManager.getRunescapeBoldFont());
        alertBanner.add(alertBannerText, BorderLayout.CENTER);

        JButton dismiss = new JButton("Dismiss");
        dismiss.setFont(FontManager.getRunescapeSmallFont());
        dismiss.setBackground(new Color(0x66, 0x4D, 0x09));
        dismiss.setForeground(Color.WHITE);
        dismiss.setFocusPainted(false);
        dismiss.setBorderPainted(false);
        dismiss.addActionListener(e -> hideAlertBanner());
        alertBanner.add(dismiss, BorderLayout.EAST);

        alertBanner.setVisible(false);
        // Bound the height so the BoxLayout doesn't stretch it.
        alertBanner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        return alertBanner;
    }

    /** Called from the poller's banner callback (any thread). Switches to EDT internally. */
    public void showAlertBanner(String message) {
        SwingUtilities.invokeLater(() -> {
            if (alertBanner == null || alertBannerText == null) return;
            alertBannerText.setText("ALERT: " + (message == null ? "Threshold reached" : message));
            alertBanner.setVisible(true);
            revalidate();
            repaint();
        });
    }

    public void hideAlertBanner() {
        SwingUtilities.invokeLater(() -> {
            if (alertBanner == null) return;
            alertBanner.setVisible(false);
            revalidate();
            repaint();
        });
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT));
        footer.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        footer.setBorder(new EmptyBorder(4, 10, 4, 10));

        JLabel info = new JLabel("v" + net.runelite.client.plugins.microbot.microbotdashboardplus.MicrobotDashboardPlusPlugin.version);
        info.setForeground(Color.GRAY);
        info.setFont(FontManager.getRunescapeSmallFont());
        footer.add(info);

        return footer;
    }

    // ---------------------------------------------------------------------
    // Listener
    // ---------------------------------------------------------------------

    private void applySnapshot(PollSnapshot snapshot) {
        if (snapshot == null) return;

        if (snapshot.isLoggedIn()) {
            statusLabel.setText("Connected");
            statusLabel.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
        } else {
            statusLabel.setText("Disconnected");
            statusLabel.setForeground(ColorScheme.PROGRESS_ERROR_COLOR);
        }

        lastPollLabel.setText("Last poll: " + TIME_FMT.format(Instant.ofEpochMilli(snapshot.getTimestampMillis())));
    }
}
