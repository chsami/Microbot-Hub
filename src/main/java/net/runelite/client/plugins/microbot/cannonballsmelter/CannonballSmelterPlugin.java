package net.runelite.client.plugins.microbot.cannonballsmelter;

import com.google.inject.Provides;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginConstants.GIRDY + "Cannonball Smelter",
        description = "Makes cannonballs",
        authors = { "Girdy", "Engin" },
        version = CannonballSmelterPlugin.version,
        minClientVersion = "1.9.9.1",
        tags = { "smithing", "girdy", "skilling" },
        iconUrl = "https://chsami.github.io/Microbot-Hub/CannonballSmelterPlugin/assets/icon.png",
        cardUrl = "https://chsami.github.io/Microbot-Hub/CannonballSmelterPlugin/assets/card.png",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class CannonballSmelterPlugin extends Plugin {
        public static final String version = "1.1.0";

        @Inject
        private CannonballSmelterConfig config;

        @Provides
        CannonballSmelterConfig provideConfig(ConfigManager configManager) {
            return configManager.getConfig(CannonballSmelterConfig.class);
        }

        @Inject
        private OverlayManager overlayManager;
        @Inject
        private CannonballSmelterOverlay cannonballSmelterOverlay;

        @Inject
        CannonballSmelterScript cannonballSmelterScript;

        @Override
        protected void startUp() throws AWTException {
			Microbot.pauseAllScripts.compareAndSet(true, false);
            if (overlayManager != null) {
                overlayManager.add(cannonballSmelterOverlay);
            }
            cannonballSmelterScript.run(config);
        }

        protected void shutDown() {
            cannonballSmelterScript.shutdown();
            overlayManager.remove(cannonballSmelterOverlay);
        }
}

