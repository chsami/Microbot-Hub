package net.runelite.client.plugins.microbot.LT.Pizza;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public class PizzaOverlay extends OverlayPanel {
    @Inject
    PizzaOverlay(PizzaPlugin plugin) {
        super(plugin);
        this.setPosition(OverlayPosition.TOP_LEFT);
        this.setNaughty();
    }

    public Dimension render(Graphics2D graphics) {
        try {
            this.panelComponent.setPreferredSize(new Dimension(200, 300));
            this.panelComponent.getChildren().add(TitleComponent.builder().text("Pizza").color(Color.GREEN).build());
            this.panelComponent.getChildren().add(LineComponent.builder().build());
            this.panelComponent.getChildren().add(LineComponent.builder().left(Microbot.status).build());
        } catch (Exception var3) {
            System.out.println(var3.getMessage());
        }

        return super.render(graphics);
    }
}