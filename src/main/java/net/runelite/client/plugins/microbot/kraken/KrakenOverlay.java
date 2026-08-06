package net.runelite.client.plugins.microbot.kraken;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;

public class KrakenOverlay extends OverlayPanel {

    private final KrakenScript script;

    @Inject
    KrakenOverlay(KrakenPlugin plugin, KrakenScript script) {
        super(plugin);
        this.script = script;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(210, 120));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Kraken " + KrakenPlugin.version)
                    .color(Color.CYAN)
                    .build());
            panelComponent.getChildren().add(LineComponent.builder().build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("State:")
                    .right(script.getState().name())
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Kills:")
                    .right(String.valueOf(script.getKillCount()))
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left(Microbot.status == null ? "" : Microbot.status)
                    .build());
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
        return super.render(graphics);
    }
}
