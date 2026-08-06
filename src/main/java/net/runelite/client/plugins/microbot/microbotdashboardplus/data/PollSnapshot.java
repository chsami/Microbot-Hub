package net.runelite.client.plugins.microbot.microbotdashboardplus.data;

import lombok.Builder;
import lombok.Value;
import net.runelite.api.Skill;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Immutable snapshot of all data the dashboard renders in one poll cycle.
 *
 * <p>Produced by {@link net.runelite.client.plugins.microbot.microbotdashboardplus.poller.GameStatePoller}
 * on each tick or scheduled refresh. Panels read fields directly; no shared
 * mutable state between poller and UI.
 *
 * <p>Use {@link #empty()} for the "no data yet" initial state.
 */
@Value
@Builder
public class PollSnapshot {

    /** Wall-clock millis when this snapshot was produced. */
    long timestampMillis;

    /** Whether the client was logged in when this snapshot was produced. */
    boolean loggedIn;

    // ------- Player section -------
    String playerName;
    int combatLevel;
    String gameState;
    int worldId;
    String profileName;
    String positionText;
    String animationText;

    // ------- Skills section -------
    /** Per-skill total XP. */
    Map<Skill, Integer> skillXp;

    /** Per-skill level (real, not virtual). */
    Map<Skill, Integer> skillLevels;

    // ------- Active scripts section -------
    List<ScriptStatus> activeScripts;

    // ------- Inventory -------
    /** Inventory items (slot 0..27) with display names + quantities. */
    List<InventoryItem> inventory;

    // ------- Nearby NPCs -------
    List<NearbyNpc> nearbyNpcs;

    // ------- Antiban + pause state (read in-process) -------
    AntibanState antibanState;

    public static PollSnapshot empty() {
        return PollSnapshot.builder()
                .timestampMillis(System.currentTimeMillis())
                .loggedIn(false)
                .playerName("--")
                .gameState("--")
                .profileName("--")
                .positionText("--")
                .animationText("--")
                .skillXp(Collections.emptyMap())
                .skillLevels(Collections.emptyMap())
                .activeScripts(Collections.emptyList())
                .inventory(Collections.emptyList())
                .nearbyNpcs(Collections.emptyList())
                .antibanState(AntibanState.builder()
                        .antibanEnabled(false)
                        .summary("--")
                        .build())
                .build();
    }

    @Value
    @Builder
    public static class ScriptStatus {
        String pluginClassName;
        String displayName;
        String status;           // "Running", "Paused", etc.
        long runtimeMillis;
    }

    @Value
    @Builder
    public static class InventoryItem {
        int slot;
        int itemId;
        String name;
        int quantity;
        boolean noted;
    }

    @Value
    @Builder
    public static class NearbyNpc {
        String name;
        int combatLevel;
        int distance;
        boolean randomEvent;
    }

    /**
     * In-process antiban and pause state. Lets a user tell a silent stall
     * (everything idle, nothing intentional) from a deliberate anti-AFK pause
     * (a micro break or action cooldown is running).
     */
    @Value
    @Builder
    public static class AntibanState {
        /** Whether antiban is globally enabled. */
        boolean antibanEnabled;
        /** True while an action cooldown is holding the script. */
        boolean actionCooldownActive;
        /** True while a micro break is holding the script. */
        boolean microBreakActive;
        /** Whether micro breaks are configured to fire at all. */
        boolean takeMicroBreaks;
        /** True when every script is globally paused (Microbot.pauseAllScripts). */
        boolean allScriptsPaused;
        /** Number of registered blocking-event handlers (login, level-up, death, and so on). */
        int blockingEventCount;
        /**
         * True if a blocking event is actively running right now. Read by
         * reflection; null when the running flag could not be read on this
         * client version.
         */
        Boolean blockingEventRunning;
        /**
         * One-line plain reason for the current hold, or "Running" when nothing
         * is holding the script. Examples: "Micro break in progress",
         * "Action cooldown", "All scripts paused".
         */
        String summary;
    }
}
