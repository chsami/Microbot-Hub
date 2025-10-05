package net.runelite.client.plugins.microbot.amethystminer;

import com.google.inject.Provides;
import net.runelite.api.ObjectID;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    public static final String version = "1.2.1";
    private static final Logger log = LoggerFactory.getLogger(AmethystMiningPlugin.class);
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
                boolean b = amethystMiningConfig.gemBag() ? AmethystMiningScript.itemsToKeep.add(AmethystMiningScript.gemBag) : AmethystMiningScript.itemsToKeep.remove(AmethystMiningScript.gemBag);
                boolean c = amethystMiningConfig.gemBag() ? AmethystMiningScript.itemsToKeep.add(AmethystMiningScript.openGemBag) : AmethystMiningScript.itemsToKeep.remove(AmethystMiningScript.openGemBag);
            }
            if (event.getKey().equals("chiselAmethysts")) {
                boolean b = amethystMiningConfig.chiselAmethysts() ? AmethystMiningScript.itemsToKeep.add(AmethystMiningScript.chisel) : AmethystMiningScript.itemsToKeep.remove(AmethystMiningScript.chisel);
            }
        }
    }

    @Subscribe
    public void onWallObjectSpawned(WallObjectSpawned event) {
        WallObject wallObject = event.getWallObject();
        if (wallObject == null)
            return;
        if (AmethystMiningScript.status == Status.MINING && wallObject.getId() == ObjectID.EMPTY_WALL) {
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
