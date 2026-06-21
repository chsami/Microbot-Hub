package net.runelite.client.plugins.microbot.vorkathhelper;

import com.google.inject.Provides;
import javax.inject.Inject;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
        name = PluginConstants.SR03 + "VorkathHelper",
        description = "Useful overlays for Vorkath",
        authors = { "Sushruth Rao (sr03)"},
        version = VorkathHelperPlugin.version,
        minClientVersion = "2.0.13",
        tags = {"vorkath", "overlays", "sr03"},
        iconUrl = "https://chsami.github.io/Microbot-Hub/VorkathHelperPlugin/assets/icon.png",
        cardUrl = "https://chsami.github.io/Microbot-Hub/VorkathHelperPlugin/assets/card.png",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class VorkathHelperPlugin extends Plugin {

    public final static String version = "1.0.0";

    @Inject
    private EventBus eventBus;

    @Inject
    private VorkathHelperScript script;

    @Inject
    private VorkathHelperScriptOverlay overlay;

    @Inject
    private OverlayManager overlayManager;

    @Provides
    VorkathHelperConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(VorkathHelperConfig.class);
    }

    @Override
    protected void startUp() {
        eventBus.register(script);
        overlayManager.add(overlay);
        script.run();
    }

    @Override
    protected void shutDown() {
        eventBus.unregister(script);
        overlayManager.remove(overlay);
        script.shutdown();
    }
}
