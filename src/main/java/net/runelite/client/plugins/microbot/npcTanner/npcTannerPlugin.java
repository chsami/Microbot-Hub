package net.runelite.client.plugins.microbot.npcTanner;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.LunarTablets.LunarTabletsPlugin;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginConstants.GAGE + "NPC Tanner", // Field to define the plugin name (required)
        description = "Tans any hide in the bank at Al-Kharid", // A brief description of the plugin (optional, default is '')
        tags = {"mm", "tanning", "npc tanner"}, // Tags to categorize the plugin (optional, default is '')
        authors = { "Gage" }, // Author(s) of the plugin (optional, default is "Unknown Author")
        version = LunarTabletsPlugin.version, // Version of the plugin (required)
        minClientVersion = "1.9.8", // Minimum client version required to run the plugin (required)
        iconUrl = "https://oldschool.runescape.wiki/images/Tanner_%28Crafting_Guild%29.png", // URL to plugin icon shown in client (optional)
        cardUrl = "https://i.postimg.cc/bwk8DmKZ/chrome-8-RXSkq-DT3-G.png", // URL to plugin card image for website (optional)
        enabledByDefault = PluginConstants.DEFAULT_ENABLED, // Whether the plugin is enabled by default
        isExternal = PluginConstants.IS_EXTERNAL // Whether the plugin is external
)
@Slf4j
public class npcTannerPlugin extends Plugin {
    public static final String version = "2.0.0";
    @Inject
    private npcTannerConfig config;
    @Provides
    npcTannerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(npcTannerConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private npcTannerOverlay TanOverlay;

    @Inject
    npcTannerScript TannerScript;


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(TanOverlay);
        }
        Rs2Antiban.activateAntiban();
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyUniversalAntibanSetup();
        Rs2Antiban.setActivity(Activity.TANNING_COWHIDE);
        TannerScript.run(config);
    }

    protected void shutDown() {
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.deactivateAntiban();
        TannerScript.shutdown();
        overlayManager.remove(TanOverlay);
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
