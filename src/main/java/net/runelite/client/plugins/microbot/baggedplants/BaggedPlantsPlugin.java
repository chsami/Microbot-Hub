package net.runelite.client.plugins.microbot.baggedplants;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.baggedplants.enums.BaggedPlantsState;
import net.runelite.client.plugins.microbot.util.misc.TimeUtils;

import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;
import java.time.Instant;

@PluginDescriptor(
        name = PluginConstants.CRANNY + "Bagged Plants",
        description = "Cranny's Bagged Plant Planter",
        version = "1.0.0",
        minClientVersion = "1.9.8",
        tags = {"skilling", "construction", "farming"},
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL,
        iconUrl = "https://chsami.github.io/Microbot-Hub/baggedplants/assets/Icon.png",
        cardUrl = "https://chsami.github.io/Microbot-Hub/baggedplants/assets/Card.png"
)
@Slf4j
public class BaggedPlantsPlugin extends Plugin {

    @Inject
    private BaggedPlantsConfig config;

    @Provides
    BaggedPlantsConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(BaggedPlantsConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private BaggedPlantsOverlay baggedPlantsOverlay;

    private final BaggedPlantsScript baggedPlantsScript = new BaggedPlantsScript();
    
    // Tracking variables
    private Instant startTime;
    private long startTotalExp;

    @Override
    protected void startUp() throws AWTException {
        Microbot.pauseAllScripts.compareAndSet(true, false);
        if (overlayManager != null) {
            overlayManager.add(baggedPlantsOverlay);
        }
        
        // Initialize tracking
        startTime = Instant.now();
        startTotalExp = Microbot.getClient().getOverallExperience();
        
        baggedPlantsScript.run(config);
    }

    @Override
    protected void shutDown() {
        baggedPlantsScript.shutdown();
        overlayManager.remove(baggedPlantsOverlay);
    }

    public BaggedPlantsState getState() {
        return baggedPlantsScript.getState();
    }
    
    public String getTimeRunning() {
        return startTime != null ? TimeUtils.getFormattedDurationBetween(startTime, Instant.now()) : "";
    }
    
    public int getTotalPlantsPlanted() {
        return baggedPlantsScript.getTotalPlantsPlanted();
    }
    
    public long getXpGained() {
        return Microbot.getClient().getOverallExperience() - startTotalExp;
    }
    
    public long getXpPerHour() {
        if (startTime == null) {
            return 0;
        }

        long secondsElapsed = java.time.Duration.between(startTime, Instant.now()).getSeconds();
        if (secondsElapsed <= 0) {
            return 0;
        }

        // Calculate xp per hour
        return (getXpGained() * 3600L) / secondsElapsed;
    }
}
