package net.runelite.client.plugins.microbot.lunartanner;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.Default + "Lunar Tanner",
        description = "Tans hides on the lunar spellbook",
        tags = {"magic", "moneymaking"},
        version = TanLeatherPlugin.version,
        minClientVersion = "2.0.13",
        cardUrl = "",
        iconUrl = "",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class TanLeatherPlugin extends Plugin {
    public static final String version = "1.0.0";
    @Inject
    private TanLeatherConfig config;

    @Provides
    TanLeatherConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(TanLeatherConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private TanLeatherOverlay tanLeatherOverlay;

    @Inject
    TanLeatherScript tanLeatherScript;


    @Override
    protected void startUp() throws AWTException {
        log.info("Starting up TanLeatherPlugin");
        if (overlayManager != null) {
            overlayManager.add(tanLeatherOverlay);
        }
        tanLeatherScript.run(config);
    }

    @Override
    protected void shutDown() {
        log.info("Shutting down TanLeatherPlugin");
        tanLeatherScript.shutdown();
        overlayManager.remove(tanLeatherOverlay);
    }
}