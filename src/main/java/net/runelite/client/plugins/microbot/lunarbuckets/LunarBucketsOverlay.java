package net.runelite.client.plugins.microbot.lunarbuckets;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.client.plugins.microbot.Microbot;

import javax.inject.Inject;
import java.awt.*;

public class LunarBucketsOverlay extends OverlayPanel {
    private final LunarBucketsPlugin plugin;

    @Inject
    LunarBucketsOverlay(LunarBucketsPlugin plugin) {
        super(plugin);
        this.plugin = plugin;
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
                .left("Status:")
                .right(Microbot.status)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Profit:")
                .right(Integer.toString(plugin.getTotalProfit()))
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Profit/h:")
                .right(Integer.toString(plugin.getProfitPerHour()))
                .build());

        return super.render(graphics);
    }
}
