package net.runelite.client.plugins.microbot.natewinemaker;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;


@PluginDescriptor(
        name = PluginDescriptor.Nate + "Wine Maker",
        description = "Nate's Wine Maker",
        tags = {"skilling", "nate", "wine", "cooking"},
        authors = {"Nate"},
        version = WinePlugin.version,
        minClientVersion = "2.0.7",
        iconUrl = "https://chsami.github.io/Microbot-Hub/WinePlugin/assets/icon.png",
        cardUrl = "https://chsami.github.io/Microbot-Hub/WinePlugin/assets/card.png",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class WinePlugin extends Plugin {
    public final static String version = "1.2.0";

    @Inject
    private Client client;
    @Inject
    private WineConfig config;
    @Inject
    private ClientThread clientThread;
    @Inject
    Notifier notifier;

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private WineOverlay wineOverlay;

    @Inject
    WineScript wineScript;

    @Provides
    WineConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(WineConfig.class);
    }


    @Override
    protected void startUp() throws AWTException {
        Microbot.pauseAllScripts.compareAndSet(true, false);
        if (overlayManager != null) {
            overlayManager.add(wineOverlay);
        }
        wineScript.run(config);
    }

    protected void shutDown() {
        wineScript.shutdown();
        overlayManager.remove(wineOverlay);
    }
}
