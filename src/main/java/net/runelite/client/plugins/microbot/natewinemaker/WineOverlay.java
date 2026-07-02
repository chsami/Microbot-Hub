package net.runelite.client.plugins.microbot.natewinemaker;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;
import java.text.NumberFormat;
import java.util.Locale;


public class WineOverlay extends OverlayPanel {

    @Inject
    WineOverlay(WinePlugin plugin) {
        super(plugin);
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(275, 800));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Nate's Wine Maker")
                    .color(Color.magenta)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left(Microbot.status)
                    .right("version: " + WinePlugin.version)
                    .build());

            NumberFormat fmt = NumberFormat.getIntegerInstance(Locale.ENGLISH);

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Wines made:")
                    .right(fmt.format(WineScript.getWinesMade()))
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("XP to 99:")
                    .right(fmt.format(WineScript.getXpToMax()))
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Wines to 99:")
                    .right(fmt.format(WineScript.getWinesToMax()))
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Est. time to 99:")
                    .right(WineScript.getTimeToMax())
                    .build());


        } catch (Exception ex) {
            Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
        }
        return super.render(graphics);
    }
}
