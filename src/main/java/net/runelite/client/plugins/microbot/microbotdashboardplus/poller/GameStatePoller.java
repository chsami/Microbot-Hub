package net.runelite.client.plugins.microbot.microbotdashboardplus.poller;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.microbot.BlockingEventManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.microbotdashboardplus.data.PollSnapshot;
import net.runelite.client.plugins.microbot.microbotdashboardplus.data.XpHistory;
import net.runelite.client.plugins.microbot.microbotdashboardplus.notify.AlertManager;
import net.runelite.client.plugins.microbot.microbotdashboardplus.notify.DiscordNotifier;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Background poller that builds a {@link PollSnapshot} on a fixed cadence and
 * notifies listeners on the EDT. Reads player state, skills, inventory, nearby
 * NPCs, active scripts, and the in-process antiban flags.
 */
@Slf4j
public class GameStatePoller {

    /** Package prefix used to identify Microbot Hub plugins. */
    private static final String MICROBOT_PACKAGE_PREFIX = "net.runelite.client.plugins.microbot.";

    /**
     * Catalog of known random-event NPC names. Matched case-insensitively
     * against NPC.getName() to flag NearbyNpc.randomEvent=true so the panel
     * renderer highlights them orange. Both wiki-disambiguation spellings are
     * listed (Bee keeper / Beekeeper, Dr Jekyll / Dr. Jekyll).
     */
    private static final Set<String> RANDOM_EVENT_NPC_NAMES = new HashSet<>(Arrays.asList(
            "genie", "sandwich lady", "drunken dwarf", "mysterious old man",
            "bee keeper", "beekeeper", "count check",
            "frog prince", "frog princess", "rick turpentine",
            "dr jekyll", "dr. jekyll",
            "niles", "miles", "giles",                  // Mime event NPCs
            "freaky forester", "prison pete",            // deferred-engagement events
            "evil bob", "leo", "pillory guard", "tilt"   // teleport-event NPCs
    ));

    /**
     * Substrings (lower-cased) that mark a plugin as infrastructure rather
     * than a user-facing script. Checked against both the display name and
     * the simple class name. Lower-cased comparison.
     *
     * <p>Captures: Antiban settings, the dashboard itself, Web Walker (utility
     * called by other scripts, not run standalone), MInventory Setups
     * (configuration helper), test harnesses, and the bare "Microbot" core
     * plugin.
     */
    private static final String[] INFRA_NAME_SUBSTRINGS = {
            "antiban",
            "harness",
            "web walker",   // covers "[M] Web Walker" and any package "WebWalker*"
            "webwalker",    // covers simple class names without spaces
            "minventory",   // "[M] MInventory Setups"
            "microbot dashboard plus", // the dashboard itself
            "test runner",  // dev-infrastructure: shouldn't appear as a user script
            "testrunner"
    };

    /**
     * Bare class simple names always excluded (defense in depth). Distinct
     * from display matching since some plugins do not set display names.
     */
    private static final Set<String> EXCLUDED_SIMPLE_CLASS_NAMES = new HashSet<>(Arrays.asList(
            "MicrobotPlugin",
            "MicrobotDashboardPlusPlugin"
    ));

    private static boolean isInfrastructurePlugin(String displayName, String simpleClassName) {
        String d = displayName == null ? "" : displayName.toLowerCase();
        String c = simpleClassName == null ? "" : simpleClassName.toLowerCase();
        if (EXCLUDED_SIMPLE_CLASS_NAMES.contains(simpleClassName)) return true;
        // Exact equals for bare "Microbot" (avoid false positives in containing strings).
        if ("microbot".equals(d)) return true;
        for (String s : INFRA_NAME_SUBSTRINGS) {
            if (d.contains(s) || c.contains(s)) return true;
        }
        return false;
    }

    private final XpHistory xpHistory = new XpHistory();
    private final List<Consumer<PollSnapshot>> listeners = new CopyOnWriteArrayList<>();

    /** Class name -> first observed enabled-millis. Resets when plugin disables. */
    private final Map<String, Long> pluginStartMillis = new HashMap<>();

    /** Per-skill last-observed level for level-up detection. */
    private final Map<Skill, Integer> lastSkillLevels = new EnumMap<>(Skill.class);
    private boolean skillBaselineEstablished = false;

    private DiscordNotifier notifier;
    private AlertManager alertManager;
    private boolean notifyLevelUp = true;
    private boolean notifyAlerts = true;

    /**
     * Optional UI-side alert callback. Fired with a short message ("Mining
     * reached level 60") whenever an alert threshold crosses, regardless of
     * Discord configuration. DashboardWindow registers one to drive the
     * in-window banner.
     */
    private Consumer<String> bannerCallback;

    private ScheduledExecutorService executor;
    private ScheduledFuture<?> scheduledTask;
    private volatile PollSnapshot lastSnapshot = PollSnapshot.empty();
    private volatile int pollIntervalSeconds = 5;
    private volatile int npcMaxDistance = 20;

    public void start(int pollIntervalSeconds) {
        this.pollIntervalSeconds = Math.max(1, pollIntervalSeconds);
        if (executor != null) return;
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "MicrobotDashboardPlus-Poller");
            t.setDaemon(true);
            return t;
        });
        scheduledTask = executor.scheduleAtFixedRate(this::tickSafely, 0, this.pollIntervalSeconds, TimeUnit.SECONDS);
        log.info("MicrobotDashboardPlus poller started (interval={}s)", this.pollIntervalSeconds);
    }

    public void stop() {
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
            scheduledTask = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        pluginStartMillis.clear();
        log.info("MicrobotDashboardPlus poller stopped");
    }

    public void addListener(Consumer<PollSnapshot> listener) {
        listeners.add(listener);
        SwingUtilities.invokeLater(() -> listener.accept(lastSnapshot));
    }

    public void removeListener(Consumer<PollSnapshot> listener) {
        listeners.remove(listener);
    }

    public PollSnapshot getLastSnapshot() { return lastSnapshot; }
    public XpHistory getXpHistory() { return xpHistory; }

    public void setNpcMaxDistance(int distance) {
        this.npcMaxDistance = Math.max(1, Math.min(200, distance));
    }
    public int getNpcMaxDistance() { return npcMaxDistance; }

    public void setNotifier(DiscordNotifier notifier) { this.notifier = notifier; }
    public void setAlertManager(AlertManager alertManager) { this.alertManager = alertManager; }
    public void setNotificationToggles(boolean levelUp, boolean alerts) {
        this.notifyLevelUp = levelUp;
        this.notifyAlerts = alerts;
    }
    public void setAlertThresholds(String csv) {
        if (alertManager != null) alertManager.setThresholdsFromConfig(csv);
    }
    public void setBannerCallback(Consumer<String> bannerCallback) {
        this.bannerCallback = bannerCallback;
    }

    public void refreshNow() {
        // Capture locally: stop() can null the field between the check and the submit.
        ScheduledExecutorService ex = executor;
        if (ex != null && !ex.isShutdown()) {
            ex.submit(this::tickSafely);
        }
    }

    private void tickSafely() {
        try {
            PollSnapshot snapshot = buildSnapshot();
            lastSnapshot = snapshot;
            detectAndFireNotifications(snapshot);
            notifyListeners(snapshot);
        } catch (Throwable t) {
            log.warn("Poll iteration failed: {}", t.getMessage(), t);
        }
    }

    // ---------------------------------------------------------------------
    // Notification triggers
    // ---------------------------------------------------------------------

    private void detectAndFireNotifications(PollSnapshot snapshot) {
        if (snapshot == null) return;

        // Level-up detection. Establish baseline only after we're logged in,
        // otherwise the login-screen 0 → real-level jump on first poll after
        // login fires a "Level up: Attack 0 -> 43" notification for every skill.
        Map<Skill, Integer> currentLevels = snapshot.getSkillLevels();
        if (currentLevels != null && !currentLevels.isEmpty()) {
            if (!skillBaselineEstablished) {
                if (snapshot.isLoggedIn()) {
                    lastSkillLevels.putAll(currentLevels);
                    skillBaselineEstablished = true;
                }
                // Skip notifications until we have a real baseline.
            } else {
                for (Map.Entry<Skill, Integer> e : currentLevels.entrySet()) {
                    Skill skill = e.getKey();
                    int newLevel = e.getValue() == null ? 0 : e.getValue();
                    Integer prev = lastSkillLevels.get(skill);
                    if (prev != null && newLevel > prev) {
                        onLevelUp(skill, prev, newLevel);
                    }
                    lastSkillLevels.put(skill, newLevel);
                }
            }
        }

    }

    private void onLevelUp(Skill skill, int from, int to) {
        String skillName = capitalize(skill.getName());

        // Alert threshold crossings take priority + use a louder prefix.
        boolean alertFired = false;
        if (alertManager != null && alertManager.checkCrossing(skill, to)) {
            alertFired = true;
            // thresholdFor is a separate call; if the config changed in between it can
            // return null, so fall back to the level that triggered the crossing.
            Integer threshold = alertManager.thresholdFor(skill);
            String alertMsg = skillName + " reached level " + (threshold != null ? threshold : to) + "!";
            if (notifyAlerts && notifier != null) {
                notifier.send("ALERT: " + alertMsg);
            }
            // Always fire the UI banner on a threshold crossing, even if
            // Discord is off or not configured.
            if (bannerCallback != null) {
                try { bannerCallback.accept(alertMsg); }
                catch (Throwable t) { log.debug("Banner callback threw: {}", t.getMessage()); }
            }
        }
        if (!alertFired && notifyLevelUp && notifier != null) {
            notifier.send("Level up: " + skillName + " " + from + " -> " + to);
        }
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }

    private void notifyListeners(PollSnapshot snapshot) {
        SwingUtilities.invokeLater(() -> {
            for (Consumer<PollSnapshot> l : listeners) {
                try { l.accept(snapshot); }
                catch (Throwable t) { log.warn("Listener threw: {}", t.getMessage(), t); }
            }
        });
    }

    // ---------------------------------------------------------------------
    // Snapshot construction
    // ---------------------------------------------------------------------

    private PollSnapshot buildSnapshot() {
        Client client = Microbot.getClient();
        if (client == null) return PollSnapshot.empty();

        // Inventory + active-scripts/plus-plugins lists must read on the
        // client thread for safety (Rs2Inventory hits widgets / item containers).
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            PollSnapshot.PollSnapshotBuilder b = PollSnapshot.builder()
                    .timestampMillis(System.currentTimeMillis());

            GameState gs = client.getGameState();
            boolean loggedIn = (gs == GameState.LOGGED_IN);
            b.loggedIn(loggedIn);
            b.gameState(gs == null ? "--" : gs.name());

            Player local = client.getLocalPlayer();
            if (local != null) {
                b.playerName(safe(local.getName()));
                b.combatLevel(local.getCombatLevel());
                WorldPoint wp = local.getWorldLocation();
                b.positionText(wp == null ? "--" : (wp.getX() + "," + wp.getY() + "," + wp.getPlane()));
                int anim = local.getAnimation();
                b.animationText(anim < 0 ? "idle" : Integer.toString(anim));
            } else {
                b.playerName("--").positionText("--").animationText("--");
            }

            b.worldId(client.getWorld());
            b.profileName(profileName());

            // Skills. Record XP samples only while logged in; on the login
            // screen client.getSkillExperience returns 0, which would otherwise
            // become the baseline and inflate every delta after login.
            Map<Skill, Integer> xp = new EnumMap<>(Skill.class);
            Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
            for (Skill s : Skill.values()) {
                if (s == Skill.OVERALL) continue;
                int currentXp = client.getSkillExperience(s);
                xp.put(s, currentXp);
                levels.put(s, client.getRealSkillLevel(s));
                if (loggedIn) {
                    xpHistory.record(s, currentXp);
                }
            }
            b.skillXp(Collections.unmodifiableMap(xp));
            b.skillLevels(Collections.unmodifiableMap(levels));

            b.inventory(collectInventory(loggedIn));
            b.nearbyNpcs(collectNearbyNpcs(client, local));
            b.activeScripts(collectActiveScripts());
            b.antibanState(collectAntibanState());

            return b.build();
        }).orElse(PollSnapshot.empty());
    }

    private static String safe(String s) { return s == null ? "--" : s; }

    private static String profileName() {
        try { return safe(Microbot.getConfigManager().getRSProfileKey()); }
        catch (Throwable t) { return "--"; }
    }

    // ---------------------------------------------------------------------
    // Inventory
    // ---------------------------------------------------------------------

    private List<PollSnapshot.InventoryItem> collectInventory(boolean loggedIn) {
        if (!loggedIn) return Collections.emptyList();
        try {
            List<Rs2ItemModel> items = Rs2Inventory.items().collect(Collectors.toList());
            if (items.isEmpty()) return Collections.emptyList();

            List<PollSnapshot.InventoryItem> out = new ArrayList<>(items.size());
            for (Rs2ItemModel item : items) {
                if (item == null) continue;
                out.add(PollSnapshot.InventoryItem.builder()
                        .slot(item.getSlot())
                        .itemId(item.getId())
                        .name(safe(item.getName()))
                        .quantity(item.getQuantity())
                        .noted(item.isNoted())
                        .build());
            }
            return Collections.unmodifiableList(out);
        } catch (Throwable t) {
            log.debug("collectInventory failed: {}", t.getMessage());
            return Collections.emptyList();
        }
    }

    // ---------------------------------------------------------------------
    // NPCs
    // ---------------------------------------------------------------------

    private List<PollSnapshot.NearbyNpc> collectNearbyNpcs(Client client, Player local) {
        if (local == null) return Collections.emptyList();
        WorldPoint playerWp = local.getWorldLocation();
        if (playerWp == null) return Collections.emptyList();

        List<PollSnapshot.NearbyNpc> out = new ArrayList<>();
        for (NPC npc : client.getNpcs()) {
            if (npc == null) continue;
            WorldPoint npcWp = npc.getWorldLocation();
            if (npcWp == null) continue;
            int dist = playerWp.distanceTo(npcWp);
            if (dist > npcMaxDistance) continue;

            String name = safe(npc.getName());
            boolean isRandomEvent = RANDOM_EVENT_NPC_NAMES.contains(name.toLowerCase());

            out.add(PollSnapshot.NearbyNpc.builder()
                    .name(name)
                    .combatLevel(npc.getCombatLevel())
                    .distance(dist)
                    .randomEvent(isRandomEvent)
                    .build());
        }
        out.sort((a, b) -> Integer.compare(a.getDistance(), b.getDistance()));
        return Collections.unmodifiableList(out);
    }

    // ---------------------------------------------------------------------
    // Scripts + runtime tracking
    // ---------------------------------------------------------------------

    /**
     * Active scripts: enabled Microbot Hub plugins minus a static exclusion
     * set (Antiban, Microbot core utility, the dashboard itself). Per-plugin
     * runtime is tracked from the first time we observe each plugin enabled.
     */
    private List<PollSnapshot.ScriptStatus> collectActiveScripts() {
        try {
            long now = System.currentTimeMillis();
            Set<String> seenThisTick = new HashSet<>();
            List<PollSnapshot.ScriptStatus> out = new ArrayList<>();

            for (Plugin p : Microbot.getPluginManager().getPlugins()) {
                if (p == null) continue;
                if (!Microbot.isPluginEnabled(p.getClass())) continue;
                if (!p.getClass().getName().startsWith(MICROBOT_PACKAGE_PREFIX)) continue;

                String simpleName = p.getClass().getSimpleName();
                String displayName = p.getName();
                if (displayName == null || displayName.isEmpty()) displayName = simpleName;

                if (isInfrastructurePlugin(displayName, simpleName)) continue;

                String className = p.getClass().getName();
                seenThisTick.add(className);
                long startMs = pluginStartMillis.computeIfAbsent(className, k -> now);
                long runtimeMs = Math.max(0, now - startMs);

                out.add(PollSnapshot.ScriptStatus.builder()
                        .pluginClassName(className)
                        .displayName(displayName)
                        .status("Running")
                        .runtimeMillis(runtimeMs)
                        .build());
            }

            // Reset runtimes for plugins that disabled since last tick.
            pluginStartMillis.keySet().retainAll(seenThisTick);

            out.sort((a, b) -> a.getDisplayName().compareToIgnoreCase(b.getDisplayName()));
            return Collections.unmodifiableList(out);
        } catch (Throwable t) {
            log.debug("collectActiveScripts failed: {}", t.getMessage());
            return Collections.emptyList();
        }
    }

    // ---------------------------------------------------------------------
    // Antiban + pause state
    // ---------------------------------------------------------------------

    /**
     * Reads the in-process antiban and pause flags so a user can tell a silent
     * stall (everything idle, nothing intentional) from a deliberate anti-AFK
     * pause (a micro break or action cooldown is holding the script).
     *
     * <p>Reads the static {@link Rs2AntibanSettings} flags, the global
     * {@link Microbot#pauseAllScripts} switch, and the registered
     * blocking-event handlers. The "blocking event running" flag has no public
     * getter, so it is read by reflection and reported as unknown (null) when
     * that fails on the running client version.
     */
    private PollSnapshot.AntibanState collectAntibanState() {
        try {
            boolean antibanEnabled = Rs2AntibanSettings.antibanEnabled;
            boolean cooldown = Rs2AntibanSettings.actionCooldownActive;
            boolean microBreak = Rs2AntibanSettings.microBreakActive;
            boolean takeMicroBreaks = Rs2AntibanSettings.takeMicroBreaks;

            boolean allPaused = false;
            try { allPaused = Microbot.pauseAllScripts != null && Microbot.pauseAllScripts.get(); }
            catch (Throwable t) { log.debug("read pauseAllScripts failed: {}", t.getMessage()); }

            int blockingCount = 0;
            Boolean blockingRunning = null;
            try {
                BlockingEventManager mgr = Microbot.getBlockingEventManager();
                if (mgr != null) {
                    try {
                        java.util.List<?> events = mgr.getEvents();
                        blockingCount = events == null ? 0 : events.size();
                    } catch (Throwable t) {
                        log.debug("read blocking events failed: {}", t.getMessage());
                    }
                    blockingRunning = readBlockingEventRunning(mgr);
                }
            } catch (Throwable t) {
                log.debug("read blocking event manager failed: {}", t.getMessage());
            }

            String summary = buildAntibanSummary(allPaused, microBreak, cooldown,
                    Boolean.TRUE.equals(blockingRunning), antibanEnabled);

            return PollSnapshot.AntibanState.builder()
                    .antibanEnabled(antibanEnabled)
                    .actionCooldownActive(cooldown)
                    .microBreakActive(microBreak)
                    .takeMicroBreaks(takeMicroBreaks)
                    .allScriptsPaused(allPaused)
                    .blockingEventCount(blockingCount)
                    .blockingEventRunning(blockingRunning)
                    .summary(summary)
                    .build();
        } catch (Throwable t) {
            log.debug("collectAntibanState failed: {}", t.getMessage());
            return PollSnapshot.AntibanState.builder()
                    .antibanEnabled(false).summary("unavailable").build();
        }
    }

    /**
     * The BlockingEventManager keeps its "is an event executing right now" flag
     * private with no public getter. Probe it by reflection so we can surface a
     * running blocker, and return null (unknown) when the field is absent or
     * unreadable on this client version.
     */
    private static Boolean readBlockingEventRunning(Object manager) {
        try {
            java.lang.reflect.Field f = manager.getClass().getDeclaredField("isRunning");
            f.setAccessible(true);
            Object v = f.get(manager);
            if (v instanceof java.util.concurrent.atomic.AtomicBoolean) {
                return ((java.util.concurrent.atomic.AtomicBoolean) v).get();
            }
            if (v instanceof Boolean) {
                return (Boolean) v;
            }
            return null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Plain one-line reason for the current hold. Highest-priority cause wins. */
    private static String buildAntibanSummary(boolean allPaused, boolean microBreak,
                                              boolean cooldown, boolean blockingRunning,
                                              boolean antibanEnabled) {
        if (allPaused) return "All scripts paused";
        if (microBreak) return "Micro break in progress";
        if (cooldown) return "Action cooldown";
        if (blockingRunning) return "Handling a blocking event";
        if (!antibanEnabled) return "Running (antiban off)";
        return "Running";
    }
}
