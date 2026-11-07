package net.runelite.client.plugins.microbot.geflipper;

import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentConstants;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.ProgressBarComponent;
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
    private static final Color TITLE_COLOR = new Color(0xFFC24B);
    private static final Color ACCENT_COLOR = new Color(0x5FC8E8);
    private static final Color LABEL_COLOR = new Color(0xD8D8D8);
    private static final Color MUTED_COLOR = Color.LIGHT_GRAY;
    private static final Color RULE_COLOR = new Color(255, 255, 255, 46);
    private static final Color BACKGROUND_COLOR = new Color(16, 18, 24, 228);
    private static final Color BAR_BACKGROUND = new Color(0x2B3038);
    private static final Color BAR_FILL = new Color(0x2E8B57);
    private static final Color BAR_FULL = new Color(0xC9A227);

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
        // Drawn above game interfaces, so an open Grand Exchange window never hides it. Overlays never take clicks
        // (outside RuneLite's Alt drag mode), so the slots underneath still work, and Alt+drag moves it.
        setLayer(OverlayLayer.ALWAYS_ON_TOP);
        setNaughty();
        panelComponent.setBackgroundColor(BACKGROUND_COLOR);
        panelComponent.setBorder(new Rectangle(8, 6, 8, 6));
        panelComponent.setGap(new Point(0, 2));
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
        if (Microbot.getPluginManager() == null) {
            displayStats = DisplayStats.empty();
            return;
        }
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

    /** A short unsigned amount for tight rows: "57.3M", "1.25B", "950K", "4,200". */
    static String compact(long amount) {
        long abs = Math.abs(amount);
        String sign = amount < 0 ? "-" : "";
        if (abs >= 1_000_000_000L) return sign + trim(String.format(Locale.ENGLISH, "%.2f", abs / 1_000_000_000.0)) + "B";
        if (abs >= 1_000_000L) return sign + trim(String.format(Locale.ENGLISH, "%.1f", abs / 1_000_000.0)) + "M";
        if (abs >= 10_000L) return sign + trim(String.format(Locale.ENGLISH, "%.0f", abs / 1_000.0)) + "K";
        return sign + String.format(Locale.ENGLISH, "%,d", abs);
    }

    private static String trim(String number) {
        return number.contains(".") ? number.replaceAll("0+$", "").replaceAll("\\.$", "") : number;
    }

    /** "45s", "12m", "3h 05m". */
    static String ago(long millis) {
        long seconds = Math.max(0, millis / 1000);
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + "m";
        return String.format(Locale.ENGLISH, "%dh %02dm", minutes / 60, minutes % 60);
    }

    /** "4:05" for a countdown. */
    static String clock(int seconds) {
        int safe = Math.max(0, seconds);
        return String.format(Locale.ENGLISH, "%d:%02d", safe / 60, safe % 60);
    }

    /** Grand Exchange slot use as "3 buy, 4 sell, 1 done", or null when the offers are unknown. */
    static String slotSummary(GrandExchangeOffer[] offers) {
        if (offers == null || offers.length == 0) return null;
        int buying = 0, selling = 0, done = 0, empty = 0;
        for (GrandExchangeOffer offer : offers) {
            GrandExchangeOfferState state = offer == null ? null : offer.getState();
            if (state == null) return null;
            switch (state) {
                case BUYING: buying++; break;
                case SELLING: selling++; break;
                case EMPTY: empty++; break;
                default: done++;
            }
        }
        if (empty == offers.length) return "All slots free";
        StringBuilder text = new StringBuilder();
        append(text, buying, "buy");
        append(text, selling, "sell");
        append(text, done, "done");
        append(text, empty, "free");
        return text.toString();
    }

    private static void append(StringBuilder text, int count, String word) {
        if (count <= 0) return;
        if (text.length() > 0) text.append(", ");
        text.append(count).append(' ').append(word);
    }

    private static Color getColorForText(String text) {
        if ("-".equals(text)) return MUTED_COLOR;
        if (text.startsWith("+") || (!text.startsWith("-") && !text.startsWith("0"))) {
            return POSITIVE_COLOR;
        } else if (text.startsWith("-")) {
            return NEGATIVE_COLOR;
        }
        return Color.WHITE;
    }

    private static Color connectionColor(MuleConnectionMonitor.Snapshot connection) {
        return MuleOverlayView.color(MuleOverlayView.linkTone(connection));
    }

    static Color activityColor(String activity) {
        if (activity == null) return MUTED_COLOR;
        if (activity.startsWith("Flipping")) return POSITIVE_COLOR;
        if (activity.startsWith("Mul") || activity.startsWith("Freeing")) return ACCENT_COLOR;
        if (activity.startsWith("Finishing")) return Color.ORANGE;
        if (activity.startsWith("Renewing")) return TITLE_COLOR;
        return MUTED_COLOR;
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        FlipperConfig.MuleMode muleMode = config == null ? FlipperConfig.MuleMode.OFF : config.muleMode();
        boolean receiver = muleMode == FlipperConfig.MuleMode.MULE_RECEIVER;
        if (config != null && !config.showOverlay() || !receiver && !Microbot.isLoggedIn()) {
            clearStats();
            return null;
        }
        if (receiver) {
            // Receivers need neither Copilot nor its account/session display data, including while on standby.
            clearStats();
        } else {
            updateCopilotStats();
        }
        FlipperScript script = plugin == null ? null : plugin.getFlipperScript();
        DisplayStats stats = displayStats;
        panelComponent.getChildren().clear();
        panelComponent.setPreferredSize(new Dimension(muleMode == FlipperConfig.MuleMode.OFF ? 210 : 236, 0));
        panelComponent.getChildren().add(TitleComponent.builder()
            .text("GE Flipper  v" + FlipperPlugin.version).color(TITLE_COLOR).build());
        if (receiver) {
            panelComponent.getChildren().add(new Section("MULE RECEIVER"));
            // Same rows as the login-screen window, so both always show the same numbers.
            MuleConnectionMonitor.Snapshot connection = script == null ? null : script.getMuleConnectionStatus();
            MuleLedger.Snapshot ledger = script == null ? null : script.getMuleLedgerSnapshot();
            String receiverStatus = script == null ? "" : script.getSlotActionStatus();
            for (MuleOverlayView.Row row : MuleOverlayView.receiverRows(connection, ledger,
                Microbot.isLoggedIn(), receiverStatus)) {
                LineComponent.LineComponentBuilder line = LineComponent.builder().left(row.label);
                if (row.value.isEmpty()) line.leftColor(MuleOverlayView.color(row.tone));
                else line.leftColor(LABEL_COLOR).right(row.value).rightColor(MuleOverlayView.color(row.tone));
                panelComponent.getChildren().add(line.build());
            }
            return super.render(graphics);
        }

        Client client = Microbot.getClient();
        String activity = script == null ? "Flipping" : script.getActivity();
        panelComponent.getChildren().add(row("Status:", activity, activityColor(activity)));
        int world = client == null ? 0 : client.getWorld();
        if (world > 0) panelComponent.getChildren().add(row("World:", String.valueOf(world), LABEL_COLOR));
        int updateSeconds = script == null ? -1 : script.getSystemUpdateSeconds();
        if (updateSeconds >= 0) {
            boolean muleFirst = muleMode == FlipperConfig.MuleMode.FLIPPER_SENDER && script.isMuleBeforeUpdatePlanned();
            panelComponent.getChildren().add(row("OSRS update in:",
                clock(updateSeconds) + (muleFirst ? ", mule first" : ""), NEGATIVE_COLOR));
        }

        panelComponent.getChildren().add(new Section("PROFIT"));
        panelComponent.getChildren().add(row("GP/hr:", stats.hourly, getColorForText(stats.hourly)));
        panelComponent.getChildren().add(row("Copilot Profit:", stats.overall, getColorForText(stats.overall)));
        if (stats.time != null && !"-".equals(stats.time)) {
            panelComponent.getChildren().add(row("Session Time:", stats.time, MUTED_COLOR));
        }
        String slots = slotSummary(client == null ? null : client.getGrandExchangeOffers());
        if (slots != null) panelComponent.getChildren().add(row("GE slots:", slots, LABEL_COLOR));

        if (muleMode != FlipperConfig.MuleMode.OFF && script != null) addMuleRows(script);

        String slotStatus = script == null ? "" : script.getSlotActionStatus();
        if (!slotStatus.isEmpty()) {
            panelComponent.getChildren().add(new Section(null));
            panelComponent.getChildren().add(LineComponent.builder()
                .left(slotStatus).leftColor(Color.ORANGE).build());
        }
        return super.render(graphics);
    }

    private void addMuleRows(FlipperScript script) {
        panelComponent.getChildren().add(new Section("MULE"));
        MuleConnectionMonitor.Snapshot connection = script.getMuleConnectionStatus();
        panelComponent.getChildren().add(row("Mule link:",
            connection == null ? "Checking" : connection.label(), connectionColor(connection)));

        long capital = script.getMuleCapitalGp();
        int keep = script.getMuleKeepGp();
        int trigger = script.getMuleTriggerGp();
        if (capital >= 0 && trigger > 0) {
            ProgressBarComponent bar = new ProgressBarComponent();
            bar.setMinimum(0);
            bar.setMaximum(trigger);
            bar.setValue(Math.min(capital, trigger));
            bar.setLabelDisplayMode(ProgressBarComponent.LabelDisplayMode.TEXT_ONLY);
            bar.setCenterLabel(compact(capital) + " / " + compact(trigger)
                + (capital >= trigger ? "  (mule due)" : ""));
            bar.setBackgroundColor(BAR_BACKGROUND);
            bar.setForegroundColor(capital >= trigger ? BAR_FULL : BAR_FILL);
            bar.setFontColor(Color.WHITE);
            panelComponent.getChildren().add(bar);
        }
        if (keep > 0) {
            String rule = trigger > keep ? "keep " + compact(keep) + ", at " + compact(trigger)
                : trigger < 0 ? "keep " + compact(keep) + ", check trigger" : "keep " + compact(keep);
            panelComponent.getChildren().add(row("Mule rule:", rule, trigger < 0 ? NEGATIVE_COLOR : LABEL_COLOR));
        }
        long muled = script.getTotalMuledGp();
        panelComponent.getChildren().add(row("Mule Sent:", formatProfit(muled),
            muled > 0 ? POSITIVE_COLOR : MUTED_COLOR));
        long lastAge = script.getLastMuleAgeMs();
        if (lastAge >= 0) {
            panelComponent.getChildren().add(row("Last mule:",
                compact(script.getLastMuleGp()) + ", " + ago(lastAge) + " ago", MUTED_COLOR));
        }
        String capitalStatus = script.getMuleCapitalStatus();
        if (capitalStatus != null && !capitalStatus.isEmpty()) {
            LineComponent.LineComponentBuilder line = LineComponent.builder().left(capitalStatus).leftColor(MUTED_COLOR);
            if (capital >= 0) line.right(compact(capital)).rightColor(MUTED_COLOR);
            panelComponent.getChildren().add(line.build());
        }
    }

    private static LineComponent row(String label, String value, Color valueColor) {
        return LineComponent.builder().left(label).leftColor(LABEL_COLOR).right(value).rightColor(valueColor).build();
    }

    /** A small section heading with a thin rule after it; a null title draws only the rule. */
    static final class Section implements LayoutableRenderableEntity {
        private final String title;
        private final Rectangle bounds = new Rectangle();
        private Point location = new Point();
        private Dimension size = new Dimension(ComponentConstants.STANDARD_WIDTH, 0);

        Section(String title) {
            this.title = title;
        }

        @Override
        public Dimension render(Graphics2D graphics) {
            FontMetrics metrics = graphics.getFontMetrics();
            int width = Math.max(0, size.width);
            int height = title == null ? 5 : metrics.getHeight() + 3;
            int middle = location.y + height / 2 + (title == null ? 0 : 1);
            int ruleStart = location.x;
            if (title != null) {
                int baseline = location.y + metrics.getAscent() + 2;
                graphics.setColor(Color.BLACK);
                graphics.drawString(title, location.x + 1, baseline + 1);
                graphics.setColor(ACCENT_COLOR);
                graphics.drawString(title, location.x, baseline);
                ruleStart = location.x + metrics.stringWidth(title) + 5;
            }
            if (ruleStart < location.x + width) {
                graphics.setColor(RULE_COLOR);
                graphics.drawLine(ruleStart, middle, location.x + width - 1, middle);
            }
            Dimension dimension = new Dimension(width, height);
            bounds.setLocation(location);
            bounds.setSize(dimension);
            return dimension;
        }

        @Override
        public Rectangle getBounds() {
            return bounds;
        }

        @Override
        public void setPreferredLocation(Point position) {
            location = position == null ? new Point() : position;
        }

        @Override
        public void setPreferredSize(Dimension preferred) {
            size = preferred == null ? new Dimension(ComponentConstants.STANDARD_WIDTH, 0) : preferred;
        }
    }
}
