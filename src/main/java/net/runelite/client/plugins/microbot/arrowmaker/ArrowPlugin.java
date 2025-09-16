package net.runelite.client.plugins.microbot.arrowmaker;

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
import net.runelite.client.plugins.microbot.arrowmaker.ArrowConfig;
import net.runelite.client.plugins.microbot.arrowmaker.ArrowOverlay;
import net.runelite.client.plugins.microbot.arrowmaker.ArrowScript;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;


@PluginDescriptor(
        name = PluginDescriptor.Nate + "Arrow Maker",
        description = "Nate's Arrow Maker",
        tags = {"MoneyMaking", "nate", "Arrow"},
        minClientVersion = "2.0.7",
        authors = {"Nate"},
        version = ArrowPlugin.version,
        cardUrl = "https://chsami.github.io/Microbot-Hub/ArrowPlugin/assets/card.png",
        iconUrl = "https://chsami.github.io/Microbot-Hub/ArrowPlugin/assets/icon.png",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class ArrowPlugin extends Plugin {
    final static String version = "1.0.0";
    @Inject
    private ArrowConfig config;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private ArrowOverlay arrowOverlay;

    @Inject
    ArrowScript arrowScript;

    @Provides
    ArrowConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(ArrowConfig.class);
    }


    @Override
    protected void startUp() throws AWTException {
        Microbot.pauseAllScripts.compareAndSet(true, false);
        if (overlayManager != null) {
            overlayManager.add(arrowOverlay);
        }
        arrowScript.run(config);
    }

    protected void shutDown() {
        arrowScript.shutdown();
        overlayManager.remove(arrowOverlay);
    }
}
