package net.runelite.client.plugins.microbot.autosmelt;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = "<html>[<font color=#00ffaf>Jiggly</font>]" + "Edgeville Auto Smelt",
        description = "Automatically smelts bars at Edgeville furnace",
        tags = {"smithing", "smelting", "bars", "edgeville", "furnace"},
        authors = {"Jiggly"},
        version = AutoSmeltPlugin.version,
        minClientVersion = "1.9.8",
        iconUrl = "https://via.placeholder.com/64x64.png?text=AS",
        cardUrl = "https://via.placeholder.com/300x200.png?text=Auto+Smelt",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class AutoSmeltPlugin extends Plugin {
    static final String version = "1.2.2";

    @Inject
    private AutoSmeltConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private AutoSmeltOverlay autoSmeltOverlay;

    @Inject
    private AutoSmeltScript autoSmeltScript;

    @Provides
    AutoSmeltConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AutoSmeltConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {
        Microbot.pauseAllScripts.compareAndSet(true, false);
        if (overlayManager != null) {
            overlayManager.add(autoSmeltOverlay);
        }
        autoSmeltScript.run(config);
        log.info("Auto Smelt Plugin started");
    }

    @Override
    protected void shutDown() {
        autoSmeltScript.shutdown();
        overlayManager.remove(autoSmeltOverlay);
        log.info("Auto Smelt Plugin stopped");
    }
}
