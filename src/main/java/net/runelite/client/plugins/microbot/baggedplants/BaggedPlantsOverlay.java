package net.runelite.client.plugins.microbot.baggedplants;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.plugins.microbot.baggedplants.enums.BaggedPlantsState;

import javax.inject.Inject;
import java.awt.*;

public class BaggedPlantsOverlay extends OverlayPanel {
    private static final Color TITLE_COLOR = Color.decode("#a4ffff");
    private static final Color BACKGROUND_COLOR = new Color(0, 0, 0, 150);
    private static final Color NORMAL_COLOR = Color.WHITE;
    private static final Color WARNING_COLOR = Color.YELLOW;
    private static final Color DANGER_COLOR = Color.RED;
    private static final Color SUCCESS_COLOR = Color.GREEN;
    
    private final BaggedPlantsPlugin plugin;

    @Inject
    public BaggedPlantsOverlay(BaggedPlantsPlugin plugin) {
        super(plugin);
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(200, 300));
            panelComponent.setBackgroundColor(BACKGROUND_COLOR);
            
            // Title
            final LineComponent title = LineComponent.builder()
                    .left("🌱 Bagged Plant Planter v1.0.0")
                    .leftColor(TITLE_COLOR)
                    .build();
            panelComponent.getChildren().add(title);
            
            // Script running time
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Runtime:")
                    .right(plugin.getTimeRunning())
                    .rightColor(NORMAL_COLOR)
                    .build());
            
            // State information
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("State:")
                    .right(plugin.getState().toString())
                    .rightColor(getStateColor(plugin.getState()))
                    .build());
            
            // Total plants planted
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Total Plants Planted:")
                    .right(String.valueOf(plugin.getTotalPlantsPlanted()))
                    .rightColor(NORMAL_COLOR)
                    .build());

            // XP information
            var xpGained = plugin.getXpGained();
            var xpPerHour = plugin.getXpPerHour();
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("XP Gained:")
                    .right(formatNumber(xpGained))
                    .rightColor(NORMAL_COLOR)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("XP/Hour:")
                    .right(formatNumber(xpPerHour))
                    .rightColor(xpPerHour > 0 ? SUCCESS_COLOR : NORMAL_COLOR)
                    .build());

        } catch (Exception ex) {
            Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
        }
        return super.render(graphics);
    }

    private String formatNumber(long number) {
        return String.format("%,d", number);
    }

    private Color getStateColor(BaggedPlantsState state) {
        if (state == null) return NORMAL_COLOR;

        switch (state) {
            case BUILD_PLANT:
            case REMOVE_PLANT:
                return SUCCESS_COLOR;
            case ENTER_HOUSE:
            case LEAVE_HOUSE:
                return WARNING_COLOR;
            case REFILL_SUPPLIES:
                return DANGER_COLOR;
            case CHECK_INVENTORY:
                return WARNING_COLOR;
            default:
                return NORMAL_COLOR;
        }
    }
}
