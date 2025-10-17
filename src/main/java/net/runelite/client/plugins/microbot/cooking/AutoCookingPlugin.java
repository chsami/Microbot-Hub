package net.runelite.client.plugins.microbot.cooking;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.cooking.scripts.AutoCookingScript;
import net.runelite.client.plugins.microbot.cooking.scripts.BurnBakingScript;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

import static net.runelite.client.plugins.PluginDescriptor.Mocrosoft;

@PluginDescriptor(
        name = PluginDescriptor.GMason + "Auto Cooking",
        description = "Microbot cooking plugin",
        tags = {"cooking", "microbot", "skilling"},
        authors = {"George"},
        version = AutoCookingPlugin.version,
        minClientVersion = "2.0.7",
        cardUrl = "https://chsami.github.io/Microbot-Hub/AutoCookingPlugin/assets/card.jpg",
        iconUrl = "https://chsami.github.io/Microbot-Hub/AutoCookingPlugin/assets/icon.jpg",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class AutoCookingPlugin extends Plugin {
    public final static String version = "1.1.1";
    @Inject
    AutoCookingScript autoCookingScript;
    @Inject
    BurnBakingScript burnBakingScript;
    @Inject
    private AutoCookingConfig config;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private AutoCookingOverlay overlay;

    @Provides
    AutoCookingConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AutoCookingConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(overlay);
        }
        switch (config.cookingActivity()) {
            case COOKING:
                autoCookingScript.run(config);
                break;
            case BURN_BAKING:
                burnBakingScript.run(config);
                break;
            default:
                Microbot.log("Invalid Cooking Activity");
        }
    }

    protected void shutDown() {
        autoCookingScript.shutdown();
        burnBakingScript.shutdown();
        overlayManager.remove(overlay);
    }
}
