package net.runelite.client.plugins.microbot.mmcaves;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.mmcaves.enums.DungeonRoute;
import net.runelite.client.plugins.microbot.mmcaves.enums.CombatStyle;
import net.runelite.client.plugins.microbot.mmcaves.enums.State;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

public class MmCavesOverlay extends OverlayPanel {
    private static final Color ACCENT = new Color(110, 210, 230);
    private final MmCavesPlugin plugin;
    private final MmCavesConfig config;

    @Inject
    MmCavesOverlay(MmCavesPlugin plugin, MmCavesConfig config) {
        super(plugin);
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        panelComponent.setPreferredSize(new Dimension(250, 0));
        panelComponent.getChildren().add(TitleComponent.builder()
                .text("MM Caves · " + MmCavesPlugin.version).color(ACCENT).build());
        MmCavesScript script = plugin.getScript();
        State state = MmCavesScript.state;
        line("Status", state.name().replace('_', ' ').toLowerCase(Locale.ROOT),
                state == State.STOP ? Color.RED : ACCENT);
        line("Runtime", plugin.startTime == null ? "--:--"
                : formatSeconds(Duration.between(plugin.startTime, Instant.now()).getSeconds()), Color.WHITE);
        if (!Microbot.isLoggedIn()) {
            line("Client", "Logged out", Color.ORANGE);
            return super.render(graphics);
        }
        line("Combat", config.combatStyle().toString(), Color.WHITE);
        DungeonRoute route = config.dungeonRoute();
        line("Route", route == DungeonRoute.HOLE_2 ? "Hole 2 · middle" : route.toString(), Color.WHITE);
        if (state == State.FOLLOW_ROUTE && route.isMapped()) {
            int index = script.routeWaypointIndex();
            line("Waypoint", Math.min(index + 1, route.waypoints().size()) + " / "
                    + route.waypoints().size(), Color.WHITE);
            line("Next action", route.pressurePadForWaypoint(index) != null ? "Pass pressure pad"
                    : route.squeezeHoleForWaypoint(index) != null ? "Squeeze-through hole" : "Walk", ACCENT);
        }
        if (state == State.FIGHT && config.combatStyle() == CombatStyle.RANGING) {
            line("Stack action", config.clickRangedAttackTargets() ? "Explicit attacks enabled"
                    : script.stackReady() ? "Holding · auto-retaliate" : "Gathering monkeys", ACCENT);
            MmCavesDecisions.StackCounts counts = script.stackCounts();
            line("Monkeys", counts == null ? "Not sampled" : counts.stacked + " stacked / "
                    + counts.outside + " outside", Color.WHITE);
            line("Step delay", (config.useCustomDelay() ? config.customAttackDelay() : 1800) + " ms", Color.WHITE);
        }
        if (script.hasStartedFight()) {
            long remaining = Math.max(0, 600 - (System.currentTimeMillis()
                    - MmCavesScript.lastAggroResetTime) / 1000);
            line("Aggro reset", state == State.RESET_AGGRO ? "In progress"
                    : remaining == 0 ? "Due" : "In " + formatSeconds(remaining),
                    remaining <= 30 ? Color.ORANGE : Color.WHITE);
        } else {
            line("Aggro reset", "Starts with combat", Color.GRAY);
        }
        int hp = (int) Rs2Player.getHealthPercentage();
        line("HP / prayer", hp + "% / " + Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER),
                hp < 50 ? Color.ORANGE : Color.WHITE);
        line("Run energy", Rs2Player.getRunEnergy() + "%", Color.WHITE);
        int slots = Rs2Inventory.emptySlotCount();
        line("Free slots", slots + " · 1 reserved for bass", slots == 0 ? Color.ORANGE : Color.WHITE);
        line("Bass held", String.valueOf(Rs2Inventory.count(365)), Color.WHITE);
        if (slots == 0 && !Rs2Inventory.contains(365)) {
            line("Warning", "Free a slot for healing", Color.ORANGE);
        } else if (hp < 50) {
            line("Healing", "Bass needed below 50%", Color.ORANGE);
        }
        return super.render(graphics);
    }

    private void line(String label, String value, Color color) {
        panelComponent.getChildren().add(LineComponent.builder()
                .left(label).leftColor(Color.LIGHT_GRAY).right(value).rightColor(color).build());
    }

    private static String formatSeconds(long seconds) {
        seconds = Math.max(0, seconds);
        return seconds >= 3600
                ? String.format("%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
                : String.format("%02d:%02d", seconds / 60, seconds % 60);
    }
}
