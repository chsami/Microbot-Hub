package net.runelite.client.plugins.microbot.autosmelt;

import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class AutoSmeltOverlay extends OverlayPanel {
    private final AutoSmeltPlugin plugin;
    private final AutoSmeltConfig config;

    @Inject
    AutoSmeltOverlay(AutoSmeltPlugin plugin, AutoSmeltConfig config) {
        super(plugin);
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(200, 150));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Auto Smelt v" + plugin.getClass().getAnnotation(PluginDescriptor.class).version())
                    .color(Color.ORANGE)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder().build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Bar Type:")
                    .right(config.barType().toString())
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Status:")
                    .right(Microbot.status)
                    .build());

            if (config.debugMode()) {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Debug:")
                        .right("Enabled")
                        .build());
            }

        } catch (Exception ex) {
            System.out.println("Error in Auto Smelt overlay: " + ex.getMessage());
        }
        return super.render(graphics);
    }
}
