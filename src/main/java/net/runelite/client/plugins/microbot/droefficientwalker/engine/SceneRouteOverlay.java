package net.runelite.client.plugins.microbot.droefficientwalker.engine;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

final class SceneRouteOverlay extends Overlay {
   private static final Color TRANSPORT_OUTLINE;
   private static final Color TRANSPORT_FILL;
   private static final BasicStroke ROUTE_STROKE;
   private static final Font LABEL_FONT;
   private final Client client;
   private final WalkerEngine walker;

   @Inject
   private SceneRouteOverlay(Client client, WalkerEngine walker) {
      this.client = client;
      this.walker = walker;
      this.setPosition(OverlayPosition.DYNAMIC);
      this.setLayer(OverlayLayer.ABOVE_SCENE);
   }

   @Override
   public Dimension render(Graphics2D graphics) {
      Player player = this.client.getLocalPlayer();
      WorldView worldView = player == null ? null : player.getWorldView();
      if (worldView == null) {
         return null;
      }

      List<WorldPoint> route = this.walker.getRemainingRoute();
      int[] remainingDistances = remainingWalkingDistances(route);
      Set<WorldPoint> transportEndpoints = this.walker.getTransportEndpoints();

      for (int waypointIndex = 0; waypointIndex < route.size(); waypointIndex++) {
         WorldPoint waypoint = route.get(waypointIndex);
         if (waypoint.getPlane() == worldView.getPlane()) {
            LocalPoint localPoint = LocalPoint.fromWorld(worldView, waypoint);
            Polygon tilePolygon = localPoint == null ? null : Perspective.getCanvasTilePoly(this.client, localPoint);
            if (tilePolygon != null) {
               Color outline = routeColor(remainingDistances[0] - remainingDistances[waypointIndex]);
               OverlayUtil.renderPolygon(graphics, tilePolygon, outline, new Color(outline.getRed(), outline.getGreen(), outline.getBlue(), 35), ROUTE_STROKE);
            }
         }
      }

      this.renderActiveTransport(graphics, worldView);
      Font previousFont = graphics.getFont();
      graphics.setFont(LABEL_FONT);

      for (int labelIndex = 0; labelIndex < route.size(); labelIndex++) {
         WorldPoint labelPoint = route.get(labelIndex);
         if (shouldLabelDistance(remainingDistances[labelIndex]) && labelPoint.getPlane() == worldView.getPlane() && !transportEndpoints.contains(labelPoint)) {
            LocalPoint localLabelPoint = LocalPoint.fromWorld(worldView, labelPoint);
            if (localLabelPoint != null && Perspective.getCanvasTilePoly(this.client, localLabelPoint) != null) {
               this.renderOutlinedTextAtLocalPoint(graphics, localLabelPoint, Integer.toString(remainingDistances[labelIndex]), 0, Color.WHITE);
            }
         }
      }

      graphics.setFont(previousFont);
      return null;
   }

   static int[] remainingWalkingDistances(List<WorldPoint> route) {
      int[] distances = new int[route.size()];

      for (int waypointIndex = route.size() - 2; waypointIndex >= 0; waypointIndex--) {
         distances[waypointIndex] = distances[waypointIndex + 1] + (route.get(waypointIndex).distanceTo(route.get(waypointIndex + 1)) == 1 ? 1 : 0);
      }

      return distances;
   }

   static boolean shouldLabelDistance(int distance) {
      return distance <= 5 || distance % 5 == 0;
   }

   static Color routeColor(int distanceFromStart) {
      return Color.getHSBColor((120.0F - 90.0F * Math.min(25, Math.max(0, distanceFromStart)) / 25.0F) / 360.0F, 0.8F, 1.0F);
   }

   private void renderActiveTransport(Graphics2D graphics, WorldView worldView) {
      WalkerEngine.TransportMarker marker = this.walker.getActiveTransportMarker();
      if (marker == null || marker.point.getPlane() != worldView.getPlane()) {
         return;
      }
      LocalPoint localPoint = LocalPoint.fromWorld(worldView, marker.point);
      Polygon tilePolygon = localPoint == null ? null : Perspective.getCanvasTilePoly(this.client, localPoint);
      if (tilePolygon == null) {
         return;
      }

      OverlayUtil.renderPolygon(graphics, tilePolygon, TRANSPORT_OUTLINE, TRANSPORT_FILL, ROUTE_STROKE);
      Font previousFont = graphics.getFont();
      graphics.setFont(LABEL_FONT);
      FontMetrics metrics = graphics.getFontMetrics();
      int firstLineOffset = -metrics.getHeight() + metrics.getAscent();
      this.renderOutlinedTextAtLocalPoint(graphics, localPoint, displayAction(marker.action), firstLineOffset, TRANSPORT_OUTLINE);
      this.renderOutlinedTextAtLocalPoint(graphics, localPoint, transportLabel(marker.action, marker.target),
         firstLineOffset + metrics.getHeight(), TRANSPORT_OUTLINE);
      graphics.setFont(previousFont);
   }

   private void renderOutlinedTextAtLocalPoint(Graphics2D graphics, LocalPoint localPoint, String text, int verticalOffset, Color color) {
      Point canvasPoint = Perspective.getCanvasTextLocation(this.client, graphics, localPoint, text, 0);
      if (canvasPoint == null) {
         return;
      }
      int x = canvasPoint.getX();
      int y = canvasPoint.getY() + verticalOffset;
      graphics.setColor(Color.BLACK);
      graphics.drawString(text, x - 1, y);
      graphics.drawString(text, x + 1, y);
      graphics.drawString(text, x, y - 1);
      graphics.drawString(text, x, y + 1);
      graphics.setColor(color);
      graphics.drawString(text, x, y);
   }

   static String displayAction(String action) {
      return action.length() <= 10 ? action : "Interact";
   }

   static String displayTarget(String target) {
      if (target.length() <= 10) {
         return target;
      }
      String finalWord = target.substring(target.lastIndexOf(32) + 1);
      if (finalWord.length() <= 10) {
         return Character.toUpperCase(finalWord.charAt(0)) + finalWord.substring(1);
      }
      return "Obstacle";
   }

   static String transportLabel(String action, String target) {
      if ("Teleport".equals(action)) {
         if (target.endsWith(" Teleport")) {
            target = target.substring(0, target.length() - " Teleport".length());
         } else if (target.startsWith("Teleport to ")) {
            target = target.substring("Teleport to ".length());
         }
      }

      return displayTarget(target);
   }

   static {
      TRANSPORT_OUTLINE = new Color(205, 128, 255);
      TRANSPORT_FILL = new Color(205, 128, 255, 46);
      ROUTE_STROKE = new BasicStroke(2.0F);
      LABEL_FONT = FontManager.getRunescapeSmallFont().deriveFont(12.0F);
   }
}
