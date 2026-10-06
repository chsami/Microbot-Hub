package net.runelite.client.plugins.microbot.bankseller;

import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;

/** Headless overlay checks; never starts the plugin or controls a client. */
public final class BankSellerOverlayTest {
    private static int assertions;

    private BankSellerOverlayTest() {
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        check(new BankSellerConfig() { }.showOverlay(), "Version overlay is enabled by default");
        boolean[] showOverlay = {true};
        BankSellerConfig config = new BankSellerConfig() {
            @Override
            public boolean showOverlay() {
                return showOverlay[0];
            }
        };
        BankSellerOverlay overlay = new BankSellerOverlay(new BankSellerPlugin(), config);
        check(overlay.getPosition() == OverlayPosition.TOP_LEFT, "Overlay defaults to top-left");
        check(overlay.getLayer() == OverlayLayer.ABOVE_WIDGETS, "Overlay stays above bank/GE widgets");
        PluginDescriptor descriptor = BankSellerPlugin.class.getAnnotation(PluginDescriptor.class);
        check(BankSellerPlugin.version.equals(descriptor.version()), "Descriptor uses the running version");

        Graphics2D graphics = new BufferedImage(400, 200, BufferedImage.TYPE_INT_ARGB).createGraphics();
        try {
            Font frameFont = graphics.getFont();
            Dimension first = overlay.render(graphics);
            check(first != null && first.width > 0 && first.height > 0, "Overlay renders a visible panel");
            check(overlay.getPanelComponent().getChildren().isEmpty(), "Rendered rows are cleared");
            graphics.setFont(frameFont);
            Dimension second = overlay.render(graphics);
            // The client's PanelComponent uses the preceding frame's measured
            // child sizes, so its first frame is a layout warm-up.
            graphics.setFont(frameFont);
            Dimension third = overlay.render(graphics);
            check(second.equals(third), "Repeated frames do not accumulate rows: " + second + " vs " + third);

            // Keep one rendered frame's components to inspect the exact version text.
            overlay.setClearChildren(false);
            graphics.setFont(frameFont);
            overlay.render(graphics);
            check(overlay.getPanelComponent().getChildren().size() == 2, "Panel contains title and version");
            LineComponent versionRow = (LineComponent) overlay.getPanelComponent().getChildren().get(1);
            Field right = LineComponent.class.getDeclaredField("right");
            right.setAccessible(true);
            check(BankSellerPlugin.version.equals(right.get(versionRow)), "Overlay shows the loaded plugin version");

            showOverlay[0] = false;
            check(overlay.render(graphics) == null, "Turning the setting off hides the overlay immediately");
            check(overlay.getPanelComponent().getChildren().isEmpty(), "Hiding clears retained overlay rows");

            overlay.setClearChildren(true);
            showOverlay[0] = true;
            graphics.setFont(frameFont);
            Dimension enabledAgain = overlay.render(graphics);
            check(enabledAgain != null && enabledAgain.width > 0 && enabledAgain.height > 0,
                    "Turning the setting on restores the overlay without restarting");
            check(overlay.getPanelComponent().getChildren().isEmpty(),
                    "Re-enabled rendering still clears rows normally");
        } finally {
            graphics.dispose();
        }
        System.out.println("BankSellerOverlayTest passed (" + assertions + " assertions)");
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
