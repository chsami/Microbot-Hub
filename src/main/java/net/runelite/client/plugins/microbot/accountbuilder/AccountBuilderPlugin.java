package net.runelite.client.plugins.microbot.accountbuilder;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginConstants.DEFAULT_PREFIX + "AccountBuilder",
        description = "Automated 1-click account builder from scratch",
        tags = {"account", "builder", "training", "quests", "microbot"},
        authors = {"T3rr0or"},
        version = AccountBuilderPlugin.VERSION,
        minClientVersion = "1.9.8",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class AccountBuilderPlugin extends Plugin {

    static final String VERSION = "1.0.0";

    @Inject
    private AccountBuilderConfig config;

    @Inject
    private AccountBuilderOverlay overlay;

    @Inject
    private OverlayManager overlayManager;

    @Getter
    private final AccountBuilderScript script = new AccountBuilderScript();

    @Provides
    AccountBuilderConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AccountBuilderConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {
        overlayManager.add(overlay);
        script.run(config);
    }

    @Override
    protected void shutDown() {
        script.shutdown();
        overlayManager.remove(overlay);
    }
}
