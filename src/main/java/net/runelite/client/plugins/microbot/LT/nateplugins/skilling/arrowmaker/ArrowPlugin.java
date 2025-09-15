package net.runelite.client.plugins.microbot.LT.nateplugins.skilling.arrowmaker;

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
import net.runelite.client.plugins.microbot.nateplugins.skilling.arrowmaker.ArrowConfig;
import net.runelite.client.plugins.microbot.nateplugins.skilling.arrowmaker.ArrowOverlay;
import net.runelite.client.plugins.microbot.nateplugins.skilling.arrowmaker.ArrowScript;
import net.runelite.client.plugins.microbot.util.mouse.VirtualMouse;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(

        name = PluginConstants.LT +"Arrow Maker",
        description = "Nate's Arrow Maker",
        version = MicroAgilityPlugin.version,
        minClientVersion = "2.0.0",
        tags = {"MoneyMaking", "nate", "Arrow"},
        iconUrl = "",
        cardUrl = "",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class ArrowPlugin extends Plugin {
    @Inject
    private Client client;
    @Inject
    private ArrowConfig config;
    @Inject
    private ClientThread clientThread;
    @Inject
    Notifier notifier;

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
