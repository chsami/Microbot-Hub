package net.runelite.client.plugins.microbot.pestcontrol;

import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class PestControlOverlay  extends OverlayPanel {
    private final PestControlPlugin plugin;

    @Inject
    PestControlOverlay(PestControlPlugin plugin)
    {
        super(plugin);
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }
    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            PestControlScript.OverlaySnapshot snapshot = plugin.getOverlaySnapshot();
            long now = System.currentTimeMillis();
            panelComponent.setPreferredSize(new Dimension(255, 0));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Micro PestControl V" + plugin.getClass().getAnnotation(PluginDescriptor.class).version())
                    .color(Color.GREEN)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder().build());

            addLine("State", snapshot.state, stateColor(snapshot.state));
            if (!snapshot.detail.isEmpty()) {
                addLine("Detail", abbreviate(snapshot.detail, 30), Color.WHITE);
            }
            addLine("Location", snapshot.location, Color.WHITE);
            addLine("PC points", pointsText(snapshot), Color.CYAN);
            addLine("Rounds played", Integer.toString(snapshot.roundsPlayed), Color.WHITE);
            addLine("Rounds won", Integer.toString(snapshot.roundsWon), Color.GREEN);
            addLine("Rounds lost", Integer.toString(snapshot.roundsLost), Color.RED);
            addLine("Last result", abbreviate(snapshot.roundResult, 30),
                    snapshot.roundResult.startsWith("WIN")
                            ? Color.GREEN
                            : snapshot.roundResult.startsWith("NON-WIN")
                                    ? Color.RED
                                    : Color.LIGHT_GRAY);
            addLine("Activity", activityText(snapshot), activityColor(snapshot));
            addLine("Target", targetText(snapshot), portalColor(snapshot.targetPortal));
            addLine("Target via", targetSource(snapshot), Color.LIGHT_GRAY);
            addLine("Opening", snapshot.openingPortal, portalColor(snapshot.openingPortal));
            addLine("Portals", snapshot.remainingPortals + " left / "
                    + snapshot.readyPortals + " ready", Color.WHITE);
            addLine("Weapon", abbreviate(snapshot.combatWeapon, 24), Color.WHITE);
            addLine("Style", snapshot.combatStyle, Color.WHITE);
            addLine("Auto Retal", snapshot.autoRetaliateOff ? "OFF" : "CHECKING",
                    snapshot.autoRetaliateOff ? Color.GREEN : Color.ORANGE);
            addLine("State age", formatAge(now, snapshot.stateEnteredAt), Color.LIGHT_GRAY);
            addLine("Progress idle", formatAge(now, snapshot.lastProgressAt),
                    progressColor(snapshot.state, now, snapshot.lastProgressAt));
            addLine("Command age", "Move " + formatAge(now, snapshot.lastMovementCommandAt)
                    + " / Hit " + formatAge(now, snapshot.lastAttackCommandAt), Color.LIGHT_GRAY);

            if ("REQUEUE".equals(snapshot.state) || "BOAT".equals(snapshot.state)) {
                addLine("Boarding", snapshot.boardingAttemptPending
                                ? "AWAITING ENTRY"
                                : "READY",
                        snapshot.boardingAttemptPending ? Color.ORANGE : Color.GREEN);
            }
        } catch(Exception ex) {
            System.out.println(ex.getMessage());
        }
        return super.render(graphics);
    }

    private void addLine(String label, String value, Color valueColor) {
        panelComponent.getChildren().add(LineComponent.builder()
                .left(label)
                .right(value)
                .rightColor(valueColor)
                .build());
    }

    private static String pointsText(PestControlScript.OverlaySnapshot snapshot) {
        if (snapshot.totalPoints >= 0) {
            return snapshot.totalPoints + " total / " + snapshot.pointsEarned + " gained";
        }
        return snapshot.pointsEarned + " gained this session";
    }

    private static String activityText(PestControlScript.OverlaySnapshot snapshot) {
        if (snapshot.activityPercent < 0) {
            return "--";
        }
        if (snapshot.activityRecoveryActive) {
            return snapshot.activityPercent + "% -> " + snapshot.activityRecoveryTarget + "%";
        }
        return snapshot.activityPercent + "% / trigger " + snapshot.activityRecoveryStart + "%";
    }

    private static Color activityColor(PestControlScript.OverlaySnapshot snapshot) {
        if (snapshot.activityPercent < 0) {
            return Color.LIGHT_GRAY;
        }
        if (snapshot.activityRecoveryActive) {
            return snapshot.activityPercent <= snapshot.activityRecoveryStart
                    ? Color.RED
                    : Color.ORANGE;
        }
        return Color.GREEN;
    }

    private static String targetText(PestControlScript.OverlaySnapshot snapshot) {
        if ("None".equals(snapshot.targetPortal)) {
            return "None";
        }
        return snapshot.targetPortal + " / " + snapshot.targetCrowd + " others";
    }

    private static String targetSource(PestControlScript.OverlaySnapshot snapshot) {
        if ("None".equals(snapshot.targetPortal)) {
            return "--";
        }
        return snapshot.targetHasAttackAction ? "NPC Attack action" : "shield state";
    }

    private static Color stateColor(String state) {
        if ("ERROR".equals(state)) {
            return Color.RED;
        }
        if ("ATTACK_PORTAL".equals(state)
                || "KILL_SPINNER".equals(state)
                || "ACTIVITY_FALLBACK".equals(state)) {
            return Color.GREEN;
        }
        if ("CHASE_PORTAL".equals(state)
                || "OPENING_SIDE".equals(state)
                || "PREPOSITION_PORTAL".equals(state)
                || "REQUEUE".equals(state)) {
            return Color.ORANGE;
        }
        return Color.WHITE;
    }

    private static Color portalColor(String portal) {
        switch (portal) {
            case "PURPLE":
                return new Color(190, 120, 255);
            case "BLUE":
                return Color.CYAN;
            case "YELLOW":
                return Color.YELLOW;
            case "RED":
                return new Color(255, 100, 100);
            default:
                return Color.LIGHT_GRAY;
        }
    }

    private static Color progressColor(String state, long now, long timestamp) {
        if (timestamp <= 0L) {
            return Color.LIGHT_GRAY;
        }
        if (!"OPENING_SIDE".equals(state)
                && !"PREPOSITION_PORTAL".equals(state)
                && !"CHASE_PORTAL".equals(state)
                && !"KILL_SPINNER".equals(state)
                && !"ATTACK_PORTAL".equals(state)
                && !"ACTIVITY_FALLBACK".equals(state)) {
            return Color.LIGHT_GRAY;
        }
        return now - timestamp >= 6_000L ? Color.RED : Color.GREEN;
    }

    private static String formatAge(long now, long timestamp) {
        if (timestamp <= 0L) {
            return "--";
        }
        long elapsed = Math.max(0L, now - timestamp);
        if (elapsed < 1_000L) {
            return "<1s";
        }
        long seconds = elapsed / 1_000L;
        if (seconds < 60L) {
            return seconds + "s";
        }
        return (seconds / 60L) + "m " + (seconds % 60L) + "s";
    }

    private static String abbreviate(String value, int maximumLength) {
        if (value == null || value.length() <= maximumLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, maximumLength - 3) + "...";
    }
}
