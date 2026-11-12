package net.runelite.client.plugins.microbot.droefficientwalker;

import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

import javax.inject.Inject;
import java.awt.*;
import java.util.List;
import java.util.Set;

/**
 * Draws route distances and active transport markers in the scene.
 */
final class SceneRouteOverlay extends Overlay
{
    private static final Color TRANSPORT_OUTLINE = new Color(205, 128, 255);
    private static final Color TRANSPORT_FILL = new Color(205, 128, 255, 46);
    private static final BasicStroke ROUTE_STROKE = new BasicStroke(2.0f);
    private static final Font LABEL_FONT = FontManager.getRunescapeSmallFont().deriveFont(12.0f);

    private final Client client;
    private final EfficientWalker walker;
    private final EfficientWalkerConfig config;

    @Inject
    private SceneRouteOverlay(Client client, EfficientWalker walker, EfficientWalkerConfig config)
    {
        this.client = client;
        this.walker = walker;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.drawRouteOnTiles())
        {
            return null;
        }
        Player player = this.client.getLocalPlayer();
        WorldView worldView = player == null ? null : player.getWorldView();
        if (worldView == null)
        {
            return null;
        }

        List<WorldPoint> path = this.walker.getRemainingRoute();
        int[] distances = remainingWalkingDistances(path);
        Set<WorldPoint> transportTiles = this.walker.getTransportEndpoints();

        for (int index = 0; index < path.size(); index++)
        {
            WorldPoint point = path.get(index);
            if (point.getPlane() != worldView.getPlane())
            {
                continue;
            }

            LocalPoint localPoint = LocalPoint.fromWorld(worldView, point);
            Polygon polygon = localPoint == null ? null : Perspective.getCanvasTilePoly(this.client, localPoint);
            if (polygon == null)
            {
                continue;
            }

            Color color = routeColor(distances[0] - distances[index]);
            OverlayUtil.renderPolygon(
                graphics,
                polygon,
                color,
                new Color(color.getRed(), color.getGreen(), color.getBlue(), 35),
                ROUTE_STROKE);
        }

        renderActiveTransport(graphics, worldView);

        Font oldFont = graphics.getFont();
        graphics.setFont(LABEL_FONT);
        for (int index = 0; index < path.size(); index++)
        {
            WorldPoint point = path.get(index);
            if (!shouldLabelDistance(distances[index])
                || point.getPlane() != worldView.getPlane()
                || transportTiles.contains(point))
            {
                continue;
            }

            LocalPoint localPoint = LocalPoint.fromWorld(worldView, point);
            if (localPoint == null || Perspective.getCanvasTilePoly(this.client, localPoint) == null)
            {
                continue;
            }

            renderOutlinedTextAtLocalPoint(graphics, localPoint, Integer.toString(distances[index]), 0, Color.WHITE);
        }
        graphics.setFont(oldFont);
        return null;
    }

    /** Cumulative contiguous walking distance remaining at each route point. */
    static int[] remainingWalkingDistances(List<WorldPoint> path)
    {
        int[] distances = new int[path.size()];
        for (int index = path.size() - 2; index >= 0; index--)
        {
            distances[index] = distances[index + 1]
                + (path.get(index).distanceTo(path.get(index + 1)) == 1 ? 1 : 0);
        }
        return distances;
    }

    /** Show a label for the final five tiles and then every fifth tile. */
    static boolean shouldLabelDistance(int distance)
    {
        return distance <= 5 || distance % 5 == 0;
    }

    /** Route color gradient: green near the player, redder toward 25+ walked tiles. */
    static Color routeColor(int walkedDistance)
    {
        float hue = (120.0f - 90.0f * Math.min(25, Math.max(0, walkedDistance)) / 25.0f) / 360.0f;
        return Color.getHSBColor(hue, 0.8f, 1.0f);
    }

    /** Draw the currently selected non-walking transport marker and its two-line caption. */
    private void renderActiveTransport(Graphics2D graphics, WorldView worldView)
    {
        EfficientWalker.TransportMarker transport = this.walker.getActiveTransportMarker();
        if (transport == null || transport.point.getPlane() != worldView.getPlane())
        {
            return;
        }

        LocalPoint localPoint = LocalPoint.fromWorld(worldView, transport.point);
        if (localPoint == null)
        {
            return;
        }

        Polygon polygon = Perspective.getCanvasTilePoly(this.client, localPoint);
        if (polygon == null)
        {
            return;
        }

        OverlayUtil.renderPolygon(graphics, polygon, TRANSPORT_OUTLINE, TRANSPORT_FILL, ROUTE_STROKE);

        Font oldFont = graphics.getFont();
        graphics.setFont(LABEL_FONT);
        FontMetrics metrics = graphics.getFontMetrics();
        int firstLineOffset = -metrics.getHeight() + metrics.getAscent();

        renderOutlinedTextAtLocalPoint(graphics, localPoint, displayAction(transport.action), firstLineOffset, TRANSPORT_OUTLINE);
        renderOutlinedTextAtLocalPoint(graphics, localPoint, transportLabel(transport.action, transport.target),
            firstLineOffset + metrics.getHeight(), TRANSPORT_OUTLINE);
        graphics.setFont(oldFont);
    }

    /** Draw outlined scene text at a tile, with a caller-supplied vertical offset. */
    private void renderOutlinedTextAtLocalPoint(Graphics2D graphics, LocalPoint localPoint, String text, int yOffset, Color color)
    {
        Point location = Perspective.getCanvasTextLocation(this.client, graphics, localPoint, text, 0);
        if (location == null)
        {
            return;
        }

        int x = location.getX();
        int y = location.getY() + yOffset;

        graphics.setColor(Color.BLACK);
        graphics.drawString(text, x - 1, y);
        graphics.drawString(text, x + 1, y);
        graphics.drawString(text, x, y - 1);
        graphics.drawString(text, x, y + 1);
        graphics.setColor(color);
        graphics.drawString(text, x, y);
    }

    /** First transport caption line. */
    static String displayAction(String action)
    {
        return action.length() <= 10 ? action : "Interact";
    }

    /** Shorten a transport target to a compact scene-overlay label. */
    static String displayTarget(String target)
    {
        if (target.length() <= 10)
        {
            return target;
        }

        String tail = target.substring(target.lastIndexOf(' ') + 1);
        if (tail.length() <= 10)
        {
            return Character.toUpperCase(tail.charAt(0)) + tail.substring(1);
        }
        return "Obstacle";
    }

    /** Normalize teleport target labels before applying the compact target formatter. */
    static String transportLabel(String action, String target)
    {
        if ("Teleport".equals(action))
        {
            if (target.endsWith(" Teleport"))
            {
                target = target.substring(0, target.length() - " Teleport".length());
            }
            else if (target.startsWith("Teleport to "))
            {
                target = target.substring("Teleport to ".length());
            }
        }
        return displayTarget(target);
    }
}
