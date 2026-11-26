package net.runelite.client.plugins.microbot.irkedmlm;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.irkedmlm.enums.MLMMiningSpot;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Scene overlay for debugging MLM spot geometry: selectable {@link WorldArea} union and blocked tiles.
 */
public class IrkedMLMAreaOverlay extends Overlay {

    private static final Color MINING_FILL = new Color(0, 200, 0, 50);
    private static final Color MINING_OUTLINE = new Color(0, 255, 0, 180);
    private static final Color BLOCKED_FILL = new Color(220, 40, 40, 70);
    private static final Color BLOCKED_OUTLINE = new Color(255, 60, 60, 220);
    private static final Color SESSION_BLOCKED_FILL = new Color(255, 140, 0, 90);
    private static final Color SESSION_BLOCKED_OUTLINE = new Color(255, 180, 0, 230);
    /** What the vein selector believes it can actually walk to right now. */
    private static final Color REACHABLE_FILL = new Color(0, 120, 255, 55);
    private static final Color REACHABLE_OUTLINE = new Color(80, 180, 255, 200);

    private final Client client;
    private final IrkedMLMConfig config;
    private final IrkedMLMScript script;

    @Inject
    IrkedMLMAreaOverlay(Client client, IrkedMLMConfig config, IrkedMLMScript script) {
        this.client = client;
        this.config = config;
        this.script = script;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
        setPriority(Overlay.PRIORITY_LOW);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!config.showMiningAreas()) {
            return null;
        }

        MLMMiningSpot spot = script.getAreaOverlaySpot();
        if (spot == null) {
            return null;
        }

        Set<WorldPoint> miningTiles = collectAreaTiles(spot.getWorldAreas());
        Set<WorldPoint> staticBlocked = new HashSet<>(IrkedMLMMapConstants.permanentRockfallBarriersFor(spot));
        if (spot.isUpstairs()) {
            staticBlocked.addAll(IrkedMLMMapConstants.SHARED_UPPER_ROCKFALL_TILES);
            staticBlocked.addAll(IrkedMLMMapConstants.SHARED_UPPER_ROCKFALL_APPROACH_TILES);
        }
        Set<WorldPoint> sessionBlocked = new HashSet<>(script.getRememberedRockfallTiles());
        sessionBlocked.removeAll(staticBlocked);

        // Blue first, so the green area and red barriers draw over it and stay readable. Blue
        // outside green is the approach margin; green with no blue is in-area but unreachable.
        Set<WorldPoint> reachable = new HashSet<>(script.getReachableTiles());
        renderTiles(graphics, reachable, REACHABLE_FILL, REACHABLE_OUTLINE);

        renderTiles(graphics, miningTiles, MINING_FILL, MINING_OUTLINE);
        renderTiles(graphics, staticBlocked, BLOCKED_FILL, BLOCKED_OUTLINE);
        renderTiles(graphics, sessionBlocked, SESSION_BLOCKED_FILL, SESSION_BLOCKED_OUTLINE);

        return null;
    }

    private static Set<WorldPoint> collectAreaTiles(List<WorldArea> areas) {
        Set<WorldPoint> tiles = new HashSet<>();
        if (areas == null) {
            return tiles;
        }
        for (WorldArea area : areas) {
            if (area == null) {
                continue;
            }
            for (int dx = 0; dx < area.getWidth(); dx++) {
                for (int dy = 0; dy < area.getHeight(); dy++) {
                    tiles.add(new WorldPoint(area.getX() + dx, area.getY() + dy, area.getPlane()));
                }
            }
        }
        return tiles;
    }

    private void renderTiles(Graphics2D graphics, Set<WorldPoint> tiles, Color fill, Color outline) {
        if (tiles == null || tiles.isEmpty()) {
            return;
        }

        graphics.setColor(fill);
        for (WorldPoint wp : tiles) {
            Polygon poly = tilePoly(wp);
            if (poly != null) {
                graphics.fill(poly);
            }
        }

        graphics.setColor(outline);
        graphics.setStroke(new BasicStroke(1f));
        for (WorldPoint wp : tiles) {
            Polygon poly = tilePoly(wp);
            if (poly != null) {
                graphics.draw(poly);
            }
        }
    }

    private Polygon tilePoly(WorldPoint wp) {
        LocalPoint lp = LocalPoint.fromWorld(client, wp);
        if (lp == null) {
            return null;
        }
        return Perspective.getCanvasTilePoly(client, lp);
    }
}
