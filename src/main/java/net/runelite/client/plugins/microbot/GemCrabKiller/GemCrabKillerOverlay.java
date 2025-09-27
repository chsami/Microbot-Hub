package net.runelite.client.plugins.microbot.GemCrabKiller;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentOrientation;
import net.runelite.client.ui.overlay.components.ImageComponent;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.SplitComponent;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import java.awt.*;
import java.awt.image.BufferedImage;

public class GemCrabKillerOverlay extends OverlayPanel {
    private static final Color TITLE_COLOR = Color.decode("#a4ffff");
    private static final Color BACKGROUND_COLOR = new Color(0, 0, 0, 150);
    private static final Color NORMAL_COLOR = Color.WHITE;
    private static final Color WARNING_COLOR = Color.YELLOW;
    private static final Color DANGER_COLOR = Color.RED;
    private static final Color SUCCESS_COLOR = Color.GREEN;
    private final GemCrabKillerPlugin plugin;
    private final ImageComponent image;

    @Inject
    GemCrabKillerOverlay(GemCrabKillerPlugin plugin) {
        super(plugin);
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
        image = new ImageComponent(getCrabImageFromResources());
        this.plugin = plugin;
    }

    private BufferedImage getCrabImageFromResources() {
        try {
            var img = ImageUtil.loadImageResource(GemCrabKillerPlugin.class, "/net/runelite/client/plugins/microbot/GemCrabKiller/GemStoneCrab.png");
            return ImageUtil.resizeImage(img, 24, 24, true);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(190, 300));
            panelComponent.setBackgroundColor(BACKGROUND_COLOR);
            // Title
            final ImageComponent imageComponent = new ImageComponent(getCrabImageFromResources());
            final LineComponent title = LineComponent.builder()
                    .left(" TaF's Gemstone Crab Killer")
                    .leftColor(Color.white)
                    .build();
            final SplitComponent iconTitleSplit = SplitComponent.builder()
                    .first(imageComponent)
                    .second(title)
                    .orientation(ComponentOrientation.HORIZONTAL)
                    .gap(new Point(2, 0))
                    .build();
            panelComponent.getChildren().add(iconTitleSplit);
            // Script running time
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Runtime:")
                    .right(plugin.getTimeRunning())
                    .rightColor(NORMAL_COLOR)
                    .build());
            // State information
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("State:")
                    .right(plugin.gemCrabKillerScript.gemCrabKillerState.name())
                    .rightColor(getStateColor(plugin.gemCrabKillerScript.gemCrabKillerState))
                    .build());
            // Total kills
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Total Kills:")
                    .right(String.valueOf(plugin.gemCrabKillerScript.totalCrabKills))
                    .rightColor(NORMAL_COLOR)
                    .build());

            // XP information
            var xpGained = plugin.getXpGained();
            var xpPerHour = plugin.getXpPerHour();
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("XP Gained:")
                    .right(formatNumber(xpGained))
                    .rightColor(NORMAL_COLOR)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("XP/Hour:")
                    .right(formatNumber(xpPerHour))
                    .rightColor(xpPerHour > 0 ? SUCCESS_COLOR : NORMAL_COLOR)
                    .build());

            // Footer with version
            panelComponent.getChildren().add(LineComponent.builder()
                    .right("v" + plugin.version)
                    .rightColor(new Color(160, 160, 160))
                    .build());

        } catch (Exception ex) {
            Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
        }
        return super.render(graphics);
    }

    private String formatNumber(long number) {
        return String.format("%,d", number);
    }

    private Color getStateColor(GemCrabKillerState state) {
        if (state == null) return NORMAL_COLOR;

        switch (state) {
            case FIGHTING:
                return DANGER_COLOR;
            case WALKING:
                return WARNING_COLOR;
            case BANKING:
                return NORMAL_COLOR;
            default:
                return NORMAL_COLOR;
        }
    }
}
