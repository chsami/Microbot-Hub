package net.runelite.client.plugins.microbot.SulphurNaguaFigther;

import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.geometry.Geometry;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.plugins.microbot.SulphurNaguaFigther.SulphurNaguaScript.SulphurNaguaState;

import javax.inject.Inject;
import java.awt.*;
import java.awt.geom.GeneralPath;

public class SulphurNaguaOverlay extends OverlayPanel {
    private static final Color BACKGROUND_COLOR = new Color(0, 0, 0, 150);
    private static final Color NORMAL_COLOR = Color.WHITE;
    private static final Color WARNING_COLOR = Color.YELLOW;
    private static final Color DANGER_COLOR = Color.RED;
    private static final Color SUCCESS_COLOR = Color.GREEN;
    private static final Color PREPARATION_COLOR = new Color(0, 170, 255);
    private static final Color ARENA_COLOR = new Color(255, 0, 0, 100);
    private static final int MAX_LOCAL_DRAW_LENGTH = 20 * Perspective.LOCAL_TILE_SIZE;

    private final SulphurNaguaPlugin plugin;
    private final Client client;

    @Inject
    SulphurNaguaOverlay(SulphurNaguaPlugin plugin, Client client) {
        super(plugin);
        this.plugin = plugin;
        this.client = client;

        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_SCENE);
        setPriority(OverlayPriority.LOW);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (plugin.sulphurNaguaScript != null && plugin.sulphurNaguaScript.getNaguaCombatArea() != null) {
            GeneralPath path = calculatePathForArea();
            if (path != null) {
                renderPath(graphics, path, ARENA_COLOR);
            }
        }

        panelComponent.setPreferredSize(new Dimension(200, 300));
        panelComponent.setBackgroundColor(BACKGROUND_COLOR);

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Sulphur Nagua Fighter")
                .leftColor(Color.white)
                .build());

        if (plugin.sulphurNaguaScript == null) {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Status:")
                    .right("Initializing...")
                    .build());
            return super.render(graphics);
        }

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Runtime:")
                .right(plugin.getTimeRunning())
                .rightColor(NORMAL_COLOR)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Status:")
                .right(plugin.sulphurNaguaScript.currentState.name())
                .rightColor(getStateColor(plugin.sulphurNaguaScript.currentState))
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Kills:")
                .right(String.valueOf(plugin.sulphurNaguaScript.totalNaguaKills))
                .rightColor(NORMAL_COLOR)
                .build());

        var xpGained = plugin.getXpGained();
        var xpPerHour = plugin.getXpPerHour();
        panelComponent.getChildren().add(LineComponent.builder()
                .left("XP Gained:")
                .right(formatNumber(xpGained))
                .rightColor(NORMAL_COLOR)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("XP/Hour:")
                .right(formatNumber(xpPerHour))
                .rightColor(xpPerHour > 0 ? SUCCESS_COLOR : NORMAL_COLOR)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .right("v" + SulphurNaguaPlugin.version)
                .rightColor(new Color(160, 160, 160))
                .build());

        return super.render(graphics);
    }

    private String formatNumber(long number) {
        return String.format("%,d", number);
    }

    private Color getStateColor(SulphurNaguaState state) {
        if (state == null) return NORMAL_COLOR;

        switch (state) {
            case FIGHTING:
                return DANGER_COLOR;
            case WALKING_TO_PREP:
            case WALKING_TO_FIGHT:
                return WARNING_COLOR;
            case PREPARATION:
                return PREPARATION_COLOR;
            case BANKING:
            default:
                return NORMAL_COLOR;
        }
    }

    private GeneralPath calculatePathForArea() {
        WorldArea area = plugin.sulphurNaguaScript.getNaguaCombatArea();
        if (area == null) {
            return null;
        }

        GeneralPath path = new GeneralPath();
        path.moveTo(area.getX(), area.getY());
        path.lineTo(area.getX() + area.getWidth(), area.getY());
        path.lineTo(area.getX() + area.getWidth(), area.getY() + area.getHeight());
        path.lineTo(area.getX(), area.getY() + area.getHeight());
        path.closePath();

        path = Geometry.clipPath(path, getSceneRectangle());
        path = Geometry.splitIntoSegments(path, 1);
        return Geometry.transformPath(path, this::transformWorldToLocal);
    }

    private void transformWorldToLocal(float[] coords) {
        final LocalPoint lp = LocalPoint.fromWorld(client, (int) coords[0], (int) coords[1]);
        if (lp != null) {
            coords[0] = lp.getX() - Perspective.LOCAL_TILE_SIZE / 2f;
            coords[1] = lp.getY() - Perspective.LOCAL_TILE_SIZE / 2f;
        }
    }

    private void renderPath(Graphics2D graphics, GeneralPath path, Color color) {
        if (client.getLocalPlayer() == null) return;
        LocalPoint playerLp = client.getLocalPlayer().getLocalLocation();
        Rectangle viewArea = new Rectangle(
                playerLp.getX() - MAX_LOCAL_DRAW_LENGTH,
                playerLp.getY() - MAX_LOCAL_DRAW_LENGTH,
                MAX_LOCAL_DRAW_LENGTH * 2,
                MAX_LOCAL_DRAW_LENGTH * 2);

        graphics.setColor(color);
        graphics.setStroke(new BasicStroke(2));

        path = Geometry.clipPath(path, viewArea);
        path = Geometry.filterPath(path, (p1, p2) ->
                Perspective.localToCanvas(client, new LocalPoint((int)p1[0], (int)p1[1]), client.getPlane()) != null &&
                        Perspective.localToCanvas(client, new LocalPoint((int)p2[0], (int)p2[1]), client.getPlane()) != null);
        path = Geometry.transformPath(path, coords ->
        {
            Point point = Perspective.localToCanvas(client, new LocalPoint((int)coords[0], (int)coords[1]), client.getPlane());
            if (point != null) {
                coords[0] = point.getX();
                coords[1] = point.getY();
            }
        });

        graphics.draw(path);
    }

    private Rectangle getSceneRectangle() {
        if (client == null) return new Rectangle();
        return new Rectangle(
                client.getBaseX() + 1, client.getBaseY() + 1,
                Constants.SCENE_SIZE - 2, Constants.SCENE_SIZE - 2);
    }
}