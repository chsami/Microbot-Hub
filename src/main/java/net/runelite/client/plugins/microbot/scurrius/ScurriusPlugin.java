package net.runelite.client.plugins.microbot.scurrius;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GraphicsObject;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GraphicsObjectCreated;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.scurrius.enums.State;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.Default + "Scurrius",
        description = "Scurrius boss bot with prayer flicking and falling-rock dodging",
        tags = {"microbot", "scurrius", "boss"},
        version = ScurriusPlugin.version,
        minClientVersion = "2.0.13",
        cardUrl = "",
        iconUrl = "",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class ScurriusPlugin extends Plugin {
    public static final String version = "1.1.4";
    @Inject
    private ScurriusConfig config;
    @Provides
    ScurriusConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(ScurriusConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private ScurriusOverlay exampleOverlay;

    @Inject
    ScurriusScript scurriusScript;

    @Inject
    private Client client;

    private volatile ScurriusTickDriver tickDriver;


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(exampleOverlay);
        }
        ScurriusScript.state = State.BANKING;
        ScurriusScript.XP.reset(System.currentTimeMillis());
        tickDriver = new ScurriusTickDriver();
        tickDriver.setMode(config.prayerMode());
        tickDriver.setLazySettings(config.lazyFlickHoldMin(), config.lazyFlickHoldMax(), config.lazyFlickVariation());
        tickDriver.setMousePrayers(config.antibanMousePrayers());
        tickDriver.setMixedWeights(config.mixedAlwaysOnWeight(), config.mixedLazyWeight());
        tickDriver.rollKillMode();
        scurriusScript.setTickDriver(tickDriver);
        scurriusScript.run(config);
    }

    protected void shutDown() {
        scurriusScript.setTickDriver(null);
        if (tickDriver != null) {
            tickDriver.shutdown();
            tickDriver = null;
        }
        scurriusScript.shutdown();
        overlayManager.remove(exampleOverlay);
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (event.getType() != ChatMessageType.GAMEMESSAGE) return;
        
        if (event.getMessage().equalsIgnoreCase("oh dear, you are dead!") && config.shutdownAfterDeath()) {
            Rs2Walker.setTarget(null);
            shutDown();
        }
    }

    @Subscribe
    public void onGraphicsObjectCreated(GraphicsObjectCreated event) {
        if (tickDriver != null && event.getGraphicsObject().getId() == ScurriusTickDriver.ROCKS_GRAPHICS_ID) {
            tickDriver.onRocksCreated(client, event.getGraphicsObject());
        }
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        ScurriusScript.XP.startIfNeeded(client.getGameState() == GameState.LOGGED_IN, client::getSkillExperience);
        if (tickDriver != null) {
            tickDriver.setMode(config.prayerMode());
            tickDriver.setLazySettings(config.lazyFlickHoldMin(), config.lazyFlickHoldMax(), config.lazyFlickVariation());
            tickDriver.setMousePrayers(config.antibanMousePrayers());
            tickDriver.setMixedWeights(config.mixedAlwaysOnWeight(), config.mixedLazyWeight());
            tickDriver.onGameTick(client);
        }
    }

    @Subscribe
    public void onAnimationChanged(AnimationChanged event) {
        if (tickDriver != null) {
            tickDriver.onAnimationChanged(client, event);
        }
    }

    @Subscribe
    public void onProjectileMoved(ProjectileMoved event) {
        if (tickDriver != null) {
            tickDriver.onProjectileMoved(client, event);
        }
    }

    @Subscribe
    public void onHitsplatApplied(HitsplatApplied event) {
        if (tickDriver != null) {
            tickDriver.onHitsplat(client, event);
        }
    }
}
