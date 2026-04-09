package net.runelite.client.plugins.microbot.moonlightmoth;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;
import java.time.Instant;

public class MoonlightMothOverlay extends OverlayPanel {

    public final MoonlightMothPlugin plugin;

    @Inject
    public MoonlightMothOverlay(MoonlightMothPlugin plugin) {
        super(plugin);
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        this.plugin = plugin;
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.getChildren().clear();
            panelComponent.setPreferredSize(new Dimension(220, 300));

            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Moonlight Moth")
                    .color(Color.CYAN)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Runtime:")
                    .right(plugin.getTimeRunning())
                    .rightColor(Color.GREEN)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("State:")
                    .right(Microbot.status)
                    .rightColor(Color.GREEN)
                    .build());

            if (plugin.scriptStartTime != null) {
                long runtimeMs = Instant.now().toEpochMilli() - plugin.scriptStartTime.toEpochMilli();
                double hoursElapsed = runtimeMs / (1000.0 * 60.0 * 60.0);

                int caughtPerHour = hoursElapsed > 0 ? (int) (plugin.script.totalCaught / hoursElapsed) : 0;
                int profitPerHour = hoursElapsed > 0 ? (int) ((plugin.script.totalCaught * plugin.script.pricePerMoth) / hoursElapsed) : 0;

                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Moths caught:")
                        .right(plugin.script.totalCaught + " (" + caughtPerHour + "/hr)")
                        .rightColor(Color.YELLOW)
                        .build());

                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Profit:")
                        .right(String.format("%,d", plugin.script.totalCaught * plugin.script.pricePerMoth) + " (" + (profitPerHour / 1000) + "k/hr)")
                        .rightColor(Color.YELLOW)
                        .build());
            }

        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
        return super.render(graphics);
    }
}
