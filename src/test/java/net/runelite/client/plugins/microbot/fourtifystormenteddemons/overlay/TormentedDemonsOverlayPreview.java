package net.runelite.client.plugins.microbot.fourtifystormenteddemons.overlay;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.FourtifysTormentedDemonsPlugin;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.config.TormentedDemonsConfig;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.util.timing.TickDelay;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.components.TooltipComponent;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

public final class TormentedDemonsOverlayPreview
{
    private static final int MARGIN = 8;
    private static final int SCALE = 3;

    private TormentedDemonsOverlayPreview()
    {
    }

    public static void main(String[] args) throws Exception
    {
        Path output = Path.of(args[0]);
        boolean hover = args.length > 2 && Boolean.parseBoolean(args[2]);
        TickDelay delay = new TickDelay();
        AtomicReference<String> timeout = new AtomicReference<>();
        delay.set(2);
        Player idle = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
            (proxy, method, arguments) -> method.getName().equals("getAnimation") ? -1 : null);
        delay.waitForDelay(idle, timeout::set);
        String status = args.length > 1 ? args[1] : timeout.get();
        Instant start = Instant.now().minusSeconds(754);
        FourtifysTormentedDemonsPlugin plugin = new FourtifysTormentedDemonsPlugin()
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
            public long getKills()
            {
                return 37;
            }

            @Override
            public long getKillsPerHour()
            {
                return 42;
            }

            @Override
            public long getLootValue()
            {
                return 3_215_400;
            }

            @Override
            public int getUniques()
            {
                return 1;
            }
        };
        TormentedDemonsConfig config = (TormentedDemonsConfig) Proxy.newProxyInstance(TormentedDemonsConfig.class.getClassLoader(),
            new Class<?>[]{TormentedDemonsConfig.class}, (proxy, method, arguments) -> {
                if ("showOverlay".equals(method.getName()))
                {
                    return true;
                }
                throw new UnsupportedOperationException(method.getName());
            });
        net.runelite.api.Point mouse = hover ? new net.runelite.api.Point(MARGIN, MARGIN) : null;
        Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[] {Client.class},
            (proxy, method, arguments) -> {
                if (method.getName().equals("getMouseCanvasPosition"))
                {
                    return mouse;
                }
                throw new UnsupportedOperationException(method.getName());
            });
        TooltipManager tooltips = new TooltipManager();
        TormentedDemonsOverlay overlay = new TormentedDemonsOverlay(plugin, config, client, tooltips);

        BufferedImage scratch = new BufferedImage(1024, 1024, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = prepare(scratch);
        Dimension size;
        try
        {
            overlay.render(g);
            size = overlay.render(g);
            overlay.setBounds(new Rectangle(0, 0, size.width, size.height));
            tooltips.clear();
            overlay.render(g);
        }
        finally
        {
            g.dispose();
        }

        TooltipComponent tip = null;
        Dimension tipSize = new Dimension();
        if (!tooltips.getTooltips().isEmpty())
        {
            Tooltip tooltip = tooltips.getTooltips().get(0);
            tip = new TooltipComponent();
            tip.setText(tooltip.getText());
            g = prepare(scratch);
            try
            {
                tipSize = tip.render(g);
            }
            finally
            {
                g.dispose();
            }
        }
        int tipSpace = tip == null ? 0 : tipSize.height + MARGIN;
        int width = Math.max(size.width, tipSize.width + 2 * MARGIN) + 2 * MARGIN;
        int height = size.height + tipSpace + 2 * MARGIN;
        BufferedImage frame = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        g = prepare(frame);
        try
        {
            g.setColor(new Color(0x4A5A32));
            g.fillRect(0, 0, width, height);
            if (tip != null)
            {
                tip.setPosition(new Point(2 * MARGIN, MARGIN));
                tip.render(g);
            }
            g.translate(MARGIN, MARGIN + tipSpace);
            overlay.render(g);
        }
        finally
        {
            g.dispose();
        }

        BufferedImage preview = new BufferedImage(width * SCALE, height * SCALE, BufferedImage.TYPE_INT_RGB);
        g = preview.createGraphics();
        try
        {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(frame, 0, 0, preview.getWidth(), preview.getHeight(), null);
        }
        finally
        {
            g.dispose();
        }
        Files.createDirectories(output.toAbsolutePath().getParent());
        if (!ImageIO.write(preview, "png", output.toFile()))
        {
            throw new IllegalStateException("PNG writer unavailable");
        }
        System.out.printf("Native overlay %dx%d; tooltip %s; preview %s%n", size.width, size.height, tip != null, output);
    }

    private static Graphics2D prepare(BufferedImage image)
    {
        Graphics2D g = image.createGraphics();
        g.setFont(FontManager.getRunescapeFont());
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        return g;
    }
}
