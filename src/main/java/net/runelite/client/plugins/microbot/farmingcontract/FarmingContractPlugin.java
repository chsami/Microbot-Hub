package net.runelite.client.plugins.microbot.farmingcontract;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@PluginDescriptor(
    name = PluginConstants.PERT + "Farming Contracts",
    description = "Automates Farming Guild contracts. Start the plugin while inside the Farming Guild.",
    tags = {"farming", "contract", "guild", "jane"},
    authors = {"Pert"},
    version = FarmingContractPlugin.VERSION,
    minClientVersion = "2.6.0",
    iconUrl = "https://chsami.github.io/Microbot-Hub/FarmingContractPlugin/assets/icon.png",
    cardUrl = "https://chsami.github.io/Microbot-Hub/FarmingContractPlugin/assets/card.png",
    enabledByDefault = PluginConstants.DEFAULT_ENABLED,
    isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class FarmingContractPlugin extends Plugin {
    static final String VERSION = "0.2.2";

    @Getter
    @Setter
    static String status = "Idle";

    @Inject
    private FarmingContractConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private FarmingContractOverlay overlay;

    @Inject
    private FarmingContractScript script;

    @Provides
    FarmingContractConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(FarmingContractConfig.class);
    }

    @Override
    protected void startUp() {
        overlayManager.add(overlay);
        script.run(config);
    }

    @Override
    protected void shutDown() {
        script.shutdown();
        overlayManager.remove(overlay);
        status = "Idle";
    }
}
