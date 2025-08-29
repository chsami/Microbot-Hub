package net.runelite.client.plugins.microbot.GlassBlowing;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.GlassBlowing.tasks.BankTask;
import net.runelite.client.plugins.microbot.GlassBlowing.tasks.BlowTask;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;
import java.util.Arrays;

@PluginDescriptor(
        name = "Glass Blowing",
        description = "Tuna's glass blowing - example TaskScript based bot",
        tags = {"Crafting", "utility"},
        enabledByDefault = false,
        authors = { "Tuna" },
        version = "1.0.0",
        minClientVersion = "1.9.8",
        isExternal = PluginConstants.IS_EXTERNAL,
        cardUrl = "",
        iconUrl = ""
)
@Slf4j
public class GlassBlowingPlugin extends Plugin {
    @Inject
    private GlassBlowingConfig config;
    @Provides
    GlassBlowingConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(GlassBlowingConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private GlassBlowingOverlay glassBlowingOverlay;

    @Inject
    TaskScript taskScript = new TaskScript();


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(glassBlowingOverlay);
        }
        taskScript.addNodes(
                Arrays.asList(
                        new BankTask(config),
                        new BlowTask(config))
        );
        taskScript.run();
    }

    protected void shutDown() {
        taskScript.shutdown();
        overlayManager.remove(glassBlowingOverlay);
    }
}
