package net.runelite.client.plugins.microbot.droslayer.walker;

import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.worldmap.WorldMapOverlay;

import javax.inject.Inject;
import java.awt.*;
import java.util.List;

/**
 * World-map route overlay.
 */
final class WorldMapRouteOverlay extends Overlay
{
    private static final BasicStroke ROUTE_STROKE = new BasicStroke(2.0f);

    private final Client client;
    private final EfficientWalker walker;
    private final WorldMapOverlay worldMapOverlay;

    @Inject
    WorldMapRouteOverlay(Client client, EfficientWalker walker, WorldMapOverlay worldMapOverlay)
    {
        this.client = client;
        this.walker = walker;
        this.worldMapOverlay = worldMapOverlay;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.MANUAL);
        drawAfterInterface(595);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        Widget worldMap = this.client.getWidget(38993927);
        List<List<WorldPoint>> paths = walker.getRouteSegments();
        if (worldMap == null || paths.isEmpty())
        {
            return null;
        }

        Shape oldClip = graphics.getClip();
        graphics.clip(worldMap.getBounds());
        graphics.setColor(Color.ORANGE);
        graphics.setStroke(ROUTE_STROKE);

        for (List<WorldPoint> path : paths)
        {
            if (path.size() < 2)
            {
                continue;
            }

            Point previous = worldMapOverlay.mapWorldPointToGraphicsPoint(path.get(0));
            for (int index = 1; index < path.size(); index++)
            {
                Point next = worldMapOverlay.mapWorldPointToGraphicsPoint(path.get(index));
                if (previous != null && next != null)
                {
                    graphics.drawLine(previous.getX(), previous.getY(), next.getX(), next.getY());
                }
                previous = next;
            }
        }

        graphics.setClip(oldClip);
        return null;
    }
}
