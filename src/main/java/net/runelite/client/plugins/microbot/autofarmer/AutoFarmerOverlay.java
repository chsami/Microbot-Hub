package net.runelite.client.plugins.microbot.autofarmer;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class AutoFarmerOverlay extends OverlayPanel {
    private static final String TITLE = "Scoots Auto Herb, Tree, Fruit Farmer";

    @Inject
    AutoFarmerOverlay(AutoFarmerPlugin plugin)
    {
        super(plugin);
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }
    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            int width = Math.max(200, graphics.getFontMetrics().stringWidth(TITLE) + 16);
            panelComponent.setPreferredSize(new Dimension(width, 300));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text(TITLE)
                    .color(Color.GREEN)
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Farming level").right(Integer.toString(AutoFarmerPlugin.farmingLevel)).build());
            var estimate = ((AutoFarmerPlugin) getPlugin()).getLevelEstimate();
            if (estimate != null) {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left(estimate.herbName() + " to level " + estimate.nextLevel())
                        .right(String.format("~%,d", estimate.herbs()))
                        .build());
            }
            panelComponent.getChildren().add(LineComponent.builder()
                    .left(AutoFarmerPlugin.status)
                    .build());


        } catch(Exception ex) {
            System.out.println(ex.getMessage());
        }
        return super.render(graphics);
    }
}
