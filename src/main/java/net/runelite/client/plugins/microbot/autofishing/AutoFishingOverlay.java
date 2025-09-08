package net.runelite.client.plugins.microbot.autofishing;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class AutoFishingOverlay extends OverlayPanel {

    private final AutoFishingPlugin plugin;

    @Inject
    AutoFishingOverlay(AutoFishingPlugin plugin)
    {
        super(plugin);
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(250, 200));
            panelComponent.setBackgroundColor(new Color(0, 0, 0, 150));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("AutoFishing v" + plugin.version)
                    .color(Color.decode("#77DD77"))
                    .build());

            panelComponent.getChildren().add(LineComponent.builder().build());

            // Script state
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("State:")
                    .right(plugin.fishingScript.getCurrentState().toString())
                    .build());

            // XP/h
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("XP/h:")
                    .right(String.valueOf(plugin.getXpPerHour()))
                    .build());

            // XP gained
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("XP gained:")
                    .right(String.valueOf(plugin.getXpGained()))
                    .build());

            // Runtime
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Runtime:")
                    .right(plugin.getFormattedRuntime())
                    .build());
        } catch(Exception ex) {
            System.out.println("AutoFishingOverlay error: " + ex.getMessage());
        }
        return super.render(graphics);
    }
}