package net.runelite.client.plugins.microbot.sandcrabs;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.Mocrosoft + "SandCrabs",
        description = "Kills SandCrab & resets",
        tags = {"Combat", "microbot", "sand", "crab", "sandcrab", "attack", "kill"},
        version = SandCrabPlugin.version,
        minClientVersion = "2.1.32",
        cardUrl = "",
        iconUrl = "",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class SandCrabPlugin extends Plugin  {
    public final static String version = "1.5.3";
    @Inject
    private SandCrabConfig config;

    @Provides
    SandCrabConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(SandCrabConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private SandCrabOverlay sandCrabOverlay;

    @Inject
    public SandCrabScript sandCrabScript;



    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(sandCrabOverlay);
        }
        sandCrabScript.run(config, this);
    }

    protected void shutDown() {
        sandCrabScript.shutdown();
        overlayManager.remove(sandCrabOverlay);
    }
}
