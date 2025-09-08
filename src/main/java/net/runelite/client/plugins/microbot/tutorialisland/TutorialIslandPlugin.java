package net.runelite.client.plugins.microbot.tutorialisland;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginConstants.MOCROSOFT + "Tutorial Island",
        description = "Microbot Tutorial Island plugin",
        authors = { "Mocrosoft" },
        version = TutorialIslandPlugin.version,
        minClientVersion = "1.9.9.1",
        tags = {"TutorialIsland", "microbot"},
        iconUrl = "https://chsami.github.io/Microbot-Hub/TutorialIslandPlugin/assets/icon.png",
        cardUrl = "https://chsami.github.io/Microbot-Hub/TutorialIslandPlugin/assets/card.png",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class TutorialIslandPlugin extends Plugin {
    public static final String version = "1.3";

    @Getter
    private boolean toggleMusic;
    @Getter
    private boolean toggleRoofs;
    @Getter
    private boolean toggleLevelUp;
    @Getter
    private boolean toggleShiftDrop;
    @Getter
    private boolean toggleDevOverlay;
    
    
    @Inject
    private TutorialIslandConfig config;
    @Provides
    TutorialIslandConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(TutorialIslandConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private TutorialIslandOverlay tutorialIslandOverlay;

    @Inject
    TutorialIslandScript tutorialIslandScript;


    @Override
    protected void startUp() throws AWTException {
        toggleMusic = config.toggleMusic();
        toggleRoofs = config.toggleRoofs();
        toggleLevelUp = config.toggleDisableLevelUp();
        toggleShiftDrop = config.toggleShiftDrop();
        toggleDevOverlay = config.toggleDevOverlay();
        
        if (overlayManager != null) {
            overlayManager.add(tutorialIslandOverlay);
        }
        
        tutorialIslandScript.run(config);
    }

    protected void shutDown() {
        tutorialIslandScript.shutdown();
        overlayManager.remove(tutorialIslandOverlay);
    }

    @Subscribe
    public void onConfigChanged(final ConfigChanged event) {
        if (!event.getGroup().equals(TutorialIslandConfig.configGroup)) return;
        
        if (event.getKey().equals(TutorialIslandConfig.toggleMusic)) {
            toggleMusic = config.toggleMusic();
        }
        
        if (event.getKey().equals(TutorialIslandConfig.toggleRoofs)) {
            toggleRoofs = config.toggleRoofs();
        }

        if (event.getKey().equals(TutorialIslandConfig.toggleLevelUp)) {
            toggleLevelUp = config.toggleDisableLevelUp();
        }

        if (event.getKey().equals(TutorialIslandConfig.toggleShiftDrop)) {
            toggleShiftDrop = config.toggleShiftDrop();
        }

        if (event.getKey().equals(TutorialIslandConfig.toggleDevOverlay)) {
            toggleDevOverlay = config.toggleDevOverlay();
        }
    }
}
