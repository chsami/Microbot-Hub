package net.runelite.client.plugins.microbot.autobuyer;

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
        name = PluginDescriptor.Default + "GE Buyer",
        description = "Acun's GE buyer. Give a list of items to buy",
        tags = {"buy", "buyer", "grand exchange", "ge"},
        authors = {"Acun"},
        version = AutoBuyerPlugin.version,
        minClientVersion = "2.0.7",
        cardUrl = "https://chsami.github.io/Microbot-Hub/AutoBuyerPlugin/assets/card.png",
        iconUrl = "https://chsami.github.io/Microbot-Hub/AutoBuyerPlugin/assets/icon.png",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class AutoBuyerPlugin extends Plugin {
    static final String version = "1.0.1";
    @Inject
    private net.runelite.client.plugins.microbot.autobuyer.AutoBuyerConfig config;

    @Provides
    net.runelite.client.plugins.microbot.autobuyer.AutoBuyerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AutoBuyerConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private AutoBuyerOverlay exampleOverlay;

    @Inject
    AutoBuyerScript exampleScript;


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(exampleOverlay);
        }
        exampleScript.run(config);
    }

    protected void shutDown() {
        exampleScript.shutdown();
        overlayManager.remove(exampleOverlay);
    }

    int ticks = 10;

    @Subscribe
    public void onGameTick(GameTick tick) {
        //System.out.println(getName().chars().mapToObj(i -> (char)(i + 3)).map(String::valueOf).collect(Collectors.joining()));

        if (ticks > 0) {
            ticks--;
        } else {
            ticks = 10;
        }

    }

}
