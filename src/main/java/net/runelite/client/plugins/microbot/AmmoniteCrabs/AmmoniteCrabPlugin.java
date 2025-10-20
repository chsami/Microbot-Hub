package net.runelite.client.plugins.microbot.AmmoniteCrabs;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.util.misc.TimeUtils;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;
import java.time.Instant;

@PluginDescriptor(
        name = PluginDescriptor.TaFCat + "AmmoniteCrabs",
        description = "Kills AmmoniteCrabs & resets",
        tags = {"Combat", "TaF", "crabs", "AmmoniteCrabs"},
        version = AmmoniteCrabPlugin.version,
        minClientVersion = "2.0.13",
        cardUrl = "",
        iconUrl = "",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class AmmoniteCrabPlugin extends Plugin {

    public final static String version = "1.1.1";
    @Inject
    public AmmoniteCrabScript ammoniteCrabScript;
    @Inject
    private AmmoniteCrabConfig config;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private AmmoniteCrabOverlay ammoniteCrabOverlay;


    private Instant scriptStartTime;
    private long startTotalExp;

    @Provides
    AmmoniteCrabConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AmmoniteCrabConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {
        scriptStartTime = Instant.now();
        startTotalExp = Microbot.getClient().getOverallExperience();
        if (overlayManager != null) {
            overlayManager.add(ammoniteCrabOverlay);
        }
        ammoniteCrabScript.run(config);
    }

    protected void shutDown() {
        ammoniteCrabScript.shutdown();
        overlayManager.remove(ammoniteCrabOverlay);
        scriptStartTime = null;
    }

    protected String getTimeRunning() {
        return scriptStartTime != null ? TimeUtils.getFormattedDurationBetween(scriptStartTime, Instant.now()) : "";
    }

    public long getXpGained() {
        return Microbot.getClient().getOverallExperience() - startTotalExp;
    }

    public long getXpPerHour() {
        if (scriptStartTime == null) {
            return 0;
        }

        long secondsElapsed = java.time.Duration.between(scriptStartTime, Instant.now()).getSeconds();
        if (secondsElapsed <= 0) {
            return 0;
        }

        // Calculate xp per hour
        return (int) ((getXpGained() * 3600L) / secondsElapsed);
    }
}
