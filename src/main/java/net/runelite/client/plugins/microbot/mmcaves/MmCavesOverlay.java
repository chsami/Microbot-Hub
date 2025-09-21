package net.runelite.client.plugins.microbot.mmcaves;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;
import java.time.Duration;
import java.time.Instant;

public class MmCavesOverlay extends OverlayPanel {

    private final MmCavesPlugin plugin;
    private final MmCavesConfig config;
    @Inject
    MmCavesOverlay(MmCavesPlugin plugin, MmCavesConfig config) {
        super(plugin);
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(200, 300));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Mm2 Caves")
                    .color(Color.GREEN)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("STATE: " + MmCavesScript.state)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("ATTACK DELAY: " + config.customAttackDelay() + " MS")
                    .build());

            Instant lastReset = Instant.ofEpochMilli(MmCavesScript.lastAggroResetTime);
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("RESET TIMER: " + formatDuration(lastReset))
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("TOTAL TIME: " + formatDuration(plugin.startTime))
                    .build());
        } catch(Exception ex) {
            System.out.println(ex.getMessage());
        }
        return super.render(graphics);
    }

    private String formatDuration(Instant instant) {
        Duration runTime = Duration.between(instant, Instant.now());
        long hours = runTime.toHours();
        long minutes = runTime.toMinutes() % 60;
        long seconds = runTime.getSeconds() % 60;

        String formattedTime;
        if (hours > 0) {
            formattedTime = String.format("%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            formattedTime = String.format("%02d:%02d", minutes, seconds);
        }
        return formattedTime;
    }
}
