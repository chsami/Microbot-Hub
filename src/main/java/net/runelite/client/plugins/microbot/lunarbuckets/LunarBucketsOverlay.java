package net.runelite.client.plugins.microbot.lunarbuckets;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.client.plugins.microbot.Microbot;

import javax.inject.Inject;
import java.awt.*;

public class LunarBucketsOverlay extends OverlayPanel {
    @Inject
    LunarBucketsOverlay(LunarBucketsPlugin plugin) {
        super(plugin);
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Lunar Buckets v" + LunarBucketsPlugin.version)
                .color(Color.CYAN)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left(Microbot.status)
                .build());

        return super.render(graphics);
    }
}
