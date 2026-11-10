package net.runelite.client.plugins.microbot.dropyramidplunder;

import java.awt.AWTEvent;
import java.awt.BasicStroke;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.plugins.microbot.util.mouse.BotEventGuard;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.components.BackgroundComponent;

/** Compact Dro card in the Slayer style: sceptre, title and status, with a collapsible stats list. */
public class PyramidPlunderOverlay extends Overlay {
    private static final int WIDTH = 280, PADDING = 10, ROW_HEIGHT = 17;
    private static final Color BACKGROUND = new Color(42, 42, 42, 156), BORDER = new Color(205, 211, 218, 225),
            TEXT = new Color(218, 222, 228), MUTED = new Color(195, 202, 211), SHADOW = new Color(0, 0, 0, 190),
            GOLD = new Color(232, 196, 82);
    private static final Font TITLE_FONT = FontManager.getRunescapeFont(),
            LABEL_FONT = FontManager.getRunescapeSmallFont(), VALUE_FONT = FontManager.getRunescapeFont();

    private final Client client;
    private final PyramidPlunderScript script;
    private final ItemManager itemManager;
    private BufferedImage sceptre;

    private volatile boolean collapsed;
    private boolean toggleClick;
    private long xpSessionStartedAt;
    private int startXp = -1;

    private final MouseAdapter inputListener = new MouseAdapter() {
        @Override public MouseEvent mousePressed(MouseEvent event) {
            if (BotEventGuard.isSynthetic()) return event;
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
            if (toggleClick && event.getButton() == MouseEvent.BUTTON1) {
                toggleClick = false;
                event.consume();
            }
            return event;
        }
    };

    private Canvas inputCanvas;
    private final AWTEventListener canvasInput = this::handleCanvasInput;

    @Inject
    PyramidPlunderOverlay(DroPyramidPlunderPlugin plugin, Client client, PyramidPlunderScript script, ItemManager itemManager) {
        super(plugin);
        this.client = client;
        this.script = script;
        this.itemManager = itemManager;
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(OverlayPriority.HIGH);
        setNaughty();
    }

    MouseAdapter getInputListener() { return inputListener; }

    /** Observe the toggle before game-input hooks; never re-enable or forward blocked input. */
    void enableCanvasInput(Canvas canvas) {
        disableCanvasInput();
        collapsed = false;
        inputCanvas = canvas;
        if (canvas != null) Toolkit.getDefaultToolkit().addAWTEventListener(canvasInput, AWTEvent.MOUSE_EVENT_MASK);
    }

    void disableCanvasInput() {
        Toolkit.getDefaultToolkit().removeAWTEventListener(canvasInput);
        inputCanvas = null;
        toggleClick = false;
    }

    private void handleCanvasInput(AWTEvent event) {
        if (!(event instanceof MouseEvent) || inputCanvas == null || event.getSource() != inputCanvas
                || BotEventGuard.isSynthetic()) return;
        MouseEvent mouse = (MouseEvent) event;
        switch (mouse.getID()) {
            case MouseEvent.MOUSE_PRESSED: inputListener.mousePressed(mouse); break;
            case MouseEvent.MOUSE_RELEASED: inputListener.mouseReleased(mouse); break;
            case MouseEvent.MOUSE_CLICKED: inputListener.mouseClicked(mouse); break;
            default: break;
        }
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        long started = script.getStartedAt();
        long runtimeMs = started > 0 ? System.currentTimeMillis() - started : 0;
        int xp = client.getSkillExperience(Skill.THIEVING);
        if (started != xpSessionStartedAt || startXp < 0 || xp < startXp) {
            xpSessionStartedAt = started;
            startXp = xp > 0 ? xp : -1;
        }
        int gained = startXp < 0 ? 0 : xp - startXp;
        long perHour = runtimeMs < 60_000 ? 0 : Math.round(gained * 3_600_000.0 / runtimeMs);

        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {"Runtime", duration(runtimeMs / 1000)});
        rows.add(new String[] {"Runs", String.valueOf(script.getRuns())});
        rows.add(new String[] {"Thieving XP", String.format(Locale.US, "%,d", gained)});
        rows.add(new String[] {"Thieving XP/hr", String.format(Locale.US, "%,d", perHour)});
        if (script.isInPyramid() && script.getCurrentRoom() > 0) {
            rows.add(new String[] {"Room", script.getCurrentRoom() + " / " + PyramidPlunderPlan.maxRoom(client.getRealSkillLevel(Skill.THIEVING))});
            double left = script.getSecondsLeft();
            if (left >= 0) rows.add(new String[] {"Time left", duration((long) left)});
            int urns = script.getUrnsLeft();
            if (urns >= 0) rows.add(new String[] {"Urns left", String.valueOf(urns)});
        }
        PyramidPlunderEntrances.Door door = script.getLastGoodDoor();
        rows.add(new String[] {"Mummy door", door == null ? "Unknown" : capitalise(door.name())});

        FontMetrics metrics = graphics.getFontMetrics(VALUE_FONT);
        int height = collapsed ? 50 : 58 + rows.size() * ROW_HEIGHT;
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        frame(graphics, WIDTH, height);

        graphics.setColor(new Color(BORDER.getRed(), BORDER.getGreen(), BORDER.getBlue(), 40));
        graphics.fillRoundRect(WIDTH - 25, 4, 22, 22, 5, 5);
        drawText(graphics, TITLE_FONT, BORDER, collapsed ? "+" : "-", WIDTH - 19, 19);

        BufferedImage icon = sceptreIcon();
        if (icon != null) {
            double scale = Math.min(30.0 / icon.getWidth(), 34.0 / icon.getHeight());
            int w = (int) (icon.getWidth() * scale), h = (int) (icon.getHeight() * scale);
            graphics.drawImage(icon, PADDING + (30 - w) / 2, 5 + (34 - h) / 2, w, h, null);
        }
        drawText(graphics, TITLE_FONT, GOLD, "DRO's Pyramid Plunder", 48, 19);
        String status = script.getStatus();
        drawText(graphics, VALUE_FONT, TEXT, fit(metrics, status == null ? "" : status, WIDTH - 48 - 30), 48, 35);
        graphics.setColor(new Color(BORDER.getRed(), BORDER.getGreen(), BORDER.getBlue(), 75));
        graphics.drawLine(PADDING, 45, WIDTH - PADDING, 45);
        if (collapsed) return new Dimension(WIDTH, height);

        int y = 64;
        for (String[] row : rows) {
            drawText(graphics, LABEL_FONT, MUTED, row[0], PADDING, y);
            drawText(graphics, VALUE_FONT, TEXT, row[1], WIDTH - PADDING - metrics.stringWidth(row[1]), y);
            y += ROW_HEIGHT;
        }
        return new Dimension(WIDTH, height);
    }

    /** The game's own Pharaoh's sceptre sprite, loaded once it is available. */
    private BufferedImage sceptreIcon() {
        if (sceptre == null) sceptre = itemManager.getImage(ItemID.PHARAOHS_SCEPTRE);
        return sceptre;
    }

    private static void frame(Graphics2D graphics, int width, int height) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setStroke(new BasicStroke(1f));
            new BackgroundComponent(BACKGROUND, new Rectangle(0, 0, width, height), true).render(g);
            g.setColor(new Color(0, 0, 0, 225));
            g.drawRect(0, 0, width - 1, height - 1);
        } finally {
            g.dispose();
        }
    }

    private static void drawText(Graphics2D graphics, Font font, Color color, String text, int x, int y) {
        graphics.setFont(font);
        graphics.setColor(SHADOW);
        graphics.drawString(text, x + 1, y + 1);
        graphics.setColor(color);
        graphics.drawString(text, x, y);
    }

    private static String fit(FontMetrics metrics, String text, int width) {
        if (metrics.stringWidth(text) <= width) return text;
        String cut = text;
        while (cut.length() > 1 && metrics.stringWidth(cut + "...") > width) cut = cut.substring(0, cut.length() - 1);
        return cut + "...";
    }

    private static String duration(long seconds) {
        long h = seconds / 3600, m = (seconds % 3600) / 60, s = seconds % 60;
        return h > 0 ? String.format(Locale.US, "%d:%02d:%02d", h, m, s) : String.format(Locale.US, "%d:%02d", m, s);
    }

    private static String capitalise(String name) {
        return name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
    }
}
