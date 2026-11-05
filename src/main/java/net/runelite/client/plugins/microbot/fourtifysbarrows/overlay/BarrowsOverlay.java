package net.runelite.client.plugins.microbot.fourtifysbarrows.overlay;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.client.plugins.microbot.fourtifysbarrows.FourtifysBarrowsPlugin;
import net.runelite.client.plugins.microbot.fourtifysbarrows.config.BarrowsConfig;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentConstants;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import net.runelite.client.util.Text;

public final class BarrowsOverlay extends OverlayPanel
{
    public static final int WIDTH = 190;
    private static final int STATUS_WIDTH = WIDTH - 2 * ComponentConstants.STANDARD_BORDER;
    private static final int TOOLTIP_WIDTH = 200;
    private static final String ELLIPSIS = "...";
    private static final Color FOURTIFY_YELLOW = new Color(0xF5DD42);
    private static final Color DETAIL = new Color(0xB9B4A8);
    private final FourtifysBarrowsPlugin plugin;
    private final BarrowsConfig config;
    private final Client client;
    private final TooltipManager tooltipManager;

    @Inject
    public BarrowsOverlay(FourtifysBarrowsPlugin plugin, BarrowsConfig config, Client client, TooltipManager tooltipManager)
    {
        super(plugin);
        setPosition(OverlayPosition.BOTTOM_LEFT);
        setResizable(false);
        panelComponent.setPreferredSize(new Dimension(WIDTH, 0));
        this.plugin = plugin;
        this.config = config;
        this.client = client;
        this.tooltipManager = tooltipManager;
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (plugin.getStartTime() == null || !config.showOverlay())
        {
            return null;
        }
        String elapsed = elapsed(plugin.getStartTime());
        return render(graphics, "Fourtifys Barrows", elapsed, plugin.getStatus(), "Chests: " + plugin.getChests());
    }

    private Dimension render(Graphics2D graphics, String activity, String runtime, String status, String... details)
    {
        panelComponent.getChildren().add(LineComponent.builder()
            .left(activity).leftColor(FOURTIFY_YELLOW)
            .right(runtime).rightColor(DETAIL)
            .build());
        for (int i = 0; i < details.length; i += 2)
        {
            panelComponent.getChildren().add(LineComponent.builder()
                .left(details[i])
                .right(i + 1 < details.length ? details[i + 1] : "")
                .build());
        }
        FontMetrics metrics = graphics.getFontMetrics();
        String shown = fit(status, metrics, STATUS_WIDTH);
        panelComponent.getChildren().add(LineComponent.builder().left(shown).build());
        Dimension size = super.render(graphics);
        if (!shown.equals(status) && isHovered())
        {
            List<String> lines = new ArrayList<>();
            for (String line : wrap(status, metrics, TOOLTIP_WIDTH))
            {
                lines.add(Text.escapeJagex(line));
            }
            tooltipManager.add(new Tooltip(String.join("</br>", lines)));
        }
        return size;
    }

    private boolean isHovered()
    {
        Point mouse = client.getMouseCanvasPosition();
        return mouse != null && getBounds().contains(mouse.getX(), mouse.getY());
    }

    static String fit(String text, FontMetrics metrics, int width)
    {
        if (metrics.stringWidth(text) <= width)
        {
            return text;
        }
        int end = text.length();
        while (end > 0 && metrics.stringWidth(text.substring(0, end).trim() + ELLIPSIS) > width)
        {
            end = text.offsetByCodePoints(end, -1);
        }
        return text.substring(0, end).trim() + ELLIPSIS;
    }

    static List<String> wrap(String text, FontMetrics metrics, int width)
    {
        List<String> lines = new ArrayList<>();
        String line = "";
        for (String word : text.trim().split("\\s+"))
        {
            String next = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && metrics.stringWidth(next) > width)
            {
                lines.add(line);
                next = word;
            }
            line = next;
        }
        lines.add(line);
        return lines;
    }

    private static String elapsed(Instant start)
    {
        long seconds = Math.max(0, Duration.between(start, Instant.now()).getSeconds());
        return String.format("%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }
}
