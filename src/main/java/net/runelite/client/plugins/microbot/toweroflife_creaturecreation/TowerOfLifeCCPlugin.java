package net.runelite.client.plugins.microbot.toweroflife_creaturecreation;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.NPC;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.Cardew + " Creature Creation",
        description = "Automates the creature creation process within the Tower of Life basement.",
        tags = {"Tower of life", "creature", "creature creation", "creation", "tol", "cc", "cd", "cardew"},
        authors = "Cardew",
        minClientVersion = "1.9.8",
        version = TowerOfLifeCCPlugin.version,
        enabledByDefault = false,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class TowerOfLifeCCPlugin extends Plugin {
    @Inject
    private TowerOfLifeCCConfig config;
    @Provides
    TowerOfLifeCCConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(TowerOfLifeCCConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private TowerOfLifeCCOverlay towerOfLifeCCOverlayOverlay;

    @Inject
    TowerOfLifeCCScript towerOfLifeCCScript;
    static final String version = "1.0.0";

    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(towerOfLifeCCOverlayOverlay);
        }
        towerOfLifeCCScript.run(config);
    }

    protected void shutDown() {
        towerOfLifeCCScript.shutdown();
        overlayManager.remove(towerOfLifeCCOverlayOverlay);
    }

    @Subscribe
    public void onNpcSpawned(NpcSpawned event) {
        NPC npc = (NPC) event.getActor();

        towerOfLifeCCScript.TryAddNpcToTargets(npc, config);
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event) {
        NPC npc = (NPC) event.getActor();

        towerOfLifeCCScript.RemoveNpcFromTargets(npc);
    }

}
