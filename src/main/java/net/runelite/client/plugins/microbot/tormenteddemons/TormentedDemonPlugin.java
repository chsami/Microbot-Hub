package net.runelite.client.plugins.microbot.tormenteddemons;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GraphicsObjectCreated;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.StringJoiner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@PluginDescriptor(
        name = PluginDescriptor.zerozero + "Tormented Demons",
        description = "Automates restocking, prayer flicking, and gear switching during Tormented Demon",
        tags = {"tormented", "flicker", "weapon", "switch", "microbot"},
        version = TormentedDemonPlugin.version,
        minClientVersion = "2.0.13",
        cardUrl = "",
        iconUrl = "",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class TormentedDemonPlugin extends Plugin {

    public static final String version = "1.2.5";

    private static final int CHANGE_ATTACK_STYLE_ANIMATION = 11387;
    private static final int MELEE_ATTACK_ANIMATION = 11392;
    private static final int MAGIC_ATTACK_ANIMATION = 11388;
    private static final int RANGE_ATTACK_ANIMATION = 11389;

    private ScheduledExecutorService scheduledExecutorService;

    @Inject
    private ConfigManager configManager;

    @Inject
    private TormentedDemonConfig config;

    @Provides
    TormentedDemonConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(TormentedDemonConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private TormentedDemonOverlay tormentedDemonOverlay;

    @Inject
    private TormentedDemonScript tormentedDemonScript;

    @Override
    protected void startUp() throws AWTException {
        scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        if (overlayManager != null) {
            overlayManager.add(tormentedDemonOverlay);
        }
        tormentedDemonScript.run(config);
    }

    @Override
    protected void shutDown() {
        tormentedDemonScript.shutdown();
        overlayManager.remove(tormentedDemonOverlay);
        if (scheduledExecutorService != null && !scheduledExecutorService.isShutdown()) {
            scheduledExecutorService.shutdown();
        }
    }

    @Subscribe
    private void onConfigChanged(ConfigChanged event) {
        if (!event.getGroup().equals("tormenteddemon")) {
            return;
        }

        if (event.getKey().equals("copyGear")) {
            Microbot.getClientThread().invoke(() -> {
                try {
                    StringJoiner gearList = new StringJoiner(",");
                    for (Item item : Microbot.getClient().getItemContainer(InventoryID.EQUIPMENT).getItems()) {
                        if (item != null && item.getId() != -1 && item.getId() != 6512) {
                            ItemComposition itemComposition = Microbot.getClient().getItemDefinition(item.getId());
                            gearList.add(itemComposition.getName());
                        }
                    }

                    String gearString = gearList.toString();
                    if (gearString.isEmpty()) {
                        Microbot.log("No gear found to copy.");
                        return;
                    }

                    StringSelection selection = new StringSelection(gearString);
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
                    Microbot.log("Current gear copied to clipboard: " + gearString);
                } catch (Exception e) {
                    Microbot.log("Failed to copy gear to clipboard: " + e.getMessage());
                }
            });
        }
    }



    @Subscribe
    public void onGraphicsObjectCreated(GraphicsObjectCreated event) {
        final GraphicsObject graphicsObject = event.getGraphicsObject();
        final int TORMENTED_VENGENCE_SPECIAL = 2856;

        if (graphicsObject.getId() != TORMENTED_VENGENCE_SPECIAL) {
            return;
        }

        final Client client = Microbot.getClient();
        final WorldPoint bombTile = client.getTopLevelWorldView().getScene().isInstance() ?
                WorldPoint.fromLocalInstance(client, graphicsObject.getLocation()) :
                WorldPoint.fromLocal(client, graphicsObject.getLocation());

        tormentedDemonScript.bombDodge.addBomb(bombTile);
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        final TormentedDemonBombDodge bombDodge = tormentedDemonScript.bombDodge;
        final WorldPoint playerLocation = Rs2Player.getWorldLocation();

        switch (bombDodge.onTick(playerLocation, config.dodgeDelay())) {
            case STEP:
                final WorldPoint safeTile = bombDodge.pickSafeTile(playerLocation,
                        Rs2Tile.getReachableTilesFromTile(playerLocation, TormentedDemonBombDodge.SAFE_TILE_RADIUS));
                if (safeTile == null) {
                    if (bombDodge.reportNoSafeTile()) {
                        Microbot.log("No free tile within 2 tiles to dodge the Tormented Demon fire bomb; you will take the hit unless you move manually.");
                    }
                    break;
                }
                scheduledExecutorService.execute(() -> Rs2Walker.walkFastCanvas(safeTile));
                break;
            case DODGED:
                Microbot.log("Stepped off the Tormented Demon fire bomb tile.");
                break;
            case HIT:
                if (config.dodgeDelay() >= TormentedDemonBombDodge.TICK_MILLIS && !bombDodge.hadNoSafeTile()) {
                    Microbot.log("Failed to dodge Tormented Demon special attack: still on the fire bomb tile when it landed. If this keeps happening, lower 'Dodge delay (ms)' in the plugin settings.");
                } else {
                    Microbot.log("Failed to dodge Tormented Demon special attack: still on the fire bomb tile when it landed (could not move in time).");
                }
                break;
            default:
                break;
        }
    }


    @Subscribe
    public void onAnimationChanged(AnimationChanged event) {
        if (event.getActor() instanceof NPC) {
            NPC npc = (NPC) event.getActor();

            Player localPlayer = Microbot.getClient().getLocalPlayer();
            if (localPlayer == null || localPlayer.getInteracting() != npc) {
                return;
            }

            int animationId = npc.getAnimation();

            if (animationId == CHANGE_ATTACK_STYLE_ANIMATION) {
                // Check which prayer is currently active
                boolean isMeleeActive = Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_MELEE);
                boolean isRangeActive = Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_RANGE);
                boolean isMagicActive = Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_MAGIC);

                // Toggle the opposite prayer based on currently active prayer
                if (isMeleeActive) {
                    Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_RANGE, true);
                } else if (isRangeActive) {
                    Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MAGIC, true);
                } else if (isMagicActive) {
                    Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, true);
                } else {
                    Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, true);
                }
            }

            if (animationId == MELEE_ATTACK_ANIMATION && !Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_MELEE)) {
                Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, true);
            }
            if (animationId == RANGE_ATTACK_ANIMATION && !Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_RANGE)) {
                Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_RANGE, true);
            }
            if (animationId == MAGIC_ATTACK_ANIMATION  && !Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_MAGIC)) {
                Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MAGIC, true);
            }
        }
    }

}