package net.runelite.client.plugins.microbot.hsblackjack;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class HSBlackJackOverlay extends OverlayPanel {

    private final HSBlackJackPlugin plugin;
    private final HSBlackJackConfig config;

    @Inject
    HSBlackJackOverlay(HSBlackJackPlugin plugin, HSBlackJackConfig config) {
        super(plugin);
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            HSBlackJackScript script = plugin.getScript();

            panelComponent.setPreferredSize(new Dimension(260, 340));

            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("HSBlackJack V" + HSBlackJackPlugin.version)
                    .color(Color.GREEN)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Blackjack:")
                    .right(config.blackjackType().toString())
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Status:")
                    .right(script.state)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Runtime:")
                    .right(script.getElapsedTime())
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Knock-outs:")
                    .right(String.valueOf(script.getKnockoutAttempts()))
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Pickpockets:")
                    .right(String.valueOf(script.getPickpocketAttempts()))
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("XP gained:")
                    .right(String.valueOf(script.getXpGained()))
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("XP/hr:")
                    .right(String.format("%.0f", script.getXpPerHour()))
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("--- History ---")
                    .build());

            for (String entry : script.getHistory()) {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left(entry)
                        .build());
            }

        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
        return super.render(graphics);
    }
}