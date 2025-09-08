package net.runelite.client.plugins.microbot.giantmole;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class GiantMoleOverlay extends OverlayPanel {

    @Inject
    GiantMoleOverlay(GiantMolePlugin plugin)
    {
        super(plugin);
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }
    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(350, 400));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("\uD83E\uDD86 Giant Mole \uD83E\uDD86")
                    .color(Color.ORANGE)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder().build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Giant Mole Location:")
                    .right(GiantMoleScript.getMoleLocation() == null ? GiantMoleScript.isMoleDead() ? "Unknown" : "Close" : GiantMoleScript.getMoleLocation().toString())
                    .build());

            panelComponent.getChildren().add(LineComponent.builder().build());

//            panelComponent.getChildren().add(LineComponent.builder()
//                    .left("Walker target:")
//                    .right(ShortestPathPlugin.getPathfinder() == null ? "Unknown" : ShortestPathPlugin.getPathfinder().getTarget().toString())
//                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("State:")
                    .right(GiantMoleScript.state.toString())
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Is dead:")
                    .right(String.valueOf(GiantMoleScript.isMoleDead()
                    ))
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Version:")
                    .right(GiantMolePlugin.version)
                    .build());


        } catch(Exception ex) {
            System.out.println(ex.getMessage());
        }
        return super.render(graphics);
    }
}
