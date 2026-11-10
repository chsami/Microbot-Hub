package net.runelite.client.plugins.microbot.droslayer.walker;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Perspective;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.plugins.microbot.droslayer.walker.sailing.core.SeaPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** Draw the immutable sea route in the ocean world-view, never on the instanced boat deck. */
final class EfficientSailingSceneOverlay extends Overlay {
    private final EfficientSailingController navigator;
    private final EfficientSailingConfig config;
    private final Client client;
    EfficientSailingSceneOverlay(EfficientSailingController navigator, EfficientSailingConfig config, Client client) {
        this.navigator = navigator; this.config = config; this.client = client;
        setPosition(OverlayPosition.DYNAMIC); setLayer(OverlayLayer.ABOVE_WIDGETS);
    }
    @Override public Dimension render(Graphics2D graphics) {
        if (!config.showOverlay() || !navigator.isBusy() || client.getGameState() != GameState.LOGGED_IN) return null;
        WorldView ocean = client.getTopLevelWorldView();
        if (ocean == null || ocean.getPlane() != 0) return null;
        EfficientSailingController.View view = navigator.getView();
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            for (SeaPoint tile : tiles(view.localRoute)) draw(g, ocean, tile, new Color(50, 220, 130), false);
            draw(g, ocean, view.destination, Color.ORANGE, true);
        } finally { g.dispose(); }
        return null;
    }
    private void draw(Graphics2D g, WorldView ocean, SeaPoint tile, Color color, boolean destination) {
        if (tile == null) return;
        LocalPoint local = LocalPoint.fromWorld(ocean, tile.x, tile.y);
        if (local == null) return;
        Polygon polygon = Perspective.getCanvasTilePoly(client, local);
        if (polygon != null) {
            Graphics2D scene = (Graphics2D) g.create();
            try {
                scene.clipRect(client.getViewportXOffset(), client.getViewportYOffset(),
                        client.getViewportWidth(), client.getViewportHeight());
                scene.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 45)); scene.fillPolygon(polygon);
                scene.setColor(color); scene.drawPolygon(polygon);
            } finally { scene.dispose(); }
        }
        net.runelite.api.Point point = Perspective.localToMinimap(client, local);
        if (point != null) {
            g.setColor(color); int size = destination ? 7 : 3;
            g.fillRect(point.getX() - size / 2, point.getY() - size / 2, size, size);
        }
    }
    static Set<SeaPoint> tiles(List<SeaPoint> route) {
        Set<SeaPoint> tiles = new LinkedHashSet<>();
        SeaPoint previous = null;
        for (SeaPoint next : route) {
            if (previous != null) {
                int steps = Math.max(Math.abs(next.x - previous.x), Math.abs(next.y - previous.y));
                for (int i = 1; i < steps && tiles.size() < 512; i++)
                    tiles.add(new SeaPoint(previous.x + (int) Math.round((next.x - previous.x) * (double) i / steps),
                            previous.y + (int) Math.round((next.y - previous.y) * (double) i / steps)));
            }
            if (tiles.size() < 512) tiles.add(next); previous = next;
            if (tiles.size() >= 512) break;
        }
        return tiles;
    }
}
