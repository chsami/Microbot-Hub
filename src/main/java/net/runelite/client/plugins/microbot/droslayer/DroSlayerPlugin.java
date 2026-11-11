package net.runelite.client.plugins.microbot.droslayer;


import com.google.inject.Provides;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Hitsplat;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Projectile;
import net.runelite.api.events.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.droslayer.combat.DroSlayerDodgeScript;
import net.runelite.client.plugins.microbot.droslayer.combat.DroSlayerFlickerScript;
import net.runelite.client.plugins.microbot.droslayer.combat.DroSlayerHighAlchScript;
import net.runelite.client.plugins.microbot.droslayer.profile.BaseProfileDro;
import net.runelite.client.plugins.microbot.inventorysetups.MInventorySetupsPlugin;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginConstants.DRO + "AIO Slayer",
        description = "Gets a task from any Slayer master, travels there with [Dro] Efficient Walker, fights with the matching gear, prayers and cannon, banks for supplies and returns for the next task. Krystilia Wilderness tasks are supported. Requires [Dro] Efficient Walker from the Microbot Hub.",
        tags = {"dro", "slayer", "aio", "combat", "task", "efficientwalker", "microbot"},
        authors = {"Big L", "Dro"},
        version = DroSlayerPlugin.version,
        minClientVersion = "2.6.55",
        iconUrl = "https://chsami.github.io/Microbot-Hub/DroSlayerPlugin/assets/icon.png",
        cardUrl = "https://chsami.github.io/Microbot-Hub/DroSlayerPlugin/assets/card.png",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class DroSlayerPlugin extends Plugin {

    public static final String version = "1.3.95";

    @Inject
    private DroSlayerConfig config;

    @Inject
    private DroSlayerScript slayerScript;

    @Inject
    private DroSlayerOverlay slayerOverlay;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private net.runelite.client.input.MouseManager mouseManager;

    @Inject
    private ConfigManager configManager;

    @Getter
    private static DroSlayerFlickerScript flickerScript = new DroSlayerFlickerScript();

    @Getter
    private static DroSlayerDodgeScript dodgeScript = new DroSlayerDodgeScript();

    @Getter
    private static DroSlayerHighAlchScript highAlchScript = new DroSlayerHighAlchScript();

    @Getter
    private static String currentTask = "";

    @Getter
    private static int taskRemaining = 0;

    @Getter
    private static boolean hasTask = false;

    @Getter
    private static DroSlayerState state = DroSlayerState.IDLE;

    public static void setState(DroSlayerState state) {
        DroSlayerPlugin.state = state;
        DroSlayerHold.stateChanged(state);
    }

    @Getter
    @Setter
    private static String currentLocation = "";

    @Getter
    @Setter
    private static int slayerPoints = 0;

    private volatile int startingSlayerXp = -1;
    @Getter
    private volatile int slayerXpGained;
    private volatile long runtimeStarted;

    public String getRuntimeDisplay() {
        long seconds = runtimeStarted == 0 ? 0 : Math.max(0, (System.nanoTime() - runtimeStarted) / 1_000_000_000L);
        return String.format(java.util.Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60);
    }

    public DroSlayerMaster getSelectedSlayerMaster() {
        return config.slayerMaster();
    }

    public boolean isWildernessSlayer() {
        return slayerScript.isWildernessSlayer();
    }

    public String getEscapesDeathsDisplay() {
        return slayerScript.getEscapesDeathsDisplay();
    }

    public String getCannonTileDisplay() { return slayerScript.getCannonTileDisplay(); }

    public String getCannonWorldDisplay() {
        return slayerScript.getCannonWorldDisplay();
    }

    private volatile long xpSessionStarted;

    public long getSlayerXpPerHour() {
        long elapsed = System.nanoTime() - xpSessionStarted;
        return xpSessionStarted == 0 || elapsed <= 0 ? 0
                : (long) (slayerXpGained * 3_600_000_000_000.0 / elapsed);
    }

    private void updateSlayerXp() {
        if (!Microbot.isLoggedIn()) return;
        int current = Microbot.getClient().getSkillExperience(net.runelite.api.Skill.SLAYER);
        if (startingSlayerXp < 0) {
            startingSlayerXp = current;
            xpSessionStarted = System.nanoTime();
        }
        slayerXpGained = Math.max(0, current - startingSlayerXp);
    }

    @Inject
    void registerPrayerSettings(net.runelite.client.eventbus.EventBus eventBus) {
        eventBus.register(new PrayerSettingsActions(DroSlayerPlugin::openTaskPrayerEditor));
    }

    static final class PrayerSettingsActions {
        private final Runnable open;
        PrayerSettingsActions(Runnable open) { this.open = open; }
        @Subscribe
        public void onConfigChanged(ConfigChanged event) {
            if (DroSlayerConfig.GROUP.equals(event.getGroup()) && "editTaskPrayers".equals(event.getKey()))
                open.run();
        }
    }

    @Provides
    DroSlayerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(DroSlayerConfig.class);
    }

    /** False when startUp refused to run because Efficient Walker is missing. */
    private volatile boolean walkerReady;

    @Override
    protected void startUp() throws AWTException {
        walkerReady = DroSlayerWalkerRequirement.efficientWalkerEnabled();
        if (!walkerReady) {
            log.warn("[Dro] Slayer not started: Efficient Walker is not installed or enabled");
            // Start-up runs on the Swing thread and the message is a modal dialog, so both the
            // stop and the message wait until start-up has returned.
            javax.swing.SwingUtilities.invokeLater(() -> {
                Microbot.stopPlugin(this);
                Microbot.showMessage(DroSlayerWalkerRequirement.MISSING_MESSAGE);
            });
            return;
        }
        runtimeStarted = System.nanoTime();
        log.info("[Dro] Slayer {} plugin started", version);
        mouseManager.registerMouseListener(0, slayerOverlay.getInputListener());
        slayerOverlay.enableCanvasInput(Microbot.getClient().getCanvas());
        startingSlayerXp = -1;
        slayerXpGained = 0;
        xpSessionStarted = 0;
        Microbot.getClientThread().invoke(() -> { updateSlayerXp(); return true; });
        DroSlayerRuntime.start(config, configManager);
        if (overlayManager != null) {
            overlayManager.add(slayerOverlay);
        }
        flickerScript.start(config);
        dodgeScript.setEnabled(config.dodgeProjectiles());
        dodgeScript.run(config);
        highAlchScript.run(config);
        slayerScript.run(config);
    }

    @Override
    protected void shutDown() {
        if (!walkerReady) return;
        walkerReady = false;
        log.info("[Dro] Slayer 2 plugin stopped");
        mouseManager.unregisterMouseListener(slayerOverlay.getInputListener());
        slayerOverlay.disableCanvasInput();
        slayerScript.shutdown();
        DroSlayerRuntime.shutdown();
        flickerScript.stop();
        dodgeScript.shutdown();
        highAlchScript.shutdown();
        if (overlayManager != null) {
            overlayManager.remove(slayerOverlay);
        }
        resetTaskInfo();
    }

    /** Settings remain usable while the plugin is stopped and unsubscribed. */
    public static void openTaskPrayerEditor() {
        javax.swing.SwingUtilities.invokeLater(() -> DroSlayerPrayerEditor.open((prayers, offensive) -> {
            DroSlayerPlugin plugin = Microbot.getPlugin(DroSlayerPlugin.class);
            if (plugin != null) plugin.slayerScript.updateTaskPrayers(prayers, offensive);
        }));
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (event.getType() != net.runelite.api.ChatMessageType.GAMEMESSAGE
                && event.getType() != net.runelite.api.ChatMessageType.SPAM) return;
        String message = net.runelite.client.util.Text.removeTags(event.getMessage()).toLowerCase(java.util.Locale.ROOT);
        slayerScript.onWildernessWeaponMessage(message);
        slayerScript.onLootMessage(message);
        if (message.contains("enough space to set up") || message.contains("cannot set up a cannon here")) {
            slayerScript.onCannonPlacementRejected();
        }
        if (message.contains("your abyssal tentacle")
                && (message.contains("degraded") || message.contains("consumed"))) {
            slayerScript.onTentacleDegraded();
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!DroSlayerConfig.GROUP.equals(event.getGroup())) {
            return;
        }

        if ("captureCurrentInventorySetup".equals(event.getKey())) {
            String name = config.captureInventorySetupName();
            if (name == null || name.trim().isEmpty()) {
                name = "Dro Slayer 2";
            }
            name = name.trim();

            try {
                MInventorySetupsPlugin inventorySetupsPlugin = Microbot.getPlugin(MInventorySetupsPlugin.class);
                if (inventorySetupsPlugin == null) {
                    log.warn("Inventory Setups plugin is not available; cannot save setup '{}'", name);
                    Microbot.showMessage("[Dro] Slayer 2: Inventory Setups is not available.");
                    return;
                }
                inventorySetupsPlugin.addInventorySetup(name);
                Microbot.showMessage("[Dro] Slayer 2 saved current loadout as Inventory Setup: " + name);
                log.info("Saved current loadout as Inventory Setup '{}'", name);
            } catch (Exception e) {
                log.error("Unable to save current Inventory Setup '{}'", name, e);
                Microbot.showMessage("[Dro] Slayer 2 could not save Inventory Setup: " + name);
            }
        }
    }

    @Subscribe
    public void onGameTick(GameTick gameTick) {
        try {
            updateSlayerXp();
            // Update dodge script enabled state from config
            dodgeScript.setEnabled(config.dodgeProjectiles());

            PrayerFlickStyle style = config.prayerFlickStyle();
            if (style == PrayerFlickStyle.LAZY_FLICK ||
                style == PrayerFlickStyle.PERFECT_LAZY_FLICK ||
                style == PrayerFlickStyle.MIXED_LAZY_FLICK) {
                flickerScript.onGameTick();
            }
        } catch (Exception e) {
            log.error("Slayer Plugin onGameTick Error: " + e.getMessage());
        }
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned npcDespawned) {
        try {
            PrayerFlickStyle style = config.prayerFlickStyle();
            if (style == PrayerFlickStyle.LAZY_FLICK ||
                style == PrayerFlickStyle.PERFECT_LAZY_FLICK ||
                style == PrayerFlickStyle.MIXED_LAZY_FLICK) {
                flickerScript.onNpcDespawned(npcDespawned);
            }
        } catch (Exception e) {
            log.error("Slayer Plugin onNpcDespawned Error: " + e.getMessage());
        }
    }

    @Subscribe
    public void onHitsplatApplied(HitsplatApplied event) {
        try {
            if (event.getActor() != Microbot.getClient().getLocalPlayer()) return;

            final Hitsplat hitsplat = event.getHitsplat();
            PrayerFlickStyle style = config.prayerFlickStyle();

            if (hitsplat.isMine() && event.getActor().getInteracting() instanceof NPC &&
                (style == PrayerFlickStyle.LAZY_FLICK ||
                 style == PrayerFlickStyle.PERFECT_LAZY_FLICK ||
                 style == PrayerFlickStyle.MIXED_LAZY_FLICK)) {
                flickerScript.onPlayerHit();
            }
        } catch (Exception e) {
            log.error("Slayer Plugin onHitsplatApplied Error: " + e.getMessage());
        }
    }

    @Subscribe
    public void onProjectileMoved(ProjectileMoved event) {
        try {
            if (!config.dodgeProjectiles()) return;

            Projectile projectile = event.getProjectile();
            // Only track projectiles targeting a WorldPoint (AOE attacks), not those targeting actors
            if (projectile.getTargetActor() == null) {
                dodgeScript.addProjectile(projectile);
            }
        } catch (Exception e) {
            log.error("Slayer Plugin onProjectileMoved Error: " + e.getMessage());
        }
    }

    @Subscribe
    public void onActorDeath(ActorDeath event) {
        try {
            if (event.getActor() instanceof NPC) {
                slayerScript.onEntDeath((NPC) event.getActor());
                return;
            }
            // Only handle local player death
            if (!(event.getActor() instanceof Player)) {
                return;
            }

            Player player = (Player) event.getActor();
            if (player != Microbot.getClient().getLocalPlayer()) {
                return;
            }

            slayerScript.recordSessionDeath();

            if (slayerScript.onWildernessDeath(player.getWorldLocation())) {
                log.warn("Wilderness death recorded; Slayer will resupply and recover its cannon");
                return;
            }
            // Player died - log out immediately to pause gravestone timer
            log.warn("Player died! Logging out to pause gravestone timer.");

            // Disable prayers immediately through BaseProfileDro.
            DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.MAINTENANCE, true, () -> {
                Rs2Prayer.disableAllPrayers();
            });

            // Reset state
            state = DroSlayerState.IDLE;

            // Stop auxiliary scripts first so they don't interfere with logout.
            slayerScript.shutdown();
            flickerScript.stop();
            dodgeScript.shutdown();
            highAlchScript.shutdown();

            // Log out through BaseProfileDro while the shared profile is still active.
            DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.MAINTENANCE, true, () -> { Rs2Player.logout(); });
            DroSlayerRuntime.shutdown();

            // Show message after logout attempt
            Microbot.showMessage("You died! Logged out to pause gravestone timer. Recover your items manually.");
            log.info("Plugin stopped and logged out due to player death.");
        } catch (Exception e) {
            log.error("Slayer Plugin onActorDeath Error: " + e.getMessage());
        }
    }

    public static void updateTaskInfo(boolean hasSlayerTask, String taskName, int remaining) {
        hasTask = hasSlayerTask;
        currentTask = taskName != null ? taskName : "";
        taskRemaining = remaining;
    }

    private static void resetTaskInfo() {
        hasTask = false;
        currentTask = "";
        taskRemaining = 0;
        state = DroSlayerState.IDLE;
        currentLocation = "";
    }
}
