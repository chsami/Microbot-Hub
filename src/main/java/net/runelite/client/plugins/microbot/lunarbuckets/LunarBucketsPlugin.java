package net.runelite.client.plugins.microbot.lunarbuckets;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@Slf4j
@PluginDescriptor(
        name = "[Ne] Lunar Buckets",
        description = "Casts Humidify to fill buckets of water",
        tags = {"lunar", "humidify", "magic", "bucket", "microbot"},
        authors = {"Ne"},
        version = LunarBucketsPlugin.version,
        minClientVersion = "1.9.9.2",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class LunarBucketsPlugin extends Plugin {
    public static final String version = "1.0.0";

    @Inject
    private LunarBucketsConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private LunarBucketsOverlay overlay;

    @Inject
    private LunarBucketsScript script;

    @Provides
    LunarBucketsConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(LunarBucketsConfig.class);
    }

    @Override
    protected void startUp() throws Exception {
        if (overlayManager != null) {
            overlayManager.add(overlay);
        }
        script.run(config);
    }

    @Override
    protected void shutDown() throws Exception {
        script.shutdown();
        overlayManager.remove(overlay);
    }
}
