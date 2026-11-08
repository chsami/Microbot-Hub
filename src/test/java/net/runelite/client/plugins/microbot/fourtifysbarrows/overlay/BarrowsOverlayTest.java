package net.runelite.client.plugins.microbot.fourtifysbarrows.overlay;

import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.client.plugins.microbot.fourtifysbarrows.FourtifysBarrowsPlugin;
import net.runelite.client.plugins.microbot.fourtifysbarrows.config.BarrowsConfig;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BarrowsOverlayTest
{
    private static final String LONG_STATUS = "Closing bank before Ring of dueling travel to Ferox";
    private static final int STATUS_WIDTH = 182;

    @Test
    public void panelKeepsItsWidthAndHeightForLongStatus()
    {
        Dimension normal = new Harness(null).draw("Ready");
        Dimension cut = new Harness(null).draw(LONG_STATUS);
        assertEquals(BarrowsOverlay.WIDTH, normal.width);
        assertEquals(normal, cut);
    }

    @Test
    public void shortStatusIsShownWholeWithoutTooltip()
    {
        Harness harness = new Harness(new Point(5, 5));
        harness.draw("Fighting Dharok");
        assertTrue(harness.tooltips.getTooltips().isEmpty());
    }

    @Test
    public void cutStatusShowsTheFullTextInATooltipOnHover()
    {
        Harness harness = new Harness(new Point(5, 5));
        harness.draw(LONG_STATUS);
        assertEquals(1, harness.tooltips.getTooltips().size());
        assertEquals(LONG_STATUS, harness.tooltips.getTooltips().get(0).getText().replace("</br>", " "));
    }

    @Test
    public void cutStatusAddsNoTooltipWithoutHover()
    {
        Harness outside = new Harness(new Point(500, 500));
        outside.draw(LONG_STATUS);
        assertTrue(outside.tooltips.getTooltips().isEmpty());
        Harness offCanvas = new Harness(null);
        offCanvas.draw(LONG_STATUS);
        assertTrue(offCanvas.tooltips.getTooltips().isEmpty());
    }

    @Test
    public void tooltipEscapesClientMarkup()
    {
        Harness harness = new Harness(new Point(5, 5));
        String status = LONG_STATUS + " <col=ff0000>now";
        harness.draw(status);
        assertFalse(harness.tooltips.getTooltips().get(0).getText().contains("<col="));
    }

    @Test
    public void fitKeepsAPrefixWithinTheStatusWidth()
    {
        FontMetrics metrics = metrics();
        String shown = BarrowsOverlay.fit(LONG_STATUS, metrics, STATUS_WIDTH);
        assertTrue(shown.endsWith("..."));
        assertTrue(LONG_STATUS.startsWith(shown.substring(0, shown.length() - 3)));
        assertTrue(metrics.stringWidth(shown) <= STATUS_WIDTH);
    }

    @Test
    public void theLongestShadesCooldownWaitFitsWithoutCuttingTheMinutes()
    {
        String status = "Shades teleport ready in 20 min";
        assertEquals(status, BarrowsOverlay.fit(status, metrics(), STATUS_WIDTH));
    }

    @Test
    public void wrapKeepsEveryWordWithinTheTooltipWidth()
    {
        FontMetrics metrics = metrics();
        List<String> lines = BarrowsOverlay.wrap(LONG_STATUS, metrics, 200);
        assertTrue(lines.size() > 1);
        assertEquals(LONG_STATUS, String.join(" ", lines));
        for (String line : lines)
        {
            assertTrue(metrics.stringWidth(line) <= 200);
        }
    }

    private static FontMetrics metrics()
    {
        Graphics2D graphics = graphics();
        try
        {
            return graphics.getFontMetrics();
        }
        finally
        {
            graphics.dispose();
        }
    }

    private static Graphics2D graphics()
    {
        Graphics2D graphics = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB).createGraphics();
        graphics.setFont(FontManager.getRunescapeFont());
        return graphics;
    }

    private static final class Harness
    {
        private final TooltipManager tooltips = new TooltipManager();
        private final Point mouse;

        private Harness(Point mouse)
        {
            this.mouse = mouse;
        }

        private Dimension draw(String status)
        {
            BarrowsOverlay overlay = new BarrowsOverlay(plugin(status), config(), client(mouse), tooltips);
            overlay.setBounds(new Rectangle(0, 0, 190, 60));
            Graphics2D graphics = graphics();
            try
            {
                // The client renders every frame; native panels settle their layout after the first one.
                overlay.render(graphics);
                tooltips.clear();
                return overlay.render(graphics);
            }
            finally
            {
                graphics.dispose();
            }
        }

        private static FourtifysBarrowsPlugin plugin(String status)
        {
            Instant start = Instant.now().minusSeconds(2537);
            return new FourtifysBarrowsPlugin()
            {
                @Override
                public Instant getStartTime()
                {
                    return start;
                }

                @Override
                public String getStatus()
                {
                    return status;
                }

                @Override
                public long getChests()
                {
                    return 37;
                }
            };
        }

        private static BarrowsConfig config()
        {
            return (BarrowsConfig) Proxy.newProxyInstance(BarrowsConfig.class.getClassLoader(),
                new Class<?>[] {BarrowsConfig.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("showOverlay"))
                    {
                        return true;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        }

        private static Client client(Point mouse)
        {
            return (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[] {Client.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("getMouseCanvasPosition"))
                    {
                        return mouse;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        }
    }
}
