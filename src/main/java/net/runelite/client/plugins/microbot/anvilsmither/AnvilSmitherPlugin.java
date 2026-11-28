package net.runelite.client.plugins.microbot.anvilsmither;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@PluginDescriptor(
        name = AnvilSmitherPlugin.PREFIX + "Anvil Smither",
        authors = {"irkedMATT"},
        version = AnvilSmitherPlugin.version,
        description = "Smiths bars next to a bank in Varrock or Prifddinas, and makes Sailing keel parts.",
        tags = {"smithing", "varrock", "sailing", "irked"},
        cardUrl = "https://chsami.github.io/Microbot-Hub/AnvilSmitherPlugin/assets/card.png",
        iconUrl = "https://chsami.github.io/Microbot-Hub/AnvilSmitherPlugin/assets/icon.png",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL,
        minClientVersion = "2.6.59"
)

@Slf4j
public class AnvilSmitherPlugin extends Plugin {
    static final String version = "1.0.1";
    static final String PREFIX = "<html>[<font color=#FF8FB1>irk</font>] ";
    @Inject
    private AnvilSmitherConfig config;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private AnvilSmitherOverlay overlay;

    @Inject
    AnvilSmitherScript script;

    @Provides
    AnvilSmitherConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AnvilSmitherConfig.class);
    }

    @Override
    protected void startUp() {
        overlay.setScript(script);
        // A recipe that can never work was explained in a message; do not sit enabled doing nothing.
        if (!script.run(config)) {
            // After startUp returns: stopped from inside it, the client goes on to mark us started.
            javax.swing.SwingUtilities.invokeLater(() -> Microbot.stopPlugin(AnvilSmitherPlugin.class));
            return;
        }
        overlayManager.add(overlay);
    }

    /**
     * Read by the launcher's Clients view to show this script's figures under its thumbnail. The client finds
     * it by name, so it must stay public with this name and return type; older clients never call it.
     */
    public java.util.Map<String, String> scriptStats() {
        return overlay.stats();
    }

    @Override
    protected void shutDown() {
        script.shutdown();
        overlayManager.remove(overlay);
    }
}
