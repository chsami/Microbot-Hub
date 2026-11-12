package net.runelite.client.plugins.microbot.droefficientwalker.engine;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.worldmap.WorldMapOverlay;

final class WorldMapRouteOverlay extends Overlay {
   private static final BasicStroke ROUTE_STROKE = new BasicStroke(2.0F);
   private final Client client;
   private final WalkerEngine walker;
   private final WorldMapOverlay worldMapOverlay;

   @Inject
   private WorldMapRouteOverlay(Client client, WalkerEngine walker, WorldMapOverlay worldMapOverlay) {
      this.client = client;
      this.walker = walker;
      this.worldMapOverlay = worldMapOverlay;
      this.setPosition(OverlayPosition.DYNAMIC);
      this.setLayer(OverlayLayer.MANUAL);
      this.drawAfterInterface(595);
   }

   @Override
   public Dimension render(Graphics2D graphics) {
      Widget mapWidget = this.client.getWidget(38993927);
      List<List<WorldPoint>> routeSegments = this.walker.getRouteSegments();
      if (mapWidget != null && !routeSegments.isEmpty()) {
         Shape previousClip = graphics.getClip();
         graphics.clip(mapWidget.getBounds());
         graphics.setColor(Color.ORANGE);
         graphics.setStroke(ROUTE_STROKE);

         for (List<WorldPoint> segment : routeSegments) {
            if (segment.size() >= 2) {
               Point previousPoint = this.worldMapOverlay.mapWorldPointToGraphicsPoint(segment.get(0));

               for (int waypointIndex = 1; waypointIndex < segment.size(); waypointIndex++) {
                  Point point = this.worldMapOverlay.mapWorldPointToGraphicsPoint(segment.get(waypointIndex));
                  if (previousPoint != null && point != null) {
                     graphics.drawLine(previousPoint.getX(), previousPoint.getY(), point.getX(), point.getY());
                  }

                  previousPoint = point;
               }
            }
         }

         graphics.setClip(previousClip);
         return null;
      } else {
         return null;
      }
   }
}
