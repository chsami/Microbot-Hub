package net.runelite.client.plugins.microbot.geflipper;

import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import java.awt.*;
import java.lang.reflect.Field;
import java.util.Locale;

public class FlipperOverlay extends OverlayPanel {
    private static final Color POSITIVE_COLOR = new Color(0x52D273);
    private static final Color NEGATIVE_COLOR = new Color(0xFF6666);
    private static final Color TITLE_COLOR = Color.CYAN;

    private final FlipperPlugin plugin;
    private final FlipperConfig config;
    private volatile DisplayStats displayStats = DisplayStats.empty();
    private volatile long statsGeneration;
    private volatile long lastFetchTime;

    @Inject
    public FlipperOverlay(FlipperPlugin plugin, FlipperConfig config) {
        super(plugin);
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    /** Forget financial display data and invalidate any queued Swing read. */
    synchronized void clearStats() {
        statsGeneration++;
        displayStats = DisplayStats.empty();
        lastFetchTime = 0;
        panelComponent.getChildren().clear();
    }

    synchronized boolean publishStats(long generation, DisplayStats stats) {
        if (generation != statsGeneration) return false;
        displayStats = stats;
        return true;
    }

    private void updateCopilotStats() {
        long now = System.currentTimeMillis();
        if (now - lastFetchTime < 1000) return;
        lastFetchTime = now;
        // Borrow the existing panel for one read; never retain Copilot's account/trade models.
        Plugin copilot = Microbot.getPluginManager().getPlugins().stream()
            .filter(p -> p.getClass().getSimpleName().equalsIgnoreCase("FlippingCopilotPlugin")
                || p.getClass().getSimpleName().equalsIgnoreCase("FlipAssistPlugin"))
            .findFirst().orElse(null);
        if (copilot == null) {
            displayStats = DisplayStats.empty();
            return;
        }
        try {
            Field panelField = copilot.getClass().getDeclaredField("statsPanel");
            panelField.setAccessible(true);
            Object panel = panelField.get(copilot);
            long generation = statsGeneration;
            SwingUtilities.invokeLater(() -> {
                if (generation != statsGeneration || !config.showOverlay() || !Microbot.isLoggedIn()) return;
                publishStats(generation, DisplayStats.read(panel));
            });
        } catch (ReflectiveOperationException unavailable) {
            displayStats = DisplayStats.empty();
        }
    }

    static final class DisplayStats {
        final String overall;
        final String hourly;
        final String time;

        DisplayStats(String overall, String hourly, String time) {
            this.overall = overall;
            this.hourly = hourly;
            this.time = time;
        }

        static DisplayStats empty() { return new DisplayStats("-", "-", null); }

        /** Read only the existing panel's formatted labels and selected scope, on EDT. */
        static DisplayStats read(Object panel) {
            if (panel == null) return empty();
            return new DisplayStats(label(panel, "totalProfitVal"),
                label(panel, "hourlyProfitVal"), label(panel, "sessionTimeVal"));
        }

        private static String label(Object panel, String name) {
            try {
                Field field = panel.getClass().getDeclaredField(name);
                field.setAccessible(true);
                Object value = field.get(panel);
                if (!(value instanceof JLabel)) return "-";
                String text = ((JLabel) value).getText();
                return text == null || text.trim().isEmpty() ? "-" : text;
            } catch (ReflectiveOperationException unavailable) {
                return "-";
            }
        }
    }

    public static String formatProfit(long amount) {
        String sign = amount > 0 ? "+" : (amount < 0 ? "-" : "");
        long abs = Math.abs(amount);
        if (abs >= 1_000_000_000L) {
            return sign + String.format(Locale.ENGLISH, "%.2fB gp", abs / 1_000_000_000.0);
        } else if (abs >= 1_000_000L) {
            return sign + String.format(Locale.ENGLISH, "%.2fM gp", abs / 1_000_000.0);
        } else if (abs >= 10_000L) {
            return sign + String.format(Locale.ENGLISH, "%.1fK gp", abs / 1_000.0);
        } else {
            return sign + String.format(Locale.ENGLISH, "%,d gp", abs);
        }
    }

    public static String formatGpHr(long amount) {
        String sign = amount > 0 ? "+" : (amount < 0 ? "-" : "");
        long abs = Math.abs(amount);
        if (abs >= 1_000_000_000L) {
            return sign + String.format(Locale.ENGLISH, "%.2fB gp/hr", abs / 1_000_000_000.0);
        } else if (abs >= 1_000_000L) {
            return sign + String.format(Locale.ENGLISH, "%.2fM gp/hr", abs / 1_000_000.0);
        } else if (abs >= 10_000L) {
            return sign + String.format(Locale.ENGLISH, "%.1fK gp/hr", abs / 1_000.0);
        } else {
            return sign + String.format(Locale.ENGLISH, "%,d gp/hr", abs);
        }
    }

    private static Color getColorForText(String text) {
        if ("-".equals(text)) return Color.LIGHT_GRAY;
        if (text.startsWith("+") || (!text.startsWith("-") && !text.startsWith("0"))) {
            return POSITIVE_COLOR;
        } else if (text.startsWith("-")) {
            return NEGATIVE_COLOR;
        }
        return Color.WHITE;
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (config != null && !config.showOverlay() || !Microbot.isLoggedIn()) {
            clearStats();
            return null;
        }
        updateCopilotStats();
        DisplayStats stats = displayStats;
        panelComponent.getChildren().clear();
        panelComponent.setPreferredSize(new Dimension(200, 0));
        panelComponent.getChildren().add(TitleComponent.builder()
            .text("Microbot Flipper v" + FlipperPlugin.version).color(TITLE_COLOR).build());
        panelComponent.getChildren().add(LineComponent.builder().left("GP/hr:")
            .right(stats.hourly).rightColor(getColorForText(stats.hourly)).build());
        panelComponent.getChildren().add(LineComponent.builder().left("Copilot Profit:")
            .right(stats.overall).rightColor(getColorForText(stats.overall)).build());
        if (stats.time != null && !"-".equals(stats.time)) {
            panelComponent.getChildren().add(LineComponent.builder().left("Session Time:")
                .right(stats.time).rightColor(Color.LIGHT_GRAY).build());
        }
        String slotStatus = plugin.getFlipperScript() == null ? "" : plugin.getFlipperScript().getSlotActionStatus();
        if (!slotStatus.isEmpty()) {
            panelComponent.getChildren().add(LineComponent.builder()
                .left(slotStatus).leftColor(Color.ORANGE).build());
        }
        return super.render(graphics);
    }
}
