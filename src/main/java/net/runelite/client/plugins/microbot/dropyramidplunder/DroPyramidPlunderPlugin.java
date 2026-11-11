package net.runelite.client.plugins.microbot.dropyramidplunder;

import com.google.inject.Provides;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
        name = PluginConstants.DRO + "Pyramid Plunder",
        description = "Pyramid Plunder in Sophanem: urns in the last two rooms, chests in every room",
        tags = {"thieving", "pyramid plunder", "minigame", "sophanem", "dro"},
        authors = {"droplugins"},
        version = DroPyramidPlunderPlugin.version,
        iconUrl = "https://chsami.github.io/Microbot-Hub/DroPyramidPlunderPlugin/assets/icon.png",
        cardUrl = "https://chsami.github.io/Microbot-Hub/DroPyramidPlunderPlugin/assets/card.png",
        minClientVersion = "2.6.55",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class DroPyramidPlunderPlugin extends Plugin {
    public static final String version = "1.0.0";

    @Inject private PyramidPlunderConfig config;
    @Inject private PyramidPlunderScript pyramidPlunderScript;
    @Inject private PyramidPlunderOverlay overlay;
    @Inject private OverlayManager overlayManager;
    @Inject private MouseManager mouseManager;
    @Inject private Client client;

    @Provides
    PyramidPlunderConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(PyramidPlunderConfig.class);
    }

    @Override
    protected void startUp() {
        overlayManager.add(overlay);
        mouseManager.registerMouseListener(0, overlay.getInputListener());
        overlay.enableCanvasInput(client.getCanvas());
        pyramidPlunderScript.run(config);
    }

    @Override
    protected void shutDown() {
        pyramidPlunderScript.shutdown();
        mouseManager.unregisterMouseListener(overlay.getInputListener());
        overlay.disableCanvasInput();
        overlayManager.remove(overlay);
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM) return;
        pyramidPlunderScript.onGameMessage(event.getMessage());
    }
}
