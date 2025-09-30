package net.runelite.client.plugins.microbot.revkiller;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.pluginscheduler.api.SchedulablePlugin;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.AndCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LogicalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.event.PluginScheduleEntrySoftStopEvent;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginConstants.GAGE + "Revenant Killer", // Field to define the plugin name (required)
        description = "Kills revenants in the wilderness.", // A brief description of the plugin (optional, default is '')
        tags = {"mm", "ranging", "revenant killer"}, // Tags to categorize the plugin (optional, default is '')
        authors = { "Gage" }, // Author(s) of the plugin (optional, default is "Unknown Author")
        version = revKillerPlugin.version, // Version of the plugin (required)
        minClientVersion = "1.9.8", // Minimum client version required to run the plugin (required)
        iconUrl = "https://chsami.github.io/Microbot-Hub/revKillerPlugin/assets/icon.png", // URL to plugin icon shown in client (optional)
        cardUrl = "https://chsami.github.io/Microbot-Hub/revKillerPlugin/assets/card.png", // URL to plugin card image for website (optional)
        enabledByDefault = PluginConstants.DEFAULT_ENABLED, // Whether the plugin is enabled by default
        isExternal = PluginConstants.IS_EXTERNAL // Whether the plugin is external
)
@Slf4j
public class revKillerPlugin extends Plugin implements SchedulablePlugin {
    public static final String version = "2.0.5";
    @Inject
    private net.runelite.client.plugins.microbot.revkiller.revKillerConfig config;
    @Provides
    net.runelite.client.plugins.microbot.revkiller.revKillerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(revKillerConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private net.runelite.client.plugins.microbot.revkiller.revKillerOverlay revKillerOverlay;

    @Inject
    net.runelite.client.plugins.microbot.revkiller.revKillerScript revKillerScript;
    LogicalCondition stopCondition = new AndCondition();

    @Inject
    private EventBus eventBus;


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(revKillerOverlay);
        }
        revKillerScript.run(config);
        revKillerScript.startPkerDetection();
        revKillerScript.startHealthCheck();
        revKillerScript.weDied = false;
        revKillerScript.shouldFlee = false;
        revKillerScript.firstRun = true;
        eventBus.register(this);
        revKillerScript.selectedWP = config.selectedRev().getWorldPoint();
        revKillerScript.selectedArrow = config.selectedArrow().getArrowID();
        revKillerScript.selectedRev = config.selectedRev().getName();
        Rs2Antiban.activateAntiban();
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyCombatSetup();
        Rs2Antiban.setActivity(Activity.KILLING_REVENANTS_MAGIC_SHORTBOW);
    }

    protected void shutDown() {
        revKillerScript.weDied = false;
        revKillerScript.shouldFlee = false;
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.deactivateAntiban();
        revKillerScript.stopFutures();
        revKillerScript.shutdown();
        eventBus.unregister(this);
        overlayManager.remove(revKillerOverlay);
    }

    @Subscribe
    public void onActorDeath(ActorDeath event) {
        //Thank you george!
        if (event.getActor().equals(Microbot.getClient().getLocalPlayer())) {
            revKillerScript.weDied = true;
        }
    }

    @Subscribe
    public void onPluginScheduleEntrySoftStopEvent(PluginScheduleEntrySoftStopEvent event) {
        try{
            if (event.getPlugin() == this) {
                Microbot.stopPlugin(this);
            }
        } catch (Exception e) {
            log.error("Error stopping plugin: ", e);
        }
    }
    @Override
    public LogicalCondition getStopCondition() {
        // Create a new stop condition
        return this.stopCondition;
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
