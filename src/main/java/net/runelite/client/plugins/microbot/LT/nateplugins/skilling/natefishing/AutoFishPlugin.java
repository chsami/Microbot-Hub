package net.runelite.client.plugins.microbot.LT.nateplugins.skilling.natefishing;

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
import net.runelite.client.plugins.microbot.agility.MicroAgilityPlugin;
import net.runelite.client.plugins.microbot.util.mouse.VirtualMouse;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(

        name = PluginConstants.LT + "Auto Fishing",
        description = "Nate's Power Fisher plugin",
        version = MicroAgilityPlugin.version,
        minClientVersion = "2.0.0",
        tags = {"Fishing", "nate", "skilling"},
        iconUrl = "",
        cardUrl = "",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class AutoFishPlugin extends Plugin {
    @Inject
    private AutoFishConfig config;

    @Provides
    AutoFishConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AutoFishConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private AutoFishOverlay fishingOverlay;

    @Inject
    private AutoFishingScript fishingScript;


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(fishingOverlay);
        }
        fishingScript.run(config);
    }

    protected void shutDown() {
        fishingScript.shutdown();
        overlayManager.remove(fishingOverlay);
    }
}
