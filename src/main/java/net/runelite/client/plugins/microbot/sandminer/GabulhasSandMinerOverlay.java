package net.runelite.client.plugins.microbot.sandminer;

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

public class GabulhasSandMinerOverlay extends OverlayPanel {
    private static final Color TITLE_COLOR = Color.decode("#ffd700");  // Gold for sand color
    private static final Color BACKGROUND_COLOR = new Color(0, 0, 0, 150);
    private static final Color NORMAL_COLOR = Color.WHITE;
    private static final Color WARNING_COLOR = Color.YELLOW;
    private static final Color SUCCESS_COLOR = Color.GREEN;

    private final GabulhasSandMinerPlugin plugin;
    private final ImageComponent image;

    @Inject
    GabulhasSandMinerOverlay(GabulhasSandMinerPlugin plugin) {
        super(plugin);
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
        image = new ImageComponent(getMiningImageFromResources());
        this.plugin = plugin;
    }

    private BufferedImage getMiningImageFromResources() {
        try {
            var img = ImageUtil.loadImageResource(GabulhasSandMinerPlugin.class,
                    "/net/runelite/client/plugins/microbot/sandminer/sandstone.png");
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

            final ImageComponent imageComponent = new ImageComponent(getMiningImageFromResources());
            final LineComponent title = LineComponent.builder()
                    .left(" Gabulhas Sand Miner")
                    .leftColor(TITLE_COLOR)
                    .build();
            final SplitComponent iconTitleSplit = SplitComponent.builder()
                    .first(imageComponent)
                    .second(title)
                    .orientation(ComponentOrientation.HORIZONTAL)
                    .gap(new Point(2, 0))
                    .build();
            panelComponent.getChildren().add(iconTitleSplit);

            // Runtime
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Runtime:")
                    .right(plugin.getTimeRunning())
                    .rightColor(NORMAL_COLOR)
                    .build());

            // Status
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Status:")
                    .right(GabulhasSandMinerInfo.botStatus.toString().replace("_", " "))
                    .rightColor(getStateColor(GabulhasSandMinerInfo.botStatus))
                    .build());

            // Rocks mined
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Rocks Mined:")
                    .right(formatNumber(plugin.rocksMined))
                    .rightColor(NORMAL_COLOR)
                    .build());

            // Rocks per hour calculation
            long rocksPerHour = calculateRocksPerHour();
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Rocks/Hour:")
                    .right(formatNumber(rocksPerHour))
                    .rightColor(rocksPerHour > 0 ? SUCCESS_COLOR : NORMAL_COLOR)
                    .build());

            // Version footer
            panelComponent.getChildren().add(LineComponent.builder()
                    .right(GabulhasSandMinerPlugin.version)
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

    private long calculateRocksPerHour() {
        if (plugin.scriptStartTime == null) return 0;

        double hoursElapsed = (System.currentTimeMillis() - plugin.scriptStartTime.toEpochMilli()) / 3600000.0;
        if (hoursElapsed <= 0) return 0;

        return (long) (plugin.rocksMined / hoursElapsed);
    }

    private Color getStateColor(GabulhasSandMinerInfo.states state) {
        if (state == null) return NORMAL_COLOR;
        switch (state) {
            case Mining:
                return SUCCESS_COLOR;
            case Depositing:
                return WARNING_COLOR;
            default:
                return NORMAL_COLOR;
        }
    }
}