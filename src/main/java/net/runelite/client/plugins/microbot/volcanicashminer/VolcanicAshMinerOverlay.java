package net.runelite.client.plugins.microbot.volcanicashminer;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;
import java.time.Duration;

@Slf4j
public class VolcanicAshMinerOverlay extends OverlayPanel {
    private final VolcanicAshMinerPlugin plugin;

    @Inject
    VolcanicAshMinerOverlay(VolcanicAshMinerPlugin plugin) {
        super(plugin);
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }


    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(200, 300));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("VolcanicAshMiner - v" + VolcanicAshMinerScript.VERSION)
                    .color(Color.GREEN)
                    .build());
            panelComponent.getChildren().add(LineComponent.builder().build());
            final Duration duration = plugin.getVolcanicAshMinerScript().getRunTime();
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Time: " + String.format("%d:%02d:%02d",
                            duration.toHours(),
                            duration.toMinutesPart(),
                            duration.toSecondsPart()))
                    .leftColor(Color.WHITE)
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("State: " + VolcanicAshMinerScript.BOT_STATUS)
                    .leftColor(Color.WHITE)
                    .build());
        } catch (Exception ex) {
            log.error("Error while rendering overlay", ex);
        }
        return super.render(graphics);
    }
}
