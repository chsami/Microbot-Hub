package net.runelite.client.plugins.microbot.autogauntletprayer;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class AutoGauntletPrayerOverlay extends OverlayPanel {

    private final AutoGauntletPrayerPlugin plugin;

    @Inject
    private AutoGauntletPrayerConfig config;

    @Inject
    AutoGauntletPrayerOverlay(AutoGauntletPrayerPlugin plugin) {
        super(plugin);
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
        setPriority(OverlayPriority.MED);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (config.enOverlay()) {

            panelComponent.setPreferredSize(new Dimension(250, 500));
        panelComponent.getChildren().clear(); // Always clear before rendering

        try {
            // === Header ===
            panelComponent.getChildren().add(
                    TitleComponent.builder()
                            .text("Gauntlet Prayer Helper")
                            .color(new Color(0x00FF88))
                            .build()
            );

            panelComponent.getChildren().add(LineComponent.builder().build());

            // === Next Prayer ===
            String nextPrayerText = (plugin != null && plugin.getNextPrayer() != null)
                    ? plugin.getNextPrayer().toString()
                    : "None";

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Next Prayer:")
                    .right(nextPrayerText)
                    .leftColor(Color.WHITE)
                    .rightColor(Color.CYAN)
                    .build());

            // === Projectile Timer ===
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Last Projectile:")
                    .right(AutoGauntletPrayerScript.getFormattedTimer1Time())
                    .leftColor(Color.WHITE)
                    .rightColor(Color.YELLOW)
                    .build());

            // === Projectile Counter ===
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Projectile Count:")
                    .right(String.valueOf(AutoGauntletPrayerScript.getTimer1Count()))
                    .leftColor(Color.WHITE)
                    .rightColor(Color.ORANGE)
                    .build());

            // === Script State (Optional) ===
            if (AutoGauntletPrayerScript.agpstate != null) {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("State:")
                        .right(AutoGauntletPrayerScript.agpstate.toString())
                        .leftColor(Color.WHITE)
                        .rightColor(Color.LIGHT_GRAY)
                        .build());
            }

            // === General Microbot Status ===
            if (Microbot.status != null && !Microbot.status.isEmpty()) {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Status:")
                        .right(Microbot.status)
                        .leftColor(Color.WHITE)
                        .rightColor(Color.GREEN)
                        .build());
            }

        } catch (Exception ex) {
            Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
        }
    }

        return super.render(graphics);
    }

}