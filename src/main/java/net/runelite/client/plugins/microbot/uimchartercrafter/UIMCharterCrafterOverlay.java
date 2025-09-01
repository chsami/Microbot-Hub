package net.runelite.client.plugins.microbot.uimchartercrafter;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class UIMCharterCrafterOverlay extends OverlayPanel {
    private final UIMCharterCrafterPlugin plugin;

    @Inject
    public UIMCharterCrafterOverlay(UIMCharterCrafterPlugin plugin) {
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
        setPriority(OverlayPriority.HIGH);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        panelComponent.getChildren().clear();
        panelComponent.getChildren().add(TitleComponent.builder()
                .text("UIM Charter Crafter")
                .color(Color.CYAN)
                .build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Status")
                .right(plugin.getStatus())
                .build());
        return super.render(graphics);
    }
}
