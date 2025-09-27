package net.runelite.client.plugins.microbot.varrockcleaner;

import com.google.inject.Provides;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;

import javax.inject.Inject;

@PluginDescriptor(
        name = PluginDescriptor.zerozero + "Museum Cleaner",
        description = "Varrock Museum Cleaner",
        tags = {"varrock", "museum", "cleaner"},
        version = VarrockCleanerPlugin.version,
        minClientVersion = "2.0.13",
        cardUrl = "",
        iconUrl = "",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class VarrockCleanerPlugin extends Plugin {

    public static final String version = "1.0.6";
    static final String CONFIG = "varrockmuseum";

    @Inject
    private VarrockCleanerScript script;

    @Inject
    private VarrockCleanerConfig config;

    @Override
    protected void startUp() {
        script.run(config);
    }

    @Override
    protected void shutDown() {
        script.stop();
    }


    @Provides
    VarrockCleanerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(VarrockCleanerConfig.class);
    }
}
