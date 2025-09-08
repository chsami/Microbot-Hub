package net.runelite.client.plugins.microbot.coordinatepicker;

import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

import javax.inject.Inject;
import java.awt.*;
import java.util.List;

public class CoordinatePickerTileOverlay extends Overlay {
    private final CoordinatePickerPlugin plugin;
    private final CoordinatePickerConfig config;

    @Inject
    public CoordinatePickerTileOverlay(CoordinatePickerPlugin plugin, CoordinatePickerConfig config) {
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
        this.plugin = plugin;
        this.config = config;
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!config.showTileOverlays()) {
            return null;
        }

        List<WorldPoint> coordinates = plugin.getPickedCoordinates();
        if (coordinates.isEmpty()) {
            return null;
        }

        Color overlayColor = config.overlayColor();

        for (int i = 0; i < coordinates.size(); i++) {
            WorldPoint wp = coordinates.get(i);
            String text = String.valueOf(i + 1);
            
            // Convert WorldPoint to LocalPoint for rendering
            LocalPoint localPoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), wp);
            if (localPoint != null) {
                // Get the tile polygon and render a tile outline with text
                Polygon tilePoly = Perspective.getCanvasTilePoly(Microbot.getClient(), localPoint);
                if (tilePoly != null) {
                    OverlayUtil.renderPolygon(graphics, tilePoly, overlayColor);
                    
                    // Render text at the center of the tile
                    Point textLocation = Perspective.getCanvasTextLocation(
                            Microbot.getClient(), graphics, localPoint, text, 0);
                    if (textLocation != null) {
                        OverlayUtil.renderTextLocation(graphics, textLocation, text, Color.WHITE);
                    }
                }
            }
        }

        return null;
    }
}
