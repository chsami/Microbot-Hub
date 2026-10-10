package net.runelite.client.plugins.microbot.tempoross;

import com.google.inject.Inject;
import lombok.Setter;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.util.Text;

import java.awt.*;
import java.util.List;

import static net.runelite.client.plugins.microbot.tempoross.TemporossScript.workArea;

public class TemporossOverlay extends Overlay {

    private final TemporossPlugin plugin;

    // Add a setter method to feed the list of NPCs
    @Setter
    private static volatile List<TemporossNpcSnapshot> npcList;
    @Setter
    private static volatile List<TemporossNpcSnapshot> fishList;
    @Setter
    private static volatile List<TemporossCloudSnapshot> cloudList;
    @Setter
    private static volatile List<TemporossNpcSnapshot> ammoList;
    @Setter
    private static volatile List<WorldPoint> lastWalkPath; // Add this field to store the walk path


    @Inject
    public TemporossOverlay(TemporossPlugin plugin) {
        super(plugin);
        this.plugin = plugin;
        setPosition(OverlayPosition.DYNAMIC);
        setPriority(100f);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!TemporossScript.cachedInMinigame){
            return null;
        }
        // Render NPC overlays if the list is not null
        if (npcList != null) {
            for (TemporossNpcSnapshot npc : npcList) {
                renderNpcOverlay(graphics, npc, Color.RED, "Fire");
            }
        }
        if (ammoList != null) {
            for (TemporossNpcSnapshot npc : ammoList) {
                String name = npc.name;
                renderNpcOverlay(graphics, npc, Color.RED, name != null ? Text.removeTags(name) : "Ammo");
            }
        }
        if (fishList != null) {
            for (TemporossNpcSnapshot npc : fishList) {
                renderNpcOverlay(graphics, npc, Color.RED, "Fish spot");
            }
        }
        if (cloudList != null) {
            for (TemporossCloudSnapshot cloud : cloudList) {
                renderLocalPoint(graphics, cloud.localLocation, Color.RED, "Cloud");
            }
        }

        if (TemporossScript.cachedInMinigame && workArea != null) {
            // draw each work area WorldPoint

            renderWorldPoint(graphics, workArea.exitNpc, Color.RED, "Exit NPC");
            renderWorldPoint(graphics, workArea.safePoint, Color.ORANGE, "Safe Point");
            renderWorldPoint(graphics, workArea.bucketPoint, Color.YELLOW, "Bucket Crate");
            renderWorldPoint(graphics, workArea.pumpPoint, Color.DARK_GRAY, "Water Pump");
            renderWorldPoint(graphics, workArea.ropePoint, Color.CYAN, "Rope Crate");
            renderWorldPoint(graphics, workArea.hammerPoint, Color.BLUE, "Hammer Crate");
            renderWorldPoint(graphics, workArea.harpoonPoint, Color.WHITE, "Harpoon Crate");
            renderWorldPoint(graphics, workArea.mastPoint, Color.PINK, "Mast Point");
            renderWorldPoint(graphics, workArea.totemPoint, Color.GREEN, "Totem Point");
            renderWorldPoint(graphics, workArea.rangePoint, Color.MAGENTA, "Range Point");
            renderWorldPoint(graphics, workArea.spiritPoolPoint, Color.ORANGE, "Spirit Pool");

            // draw each lastWalkPath WorldPoint
            if (lastWalkPath != null) {
                for (WorldPoint point : lastWalkPath) {
                    renderWorldPoint(graphics, point, Color.GREEN, "");
                }
            }
        }

        return null;
    }

    private void renderWorldPoint(Graphics2D graphics, WorldPoint point, Color color, String label) {
        if (point == null) {
            return;
        }
        //WorldPoint pl = WorldPoint.toLocalInstance(Microbot.getClient().getTopLevelWorldView(), point).stream().findFirst().orElse(null);
        LocalPoint localPoint = LocalPoint.fromWorld(Microbot.getClient(), point);
        if (localPoint == null) {
            return;
        }

        Polygon poly = Perspective.getCanvasTilePoly(Microbot.getClient(), localPoint);
        if (poly != null) {
            OverlayUtil.renderPolygon(graphics, poly, color);

            // Draw the label
            Point textLocation = Perspective.getCanvasTextLocation(Microbot.getClient(), graphics, localPoint, label, 0);
            if (textLocation != null) {
                OverlayUtil.renderTextLocation(graphics, textLocation, label, Color.WHITE);
            }
        }
    }

    private void renderLocalPoint(Graphics2D graphics, LocalPoint localPoint, Color color, String label) {
        if (localPoint == null) {
            return;
        }
        Polygon polygon = Perspective.getCanvasTilePoly(Microbot.getClient(), localPoint);
        if (polygon != null) {
            OverlayUtil.renderPolygon(graphics, polygon, color);
            Point textLocation = Perspective.getCanvasTextLocation(Microbot.getClient(), graphics,
                    localPoint, label, 40);
            if (textLocation != null) {
                OverlayUtil.renderTextLocation(graphics, textLocation, label, Color.WHITE);
            }
        }
    }

    // Add this method to render overlays for NPCs
    private void renderNpcOverlay(Graphics2D graphics, TemporossNpcSnapshot npc, Color color, String label) {
        renderLocalPoint(graphics, npc != null ? npc.localLocation : null, color, label);
    }
}
