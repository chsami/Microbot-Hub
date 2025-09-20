package net.runelite.client.plugins.microbot.volcanicashminer;

import lombok.Getter;
import net.runelite.client.eventbus.Subscribe;
import com.google.inject.Provides;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.pluginscheduler.api.SchedulablePlugin;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.AndCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LogicalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.event.PluginScheduleEntryPostScheduleTaskEvent;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.TaFCat + "Volcanic Ash Miner",
        description = "Start either at the ash mine on Fossil Island or with a digsite pendant in your inventory. Have a pickaxe in your inventory or equipped.",
        authors = { "TaF" },
        version = VolcanicAshMinerScript.VERSION,
        minClientVersion = "2.0.9",
        tags = {"volcanic", "ash", "mining", "ironman", "taf", "microbot"},
        iconUrl = "https://chsami.github.io/Microbot-Hub/volcanicashminer/assets/icon.png",
        cardUrl = "https://chsami.github.io/Microbot-Hub/volcanicashminer/assets/card.png",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class VolcanicAshMinerPlugin extends Plugin implements SchedulablePlugin {
    @Inject
    private VolcanicAshMinerConfig config;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private VolcanicAshMinerOverlay volcanicAshMinerOverlay;
    @Getter
    @Inject
    private VolcanicAshMinerScript volcanicAshMinerScript;
    private LogicalCondition stopCondition = new AndCondition();

    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(volcanicAshMinerOverlay);
        }
        volcanicAshMinerScript.run(config);
    }

    @Override
    protected void shutDown() {
        volcanicAshMinerScript.shutdown();
        overlayManager.remove(volcanicAshMinerOverlay);
    }

    @Provides
    VolcanicAshMinerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(VolcanicAshMinerConfig.class);
    }

    @Subscribe
    public void onPluginScheduleEntryPostScheduleTaskEvent(PluginScheduleEntryPostScheduleTaskEvent event) {
        if (event.getPlugin() == this) {
            if (volcanicAshMinerScript != null) {
                Rs2Bank.walkToBank();
            }
            Microbot.stopPlugin(this);
        }
    }

    @Override
    public LogicalCondition getStopCondition() {
        // Create a new stop condition
        return this.stopCondition;
    }
}