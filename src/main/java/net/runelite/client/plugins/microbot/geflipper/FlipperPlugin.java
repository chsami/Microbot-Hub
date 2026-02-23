package net.runelite.client.plugins.microbot.geflipper;

import com.google.inject.Inject;
import com.google.inject.Provides;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;

import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.Choken + "Flipper",
        description = "Flipping copilot automation",
        tags = {"flip", "ge", "grand", "exchange", "automation"},
        authors = {"Choken"},
        version = FlipperPlugin.version,
        minClientVersion = "2.0.7",
        cardUrl = "https://chsami.github.io/Microbot-Hub/FlipperPlugin/assets/card.jpg",
        iconUrl = "https://chsami.github.io/Microbot-Hub/FlipperPlugin/assets/icon.jpg",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class FlipperPlugin extends Plugin {
    public static final String version = "1.2.0";
    @Inject
    private Client client;
    @Inject
    private FlipperScript flipperScript;
    @Inject
    private net.runelite.client.plugins.microbot.geflipper.FlipperConfig config;

    @Provides
    net.runelite.client.plugins.microbot.geflipper.FlipperConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(FlipperConfig.class);
    }

    @Override
    protected void startUp() throws AWTException{
        flipperScript.run();
    }

    @Override
    protected void shutDown() {
        flipperScript.state = State.GOING_TO_GE;
        flipperScript.shutdown();
    }
}