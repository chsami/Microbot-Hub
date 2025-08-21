package net.runelite.client.plugins.microbot.woodcutting;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginConstants.MOCROSOFT + "Auto Woodcutting",
        description = "Microbot woodcutting plugin",
        tags = {"Woodcutting", "microbot", "skilling"},
        authors = {"Mocrosoft"},
        version = AutoWoodcuttingPlugin.version,
        minClientVersion = "1.9.8",
        iconUrl = "https://chsami.github.io/Microbot-Hub/AutoWoodcuttingPlugin/assets/icon.png",
        cardUrl = "https://chsami.github.io/Microbot-Hub/AutoWoodcuttingPlugin/assets/card.png",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class AutoWoodcuttingPlugin extends Plugin {
    static final String version = "1.6.5";

    @Inject
    private AutoWoodcuttingConfig config;

    @Provides
    AutoWoodcuttingConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AutoWoodcuttingConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private AutoWoodcuttingOverlay woodcuttingOverlay;

    @Inject
    AutoWoodcuttingScript autoWoodcuttingScript;


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(woodcuttingOverlay);
        }
        autoWoodcuttingScript.run(config);
    }

    protected void shutDown() {
        autoWoodcuttingScript.shutdown();
        overlayManager.remove(woodcuttingOverlay);
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
            if (event.getType() == ChatMessageType.GAMEMESSAGE) {
                String message = event.getMessage().toLowerCase();
                if (message.equals("you can't light a fire here.")){
            autoWoodcuttingScript.cannotLightFire = true;}
            }
        }
    }
