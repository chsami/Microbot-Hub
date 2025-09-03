package net.runelite.client.plugins.microbot.LunarTablets;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.plugins.microbot.PluginConstants;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginConstants.GAGE + "Lunar Tablets", // Field to define the plugin name (required)
        description = "Creates lunar tablets at the lunar lectern", // A brief description of the plugin (optional, default is '')
        tags = {"mm", "magic", "lunar tablets"}, // Tags to categorize the plugin (optional, default is '')
        authors = { "Gage" }, // Author(s) of the plugin (optional, default is "Unknown Author")
        version = LunarTabletsPlugin.version, // Version of the plugin (required)
        minClientVersion = "1.9.8", // Minimum client version required to run the plugin (required)
        iconUrl = "https://oldschool.runescape.wiki/images/Lectern_%28Lunar%29.png", // URL to plugin icon shown in client (optional)
        cardUrl = "https://i.postimg.cc/3Rp2P56K/chrome-k-OI9-Fh6-A5g.png", // URL to plugin card image for website (optional)
        enabledByDefault = PluginConstants.DEFAULT_ENABLED, // Whether the plugin is enabled by default
        isExternal = PluginConstants.IS_EXTERNAL // Whether the plugin is external
)
@Slf4j
public class LunarTabletsPlugin extends Plugin {
    public static final String version = "2.0.0";
    @Inject
    private LunarTabletsConfig config;
    @Provides
    LunarTabletsConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(LunarTabletsConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private LunarTabletsOverlay lunartabletOverlay;

    @Inject
    LunarTabletsScript lunartabletscript;


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(lunartabletOverlay);
        }
        Rs2Antiban.activateAntiban(); // Enable Anti Ban
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyCraftingSetup();
        Rs2Antiban.setActivity(Activity.CREATING_TELEPORT_TABLETS_AT_LECTERN_LUNAR);
        lunartabletscript.run(config);
    }

    protected void shutDown() {
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.deactivateAntiban();
        lunartabletscript.shutdown();
        overlayManager.remove(lunartabletOverlay);
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
