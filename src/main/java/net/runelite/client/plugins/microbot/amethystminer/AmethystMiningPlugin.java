package net.runelite.client.plugins.microbot.amethystminer;

import com.google.inject.Provides;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.WallObject;
import net.runelite.api.events.WallObjectSpawned;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.amethystminer.enums.Status;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.See1Duck + " Amethyst Miner",
        description = "Automates mining amethyst in the mining guild",
        tags = {"mining", "amethyst", "mining guild"},
        version = AmethystMiningPlugin.version,
        minClientVersion = "2.0.13",
        cardUrl = "",
        iconUrl = "",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class AmethystMiningPlugin extends Plugin {
    public static final String version = "1.2.2";
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private AmethystMiningOverlay amethystMiningOverlay;
    @Inject
    private AmethystMiningScript amethystMiningScript;
    @Inject
    private AmethystMiningConfig amethystMiningConfig;

    @Provides
    AmethystMiningConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AmethystMiningConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {
        overlayManager.add(amethystMiningOverlay);
        amethystMiningScript.run(amethystMiningConfig);
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (event.getGroup().equals("AmethystMining")) {
            if (event.getKey().equals("gemBag")) {
                if (amethystMiningConfig.gemBag()) {
                    AmethystMiningScript.itemsToKeep.add(AmethystMiningScript.gemBag);
                } else {
                    AmethystMiningScript.itemsToKeep.remove(AmethystMiningScript.gemBag);
                }
                if (amethystMiningConfig.gemBag()) {
                    AmethystMiningScript.itemsToKeep.add(AmethystMiningScript.openGemBag);
                } else {
                    AmethystMiningScript.itemsToKeep.remove(AmethystMiningScript.openGemBag);
                }
            }
            if (event.getKey().equals("chiselAmethysts")) {
                if (amethystMiningConfig.chiselAmethysts()) {
                    AmethystMiningScript.itemsToKeep.add(AmethystMiningScript.chisel);
                } else {
                    AmethystMiningScript.itemsToKeep.remove(AmethystMiningScript.chisel);
                }
            }
        }
    }

    @Subscribe
    public void onWallObjectSpawned(WallObjectSpawned event) {
        WallObject wallObject = event.getWallObject();
        if (wallObject == null)
            return;
        if (AmethystMiningScript.status == Status.MINING && wallObject.getId() == ObjectID.AMETHYSTROCK_EMPTY) {
            if (AmethystMiningScript.oreVein != null) {
                if (wallObject.getWorldLocation().equals(AmethystMiningScript.oreVein.getWorldLocation())) {
                    AmethystMiningScript.oreVein = null;
                }
            }
        }
    }

    protected void shutDown() {
        amethystMiningScript.shutdown();
        overlayManager.remove(amethystMiningOverlay);
    }
}
