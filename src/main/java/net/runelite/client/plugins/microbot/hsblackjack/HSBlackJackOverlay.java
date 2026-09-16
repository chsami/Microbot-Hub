package net.runelite.client.plugins.microbot.hsblackjack;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ImageComponent;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.imageio.ImageIO;
import javax.inject.Inject;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class HSBlackJackOverlay extends OverlayPanel {

    private static final String IMAGE_PATH = "C:\\Users\\MAIN\\Downloads\\halalskills menaphite thug blackjack.jpg";
    private static final int IMAGE_MAX_WIDTH = 220;

    private final HSBlackJackPlugin plugin;
    private final HSBlackJackConfig config;

    private BufferedImage headerImage;
    private boolean imageLoadAttempted = false;

    @Inject
    HSBlackJackOverlay(HSBlackJackPlugin plugin, HSBlackJackConfig config) {
        super(plugin);
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
    }

    /**
     * Loads the header image from disk once and caches it, scaled down to a
     * sensible width so it doesn't dominate the overlay panel. If the file
     * can't be found/read, we just skip the image silently rather than
     * breaking the rest of the panel.
     */
    private BufferedImage getHeaderImage() {
        if (imageLoadAttempted) {
            return headerImage;
        }
        imageLoadAttempted = true;

        try {
            BufferedImage original = ImageIO.read(new File(IMAGE_PATH));
            if (original == null) {
                return null;
            }

            if (original.getWidth() <= IMAGE_MAX_WIDTH) {
                headerImage = original;
                return headerImage;
            }

            int newHeight = (int) (original.getHeight() * (IMAGE_MAX_WIDTH / (double) original.getWidth()));
            BufferedImage scaled = new BufferedImage(IMAGE_MAX_WIDTH, newHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(original, 0, 0, IMAGE_MAX_WIDTH, newHeight, null);
            g.dispose();
            headerImage = scaled;
        } catch (IOException | RuntimeException ex) {
            System.out.println("HSBlackJack: could not load header image - " + ex.getMessage());
            headerImage = null;
        }

        return headerImage;
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            HSBlackJackScript script = plugin.getScript();

            panelComponent.setPreferredSize(new Dimension(260, 340));

            BufferedImage image = getHeaderImage();
            if (image != null) {
                panelComponent.getChildren().add(new ImageComponent(image));
            }

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