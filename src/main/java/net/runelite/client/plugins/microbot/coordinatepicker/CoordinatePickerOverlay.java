package net.runelite.client.plugins.microbot.coordinatepicker;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ButtonComponent;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Clipboard;
import java.util.List;

public class CoordinatePickerOverlay extends OverlayPanel {
    private final CoordinatePickerPlugin plugin;
    private final CoordinatePickerConfig config;
    private final ButtonComponent clearButton;
    private final ButtonComponent copyButton;
    private final ButtonComponent exportButton;

    @Inject
    CoordinatePickerOverlay(CoordinatePickerPlugin plugin, CoordinatePickerConfig config) {
        super(plugin);
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();

        // Create buttons
        clearButton = new ButtonComponent("Clear All");
        clearButton.setPreferredSize(new Dimension(80, 20));
        clearButton.setParentOverlay(this);
        clearButton.setFont(FontManager.getRunescapeSmallFont());
        clearButton.setOnClick(() -> {
            plugin.clearCoordinates();
        });

        copyButton = new ButtonComponent("Copy List");
        copyButton.setPreferredSize(new Dimension(80, 20));
        copyButton.setParentOverlay(this);
        copyButton.setFont(FontManager.getRunescapeSmallFont());
        copyButton.setOnClick(() -> {
            String coordinates = plugin.getCoordinatesAsList();
            StringSelection selection = new StringSelection(coordinates);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(selection, null);
            Microbot.showMessage("Coordinates copied to clipboard!");
        });

        exportButton = new ButtonComponent("Copy Text");
        exportButton.setPreferredSize(new Dimension(80, 20));
        exportButton.setParentOverlay(this);
        exportButton.setFont(FontManager.getRunescapeSmallFont());
        exportButton.setOnClick(() -> {
            String coordinates = plugin.getCoordinatesAsString();
            StringSelection selection = new StringSelection(coordinates);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(selection, null);
            Microbot.showMessage("Coordinate text copied to clipboard!");
        });
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!config.showOverlay()) {
            return null;
        }

        try {
            panelComponent.setPreferredSize(new Dimension(250, 300));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Coordinate Picker v" + plugin.getClass().getAnnotation(PluginDescriptor.class).version())
                    .color(Color.CYAN)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder().build());

            List<WorldPoint> coordinates = plugin.getPickedCoordinates();
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Coordinates:")
                    .right(String.format("%d/%d", coordinates.size(), config.maxCoordinates()))
                    .build());

            if (coordinates.isEmpty()) {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Right-click a tile")
                        .build());
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("and select 'Add coordinate'")
                        .build());
            } else {
                // Show last few coordinates
                int maxDisplay = Math.min(8, coordinates.size());
                for (int i = coordinates.size() - maxDisplay; i < coordinates.size(); i++) {
                    WorldPoint wp = coordinates.get(i);
                    panelComponent.getChildren().add(LineComponent.builder()
                            .left(String.format("%d.", i + 1))
                            .right(String.format("(%d, %d, %d)", wp.getX(), wp.getY(), wp.getPlane()))
                            .build());
                }

                if (coordinates.size() > maxDisplay) {
                    panelComponent.getChildren().add(LineComponent.builder()
                            .left("...")
                            .build());
                }

                panelComponent.getChildren().add(LineComponent.builder().build());

                // Add buttons
                panelComponent.getChildren().add(clearButton);
                panelComponent.getChildren().add(copyButton);
                panelComponent.getChildren().add(exportButton);
            }

            panelComponent.getChildren().add(LineComponent.builder().build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Status:")
                    .right(Microbot.status)
                    .build());

        } catch (Exception ex) {
            System.out.println("Error in coordinate picker overlay: " + ex.getMessage());
        }
        return super.render(graphics);
    }
}
