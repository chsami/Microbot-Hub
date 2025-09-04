package net.runelite.client.plugins.microbot.delveprayerhelper;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class DelvePrayerHelperOverlay extends OverlayPanel {

    @Inject
    private DelvePrayerHelperScript delvePrayerHelperScript;

    @Inject
    DelvePrayerHelperOverlay(DelvePrayerHelperPlugin plugin)
    {
        super(plugin);
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(200, 300));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Engin's Delve Prayer Helper V" + DelvePrayerHelperPlugin.version)
                    .color(Color.GREEN)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder().build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Current projectile: " + Microbot.status)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Offensive Prayer on: " + delvePrayerHelperScript.isOffensivePrayerOn())
                    .build());


        } catch(Exception ex) {
            System.out.println(ex.getMessage());
        }
        return super.render(graphics);
    }
}
