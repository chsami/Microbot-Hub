package net.runelite.client.plugins.microbot.autofishing;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginConstants.DEFAULT_PREFIX + "Fishing",
        description = "Automated fishing plugin with banking support",
        tags = {"fishing", "skilling"},
        authors = {"AI Agent"},
        version = AutoFishingPlugin.version,
        minClientVersion = "1.9.8",
        iconUrl = "https://chsami.github.io/Microbot-Hub/AutoFishingPlugin/assets/icon.png",
        cardUrl = "https://chsami.github.io/Microbot-Hub/AutoFishingPlugin/assets/card.png",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class AutoFishingPlugin extends Plugin {
    static final String version = "1.0.5";
    @Inject
    private AutoFishingConfig config;

    @Provides
    AutoFishingConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AutoFishingConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private AutoFishingOverlay fishingOverlay;

    @Inject
    AutoFishingScript fishingScript;

    private int startXp = 0;
    private long startTime = 0;

    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(fishingOverlay);
        }
        fishingScript.run(config);
        startXp = Microbot.getClient().getSkillExperience(Skill.FISHING);
        startTime = System.currentTimeMillis();
    }

    @Override
    protected void shutDown() {
        fishingScript.shutdown();
        overlayManager.remove(fishingOverlay);
    }

    public int getXpGained() {
        return Microbot.getClient().getSkillExperience(Skill.FISHING) - startXp;
    }

    public long getRuntimeMillis() {
        return System.currentTimeMillis() - startTime;
    }

    public String getFormattedRuntime() {
        long millis = getRuntimeMillis();
        long hours = millis / 3600000;
        long minutes = (millis % 3600000) / 60000;
        long seconds = ((millis % 3600000) % 60000) / 1000;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    public int getXpPerHour() {
        long runtime = getRuntimeMillis();
        if (runtime == 0) return 0;
        return (int) (getXpGained() * 3600000.0 / runtime);
    }
}