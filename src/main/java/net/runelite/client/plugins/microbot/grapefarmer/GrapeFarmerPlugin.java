package net.runelite.client.plugins.microbot.grapefarmer;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.grapefarmer.GrapeFarmerConfig;
import net.runelite.client.plugins.microbot.grapefarmer.GrapeFarmerOverlay;
import net.runelite.client.plugins.microbot.grapefarmer.GrapeFarmerScript;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.LiftedMango + "Grape farmer",
        description = "Grape farmer",
        tags = {"skilling", "farming", "money making"},
        version = GrapeFarmerPlugin.version,
        minClientVersion = "2.0.13",
        cardUrl = "",
        iconUrl = "",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class GrapeFarmerPlugin extends Plugin {
    public static final String version = "1.0.0";
    @Inject
    private net.runelite.client.plugins.microbot.grapefarmer.GrapeFarmerConfig config;
    @Provides
    net.runelite.client.plugins.microbot.grapefarmer.GrapeFarmerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(GrapeFarmerConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private GrapeFarmerOverlay grapeFarmerOverlay;

    @Inject
    GrapeFarmerScript grapeFarmerScript;


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(grapeFarmerOverlay);
        }
        grapeFarmerScript.run(config);
    }

    protected void shutDown() {
        grapeFarmerScript.shutdown();
        overlayManager.remove(grapeFarmerOverlay);
    }
    int ticks = 10;
    @Subscribe
    public void onGameTick(GameTick tick)
    {
        //System.out.println(getName().chars().mapToObj(i -> (char)(i + 3)).map(String::valueOf).collect(Collectors.joining()));

        if (ticks > 0) {
            ticks--;
        } else {
            ticks = 10;
        }

    }

}
