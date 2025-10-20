package net.runelite.client.plugins.microbot.bluedragons;

import lombok.Setter;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

import javax.inject.Inject;
import java.awt.*;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

public class BlueDragonsOverlay extends OverlayPanel {

    private final Client client;
    @Setter
    private BlueDragonsScript script;
    private Instant startTime;
    public static int dragonKillCount = 0;
    public static int bonesCollected = 0;
    public static int hidesCollected = 0;

    // For formatting hitpoint values
    private final NumberFormat formatter = NumberFormat.getInstance(Locale.US);

    // Model outline renderer for highlighting NPCs
    private final ModelOutlineRenderer modelOutlineRenderer;

    // Reference to the config
    @Setter
    private BlueDragonsConfig config;

    @Inject
    public BlueDragonsOverlay(Client client, ModelOutlineRenderer modelOutlineRenderer) {
        this.client = client;
        this.modelOutlineRenderer = modelOutlineRenderer;
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_SCENE);
        startTime = Instant.now();
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (script == null || !script.isRunning()) {
            return null;
        }

        // Always draw the safe spot location
        drawSafeSpot(graphics);

        // Draw overlay panel with information
        panelComponent.setPreferredSize(new Dimension(280, 350));

        // Title with blue dragon emoji and fancy formatting
        panelComponent.getChildren().add(
                TitleComponent.builder()
                        .text("\uD83D\uDC09 00 Blue Dragons \uD83D\uDC09")
                        .color(new Color(0, 170, 255))
                        .build()
        );

        // Runtime section
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Session Time:")
                .right(formatDuration(Duration.between(startTime, Instant.now())))
                .rightColor(new Color(255, 215, 0)) // Gold
                .build());

        // Add section divider
        addSectionDivider("Current Status");

        // Status section with improved colors
        panelComponent.getChildren().add(
                LineComponent.builder()
                        .left("Bot State:")
                        .right(formatState())
                        .rightColor(getStateColor())
                        .build()
        );

        // Location information in a more compact format
        WorldPoint playerLocation = Microbot.getClient().getLocalPlayer().getWorldLocation();
        int distanceToSafeSpot = playerLocation.distanceTo(BlueDragonsScript.SAFE_SPOT);

        panelComponent.getChildren().add(
                LineComponent.builder()
                        .left("Safe Spot:")
                        .right(distanceToSafeSpot + " tiles away")
                        .rightColor(getSafeSpotColor(distanceToSafeSpot))
                        .build()
        );

        // Dragon tracking section
        addSectionDivider("Dragon Tracking");

        NPC nearestDragon = Rs2Npc.getNpc("Blue dragon");
        boolean isTargeting = nearestDragon != null &&
                script.getCurrentTargetId() != null &&
                script.getCurrentTargetId() == nearestDragon.getId();

        panelComponent.getChildren().add(
                LineComponent.builder()
                        .left("Target Status:")
                        .right(getDragonStatus(nearestDragon, isTargeting))
                        .rightColor(getDragonStatusColor(nearestDragon, isTargeting))
                        .build()
        );

        if (nearestDragon != null) {
            panelComponent.getChildren().add(
                    LineComponent.builder()
                            .left("Distance:")
                            .right(playerLocation.distanceTo(nearestDragon.getWorldLocation()) + " tiles")
                            .rightColor(new Color(135, 206, 235)) // Sky Blue
                            .build()
            );
        }

        // Statistics section
        addSectionDivider("Loot Tracker");

        // Stats in a grid-like format
        panelComponent.getChildren().add(
                LineComponent.builder()
                        .left("Dragons Killed:")
                        .right(formatNumber(dragonKillCount))
                        .rightColor(new Color(50, 205, 50)) // Lime Green
                        .build()
        );

        panelComponent.getChildren().add(
                LineComponent.builder()
                        .left("Bones Collected:")
                        .right(formatNumber(bonesCollected))
                        .rightColor(new Color(222, 184, 135)) // Burlywood
                        .build()
        );

        panelComponent.getChildren().add(
                LineComponent.builder()
                        .left("Hides Collected:")
                        .right(formatNumber(hidesCollected))
                        .rightColor(new Color(70, 130, 180)) // Steel Blue
                        .build()
        );

        return super.render(graphics);
    }


    private String formatState() {
        if (BlueDragonsScript.currentState == null) {
            return "UNKNOWN";
        }

        String state = BlueDragonsScript.currentState.toString();
        return state.charAt(0) + state.substring(1).toLowerCase();
    }

    private Color getStateColor() {
        if (BlueDragonsScript.currentState == null) {
            return Color.RED;
        }

        switch (BlueDragonsScript.currentState) {
            case BANKING:
                return Color.YELLOW;
            case TRAVEL_TO_DRAGONS:
                return Color.ORANGE;
            case FIGHTING:
                return Color.GREEN;
            case LOOTING:
                return new Color(218, 165, 32); // Gold
            default:
                return Color.WHITE;
        }
    }

    private void drawSafeSpot(Graphics2D graphics) {
        // Use the non-deprecated method to convert world point to local point
        LocalPoint localSafeSpot = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), BlueDragonsScript.SAFE_SPOT);

        if (localSafeSpot != null) {
            Polygon safeTile = Perspective.getCanvasTilePoly(client, localSafeSpot);
            if (safeTile != null) {
                graphics.setColor(new Color(0, 255, 0, 50));
                graphics.fillPolygon(safeTile);
                graphics.setColor(Color.GREEN);
                graphics.drawPolygon(safeTile);
            }
        }
    }

    private String formatDuration(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    public void resetStats() {
        startTime = Instant.now();
        dragonKillCount = 0;
        bonesCollected = 0;
        hidesCollected = 0;
    }

    private void addSectionDivider(String sectionName) {
        panelComponent.getChildren().add(LineComponent.builder().build());
        panelComponent.getChildren().add(
                LineComponent.builder()
                        .left(sectionName)
                        .leftColor(new Color(255, 165, 0)) // Orange
                        .build()
        );
    }

    private String getDragonStatus(NPC dragon, boolean isTargeting) {
        if (dragon == null) return "No dragons";
        return isTargeting ? "Fighting" : "Available";
    }

    private Color getDragonStatusColor(NPC dragon, boolean isTargeting) {
        if (dragon == null) return new Color(169, 169, 169); // Dark Gray
        return isTargeting ? new Color(220, 20, 60) : new Color(50, 205, 50); // Crimson : Lime Green
    }

    private Color getSafeSpotColor(int distance) {
        if (distance == 0) return new Color(50, 205, 50); // Lime Green
        if (distance <= 5) return new Color(255, 165, 0); // Orange
        return new Color(220, 20, 60); // Crimson
    }

    private String formatNumber(int number) {
        return formatter.format(number);
    }
}
