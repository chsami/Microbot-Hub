package net.runelite.client.plugins.microbot.geflipper;

import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
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
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
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
    private static final int ROW_GAP = 2;
    private static final Rectangle PANEL_BORDER = new Rectangle(8, 6, 8, 6);

    /**
     * Inventories the panel can sit over, most specific first: the Grand Exchange, bank and trade screens show their
     * own inventory in place of the normal one.
     */
    private static final int[] INVENTORIES = {InterfaceID.GeOffersSide.ITEMS, InterfaceID.Bankside.ITEMS,
        InterfaceID.Tradeside.SIDE_LAYER, InterfaceID.Inventory.ITEMS};
    private static final int INVENTORY_ROWS = 7;
    private static final int INVENTORY_COLUMNS = 4;
    /** How much wider and taller than the item grid its side-panel tab may be and still be used. */
    private static final int MAX_TAB_MARGIN = 60;
    private static final int MIN_SPOT_WIDTH = 120;
    private static final int MIN_SPOT_HEIGHT = 60;
    /** Rows that may be left out when the panel doesn't fit over the inventory; the highest level goes first. */
    private static final int ALWAYS = 0, DROP_LAST = 1, DROP_SECOND = 2, DROP_FIRST = 3;
    /** Measures rows before they are drawn over the inventory. Client thread only, like rendering. */
    private static final BufferedImage MEASURE = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);

    private final FlipperPlugin plugin;
    private final FlipperConfig config;
    /** One entry per panel row: ALWAYS, or the DROP_* level at which it is left out when space is short. */
    private final List<Integer> dropLevels = new ArrayList<>();
    private volatile DisplayStats displayStats = DisplayStats.empty();
    private volatile long statsGeneration;
    private volatile long lastFetchTime;

    @Inject
    public FlipperOverlay(FlipperPlugin plugin, FlipperConfig config) {
        super(plugin);
        this.plugin = plugin;
        this.config = config;
        // Sits over the inventory below its top row (see inventorySpot), or top left while no inventory is showing.
        setPosition(OverlayPosition.TOP_LEFT);
        // Drawn above game interfaces, so neither the inventory nor an open Grand Exchange window hides it. Overlays
        // never take clicks (outside RuneLite's Alt drag mode), so the slots underneath still work. Alt+drag moves
        // it anywhere and Alt+right-click puts it back.
        setLayer(OverlayLayer.ALWAYS_ON_TOP);
        setNaughty();
        panelComponent.setBackgroundColor(BACKGROUND_COLOR);
        panelComponent.setBorder(new Rectangle(PANEL_BORDER));
        panelComponent.setGap(new Point(0, ROW_GAP));
    }

    /** Forget financial display data and invalidate any queued Swing read. */
    synchronized void clearStats() {
        statsGeneration++;
        displayStats = DisplayStats.empty();
        lastFetchTime = 0;
        clearRows();
    }

    private void clearRows() {
        panelComponent.getChildren().clear();
        dropLevels.clear();
    }

    private void add(LayoutableRenderableEntity row) {
        add(row, ALWAYS);
    }

    private void add(LayoutableRenderableEntity row, int dropLevel) {
        panelComponent.getChildren().add(row);
        dropLevels.add(dropLevel);
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
        Client client = Microbot.getClient();
        Rectangle spot = inventorySpot(client);
        // Skip the frame the placement changes: RuneLite laid this one out for the old placement.
        if (!usePlacement(spot == null ? OverlayPosition.TOP_LEFT : OverlayPosition.DETACHED)) return null;
        // Over the inventory the panel would hide a right-click menu opened there, so step aside while it's open.
        if (spot != null && menuCovers(client, spot)) return null;
        if (receiver) {
            // Receivers need neither Copilot nor its account/session display data, including while on standby.
            clearStats();
        } else {
            updateCopilotStats();
        }
        FlipperScript script = plugin == null ? null : plugin.getFlipperScript();
        DisplayStats stats = displayStats;
        clearRows();
        int width = muleMode == FlipperConfig.MuleMode.OFF ? 210 : 236;
        add(TitleComponent.builder().text("GE Flipper  v" + FlipperPlugin.version).color(TITLE_COLOR).build());
        if (receiver) {
            add(new Section("MULE RECEIVER"), DROP_LAST);
            // Same rows as the login-screen window, so both always show the same numbers.
            MuleConnectionMonitor.Snapshot connection = script == null ? null : script.getMuleConnectionStatus();
            MuleLedger.Snapshot ledger = script == null ? null : script.getMuleLedgerSnapshot();
            String receiverStatus = script == null ? "" : script.getSlotActionStatus();
            for (MuleOverlayView.Row row : MuleOverlayView.receiverRows(connection, ledger,
                Microbot.isLoggedIn(), receiverStatus)) {
                LineComponent.LineComponentBuilder line = LineComponent.builder().left(row.label);
                if (row.value.isEmpty()) line.leftColor(MuleOverlayView.color(row.tone));
                else line.leftColor(LABEL_COLOR).right(row.value).rightColor(MuleOverlayView.color(row.tone));
                add(line.build());
            }
            return draw(graphics, spot, width);
        }

        String activity = script == null ? "Flipping" : script.getActivity();
        add(row("Status:", activity, activityColor(activity)));
        int world = client == null ? 0 : client.getWorld();
        if (world > 0) add(row("World:", String.valueOf(world), LABEL_COLOR), DROP_FIRST);
        int updateSeconds = script == null ? -1 : script.getSystemUpdateSeconds();
        if (updateSeconds >= 0) {
            boolean muleFirst = muleMode == FlipperConfig.MuleMode.FLIPPER_SENDER && script.isMuleBeforeUpdatePlanned();
            add(row("OSRS update in:", clock(updateSeconds) + (muleFirst ? ", mule first" : ""), NEGATIVE_COLOR));
        }

        add(new Section("PROFIT"), DROP_LAST);
        add(row("GP/hr:", stats.hourly, getColorForText(stats.hourly)));
        add(row("Copilot Profit:", stats.overall, getColorForText(stats.overall)));
        if (stats.time != null && !"-".equals(stats.time)) {
            add(row("Session Time:", stats.time, MUTED_COLOR), DROP_FIRST);
        }
        String slots = slotSummary(client == null ? null : client.getGrandExchangeOffers());
        if (slots != null) add(row("GE slots:", slots, LABEL_COLOR), DROP_LAST);

        if (muleMode != FlipperConfig.MuleMode.OFF && script != null) addMuleRows(script);

        String slotStatus = script == null ? "" : script.getSlotActionStatus();
        if (!slotStatus.isEmpty()) {
            add(new Section(null), DROP_LAST);
            add(LineComponent.builder().left(slotStatus).leftColor(Color.ORANGE).build());
        }
        return draw(graphics, spot, width);
    }

    /**
     * Where the panel sits this frame: over the inventory below its top row, so the coins there stay in view. Null
     * puts it in the normal top-left corner: while no inventory is showing, and for good once the player has moved
     * the panel themselves (Alt+drag, which RuneLite remembers). Alt+right-click clears that and brings it back here.
     * Client thread only (render), as it reads widgets.
     */
    private Rectangle inventorySpot(Client client) {
        if (client == null || getPreferredLocation() != null || getPreferredPosition() != null) return null;
        for (int id : INVENTORIES) {
            Widget items = client.getWidget(id);
            if (items == null || items.isHidden()) continue;
            Widget tab = items.getParent();
            Rectangle spot = belowTopRow(items.getBounds(), topRowBottom(items), tab == null ? null : tab.getBounds());
            if (spot != null) return spot;
        }
        return null;
    }

    /** The bottom edge of the first row of item slots, or -1 when the slots can't be read. */
    static int topRowBottom(Widget items) {
        Widget[] slots = items.getDynamicChildren();
        if (slots == null) return -1;
        int bottom = -1;
        for (int i = 0; i < Math.min(INVENTORY_COLUMNS, slots.length); i++) {
            Rectangle slot = slots[i] == null ? null : slots[i].getBounds();
            if (slot != null && slot.height > 0) bottom = Math.max(bottom, slot.y + slot.height);
        }
        return bottom;
    }

    /**
     * The part of an inventory below its top row, widened to the inventory's side-panel tab when that tab is known
     * and only a little bigger than the item grid. Null when the inventory isn't laid out or the space is too small.
     */
    static Rectangle belowTopRow(Rectangle items, int topRowBottom, Rectangle tab) {
        if (items == null || items.width <= 0 || items.height <= 0) return null;
        boolean rowKnown = topRowBottom > items.y && topRowBottom <= items.y + items.height / 2;
        int top = (rowKnown ? topRowBottom : items.y + items.height / INVENTORY_ROWS) + ROW_GAP;
        int left = items.x;
        int right = items.x + items.width;
        int bottom = items.y + items.height;
        if (tab != null && tab.contains(items) && tab.width - items.width <= MAX_TAB_MARGIN
            && tab.height - items.height <= MAX_TAB_MARGIN) {
            left = tab.x + ROW_GAP;
            right = tab.x + tab.width - ROW_GAP;
            bottom = tab.y + tab.height - ROW_GAP;
        }
        if (right - left < MIN_SPOT_WIDTH || bottom - top < MIN_SPOT_HEIGHT) return null;
        return new Rectangle(left, top, right - left, bottom - top);
    }

    /** Moves between the inventory spot (detached) and the corner; false on the frame it moves. */
    private boolean usePlacement(OverlayPosition wanted) {
        if (getPosition() == wanted) return true;
        setPosition(wanted);
        return false;
    }

    private static boolean menuCovers(Client client, Rectangle spot) {
        return client.isMenuOpen()
            && spot.intersects(client.getMenuX(), client.getMenuY(), client.getMenuWidth(), client.getMenuHeight());
    }

    /** Draws the rows: as a normal panel, or fitted into the inventory spot. */
    private Dimension draw(Graphics2D graphics, Rectangle spot, int width) {
        if (spot == null) {
            panelComponent.setPreferredSize(new Dimension(width, 0));
            return super.render(graphics);
        }
        // RuneLite drew this frame where the panel last was; move to the spot now. The bounds keep the new place for
        // the next frame, and for Alt+drag, which picks the panel up where it is drawn.
        Rectangle bounds = getBounds();
        graphics.translate(spot.x - bounds.x, spot.y - bounds.y);
        bounds.setLocation(spot.x, spot.y);
        panelComponent.setPreferredSize(new Dimension(spot.width, 0));
        try {
            fitHeight(graphics, spot.height);
            return panelComponent.render(graphics);
        } finally {
            if (isClearChildren()) clearRows();
        }
    }

    /**
     * Leaves out the least important rows until the panel fits the space over the inventory. A panel sizes itself
     * from the rows it laid out last time, so this lays the final rows out once more before they are drawn.
     */
    private void fitHeight(Graphics2D graphics, int maxHeight) {
        List<LayoutableRenderableEntity> rows = panelComponent.getChildren();
        if (rows.isEmpty() || rows.size() != dropLevels.size()) return;
        layOut(graphics);
        int height = PANEL_BORDER.y + PANEL_BORDER.height - ROW_GAP;
        for (LayoutableRenderableEntity row : rows) height += row.getBounds().height + ROW_GAP;
        boolean dropped = false;
        for (int level = DROP_FIRST; level > ALWAYS && height > maxHeight; level--) {
            for (int i = rows.size() - 1; i >= 0 && height > maxHeight; i--) {
                if (dropLevels.get(i) != level) continue;
                height -= rows.get(i).getBounds().height + ROW_GAP;
                rows.remove(i);
                dropLevels.remove(i);
                dropped = true;
            }
        }
        if (dropped) layOut(graphics);
    }

    /** Lays the rows out without drawing them on screen. */
    private void layOut(Graphics2D graphics) {
        Graphics2D measure = MEASURE.createGraphics();
        try {
            measure.setFont(graphics.getFont());
            measure.setRenderingHints(graphics.getRenderingHints());
            panelComponent.render(measure);
        } finally {
            measure.dispose();
        }
    }

    private void addMuleRows(FlipperScript script) {
        add(new Section("MULE"), DROP_LAST);
        MuleConnectionMonitor.Snapshot connection = script.getMuleConnectionStatus();
        add(row("Mule link:", connection == null ? "Checking" : connection.label(), connectionColor(connection)));

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
            add(bar);
        }
        if (keep > 0) {
            String rule = trigger > keep ? "keep " + compact(keep) + ", at " + compact(trigger)
                : trigger < 0 ? "keep " + compact(keep) + ", check trigger" : "keep " + compact(keep);
            // A trigger that needs checking is a problem to show, not detail to drop.
            add(row("Mule rule:", rule, trigger < 0 ? NEGATIVE_COLOR : LABEL_COLOR), trigger < 0 ? ALWAYS : DROP_SECOND);
        }
        long muled = script.getTotalMuledGp();
        add(row("Mule Sent:", formatProfit(muled), muled > 0 ? POSITIVE_COLOR : MUTED_COLOR));
        long lastAge = script.getLastMuleAgeMs();
        if (lastAge >= 0) {
            add(row("Last mule:", compact(script.getLastMuleGp()) + ", " + ago(lastAge) + " ago", MUTED_COLOR),
                DROP_FIRST);
        }
        String capitalStatus = script.getMuleCapitalStatus();
        if (capitalStatus != null && !capitalStatus.isEmpty()) {
            LineComponent.LineComponentBuilder line = LineComponent.builder().left(capitalStatus).leftColor(MUTED_COLOR);
            if (capital >= 0) line.right(compact(capital)).rightColor(MUTED_COLOR);
            add(line.build(), DROP_SECOND);
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
