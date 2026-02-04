package net.runelite.client.plugins.microbot.royaltitans;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GraphicsObject;
import net.runelite.api.GroundObject;
import net.runelite.api.Tile;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GraphicsObjectCreated;
import net.runelite.api.events.GroundObjectDespawned;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.misc.TimeUtils;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.PluginConstants.DONDER;

@PluginDescriptor(
        name = DONDER + "'s Royal Titans",
        description = "Kills the Royal Titans boss solo or with another bot",
        tags = {"Combat", "bossing", "Royal Titans", "Ice giant", "Fire giant", "Duo"},
        version = RoyalTitansPlugin.version,
        minClientVersion = "2.0.13",
        cardUrl = "",
        iconUrl = "",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class RoyalTitansPlugin extends Plugin {
    public final static String version = "2.0.0";
    private static final Integer GRAPHICS_OBJECT_FIRE = 3218;
    private static final Integer GRAPHICS_OBJECT_ICE = 3221;
    private static final Integer ENRAGE_ELEMENTAL_BLAST_SAFESPOT = 56003;

    @Inject
    public RoyalTitansScript royalTitansScript;
    private ScheduledExecutorService scheduledExecutorService;
    @Inject
    private RoyalTitansConfig config;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private RoyalTitansOverlay royalTitansOverlay;

    private Instant scriptStartTime;

    private ScheduledFuture<?> walkToEnrageTileFuture;

    @Provides
    RoyalTitansConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(RoyalTitansConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {
        scriptStartTime = Instant.now();
        scheduledExecutorService = Executors.newScheduledThreadPool(50);
        if (overlayManager != null) {
            overlayManager.add(royalTitansOverlay);
        }
        royalTitansScript.run(config);
        Rs2Tile.init();
    }

    @Override
    protected void shutDown() {
        royalTitansScript.shutdown();
        scriptStartTime = null;
        overlayManager.remove(royalTitansOverlay);
        if (scheduledExecutorService != null && !scheduledExecutorService.isShutdown()) {
            scheduledExecutorService.shutdown();
        }
    }

    protected String getTimeRunning() {
        return scriptStartTime != null ? TimeUtils.getFormattedDurationBetween(scriptStartTime, Instant.now()) : "";
    }

    @Subscribe
    public void onGraphicsObjectCreated(GraphicsObjectCreated event) {
        final GraphicsObject graphicsObject = event.getGraphicsObject();
        if (graphicsObject.getId() == GRAPHICS_OBJECT_ICE || graphicsObject.getId() == GRAPHICS_OBJECT_FIRE) {
            royalTitansScript.addDangerousTile(graphicsObject.getLocation());
        }
    }

    @Subscribe
    public void onGroundObjectDespawned(GroundObjectDespawned event) {
        final GroundObject groundObject = event.getGroundObject();
        if (groundObject.getId() == ENRAGE_ELEMENTAL_BLAST_SAFESPOT) {
            Microbot.log("Enrage tile despawned");
            if (walkToEnrageTileFuture != null && !walkToEnrageTileFuture.isDone()) {
                log.info("Cancelling walking to enrage tile.");
                walkToEnrageTileFuture.cancel(true);
            }
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (event.getType() != ChatMessageType.GAMEMESSAGE) return;

        String msg = event.getMessage();
        String lootMsg = "You are eligible";
        if (msg.contains(lootMsg)) {
            royalTitansScript.setState(RoyalTitansBotStatus.LOOTING);
        }
    }

    @Subscribe
    public void onGroundObjectSpawned(GroundObjectSpawned event) {
        final GroundObject groundObject = event.getGroundObject();
        final Tile tile = event.getTile();
        if (groundObject.getId() == ENRAGE_ELEMENTAL_BLAST_SAFESPOT) {
            try {
                royalTitansScript.setEnragedTile(tile);
                walkToEnrageTileFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
                    if (!Rs2Player.getLocalLocation().equals(tile.getLocalLocation())) {
                        log.info("Walking to safe tile...");
                        Rs2Walker.walkFastCanvas(tile.getWorldLocation());
                        log.info("Done walking to safe tile...");
                    }
                }, 0, 600, TimeUnit.MILLISECONDS);

                scheduledExecutorService.schedule(() -> { //This is faster than waiting for the despawning of the tile. DPS increase.
                    log.info("Resetting enraged tile and titan focus.");
                    royalTitansScript.resetEnragedTile();
                }, Rs2Random.between(6300, 6600), TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                Microbot.log("Error while walking to enrage tile: " + e.getMessage());
            }
        }
    }
}
