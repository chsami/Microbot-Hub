package net.runelite.client.plugins.microbot.eventdismiss;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class EventDismissOverlay extends OverlayPanel {

    private final EventDismissConfig config;

    @Inject
    EventDismissOverlay(EventDismissPlugin plugin, EventDismissConfig config) {
        super(plugin);
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!config.showOverlay()) {
            return null;
        }

        boolean lampFeaturesActive = config.genieAction() == EventAction.ACCEPT
                || config.countCheckAction() == EventAction.ACCEPT
                || config.checkForLamps();

        if (!lampFeaturesActive) {
            return null;
        }

        panelComponent.setPreferredSize(new Dimension(200, 0));

        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Event Dismiss")
                .color(Color.CYAN)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Lamp Skill:")
                .right(config.lampSkill().getName())
                .rightColor(Color.YELLOW)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Lamps Used:")
                .right(String.valueOf(LampUtility.getLampsUsed()))
                .rightColor(Color.GREEN)
                .build());

        if (LampUtility.getLampsUsed() > 0 && LampUtility.getLastLampTime() > 0) {
            long secondsAgo = (System.currentTimeMillis() - LampUtility.getLastLampTime()) / 1000;
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Last Lamp:")
                    .right(formatDuration(secondsAgo))
                    .rightColor(Color.LIGHT_GRAY)
                    .build());
        }

        return super.render(graphics);
    }

    private static String formatDuration(long seconds) {
        if (seconds < 60) {
            return seconds + "s ago";
        }
        return (seconds / 60) + "m ago";
    }
}
