package net.runelite.client.plugins.microbot.barbarianfishing;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

public class BarbarianFishingOverlay extends OverlayPanel
{
    private final BarbarianFishingPlugin plugin;

    @Inject
    BarbarianFishingOverlay(BarbarianFishingPlugin plugin)
    {
        super(plugin);
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        try
        {
            BarbarianFishingScript script = plugin.getScript();
            panelComponent.setPreferredSize(new Dimension(240, 300));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("\uD83E\uDD86 Barbarian Fisher \uD83E\uDD86")
                    .color(Color.ORANGE)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder().build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Location:")
                    .right(script != null ? script.getLocation().getDisplayName() : "Unknown")
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Status:")
                    .right(script != null ? script.getStatus() : "Idle")
                    .build());

            if (script != null && script.getStartedAt() != null)
            {
                Duration elapsed = Duration.between(script.getStartedAt(), Instant.now());
                long seconds = Math.max(1, elapsed.getSeconds());
                int xpGained = script.getFishingXpGained();
                long xpPerHour = (long) (xpGained * (3600.0 / seconds));
                NumberFormat fmt = NumberFormat.getIntegerInstance(Locale.US);

                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Runtime:")
                        .right(formatDuration(elapsed))
                        .build());

                panelComponent.getChildren().add(LineComponent.builder()
                        .left("XP Gained:")
                        .right(fmt.format(xpGained))
                        .build());

                panelComponent.getChildren().add(LineComponent.builder()
                        .left("XP / Hr:")
                        .right(fmt.format(xpPerHour))
                        .build());

                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Fish Caught:")
                        .right(fmt.format(script.getFishCaught()))
                        .build());
            }

            if (script != null && script.isHasBarrel())
            {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Fish Barrel:")
                        .right(script.isBarrelFull() ? "Full" : "Filling")
                        .build());
            }

            panelComponent.getChildren().add(LineComponent.builder().build());
            Rs2Antiban.renderAntibanOverlayComponents(panelComponent);

            panelComponent.getChildren().add(LineComponent.builder()
                    .left(Microbot.status)
                    .right("v" + BarbarianFishingPlugin.version)
                    .build());
        }
        catch (Exception ex)
        {
            Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
        }
        return super.render(graphics);
    }

    private static String formatDuration(Duration duration)
    {
        long seconds = duration.getSeconds();
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }
}
