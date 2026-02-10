package net.runelite.client.plugins.microbot.slayer;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class SlayerOverlay extends OverlayPanel {

    @Inject
    SlayerOverlay(SlayerPlugin plugin) {
        super(plugin);
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(200, 150));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Micro Slayer V" + SlayerPlugin.version)
                    .color(Color.GREEN)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder().build());

            // Show slayer points
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Slayer Points:")
                    .right(String.valueOf(SlayerPlugin.getSlayerPoints()))
                    .rightColor(Color.YELLOW)
                    .build());

            // Show current state
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("State:")
                    .right(SlayerPlugin.getState().getDisplayName())
                    .rightColor(getStateColor(SlayerPlugin.getState()))
                    .build());

            if (SlayerPlugin.isHasTask()) {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Task:")
                        .right(SlayerPlugin.getCurrentTask())
                        .build());

                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Remaining:")
                        .right(String.valueOf(SlayerPlugin.getTaskRemaining()))
                        .build());

                // Show location if set
                String location = SlayerPlugin.getCurrentLocation();
                if (location != null && !location.isEmpty()) {
                    panelComponent.getChildren().add(LineComponent.builder()
                            .left("Location:")
                            .right(location)
                            .build());
                }
            } else {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("No active task")
                        .build());
            }

        } catch (Exception ex) {
            // Overlay render errors are non-critical, silently ignore
        }
        return super.render(graphics);
    }

    private Color getStateColor(SlayerState state) {
        switch (state) {
            case IDLE:
                return Color.GRAY;
            case GETTING_TASK:
                return Color.MAGENTA;
            case SKIPPING_TASK:
                return Color.PINK;
            case RESTORING_AT_POH:
                return new Color(0, 191, 255); // Deep sky blue
            case DETECTING_TASK:
                return Color.YELLOW;
            case BANKING:
                return Color.ORANGE;
            case TRAVELING:
                return Color.CYAN;
            case AT_LOCATION:
                return Color.GREEN;
            case FIGHTING:
                return Color.RED;
            default:
                return Color.WHITE;
        }
    }
}
