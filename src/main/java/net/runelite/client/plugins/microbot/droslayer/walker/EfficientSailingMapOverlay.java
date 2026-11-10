package net.runelite.client.plugins.microbot.droslayer.walker;

import java.awt.*;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.droslayer.walker.sailing.core.SeaPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.worldmap.WorldMapOverlay;

/** Route is a charted/planned centerline, not a guarantee of uncharted sea safety. */
final class EfficientSailingMapOverlay extends Overlay {
    private final EfficientSailingController navigator;
    private final EfficientSailingConfig config;
    private final WorldMapOverlay map;
    private final Client client;
    EfficientSailingMapOverlay(EfficientSailingController navigator, EfficientSailingConfig config, WorldMapOverlay map, Client client) {
        this.navigator = navigator; this.config = config; this.map = map; this.client = client;
        setPosition(OverlayPosition.DYNAMIC); setLayer(OverlayLayer.ABOVE_WIDGETS);
    }
    @Override public Dimension render(Graphics2D graphics) {
        if (!config.showOverlay() || !navigator.isBusy()) return null;
        Widget widget = client.getWidget(MicrobotSailingFacade.WORLD_MAP_ROOT);
        if (widget == null || widget.isHidden()) return null;
        EfficientSailingController.View view = navigator.getView();
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.clip(widget.getBounds());
            g.setColor(new Color(50,220,130,150));
            g.setStroke(new BasicStroke(1.5f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND,1,new float[]{7,5},0));
            net.runelite.api.Point previous = null;
            for (SeaPoint p : view.route) {
                net.runelite.api.Point next = point(p);
                if (next != null && previous != null) g.drawLine(previous.getX(), previous.getY(), next.getX(), next.getY());
                previous = next;
            }
            g.setColor(Color.GREEN);g.setStroke(new BasicStroke(2.5f));previous=null;
            for(SeaPoint p:view.localRoute) {
                net.runelite.api.Point next=point(p);
                if(next!=null&&previous!=null)g.drawLine(previous.getX(),previous.getY(),next.getX(),next.getY());
                previous=next;
            }
            g.drawString("Dashed: provisional voyage | Solid: observed local route",widget.getBounds().x+12,widget.getBounds().y+22);
            net.runelite.api.Point target = point(view.destination);
            if (target != null) {
                g.drawOval(target.getX() - 5, target.getY() - 5, 10, 10);
                g.drawString("Sail", target.getX() + 8, target.getY() - 6);
            }
        } finally { g.dispose(); }
        return null;
    }
    private net.runelite.api.Point point(SeaPoint p) {
        return p == null ? null : map.mapWorldPointToGraphicsPoint(new WorldPoint(p.x, p.y, 0));
    }
}
