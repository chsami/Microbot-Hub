package net.runelite.client.plugins.microbot.geflipper;

import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import javax.swing.JLabel;
import java.awt.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

public class FlipperOverlay extends OverlayPanel {
    private static final Color POSITIVE_COLOR = new Color(0x52D273);
    private static final Color NEGATIVE_COLOR = new Color(0xFF6666);
    private static final Color TITLE_COLOR = Color.CYAN;

    private final FlipperPlugin plugin;
    private final FlipperConfig config;

    private Plugin flippingCopilot;
    private Object flipManager;
    private Object sessionManager;
    private Object statsPanel;

    private String overallProfitStr = "0 gp";
    private Color overallProfitColor = Color.WHITE;
    private String gpHrStr = "0 gp/hr";
    private Color gpHrColor = Color.WHITE;
    private String sessionProfitStr = "0 gp";
    private Color sessionProfitColor = Color.WHITE;
    private String sessionTimeStr = null;

    private long lastFetchTime = 0;

    @Inject
    public FlipperOverlay(FlipperPlugin plugin, FlipperConfig config) {
        super(plugin);
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    private void updateCopilotStats() {
        long now = System.currentTimeMillis();
        if (now - lastFetchTime < 1000) return;
        lastFetchTime = now;

        if (flippingCopilot == null) {
            flippingCopilot = Microbot.getPluginManager()
                .getPlugins()
                .stream()
                .filter(p -> p.getClass().getSimpleName().equalsIgnoreCase("FlippingCopilotPlugin"))
                .findFirst()
                .orElse(null);
        }
        if (flippingCopilot == null) {
            overallProfitStr = "Copilot not found";
            overallProfitColor = Color.GRAY;
            gpHrStr = "-";
            gpHrColor = Color.GRAY;
            return;
        }

        try {
            if (flipManager == null) {
                Field fmField = flippingCopilot.getClass().getDeclaredField("flipManager");
                fmField.setAccessible(true);
                flipManager = fmField.get(flippingCopilot);
            }
            if (sessionManager == null) {
                Field smField = flippingCopilot.getClass().getDeclaredField("sessionManager");
                smField.setAccessible(true);
                sessionManager = smField.get(flippingCopilot);
            }
            if (statsPanel == null) {
                try {
                    Field spField = flippingCopilot.getClass().getDeclaredField("statsPanel");
                    spField.setAccessible(true);
                    statsPanel = spField.get(flippingCopilot);
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}

        long overallProfit = 0;
        long sessionProfit = 0;
        long gpHr = 0;
        boolean gotOverall = false;
        boolean gotSession = false;

        // 1. Query flipManager for exact overall & session profits
        if (flipManager != null) {
            try {
                // calculateStats(0, null) calculates all-time stats across all accounts
                Method calculateStats = flipManager.getClass().getMethod("calculateStats", int.class, Integer.class);
                Object overallStats = calculateStats.invoke(flipManager, 0, null);
                if (overallStats != null) {
                    Field profitField = overallStats.getClass().getField("profit");
                    overallProfit = profitField.getLong(overallStats);
                    gotOverall = true;
                }

                // getIntervalStats() gets current interval / session stats
                Method getIntervalStats = flipManager.getClass().getMethod("getIntervalStats");
                Object intervalStats = getIntervalStats.invoke(flipManager);
                if (intervalStats != null) {
                    Field profitField = intervalStats.getClass().getField("profit");
                    sessionProfit = profitField.getLong(intervalStats);
                    gotSession = true;
                }
            } catch (Exception ignored) {}
        }

        // 2. Query sessionManager for runtime & compute gp/hr
        if (sessionManager != null) {
            try {
                Method getCachedSessionData = sessionManager.getClass().getMethod("getCachedSessionData");
                Object sessionData = getCachedSessionData.invoke(sessionManager);
                if (sessionData != null) {
                    Field durationField = sessionData.getClass().getField("durationMillis");
                    long durationMillis = durationField.getLong(sessionData);
                    if (durationMillis > 0) {
                        double hours = durationMillis / 3600000.0;
                        if (hours > 0) {
                            gpHr = (long) (sessionProfit / hours);
                        }
                        long totalSeconds = durationMillis / 1000;
                        long h = totalSeconds / 3600;
                        long m = (totalSeconds % 3600) / 60;
                        long s = totalSeconds % 60;
                        sessionTimeStr = String.format("%02d:%02d:%02d", h, m, s);
                    }
                }
            } catch (Exception ignored) {}
        }

        // 3. Check statsPanel UI labels if available to supplement
        if (statsPanel != null) {
            try {
                // If hourlyProfitVal label has Copilot's formatted text
                Field hourlyField = statsPanel.getClass().getDeclaredField("hourlyProfitVal");
                hourlyField.setAccessible(true);
                Object hourlyObj = hourlyField.get(statsPanel);
                if (hourlyObj instanceof JLabel) {
                    String text = ((JLabel) hourlyObj).getText();
                    if (text != null && !text.trim().isEmpty() && !text.equals("0 gp/hr") && gpHr == 0) {
                        gpHrStr = text;
                        gpHrColor = getColorForText(text);
                    }
                }

                // If overall wasn't found from flipManager, read totalProfitVal
                if (!gotOverall) {
                    Field totalField = statsPanel.getClass().getDeclaredField("totalProfitVal");
                    totalField.setAccessible(true);
                    Object totalObj = totalField.get(statsPanel);
                    if (totalObj instanceof JLabel) {
                        String text = ((JLabel) totalObj).getText();
                        if (text != null && !text.trim().isEmpty()) {
                            overallProfitStr = text;
                            overallProfitColor = getColorForText(text);
                            gotOverall = true;
                        }
                    }
                }

                // Session time label
                Field timeField = statsPanel.getClass().getDeclaredField("sessionTimeVal");
                timeField.setAccessible(true);
                Object timeObj = timeField.get(statsPanel);
                if (timeObj instanceof JLabel) {
                    String text = ((JLabel) timeObj).getText();
                    if (text != null && !text.trim().isEmpty() && !text.equals("00:00:00")) {
                        sessionTimeStr = text;
                    }
                }
            } catch (Exception ignored) {}
        }

        if (gotOverall) {
            overallProfitStr = formatProfit(overallProfit);
            overallProfitColor = overallProfit > 0 ? POSITIVE_COLOR : (overallProfit < 0 ? NEGATIVE_COLOR : Color.WHITE);
        }
        if (gotSession) {
            sessionProfitStr = formatProfit(sessionProfit);
            sessionProfitColor = sessionProfit > 0 ? POSITIVE_COLOR : (sessionProfit < 0 ? NEGATIVE_COLOR : Color.WHITE);
        }
        if (gpHr != 0 || !gpHrStr.contains("gp/hr")) {
            gpHrStr = formatGpHr(gpHr);
            gpHrColor = gpHr > 0 ? POSITIVE_COLOR : (gpHr < 0 ? NEGATIVE_COLOR : Color.WHITE);
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
        if (text.startsWith("+") || (!text.startsWith("-") && !text.startsWith("0"))) {
            return POSITIVE_COLOR;
        } else if (text.startsWith("-")) {
            return NEGATIVE_COLOR;
        }
        return Color.WHITE;
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (config != null && !config.showOverlay()) return null;
        if (!Microbot.isLoggedIn()) return null;

        updateCopilotStats();

        panelComponent.getChildren().clear();
        panelComponent.setPreferredSize(new Dimension(200, 0));

        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Microbot Flipper v" + FlipperPlugin.version)
                .color(TITLE_COLOR)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("GP/hr:")
                .right(gpHrStr)
                .rightColor(gpHrColor)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Overall Profit:")
                .right(overallProfitStr)
                .rightColor(overallProfitColor)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Session Profit:")
                .right(sessionProfitStr)
                .rightColor(sessionProfitColor)
                .build());

        if (sessionTimeStr != null && !sessionTimeStr.isEmpty()) {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Session Time:")
                    .right(sessionTimeStr)
                    .rightColor(Color.LIGHT_GRAY)
                    .build());
        }

        return super.render(graphics);
    }
}
