package net.runelite.client.plugins.microbot.banchecker;

import com.google.inject.Provides;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import javax.inject.Inject;
@PluginDescriptor(
        name = "Ban Checker",
        description = "Checks if account is banned based on login screen.",
        tags = {"ban", "checker"},
        authors = {"BanaanBakje"},
        version = BanCheckerPlugin.version,
        iconUrl= "https://chsami.github.io/Microbot-Hub/BanCheckerPlugin/assets/icon.png",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class BanCheckerPlugin extends Plugin
{
    static final String version = "1.0.0";
    @Inject
    private BanCheckerConfig config;

    @Inject
    private BanCheckerScript script;

    @Provides
    BanCheckerConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(BanCheckerConfig.class);
    }

    @Override
    protected void startUp() throws Exception
    {
        script.run(config);
        script.loopCheck(config);
    }

    @Override
    protected void shutDown() throws Exception
    {
        script.stopLoop();
        script.isBanned = false;
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        switch (event.getGameState())
        {
            case LOGGED_IN:
            case LOADING:
            case HOPPING:
                script.isBanned = false;
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (config.enabled()) {
            script.shouldRun = true;
            script.run(config);
        }
        else {
            script.isBanned = false;
        }
        if (config.loopCheck()) {
            script.loopCheck(config);
        }
        else {
            script.stopLoop();
        }
    }
}

