package net.runelite.client.plugins.microbot.droefficientwalker;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;

public final class MinimapOrbs
{
    private static final int[] ORB_WIDGETS = {
        InterfaceID.Orbs.XP_DROPS, InterfaceID.Orbs.ORB_HEALTH, InterfaceID.Orbs.ORB_PRAYER,
        InterfaceID.Orbs.ORB_RUNENERGY, InterfaceID.Orbs.ORB_SPECENERGY, InterfaceID.Orbs.ORB_STORE,
        InterfaceID.Orbs.ORB_CONTENTRECOM, InterfaceID.Orbs.WORLDMAP, InterfaceID.Orbs.WIKI,
        InterfaceID.ToplevelOsrsStretch.COMPASSCLICK, InterfaceID.ToplevelPreEoc.COMPASSCLICK, InterfaceID.Toplevel.COMPASSCLICK
    };

    private MinimapOrbs() { }

    public static boolean covers(Client client, Point point)
    {
        if (client == null || point == null) return false;
        List<Rectangle> bounds = new ArrayList<>();
        for (int widgetId : ORB_WIDGETS)
        {
            Widget widget = client.getWidget(widgetId);
            if (widget != null && !widget.isHidden()) bounds.add(widget.getBounds());
        }
        return isPointInsideAny(point, bounds);
    }

    public static boolean coversOnClientThread(Point point)
    {
        if (point == null || Microbot.getClientThread() == null) return false;
        return Microbot.getClientThread().runOnClientThreadOptional(() -> covers(Microbot.getClient(), point)).orElse(false);
    }

    static boolean isPointInsideAny(Point point, List<Rectangle> bounds)
    {
        if (point == null) return false;
        for (Rectangle rectangle : bounds)
        {
            if (rectangle != null && rectangle.contains(point.getX(), point.getY())) return true;
        }
        return false;
    }
}
