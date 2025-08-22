package net.runelite.client.plugins.microbot.autofishing;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.autofishing.enums.AutoFishingState;
import net.runelite.client.plugins.microbot.autofishing.enums.HarpoonType;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginConstants.BGA + "Auto Fishing",
        description = "Automated fishing plugin with banking support",
        tags = {"fishing", "skilling"},
        authors = {"bga"},
        version = AutoFishingPlugin.version,
        minClientVersion = "1.9.8",
        iconUrl = "https://chsami.github.io/Microbot-Hub/AutoFishingPlugin/assets/icon.png",
        cardUrl = "https://chsami.github.io/Microbot-Hub/AutoFishingPlugin/assets/card.png",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class AutoFishingPlugin extends Plugin {
    static final String version = "1.0.0";
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
    private AutoFishingScript fishingScript;

    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(fishingOverlay);
        }
        fishingScript.run(config);
    }

    protected void shutDown() {
        fishingScript.shutdown();
        overlayManager.remove(fishingOverlay);
    }
    
    @Subscribe
    public void onGameTick(GameTick tick) {
        if (fishingScript.isRunning() && fishingScript.getCurrentState() == AutoFishingState.FISHING) {
            HarpoonType selectedHarpoon = fishingScript.getSelectedHarpoon();
            if (selectedHarpoon != null && selectedHarpoon != HarpoonType.NONE && Rs2Combat.getSpecEnergy() >= 100) {
                Rs2Combat.setSpecState(true, 1000);
            }
        }
    }
}