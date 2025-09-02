package net.runelite.client.plugins.microbot.herbiboar;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.ImageUtil;
import org.slf4j.event.Level;

import javax.inject.Inject;
import java.awt.*;
import java.awt.image.BufferedImage;

@Slf4j
public class HerbiboarOverlay extends Overlay {

    private final HerbiboarPlugin plugin;
    private final int panelWidth = 170;
    private final int panelHeight = 130;
    private final Color[] rainbowColors = {
            Color.RED, Color.ORANGE, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA
    };
    @Getter
    @Setter
    private BufferedImage herbiIcon;
    @Getter
    private int size = 24;
    private Rectangle bounds;
    private Widget boundsWidget;
    private int rainbowIndex = 0;
    private long lastRainbowChange = System.currentTimeMillis();

    @Inject
    HerbiboarOverlay(HerbiboarPlugin plugin) {
        super(plugin);
        this.plugin = plugin;

        Microbot.log(Level.DEBUG,"Initializing Herbiboar Overlay");

        setLayer(OverlayLayer.ALWAYS_ON_TOP);
        setPosition(OverlayPosition.DYNAMIC);
        setNaughty();

        setBounds();

        setHerbiIcon(getScaledHerbiIcon());
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!Microbot.isLoggedIn()) {
            return null;
        }
        try {
            setBounds();

            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // Draw background
            graphics.setColor(new Color(0, 0, 0, 200));
            graphics.fillRect(bounds.x, bounds.y, panelWidth, panelHeight);

            // Draw border
            graphics.setColor(new Color(33, 173, 4, 131));
            graphics.setStroke(new BasicStroke(2));
            graphics.drawRect(bounds.x, bounds.y, panelWidth - 1, panelHeight - 1);

            Font originalFont = graphics.getFont();
            int currentY = bounds.y + 27;

            // === TITLE with custom font ===
            renderCustomText(graphics, "HERBIBOAR", bounds.x, currentY, panelWidth,
                    new Font("Ink Free", Font.BOLD, 14), Color.GREEN, true, true); // Font size reduced from 20 to 14
            currentY += 13;

            // === Separator line ===
            graphics.setColor(new Color(255, 255, 255, 100));
            graphics.drawLine(bounds.x + 10, currentY, bounds.x + panelWidth - 10, currentY);
            currentY += 25;

            // === STATUS ===
            renderCustomText(graphics, HerbiboarScript.getState().getDescription(), bounds.x, currentY, panelWidth,
                    new Font("Arial", Font.BOLD, 12), getStatusColor(), true, true);
            currentY += 18;

            // === Data with consistent spacing ===
            currentY += 5;
            currentY = renderDataLine(graphics, "XP/H:", String.format("%,d", plugin.getXpPerHour()),
                    bounds.x, currentY, panelWidth);
            currentY = renderDataLine(graphics, "XP Gained:", String.format("%,d", plugin.getXpGained()),
                    bounds.x, currentY, panelWidth);
            currentY = renderDataLine(graphics, "Caught:", String.valueOf(HerbiboarScript.herbiCaught),
                    bounds.x, currentY, panelWidth);

            // === Icon ===
            if (getHerbiIcon() != null) {
                int iconX = bounds.x + panelWidth - 32; // Adjusted for bigger icon
                int iconY = bounds.y + 12;
                graphics.drawImage(getHerbiIcon(), iconX, iconY, null);
            }

            if (getHerbiIcon() != null) {
                int iconX = bounds.x + 12;
                int iconY = bounds.y + 12;
                graphics.drawImage(getHerbiIcon(), iconX, iconY, null);
            }


            graphics.setFont(originalFont);

            // Return null for custom overlays
            return null;

        } catch (Exception ex) {
            System.out.println(ex.getMessage());
            Microbot.log(Level.DEBUG,"Error rendering The Mess Overlay: {}", ex.getMessage());
        }

        return null;
    }

    private void setBounds() {
        boundsWidget = Rs2Widget.getWidget(ComponentID.CHATBOX_PARENT);
        if (boundsWidget != null) {
            bounds = new Rectangle(
                    boundsWidget.getBounds().x + 5, boundsWidget.getBounds().y - boundsWidget.getHeight() + 30,
                    panelWidth, panelHeight
            );
        } else {
            bounds = new Rectangle(
                    10, 10,  // Default position if widget is not found
                    panelWidth, panelHeight
            );
        }
    }

    private void renderCustomText(Graphics2D graphics, String text, int x, int y, int width,
                                  Font font, Color color, boolean centered, boolean withShadow) {
        graphics.setFont(font);
        FontMetrics fm = graphics.getFontMetrics();

        int textX = centered ? x + (width - fm.stringWidth(text)) / 2 : x + 8;

        if (withShadow) {
            graphics.setColor(Color.BLACK);
            graphics.drawString(text, textX + 1, y + 1);
        }

        graphics.setColor(color);
        graphics.drawString(text, textX, y);
    }

    private int renderDataLine(Graphics2D graphics, String label, String value, int x, int y, int width) {
        FontMetrics fm = graphics.getFontMetrics();

        int labelX = x + 8;
        int valueX = x + width - 8 - fm.stringWidth(value);

        graphics.setColor(Color.WHITE);
        graphics.drawString(label, labelX, y);


        graphics.setColor(Color.GREEN);
        graphics.drawString(value, valueX, y);

        // Return next Y position using the font height
        int maxHeight = fm.getHeight();
        return y + maxHeight + 2;
    }

    private Color getStatusColor() {
        switch (HerbiboarScript.getState()) {
            case HARVEST:
                long now = System.currentTimeMillis();
                if (now - lastRainbowChange >= 600) {
                    rainbowIndex = (rainbowIndex + 1) % rainbowColors.length;
                    lastRainbowChange = now;
                }
                return rainbowColors[rainbowIndex];
            default:
                return Color.GREEN;
        }
    }

    private BufferedImage getScaledHerbiIcon() {
        if (getHerbiIcon() != null && getSize() == 24) { // Changed from 16 to 24
            return getHerbiIcon();
        }

        BufferedImage icon = ImageUtil.loadImageResource(HerbiboarPlugin.class, "icon.png");
        if (icon == null) {
            return null;
        }

        icon = ImageUtil.resizeImage(icon, 24, 24, true); // Changed from 16x16 to 24x24
        return icon;
    }
}