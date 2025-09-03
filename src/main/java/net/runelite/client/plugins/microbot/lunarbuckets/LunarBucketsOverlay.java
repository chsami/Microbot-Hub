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
        panelComponent.getChildren().clear();

        String title = "Lunar Buckets v" + LunarBucketsPlugin.version;
        String statusRight = Microbot.status;
        String profitRight = Integer.toString(plugin.getTotalProfit());
        String pphRight = Integer.toString(plugin.getProfitPerHour());

        FontMetrics fm = graphics.getFontMetrics();
        int width = fm.stringWidth(title);
        width = Math.max(width, fm.stringWidth("Status:" + statusRight));
        width = Math.max(width, fm.stringWidth("Profit:" + profitRight));
        width = Math.max(width, fm.stringWidth("Profit/h:" + pphRight));
        panelComponent.setPreferredSize(new Dimension(width + 20, 0));

        panelComponent.getChildren().add(TitleComponent.builder()
                .text(title)
                .color(Color.CYAN)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Status:")
                .right(statusRight)
                .build());

		panelComponent.getChildren().add(LineComponent.builder()
				.left("Profit/h:")
				.right(pphRight)
				.build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Total Profit:")
                .right(profitRight)
                .build());

        return super.render(graphics);
    }
}
