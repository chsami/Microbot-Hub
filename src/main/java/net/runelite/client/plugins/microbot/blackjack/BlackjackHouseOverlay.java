package net.runelite.client.plugins.microbot.blackjack;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

public class BlackjackHouseOverlay extends Overlay
{
    private static final Color BORDER = new Color(55, 195, 210, 160);
    private static final Color FILL = new Color(55, 195, 210, 25);
    private static final BasicStroke STROKE = new BasicStroke(1);
    private final Client client;
    private final BlackjackPlugin plugin;

    @Inject
    BlackjackHouseOverlay(Client client, BlackjackPlugin plugin)
    {
        this.client = client;
        this.plugin = plugin;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        BlackjackScript script = plugin.getScript();
        if (script == null || !script.shouldHighlightBanditHouse() || client.getTopLevelWorldView() == null)
        {
            return null;
        }
        WorldArea house = BlackjackScript.banditHouseArea();
        if (client.getTopLevelWorldView().getPlane() != house.getPlane())
        {
            return null;
        }
        for (int x = house.getX(); x < house.getX() + house.getWidth(); x++)
        {
            for (int y = house.getY(); y < house.getY() + house.getHeight(); y++)
            {
                LocalPoint local = LocalPoint.fromWorld(client.getTopLevelWorldView(), new WorldPoint(x, y, house.getPlane()));
                Polygon polygon = local == null ? null : Perspective.getCanvasTilePoly(client, local);
                if (polygon != null)
                {
                    OverlayUtil.renderPolygon(graphics, polygon, BORDER, FILL, STROKE);
                }
            }
        }
        return null;
    }
}
