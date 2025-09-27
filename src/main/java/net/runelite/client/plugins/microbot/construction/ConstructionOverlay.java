package net.runelite.client.plugins.microbot.construction;

import net.runelite.client.plugins.microbot.construction.ConstructionPlugin;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class ConstructionOverlay extends OverlayPanel {

    private final net.runelite.client.plugins.microbot.construction.ConstructionPlugin plugin;

    @Inject
    public ConstructionOverlay(ConstructionPlugin plugin) {
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        panelComponent.getChildren().clear();

        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Construction Script")
                .color(Color.YELLOW)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("State:")
                .right(plugin.getState().toString())
                .build());

        return super.render(graphics);
    }
}
