package net.runelite.client.plugins.microbot.jewelleryenchant;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;
import javax.inject.Inject;

@PluginDescriptor(
        name = PluginConstants.Lumusi + "Jewellery Enchant",
        description = "Enchants all types of jewellery, with smart staff & rune handling.",
        tags = {"magic", "enchant", "jewellery", "skilling", "microbot"},
        authors = { "Lumusi" },
        version = JewelleryEnchantPlugin.version,
        minClientVersion = "1.9.8",
        cardUrl = "https://chsami.github.io/Microbot-Hub/JewelleryEnchantPlugin/assets/card.png",
        iconUrl = "https://chsami.github.io/Microbot-Hub/JewelleryEnchantPlugin/assets/icon.png",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class JewelleryEnchantPlugin extends Plugin {
     static final String version = "1.0.1";

    @Inject
    private JewelleryEnchantConfig config;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private JewelleryEnchantOverlay jewelleryEnchantOverlay;
    @Inject
    private JewelleryEnchantScript jewelleryEnchantScript;

    @Provides
    JewelleryEnchantConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(JewelleryEnchantConfig.class);
    }
    @Override
    protected void startUp() throws Exception {
        if (overlayManager != null) {
            overlayManager.add(jewelleryEnchantOverlay);
        }
        jewelleryEnchantScript.run(config);
    }
    @Override
    protected void shutDown() throws Exception {
        jewelleryEnchantScript.shutdown();
        overlayManager.remove(jewelleryEnchantOverlay);
    }
}