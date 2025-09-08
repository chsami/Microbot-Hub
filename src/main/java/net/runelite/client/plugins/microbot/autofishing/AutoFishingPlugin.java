package net.runelite.client.plugins.microbot.autofishing;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.autofishing.enums.AutoFishingState;
import net.runelite.client.plugins.microbot.autofishing.enums.HarpoonType;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
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
    static final String version = "1.0.2";
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
    private int currentXp = 0;
    private long startTime = 0;

    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(fishingOverlay);
        }
        fishingScript.run(config);
        startXpAndTime(Microbot.getClient().getSkillExperience(Skill.FISHING));
    }

    protected void shutDown() {
        fishingScript.shutdown();
        overlayManager.remove(fishingOverlay);
        startXp = 0;
        currentXp = 0;
        startTime = 0;
    }
    
    @Subscribe
    public void onGameTick(GameTick tick) {
        if (fishingScript.isRunning() && fishingScript.getCurrentState() == AutoFishingState.FISHING) {
            HarpoonType selectedHarpoon = fishingScript.getSelectedHarpoon();
            if (selectedHarpoon != null && selectedHarpoon != HarpoonType.NONE && Rs2Combat.getSpecEnergy() >= 100) {
                Rs2Combat.setSpecState(true, 1000);
            }
        }
        updateCurrentXp(Microbot.getClient().getSkillExperience(Skill.FISHING));
    }

    public void startXpAndTime(int startXp) {
        this.startXp = startXp;
        this.startTime = System.currentTimeMillis();
    }

    public void updateCurrentXp(int currentXp) {
        this.currentXp = currentXp;
    }

    public int getXpGained() {
        return currentXp - startXp;
    }

    public long getRuntimeMillis() {
        return System.currentTimeMillis() - startTime;
    }

    public String getFormattedRuntime() {
        long millis = getRuntimeMillis();
        long seconds = (millis / 1000) % 60;
        long minutes = (millis / (1000 * 60)) % 60;
        long hours = (millis / (1000 * 60 * 60));
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    public int getXpPerHour() {
        long runtime = getRuntimeMillis();
        if (runtime == 0) return 0;
        double hours = runtime / 3600000.0;
        return (int) (getXpGained() / hours);
    }
}