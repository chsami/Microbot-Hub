package net.runelite.client.plugins.microbot.accountstarter;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

/** No @Subscribe handlers: they break for sideloaded plugins, so any event wiring is registered by hand. */
@PluginDescriptor(
        name = AccountStarterPlugin.PREFIX + "Account Starter",
        description = "Sets up a fresh F2P account through Adventure Paths and Mudfoot's Big Adventure. Quest-only mode for mains.",
        tags = {"account builder", "adventure paths", "f2p", "quest", "irked"},
        authors = {"irkedMATT"},
        version = AccountStarterPlugin.version,
        minClientVersion = "2.6.65",
        iconUrl = "https://chsami.github.io/Microbot-Hub/AccountStarterPlugin/assets/icon.png",
        cardUrl = "https://chsami.github.io/Microbot-Hub/AccountStarterPlugin/assets/card.png",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class AccountStarterPlugin extends Plugin {
    static final String version = "1.0.1";
    static final String PREFIX = "<html>[<font color=#FF8FB1>irk</font>] ";

    @Inject
    private AccountStarterConfig config;
    @Inject
    private AccountStarterScript script;
    @Inject
    private ItemManager itemManager;
    @Inject
    private EventBus eventBus;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private AccountStarterOverlay overlay;
    private EventBus.Subscriber tickSubscriber;

    @Provides
    AccountStarterConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AccountStarterConfig.class);
    }

    @Override
    protected void startUp() {
        script.run(config, this, itemManager);
        overlayManager.add(overlay);
        tickSubscriber = eventBus.register(GameTick.class, script::onGameTick, 0f);
    }

    @Override
    protected void shutDown() {
        if (tickSubscriber != null) {
            eventBus.unregister(tickSubscriber);
            tickSubscriber = null;
        }
        overlayManager.remove(overlay);
        script.shutdown();
    }
}
