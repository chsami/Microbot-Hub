package net.runelite.client.plugins.microbot.lunarplankmake;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class LunarPlankMakeOverlay extends OverlayPanel {

    @Inject
    LunarPlankMakeOverlay(LunarPlankMakePlugin plugin) {
        super(plugin);
        setPosition(OverlayPosition.ABOVE_CHATBOX_RIGHT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        panelComponent.setPreferredSize(new Dimension(200, 300));
        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Plank Make " + LunarPlankMakePlugin.version)
                .color(Color.YELLOW)
                .build());

        // Update to display the combined message
        panelComponent.getChildren().add(LineComponent.builder()
                .left(LunarPlankMakeScript.combinedMessage)
                .build());

        return super.render(graphics);
    }
}