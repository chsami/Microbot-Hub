package net.runelite.client.plugins.microbot.SulphurNaguaFigther; // Make sure the package name is correct

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
import net.runelite.client.plugins.microbot.SulphurNaguaFigther.SulphurNaguaScript.SulphurNaguaState; // IMPORTANT IMPORT

import javax.inject.Inject;
import java.awt.*;
import java.awt.geom.GeneralPath;

/**
 * The overlay for the Sulphur Nagua script.
 * This class is responsible for drawing the information panel and the combat area on the screen.
 */
public class SulphurNaguaOverlay extends OverlayPanel {
    // --- Constants and Colors ---
    private static final Color BACKGROUND_COLOR = new Color(0, 0, 0, 150);
    private static final Color NORMAL_COLOR = Color.WHITE;
    private static final Color WARNING_COLOR = Color.YELLOW;
    private static final Color DANGER_COLOR = Color.RED;
    private static final Color SUCCESS_COLOR = Color.GREEN;
    private static final Color PREPARATION_COLOR = new Color(0, 170, 255); // Light blue for preparation

    private static final Color ARENA_COLOR = new Color(255, 0, 0, 100); // Red, semi-transparent for the arena
    private static final int MAX_LOCAL_DRAW_LENGTH = 20 * Perspective.LOCAL_TILE_SIZE;

    // --- Injected dependencies ---
    private final SulphurNaguaPlugin plugin;
    private final Client client;

    @Inject
    SulphurNaguaOverlay(SulphurNaguaPlugin plugin, Client client) {
        super(plugin);
        this.plugin = plugin;
        this.client = client;

        // Overlay configuration
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_SCENE);
        setPriority(OverlayPriority.LOW);
        setNaughty();
    }

    /**
     * The main render method called every frame.
     * It draws both the on-ground combat area and the info panel.
     * @param graphics The Graphics2D object to draw on.
     * @return The dimensions of the overlay panel.
     */
    @Override
    public Dimension render(Graphics2D graphics) {
        // --- Part 1: Draw the combat area on the ground ---
        if (plugin.sulphurNaguaScript != null && plugin.sulphurNaguaScript.getNaguaCombatArea() != null) {
            GeneralPath path = calculatePathForArea();
            if (path != null) {
                renderPath(graphics, path, ARENA_COLOR);
            }
        }

        // --- Part 2: Your existing info panel ---
        panelComponent.setPreferredSize(new Dimension(200, 300));
        panelComponent.setBackgroundColor(BACKGROUND_COLOR);

        // Title
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Sulphur Nagua Fighter")
                .leftColor(Color.white)
                .build());

        // Initializing state
        if (plugin.sulphurNaguaScript == null) {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Status:")
                    .right("Initializing...")
                    .build());
            return super.render(graphics);
        }

        // Runtime
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Runtime:")
                .right(plugin.getTimeRunning())
                .rightColor(NORMAL_COLOR)
                .build());

        // Current Status
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Status:")
                .right(plugin.sulphurNaguaScript.currentState.name())
                .rightColor(getStateColor(plugin.sulphurNaguaScript.currentState))
                .build());

        // Kills
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Kills:")
                .right(String.valueOf(plugin.sulphurNaguaScript.totalNaguaKills))
                .rightColor(NORMAL_COLOR)
                .build());

        // XP Information
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

        // Footer with version
        panelComponent.getChildren().add(LineComponent.builder()
                .right("v" + SulphurNaguaScript.version)
                .rightColor(new Color(160, 160, 160))
                .build());

        return super.render(graphics);
    }

    /**
     * Formats a number with commas for better readability.
     * @param number The number to format.
     * @return The formatted string.
     */
    private String formatNumber(long number) {
        return String.format("%,d", number);
    }

    /**
     * Determines the color for the current state text.
     * @param state The current state of the script.
     * @return The corresponding color.
     */
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

    // --- METHODS FOR DRAWING THE ARENA ---

    /**
     * Creates the path for the arena to be drawn.
     * THIS METHOD HAS BEEN CORRECTED.
     * @return The GeneralPath representing the combat area.
     */
    private GeneralPath calculatePathForArea() {
        WorldArea area = plugin.sulphurNaguaScript.getNaguaCombatArea();
        if (area == null) {
            return null;
        }

        // Manually create the path instead of using 'toAwtGeneralPath'
        GeneralPath path = new GeneralPath();
        path.moveTo(area.getX(), area.getY()); // Starts at the south-west corner
        path.lineTo(area.getX() + area.getWidth(), area.getY()); // Line to the south-east corner
        path.lineTo(area.getX() + area.getWidth(), area.getY() + area.getHeight()); // Line to the north-east corner
        path.lineTo(area.getX(), area.getY() + area.getHeight()); // Line to the north-west corner
        path.closePath(); // Closes the path back to the south-west corner

        // The rest of the logic remains the same
        path = Geometry.clipPath(path, getSceneRectangle());
        path = Geometry.splitIntoSegments(path, 1);
        return Geometry.transformPath(path, this::transformWorldToLocal);
    }

    /**
     * Transforms world coordinates to local coordinates for drawing.
     * @param coords The coordinates array to be transformed.
     */
    private void transformWorldToLocal(float[] coords) {
        final LocalPoint lp = LocalPoint.fromWorld(client, (int) coords[0], (int) coords[1]);
        if (lp != null) {
            coords[0] = lp.getX() - Perspective.LOCAL_TILE_SIZE / 2f;
            coords[1] = lp.getY() - Perspective.LOCAL_TILE_SIZE / 2f;
        }
    }

    /**
     * Renders the given path on the screen with the specified color.
     * @param graphics The Graphics2D object.
     * @param path The path to draw.
     * @param color The color to use.
     */
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

        // Clip, filter, and transform the path to canvas coordinates before drawing
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

    /**
     * Gets the rectangle representing the current scene.
     * @return The scene rectangle.
     */
    private Rectangle getSceneRectangle() {
        if (client == null) return new Rectangle();
        return new Rectangle(
                client.getBaseX() + 1, client.getBaseY() + 1,
                Constants.SCENE_SIZE - 2, Constants.SCENE_SIZE - 2);
    }
}