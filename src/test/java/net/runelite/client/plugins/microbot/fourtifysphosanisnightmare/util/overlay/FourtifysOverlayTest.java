package net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.util.overlay;

import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.lang.reflect.Proxy;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FourtifysOverlayTest
{
    private static final String LONG_STATUS = "Teleporting to Ferox Enclave: Out of prayer restores";
    private static final int STATUS_WIDTH = 182;

    @Test
    public void panelKeepsItsWidthAndHeightForLongStatus()
    {
        Dimension normal = new Harness(null).draw("Ready");
        Dimension cut = new Harness(null).draw(LONG_STATUS);
        assertEquals(FourtifysOverlay.WIDTH, normal.width);
        assertEquals(normal, cut);
    }

    @Test
    public void widestPluginNameSharesTheFirstLineWithTheRuntime()
    {
        // A wrapped first line would make the panel taller.
        assertEquals(new Harness(null).draw("F", "Ready"), new Harness(null).draw("Fourtifys Nightmare", "Ready"));
    }

    @Test
    public void shortStatusIsShownWholeWithoutTooltip()
    {
        Harness harness = new Harness(new Point(5, 5));
        harness.draw("Attacking The Nightmare");
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
        String shown = FourtifysOverlay.fit(LONG_STATUS, metrics, STATUS_WIDTH);
        assertTrue(shown.endsWith("..."));
        assertTrue(LONG_STATUS.startsWith(shown.substring(0, shown.length() - 3)));
        assertTrue(metrics.stringWidth(shown) <= STATUS_WIDTH);
    }

    @Test
    public void wrapKeepsEveryWordWithinTheTooltipWidth()
    {
        FontMetrics metrics = metrics();
        List<String> lines = FourtifysOverlay.wrap(LONG_STATUS, metrics, 200);
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

    private static final class Harness extends FourtifysOverlay
    {
        private final TooltipManager tooltips;

        private Harness(Point mouse)
        {
            this(mouse, new TooltipManager());
        }

        private Harness(Point mouse, TooltipManager tooltips)
        {
            super(null, client(mouse), tooltips);
            this.tooltips = tooltips;
            setBounds(new Rectangle(0, 0, 190, 60));
        }

        @Override
        public Dimension render(Graphics2D graphics)
        {
            return null;
        }

        private Dimension draw(String status)
        {
            return draw("Fourtifys Nightmare", status);
        }

        private Dimension draw(String title, String status)
        {
            Graphics2D graphics = graphics();
            try
            {
                // The client renders every frame; native panels settle their layout after the first one.
                render(graphics, title, "00:42:17", status, "Kills: 37", "Loot: 3.2M");
                tooltips.clear();
                return render(graphics, title, "00:42:17", status, "Kills: 37", "Loot: 3.2M");
            }
            finally
            {
                graphics.dispose();
            }
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
