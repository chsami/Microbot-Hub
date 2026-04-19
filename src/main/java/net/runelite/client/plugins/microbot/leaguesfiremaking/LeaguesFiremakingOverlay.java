package net.runelite.client.plugins.microbot.leaguesfiremaking;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class LeaguesFiremakingOverlay extends OverlayPanel {

    @Inject
    private LeaguesFiremakingScript script;

    @Inject
    public LeaguesFiremakingOverlay() {
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        panelComponent.setPreferredSize(new Dimension(200, 0));

        panelComponent.getChildren().add(TitleComponent.builder()
                .text("AI Firemaking v" + LeaguesFiremakingPlugin.version)
                .color(Color.GREEN)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Status")
                .right(script.getStatus())
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("State")
                .right(script.getState().name())
                .build());

        FireLine line = script.getCurrentLine();
        if (line != null) {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Line length")
                    .right(String.valueOf(line.getLength()))
                    .build());
        }

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Microbot")
                .right(Microbot.status)
                .build());

        return super.render(graphics);
    }
}
