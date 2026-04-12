package net.runelite.client.plugins.microbot.farmtreerun;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

/**
 * Made by Acun
 */
@PluginDescriptor(
        name = PluginDescriptor.Default + "Farm tree runner",
        description = "Acun's farm tree runner. Supports regular, fruit and hardwood trees",
        tags = {"Farming", "Tree run"},
        authors = {"Acun"},
        version = FarmTreeRunPlugin.version,
        minClientVersion = "2.1.0",
        cardUrl = "https://chsami.github.io/Microbot-Hub/FarmTreeRunPlugin/assets/card.jpg",
        iconUrl = "https://chsami.github.io/Microbot-Hub/FarmTreeRunPlugin/assets/icon.jpg",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class FarmTreeRunPlugin extends Plugin {
    public static final String version = "1.1.1";
    @Inject
    private FarmTreeRunConfig config;
    @Provides
    FarmTreeRunConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(FarmTreeRunConfig.class);
    }

    @Inject
    net.runelite.client.plugins.microbot.farmtreerun.FarmTreeRunScript FarmTreeRunScript;

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private FarmTreeRunOverlay exampleOverlay;

    @Inject
    net.runelite.client.plugins.microbot.farmtreerun.FarmTreeRunScript farmTreeRunScript;


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(exampleOverlay);
        }
        farmTreeRunScript.run(config);
    }

    protected void shutDown() {
        farmTreeRunScript.shutdown();
        overlayManager.remove(exampleOverlay);
    }
    int ticks = 10;
    @Subscribe
    public void onGameTick(GameTick tick)
    {
        //System.out.println(getName().chars().mapToObj(i -> (char)(i + 3)).map(String::valueOf).collect(Collectors.joining()));

        if (ticks > 0) {
            ticks--;
        } else {
            ticks = 10;
        }

    }

}
