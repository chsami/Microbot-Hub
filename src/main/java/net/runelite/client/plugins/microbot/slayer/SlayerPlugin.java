package net.runelite.client.plugins.microbot.slayer;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Hitsplat;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Projectile;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.slayer.combat.SlayerDodgeScript;
import net.runelite.client.plugins.microbot.slayer.combat.SlayerFlickerScript;
import net.runelite.client.plugins.microbot.slayer.combat.SlayerHighAlchScript;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginConstants.BIGL + "Slayer",
        description = "Automated slayer with banking and travel support",
        tags = {"slayer", "combat", "task"},
        authors = {"Big L"},
        version = SlayerPlugin.version,
        minClientVersion = "2.1.11",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class SlayerPlugin extends Plugin {

    static final String version = "1.0.0";

    @Inject
    private SlayerConfig config;

    @Inject
    private SlayerScript slayerScript;

    @Inject
    private SlayerOverlay slayerOverlay;

    @Inject
    private OverlayManager overlayManager;

    @Getter
    private static SlayerFlickerScript flickerScript = new SlayerFlickerScript();

    @Getter
    private static SlayerDodgeScript dodgeScript = new SlayerDodgeScript();

    @Getter
    private static SlayerHighAlchScript highAlchScript = new SlayerHighAlchScript();

    @Getter
    private static String currentTask = "";

    @Getter
    private static int taskRemaining = 0;

    @Getter
    private static boolean hasTask = false;

    @Getter
    @Setter
    private static SlayerState state = SlayerState.IDLE;

    @Getter
    @Setter
    private static String currentLocation = "";

    @Getter
    @Setter
    private static int slayerPoints = 0;

    @Provides
    SlayerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(SlayerConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {
        log.info("Slayer plugin started");
        if (overlayManager != null) {
            overlayManager.add(slayerOverlay);
        }
        flickerScript.start(config);
        dodgeScript.setEnabled(config.dodgeProjectiles());
        dodgeScript.run();
        highAlchScript.run(config);
        slayerScript.run(config);
    }

    @Override
    protected void shutDown() {
        log.info("Slayer plugin stopped");
        slayerScript.shutdown();
        flickerScript.stop();
        dodgeScript.shutdown();
        highAlchScript.shutdown();
        if (overlayManager != null) {
            overlayManager.remove(slayerOverlay);
        }
        resetTaskInfo();
    }

    @Subscribe
    public void onGameTick(GameTick gameTick) {
        try {
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
            // Only handle local player death
            if (!(event.getActor() instanceof Player)) {
                return;
            }

            Player player = (Player) event.getActor();
            if (player != Microbot.getClient().getLocalPlayer()) {
                return;
            }

            // Player died - log out immediately to pause gravestone timer
            log.warn("Player died! Logging out to pause gravestone timer.");

            // Disable prayers immediately
            Rs2Prayer.disableAllPrayers();

            // Reset state
            state = SlayerState.IDLE;

            // Stop all scripts first so they don't interfere with logout
            slayerScript.shutdown();
            flickerScript.stop();
            dodgeScript.shutdown();
            highAlchScript.shutdown();

            // Log out to pause the gravestone timer
            Rs2Player.logout();

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
        state = SlayerState.IDLE;
        currentLocation = "";
    }
}
