package net.runelite.client.plugins.microbot.microbotdashboardplus;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.microbotdashboardplus.notify.AlertManager;
import net.runelite.client.plugins.microbot.microbotdashboardplus.notify.DiscordNotifier;
import net.runelite.client.plugins.microbot.microbotdashboardplus.poller.GameStatePoller;
import net.runelite.client.plugins.microbot.microbotdashboardplus.window.DashboardWindow;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

import javax.inject.Inject;
import javax.swing.SwingUtilities;
import java.awt.AWTException;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * MicrobotDashboardPlus is part of the Microbot Plus suite.
 *
 * <p>The dashboard opens in a native floating RuneLite window. A compact
 * sidebar panel lives in the right-hand toolbar; clicking
 * <strong>Open Dashboard</strong> launches the full {@link DashboardWindow}.
 * Game state is read in-process via {@link Microbot#getClient()} and the Rs2
 * utility APIs, so there is no embedded HTTP server and no Agent Server
 * dependency.
 *
 * <h2>Known limitations</h2>
 * <ul>
 *     <li>Active scripts list is heuristic (enumerates enabled plugins).</li>
 *     <li>The blocking-event "running" flag has no public getter on the client,
 *         so the Antiban State panel reads it by reflection and omits it when
 *         that is not available on the running client version.</li>
 * </ul>
 */
@PluginDescriptor(
        name = "<html>[<font color=#BB86FC>P</font>] " + "Microbot Dashboard Plus",
        description = "Native Swing monitoring dashboard for your Microbot session. Floating window plus a compact sidebar panel. No HTTP, no Agent Server dependency.",
        tags = {"dashboard", "monitoring", "microbot", "plus"},
        authors = {"pjmarz"},
        version = MicrobotDashboardPlusPlugin.version,
        minClientVersion = "2.0.13",
        cardUrl = "https://chsami.github.io/Microbot-Hub/MicrobotDashboardPlusPlugin/assets/card.png",
        iconUrl = "https://chsami.github.io/Microbot-Hub/MicrobotDashboardPlusPlugin/assets/icon.png",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class MicrobotDashboardPlusPlugin extends Plugin {

    public static final String version = "1.2.0";

    @Inject
    private MicrobotDashboardPlusConfig config;

    @Inject
    private ClientToolbar clientToolbar;

    private GameStatePoller poller;
    private DashboardPanel sidebarPanel;
    private NavigationButton navButton;
    private DashboardWindow window;
    private DiscordNotifier notifier;
    private AlertManager alertManager;

    @Provides
    MicrobotDashboardPlusConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(MicrobotDashboardPlusConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {
        // 0. Notification stack.
        notifier = new DiscordNotifier();
        notifier.setWebhookUrl(config.discordWebhookUrl());
        notifier.start();
        alertManager = new AlertManager();
        alertManager.setThresholdsFromConfig(config.alertThresholds());

        // 1. Build the poller. Single-thread executor; starts immediately.
        poller = new GameStatePoller();
        poller.setNpcMaxDistance(config.npcMaxDistance());
        poller.setNotifier(notifier);
        poller.setAlertManager(alertManager);
        poller.setNotificationToggles(
                config.notifyLevelUp(),
                config.notifyAlerts());
        poller.start(config.pollIntervalSeconds());

        if (config.notifySessionLifecycle()) {
            notifier.send("Dashboard session started.");
        }

        // 2. Build the floating window (hidden until shown).
        window = new DashboardWindow(poller, config);

        // 3. Build the sidebar panel + register it.
        sidebarPanel = new DashboardPanel(poller, this::showWindow);
        navButton = NavigationButton.builder()
                .tooltip("Microbot Dashboard Plus")
                .icon(buildPlaceholderIcon())
                .priority(7)
                .panel(sidebarPanel)
                .build();
        clientToolbar.addNavigation(navButton);

        // 4. Optional auto-open on enable.
        if (config.autoOpenDashboard()) {
            showWindow();
        }

        Microbot.log("MicrobotDashboardPlus v" + version + " started (native Swing mode)");
    }

    @Override
    protected void shutDown() {
        if (notifier != null && config.notifySessionLifecycle()) {
            notifier.send("Dashboard session stopped.");
        }
        if (window != null) {
            window.disposeWindow();
            window = null;
        }
        if (sidebarPanel != null) {
            sidebarPanel.detach();
            sidebarPanel = null;
        }
        if (navButton != null) {
            clientToolbar.removeNavigation(navButton);
            navButton = null;
        }
        if (poller != null) {
            poller.stop();
            poller = null;
        }
        if (notifier != null) {
            notifier.shutdown();
            notifier = null;
        }
        alertManager = null;
        Microbot.log("MicrobotDashboardPlus v" + version + " stopped");
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!"MicrobotDashboardPlus".equals(event.getGroup())) return;
        String key = event.getKey();

        if (poller != null) {
            switch (key) {
                case "pollIntervalSeconds":
                    poller.stop();
                    poller.start(config.pollIntervalSeconds());
                    break;
                case "npcMaxDistance":
                    poller.setNpcMaxDistance(config.npcMaxDistance());
                    poller.refreshNow();
                    break;
                case "alertThresholds":
                    poller.setAlertThresholds(config.alertThresholds());
                    break;
                case "discordWebhookUrl":
                    if (notifier != null) notifier.setWebhookUrl(config.discordWebhookUrl());
                    break;
                case "notifyLevelUp":
                case "notifyAlerts":
                    poller.setNotificationToggles(
                            config.notifyLevelUp(),
                            config.notifyAlerts());
                    break;
                default:
                    break;
            }
        }

        // Section visibility: any show* key triggers a re-evaluation.
        if (window != null && key != null && key.startsWith("show")) {
            window.applyVisibility();
        }
    }

    private void showWindow() {
        if (window == null) return;
        SwingUtilities.invokeLater(window::showOrFocus);
    }

    /**
     * Programmatic 16x16 dashboard icon: a dark background with a small
     * "rising chart line" in RuneLite green to evoke the dashboard.
     */
    private static BufferedImage buildPlaceholderIcon() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_ANTIALIAS_ON);

            // Dark rounded background.
            g.setColor(new Color(0x2B, 0x2B, 0x2B));
            g.fillRoundRect(0, 0, 16, 16, 4, 4);

            // Subtle border.
            g.setColor(new Color(0x44, 0x44, 0x44));
            g.drawRoundRect(0, 0, 15, 15, 4, 4);

            // Rising chart line in RuneLite green.
            g.setColor(new Color(0x00, 0xAA, 0x00));
            g.setStroke(new java.awt.BasicStroke(1.5f));
            // Polyline: low-left up to high-right.
            int[] xs = {3, 6,  8, 11, 13};
            int[] ys = {12, 9, 10,  5,  4};
            g.drawPolyline(xs, ys, xs.length);

            // Small dot at the right endpoint for emphasis.
            g.fillOval(12, 3, 3, 3);
        } finally {
            g.dispose();
        }
        return img;
    }
}
