package net.runelite.client.plugins.microbot.microbotdashboardplus;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

/**
 * Configuration for the MicrobotDashboardPlus plugin.
 *
 * <p>Sections:
 * <ul>
 *     <li><b>Behavior</b> - core polling + window open semantics.</li>
 *     <li><b>Layout</b> - per-section visibility toggles.</li>
 *     <li><b>Notifications</b> - Discord webhook + which events fire.</li>
 *     <li><b>Alerts</b> - per-skill level-threshold alerts.</li>
 * </ul>
 */
@ConfigGroup("MicrobotDashboardPlus")
@ConfigInformation(
    "<h2>Microbot Dashboard Plus</h2>" +
    "<h3>Version: " + MicrobotDashboardPlusPlugin.version + "</h3>" +
    "<p>Aggregate session dashboard. A floating window with nine live-updating panels: Player, Active Scripts, Inventory, Skills, Nearby NPCs, Antiban State, XP Chart, Event Log, and Guide. A green chart-line icon in the right sidebar (while the plugin is enabled) opens the dashboard.</p>" +
    "<p></p>" +
    "<h3>Behavior</h3>" +
    "<p>1. <strong>Auto-open dashboard:</strong> opens the floating window automatically when the plugin enables. Untick to launch it manually from the sidebar.</p>" +
    "<p></p>" +
    "<p>2. <strong>Poll interval:</strong> seconds between dashboard refreshes from game state. Lower is more responsive but uses slightly more CPU. Default 5, range 1 to 60.</p>" +
    "<p></p>" +
    "<p>3. <strong>Nearby NPCs max distance:</strong> tile radius for the Nearby NPCs panel. Higher shows more NPCs and polls a little slower. Default 20, range 1 to 200.</p>" +
    "<p></p>" +
    "<h3>Layout</h3>" +
    "<p>4. <strong>Panel toggles:</strong> one show or hide switch per panel (Player, Active Scripts, Inventory, Skills, Nearby NPCs, Antiban State, XP Chart, Event Log, Guide). Untick any you do not want in the window. Hide the Guide once you know the panels.</p>" +
    "<p></p>" +
    "<h3>Notifications</h3>" +
    "<p>5. <strong>Discord webhook URL:</strong> paste a channel webhook to send alerts to Discord. Leave blank to disable Discord. Keep this URL secret.</p>" +
    "<p></p>" +
    "<p>6. <strong>Notify on level-up:</strong> posts to Discord when any skill levels up.</p>" +
    "<p></p>" +
    "<p>7. <strong>Notify on session start or stop:</strong> posts when the plugin enables or disables. Off by default.</p>" +
    "<p></p>" +
    "<p>8. <strong>Notify on alert threshold:</strong> posts when a configured Alert Threshold is crossed.</p>" +
    "<p></p>" +
    "<h3>Alerts</h3>" +
    "<p>9. <strong>Alert thresholds:</strong> comma-separated SKILL:LEVEL pairs, for example MINING:60, WOODCUTTING:80. Use uppercase OSRS skill names. A crossing fires an in-dashboard banner and, if enabled above, a Discord notification.</p>" +
    "<p></p>" +
    "<p>10. <strong>Skill targets (ETA):</strong> comma-separated SKILL:LEVEL pairs, for example MINING:70, AGILITY:60. The Skills section shows an ETA to each target from the current XP per hour. A skill with no target still shows an ETA to its next level while it is being trained.</p>" +
    "<p></p>" +
    "<h3>Panels of note</h3>" +
    "<p><strong>Antiban State:</strong> shows whether the script is running or is being held by an intentional anti-AFK pause such as a micro break, an action cooldown, a global pause, or a blocking event. Use it to tell a real stall from expected behavior.</p>"
)
public interface MicrobotDashboardPlusConfig extends Config {

    @ConfigSection(name = "Behavior", description = "Window + polling settings", position = 0)
    String behaviorSection = "behavior";

    @ConfigSection(name = "Layout", description = "Which sections to show", position = 1)
    String layoutSection = "layout";

    @ConfigSection(name = "Notifications", description = "Discord webhook + which events fire", position = 2)
    String notificationsSection = "notifications";

    @ConfigSection(name = "Alerts", description = "Per-skill level-threshold alerts", position = 3)
    String alertsSection = "alerts";

    // ------------------------------------------------------------------
    // Behavior
    // ------------------------------------------------------------------

    @ConfigItem(
            keyName = "autoOpenDashboard",
            name = "Auto-open dashboard on startup",
            description = "When the plugin enables, open the floating dashboard window automatically. Untick this if you'd rather launch it manually from the sidebar panel.",
            position = 0,
            section = behaviorSection
    )
    default boolean autoOpenDashboard() {
        return true;
    }

    @ConfigItem(
            keyName = "pollIntervalSeconds",
            name = "Poll interval (sec)",
            description = "How often to refresh the dashboard from in-process game state. Lower = more responsive, slightly higher CPU. Default 5.",
            position = 1,
            section = behaviorSection
    )
    @Range(min = 1, max = 60)
    default int pollIntervalSeconds() {
        return 5;
    }

    @ConfigItem(
            keyName = "npcMaxDistance",
            name = "Nearby NPCs max distance (tiles)",
            description = "Maximum tile distance for NPCs to show in the Nearby NPCs section. Higher = more NPCs visible, slightly slower poll.",
            position = 2,
            section = behaviorSection
    )
    @Range(min = 1, max = 200)
    default int npcMaxDistance() {
        return 20;
    }

    // ------------------------------------------------------------------
    // Layout (per-section visibility)
    // ------------------------------------------------------------------

    @ConfigItem(keyName = "showPlayer", name = "Show Player", description = "Show the Player section.", position = 0, section = layoutSection)
    default boolean showPlayer() { return true; }

    @ConfigItem(keyName = "showActiveScripts", name = "Show Active Scripts", description = "Show the Active Scripts section.", position = 1, section = layoutSection)
    default boolean showActiveScripts() { return true; }

    @ConfigItem(keyName = "showInventory", name = "Show Inventory", description = "Show the Inventory section.", position = 2, section = layoutSection)
    default boolean showInventory() { return true; }

    @ConfigItem(keyName = "showSkills", name = "Show Skills", description = "Show the Skills section.", position = 3, section = layoutSection)
    default boolean showSkills() { return true; }

    @ConfigItem(keyName = "showNearbyNpcs", name = "Show Nearby NPCs", description = "Show the Nearby NPCs section.", position = 4, section = layoutSection)
    default boolean showNearbyNpcs() { return true; }

    @ConfigItem(keyName = "showAntibanState", name = "Show Antiban State", description = "Show the Antiban State section. It tells a silent stall apart from an intentional anti-AFK pause such as a micro break or action cooldown.", position = 5, section = layoutSection)
    default boolean showAntibanState() { return true; }

    @ConfigItem(keyName = "showXpChart", name = "Show XP Chart", description = "Show the XP-over-time chart section.", position = 6, section = layoutSection)
    default boolean showXpChart() { return true; }

    @ConfigItem(keyName = "showEventLog", name = "Show Event Log", description = "Show the Event Log ring buffer section.", position = 7, section = layoutSection)
    default boolean showEventLog() { return true; }

    @ConfigItem(keyName = "showGuide", name = "Show Guide", description = "Show the Guide section at the bottom of the dashboard window. Hide it once you're familiar with the panels and config options.", position = 8, section = layoutSection)
    default boolean showGuide() { return true; }

    // ------------------------------------------------------------------
    // Notifications (Discord webhook)
    // ------------------------------------------------------------------

    @ConfigItem(
            keyName = "discordWebhookUrl",
            name = "Discord webhook URL",
            description = "Paste a Discord channel webhook URL (https://discord.com/api/webhooks/...). Leave blank to disable Discord notifications. Treat this URL as a secret. Do not share it.",
            position = 0,
            section = notificationsSection,
            secret = true
    )
    default String discordWebhookUrl() {
        return "";
    }

    @ConfigItem(
            keyName = "notifyLevelUp",
            name = "Notify on level-up",
            description = "Send a Discord message when any skill level increases.",
            position = 1,
            section = notificationsSection
    )
    default boolean notifyLevelUp() {
        return true;
    }

    @ConfigItem(
            keyName = "notifySessionLifecycle",
            name = "Notify on session start/stop",
            description = "Send a Discord message when the dashboard plugin enables (session start) or disables (session stop).",
            position = 2,
            section = notificationsSection
    )
    default boolean notifySessionLifecycle() {
        return false;
    }

    @ConfigItem(
            keyName = "notifyAlerts",
            name = "Notify on alert threshold",
            description = "Send a Discord message when any configured Alert Threshold is crossed (see Alerts section).",
            position = 3,
            section = notificationsSection
    )
    default boolean notifyAlerts() {
        return true;
    }

    // ------------------------------------------------------------------
    // Alerts
    // ------------------------------------------------------------------

    @ConfigItem(
            keyName = "alertThresholds",
            name = "Alert thresholds",
            description = "Comma-separated SKILL:LEVEL pairs. Example: MINING:60, WOODCUTTING:80, FISHING:70. Skill names follow the OSRS API enum (uppercase). Crossings fire an in-dashboard banner and (if enabled) a Discord notification.",
            position = 0,
            section = alertsSection
    )
    default String alertThresholds() {
        return "";
    }

    @ConfigItem(
            keyName = "skillTargets",
            name = "Skill targets (ETA)",
            description = "Comma-separated SKILL:LEVEL pairs the Skills section uses for its ETA column. Example: MINING:70, AGILITY:60. Skill names follow the OSRS API enum (uppercase). A skill with no target still shows an ETA to its next level while it is being trained.",
            position = 1,
            section = alertsSection
    )
    default String skillTargets() {
        return "";
    }
}
