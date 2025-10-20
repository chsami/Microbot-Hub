package net.runelite.client.plugins.microbot.pumper;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.pumper.PumperConfig;
import net.runelite.client.plugins.microbot.pumper.PumperOverlay;
import net.runelite.client.plugins.microbot.pumper.PumperScript;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.LiftedMango + "Pumper",
        description = "LiftedMango's Pumper",
        tags = {"pure", "strength", "str", "pump", "blast furnace"},
        version = PumperPlugin.version,
        minClientVersion = "2.0.13",
        cardUrl = "",
        iconUrl = "",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class PumperPlugin extends Plugin {
    public static final String version = "1.0.7";
    @Inject
    private PumperConfig config;

    @Provides
    PumperConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(PumperConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private PumperOverlay pumperOverlay;

    @Inject
    PumperScript pumperScript;


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(pumperOverlay);
        }
        pumperScript.run(config);
    }

    protected void shutDown() {
        pumperScript.shutdown();
        overlayManager.remove(pumperOverlay);
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
