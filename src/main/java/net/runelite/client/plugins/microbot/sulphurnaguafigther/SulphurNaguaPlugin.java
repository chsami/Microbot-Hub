package net.runelite.client.plugins.microbot.sulphurnaguafigther; // Passen Sie den Paketnamen an

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
        name = PluginConstants.VIP + "Sulphur Nagua Fighter",
        description = "Automatically fights Sulphur Naguas in Varlamore, handling potions and combat.",
        tags = {"combat", "pvm", "nagua", "varlamore", "microbot"},
        authors = { "VIP" },
        version = SulphurNaguaPlugin.version,
        minClientVersion = "1.9.8",
        cardUrl = "https://chsami.github.io/Microbot-Hub/SulphurNaguaPlugin/assets/card.png",
        iconUrl = "https://chsami.github.io/Microbot-Hub/SulphurNaguaPlugin/assets/icon.png",
        isExternal = PluginConstants.IS_EXTERNAL,
        enabledByDefault = false
)
@Slf4j
public class SulphurNaguaPlugin extends Plugin {

    public static final String version = "2.0.0";

    @Inject
    SulphurNaguaScript sulphurNaguaScript;
    @Inject
    private SulphurNaguaConfig config;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private SulphurNaguaOverlay sulphurNaguaOverlay;

    private Instant scriptStartTime;

    @Provides
    SulphurNaguaConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(SulphurNaguaConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {
        scriptStartTime = Instant.now();
        if (overlayManager != null) {
            overlayManager.add(sulphurNaguaOverlay);
        }
        sulphurNaguaScript.run(config);
    }

    @Override
    protected void shutDown() {
        sulphurNaguaScript.shutdown();
        overlayManager.remove(sulphurNaguaOverlay);
        scriptStartTime = null;
        sulphurNaguaScript.totalNaguaKills = 0;
    }


    protected String getTimeRunning() {
        return scriptStartTime != null ? TimeUtils.getFormattedDurationBetween(scriptStartTime, Instant.now()) : "";
    }

    public long getXpGained() {
        long scriptStartExp = sulphurNaguaScript.getStartTotalExp(); // Lombok's @Getter provides this method
        if (scriptStartExp == 0) return 0; // The script has not initialized yet.

        return Microbot.getClient().getOverallExperience() - scriptStartExp;
    }

    public long getXpPerHour() {
        if (scriptStartTime == null) return 0;

        long secondsElapsed = java.time.Duration.between(scriptStartTime, Instant.now()).getSeconds();
        if (secondsElapsed <= 0) return 0;

        return (getXpGained() * 3600L) / secondsElapsed;
    }
}