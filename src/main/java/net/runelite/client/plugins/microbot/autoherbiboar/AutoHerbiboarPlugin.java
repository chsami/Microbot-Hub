package net.runelite.client.plugins.microbot.autoherbiboar;

import com.google.inject.Provides;
import com.google.common.collect.ImmutableSet;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GroundObjectDespawned;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.autoherbiboar.dependencies.HerbiboarSearchSpot;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;
import java.util.Set;

@PluginDescriptor(
        name = PluginConstants.BGA + "Auto Herbiboar",
        description = "Automaticalli hunts herbiboars with trail tracking and banking support",
        tags = {"skilling", "hunter"},
        authors = {"bga"},
        version = AutoHerbiboarPlugin.version,
        minClientVersion = "1.9.8",
        iconUrl = "https://chsami.github.io/Microbot-Hub/AutoHerbiboarPlugin/assets/icon.png",
        cardUrl = "https://chsami.github.io/Microbot-Hub/AutoHerbiboarPlugin/assets/card.png",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class AutoHerbiboarPlugin extends Plugin {
    static final String version = "1.1.0";
    
    private static final Set<Integer> START_OBJECT_IDS = ImmutableSet.of(
        ObjectID.HUNTING_TRAIL_SPAWN_FOSSIL1,
        ObjectID.HUNTING_TRAIL_SPAWN_FOSSIL2,
        ObjectID.HUNTING_TRAIL_SPAWN_FOSSIL3,
        ObjectID.HUNTING_TRAIL_SPAWN_FOSSIL4,
        ObjectID.HUNTING_TRAIL_SPAWN_FOSSIL5
    );
    
    @Inject
    private AutoHerbiboarConfig config;

    @Provides
    AutoHerbiboarConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AutoHerbiboarConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private AutoHerbiboarOverlay overlay;

    @Inject
    private AutoHerbiboarScript script;
    @Override
    protected void startUp() throws AWTException {
        overlayManager.add(overlay);
        script.run(config);
    }

    @Override
    protected void shutDown() {
        overlayManager.remove(overlay);
        script.shutdown();
    }
    
    @Subscribe
    public void onChatMessage(ChatMessage chatMessage) {
        if (chatMessage.getType() == ChatMessageType.GAMEMESSAGE) {
            String message = chatMessage.getMessage();
            if (message.equals("The creature has successfully confused you with its tracks, leading you round in circles.") || 
                message.equals("You'll need to start again.")) {
                script.handleConfusionMessage();
            } else if (message.equals("Nothing seems to be out of place here.")) {
                script.handleDeadEndTunnel();
            } else if (message.equals("You fail to find any sign of unusual creatures.")) {
                script.handleFailedSearch();
            }
        }
    }
    
    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event) {
        onTileObject(null, event.getGameObject());
    }

    @Subscribe
    public void onGameObjectDespawned(GameObjectDespawned event) {
        onTileObject(event.getGameObject(), null);
    }

    @Subscribe
    public void onGroundObjectSpawned(GroundObjectSpawned event) {
        onTileObject(null, event.getGroundObject());
    }

    @Subscribe
    public void onGroundObjectDespawned(GroundObjectDespawned event) {
        onTileObject(event.getGroundObject(), null);
    }

    // store relevant game objects in script maps
    private void onTileObject(TileObject oldObject, TileObject newObject) {
        if (oldObject != null) {
            WorldPoint oldLocation = oldObject.getWorldLocation();
            script.getStarts().remove(oldLocation);
            script.getTrailObjects().remove(oldLocation);
            script.getTunnels().remove(oldLocation);
        }

        if (newObject == null) {
            return;
        }

        // start objects
        if (START_OBJECT_IDS.contains(newObject.getId())) {
            script.getStarts().put(newObject.getWorldLocation(), newObject);
            return;
        }

        // game objects to trigger next trail (mushrooms, mud, seaweed, etc)
        if (HerbiboarSearchSpot.isSearchSpot(newObject.getWorldLocation())) {
            script.getTrailObjects().put(newObject.getWorldLocation(), newObject);
            return;
        }

        // herbiboar tunnel
        if (script.getEndLocations().contains(newObject.getWorldLocation())) {
            script.getTunnels().put(newObject.getWorldLocation(), newObject);
        }
    }
}
