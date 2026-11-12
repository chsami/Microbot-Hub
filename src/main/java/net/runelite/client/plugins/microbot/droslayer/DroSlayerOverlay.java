package net.runelite.client.plugins.microbot.droslayer;

import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.*;
import javax.inject.Inject;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.event.MouseEvent;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.util.ImageUtil;
import java.util.ArrayList;
import java.util.List;

/** Firecape-style compact card, retaining Slayer telemetry with a silver Wilderness theme. */
public class DroSlayerOverlay extends Overlay {
    private static final int WIDTH = 280, PADDING = 10, ROW_HEIGHT = 17;
    private static final Color BORDER = new Color(205, 211, 218, 225), TEXT = new Color(218, 222, 228),
        MUTED = new Color(195, 202, 211), SHADOW = new Color(0, 0, 0, 190), HOLD = new Color(255, 196, 86);
    private static final Font TITLE_FONT = FontManager.getRunescapeFont(),
        LABEL_FONT = FontManager.getRunescapeSmallFont(), VALUE_FONT = FontManager.getRunescapeFont();
    private final DroSlayerPlugin plugin;
    private final java.util.EnumMap<DroSlayerMaster, BufferedImage> masterIcons = new java.util.EnumMap<>(DroSlayerMaster.class);
    private volatile boolean collapsed = true;
    private boolean toggleClick;
    private final MouseAdapter inputListener = new MouseAdapter() {
        @Override public MouseEvent mousePressed(MouseEvent event) {
            if (net.runelite.client.plugins.microbot.util.mouse.BotEventGuard.isSynthetic()) return event;
            Rectangle bounds = getBounds();
            Rectangle button = new Rectangle(bounds.x + bounds.width - 25, bounds.y + 4, 22, 22);
            if (event.getButton() == MouseEvent.BUTTON1 && !event.isAltDown() && bounds.width > 0 && button.contains(event.getPoint())) {
                collapsed = !collapsed;
                toggleClick = true;
                event.consume();
            }
            return event;
        }
        @Override public MouseEvent mouseReleased(MouseEvent event) {
            if (toggleClick && event.getButton() == MouseEvent.BUTTON1) event.consume();
            return event;
        }
        @Override public MouseEvent mouseClicked(MouseEvent event) {
            if (toggleClick && event.getButton() == MouseEvent.BUTTON1) { toggleClick = false; event.consume(); }
            return event;
        }
    };

    MouseAdapter getInputListener() { return inputListener; }
    void resetInput() { toggleClick = false; }

    private Canvas inputCanvas;
    private final java.awt.event.AWTEventListener canvasInput = this::handleCanvasInput;

    /** Observe the button before game-input hooks; never re-enable or forward blocked input. */
    void enableCanvasInput(Canvas canvas) {
        disableCanvasInput();
        collapsed = true;
        inputCanvas = canvas;
        if (canvas != null) Toolkit.getDefaultToolkit().addAWTEventListener(canvasInput, AWTEvent.MOUSE_EVENT_MASK);
    }

    void disableCanvasInput() {
        Toolkit.getDefaultToolkit().removeAWTEventListener(canvasInput);
        inputCanvas = null;
        resetInput();
    }

    void handleCanvasInput(AWTEvent event) {
        if (!(event instanceof MouseEvent) || inputCanvas == null || event.getSource() != inputCanvas
                || net.runelite.client.plugins.microbot.util.mouse.BotEventGuard.isSynthetic()) return;
        MouseEvent mouse = (MouseEvent) event;
        switch (mouse.getID()) {
            case MouseEvent.MOUSE_PRESSED: inputListener.mousePressed(mouse); break;
            case MouseEvent.MOUSE_RELEASED: inputListener.mouseReleased(mouse); break;
            case MouseEvent.MOUSE_CLICKED: inputListener.mouseClicked(mouse); break;
            default: break;
        }
    }

    @Inject
    DroSlayerOverlay(DroSlayerPlugin plugin) {
        super(plugin);
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(OverlayPriority.HIGH);
        setNaughty();
        for (DroSlayerMaster master : DroSlayerMaster.values()) {
            masterIcons.put(master, ImageUtil.loadImageResource(DroSlayerOverlay.class,
                master.name().toLowerCase(java.util.Locale.ROOT) + "-chathead.png"));
        }
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        List<String[]> rows = new ArrayList<>();
        String holdReason = DroSlayerHold.current(DroSlayerPlugin.getState(), System.currentTimeMillis());
        if (holdReason != null) {
            rows.add(new String[]{"Waiting", holdReason});
            rows.add(new String[]{"To do", DroSlayerHold.currentAction(DroSlayerPlugin.getState(), System.currentTimeMillis())});
        }
        rows.add(new String[]{"Runtime", plugin.getRuntimeDisplay()});
        if (plugin.isWildernessSlayer()) rows.add(new String[]{"Escapes/deaths", plugin.getEscapesDeathsDisplay()});
        rows.add(new String[]{"Slayer Points", String.valueOf(DroSlayerPlugin.getSlayerPoints())});
        rows.add(new String[]{"Slayer XP", String.format(java.util.Locale.US, "%,d", plugin.getSlayerXpGained())});
        rows.add(new String[]{"Slayer XP/hr", String.format(java.util.Locale.US, "%,d", plugin.getSlayerXpPerHour())});
        rows.add(new String[]{"Walker", DroSlayerRuntime.walkerStatus() +
            (DroSlayerRuntime.walkerMode() == null ? "" : " / " + DroSlayerRuntime.walkerMode())});
        if (DroSlayerPlugin.isHasTask()) {
            String task = DroSlayerPlugin.getCurrentTask();
            if (task != null && "Chaos druids".equalsIgnoreCase(task.trim())) {
                String location = DroSlayerPlugin.getCurrentLocation();
                task = location != null && location.toLowerCase(java.util.Locale.ROOT).contains("elder")
                    ? "Elder Chaos druid" : "Chaos druid";
            }
            rows.add(new String[]{"Task", task});
            rows.add(new String[]{"Remaining", String.valueOf(DroSlayerPlugin.getTaskRemaining())});
            String location = DroSlayerPlugin.getCurrentLocation();
            if (location != null && !location.isEmpty()) rows.add(new String[]{"Location", location});
            if (plugin.isWildernessSlayer() || !"—".equals(plugin.getCannonTileDisplay())) {
                rows.add(new String[]{"Cannon world", plugin.getCannonWorldDisplay()});
            }
        } else rows.add(new String[]{"Task", "No active task"});
        List<String[]> lines = new ArrayList<>();
        FontMetrics metrics = graphics.getFontMetrics(VALUE_FONT);
        for (String[] row : rows) {
            String value = row[1] == null ? "-" : row[1];
            boolean first = true;
            while (!value.isEmpty()) {
                int end = value.length();
                while (end > 1 && metrics.stringWidth(value.substring(0, end)) > WIDTH - PADDING * 2 - 105) end--;
                if (end < value.length()) {
                    int space = value.lastIndexOf(' ', end);
                    if (space > 0) end = space;
                }
                lines.add(new String[]{first ? row[0] : "", value.substring(0, end)});
                value = value.substring(end).trim();
                first = false;
            }
        }
        int height = collapsed ? 50 : 58 + lines.size() * ROW_HEIGHT;
        boolean wilderness = plugin.isWildernessSlayer();
        Color border = BORDER;
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        net.runelite.client.plugins.microbot.droslayer.DroOverlayFrame.render(graphics, WIDTH, height);
        graphics.setColor(new Color(border.getRed(), border.getGreen(), border.getBlue(), 40));
        graphics.fillRoundRect(WIDTH - 25, 4, 22, 22, 5, 5);
        drawText(graphics, TITLE_FONT, border, collapsed ? "+" : "-", WIDTH - 19, 19);
        BufferedImage icon = masterIcons.get(plugin.getSelectedSlayerMaster());
        if (icon != null) {
            double scale = Math.min(30.0 / icon.getWidth(), 34.0 / icon.getHeight());
            int width = (int) (icon.getWidth() * scale), iconHeight = (int) (icon.getHeight() * scale);
            graphics.drawImage(icon, PADDING + (30 - width) / 2, 5 + (34 - iconHeight) / 2, width, iconHeight, null);
        }
        drawText(graphics, TITLE_FONT, wilderness ? TEXT : border,
            wilderness ? "DRO's Wildy Slayer" : "DRO's Slayer", 48, 19);
        // The header is all that shows while collapsed, so a wait on the player is named here.
        String hold = DroSlayerHold.current(DroSlayerPlugin.getState(), System.currentTimeMillis());
        drawText(graphics, VALUE_FONT, hold == null ? getStateColor(DroSlayerPlugin.getState()) : HOLD,
            hold == null ? DroSlayerPlugin.getState().getDisplayName() : fit(graphics, "Waiting: " + hold, WIDTH - 48 - PADDING), 48, 35);
        graphics.setColor(new Color(border.getRed(), border.getGreen(), border.getBlue(), 75));
        graphics.drawLine(PADDING, 45, WIDTH - PADDING, 45);
        if (collapsed) return new Dimension(WIDTH, height);
        int y = 64;
        for (String[] row : lines) {
            drawText(graphics, LABEL_FONT, MUTED, row[0], PADDING, y);
            Color valueColor = "State".equals(row[0]) ? getStateColor(DroSlayerPlugin.getState()) : TEXT;
            drawText(graphics, VALUE_FONT, valueColor, row[1], WIDTH - PADDING - metrics.stringWidth(row[1]), y);
            y += ROW_HEIGHT;
        }
        return new Dimension(WIDTH, height);
    }

    /** Shortens a header line to the panel width; the full text is in the expanded rows. */
    static String fit(Graphics2D graphics, String text, int width) {
        FontMetrics metrics = graphics.getFontMetrics(VALUE_FONT);
        if (metrics.stringWidth(text) <= width) return text;
        int end = text.length();
        while (end > 1 && metrics.stringWidth(text.substring(0, end) + "...") > width) end--;
        return text.substring(0, end).trim() + "...";
    }

    private static void drawText(Graphics2D graphics, Font font, Color color, String text, int x, int y) {
        graphics.setFont(font);
        graphics.setColor(SHADOW);
        graphics.drawString(text, x + 1, y + 1);
        graphics.setColor(color);
        graphics.drawString(text, x, y);
    }

    private Color getStateColor(DroSlayerState state) {
        switch (state) {
            case IDLE:
                return Color.GRAY;
            case GETTING_TASK:
                return Color.MAGENTA;
            case SKIPPING_TASK:
                return Color.PINK;
            case RESTORING_AT_POH:
                return new Color(0, 191, 255); // Deep sky blue
            case DETECTING_TASK:
                return Color.YELLOW;
            case BANKING:
                return Color.ORANGE;
            case TRAVELING:
                return Color.CYAN;
            case AT_LOCATION:
                return Color.GREEN;
            case FIGHTING:
                return Color.RED;
            default:
                return Color.WHITE;
        }
    }
}
