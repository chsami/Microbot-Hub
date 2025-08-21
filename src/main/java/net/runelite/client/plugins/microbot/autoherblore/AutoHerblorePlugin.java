package net.runelite.client.plugins.microbot.autoherblore;

import com.google.inject.Provides;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import javax.inject.Inject;
import java.awt.AWTException;

@PluginDescriptor(
        name = PluginConstants.BGA + "Auto Herblore",
        description = "Performs various herblore tasks...",
        tags = {"herblore", "skilling"},
        authors = {"bga"},
        version = AutoHerblorePlugin.version,
        minClientVersion = "1.9.8",
        iconUrl = "https://chsami.github.io/Microbot-Hub/AutoHerblorePlugin/assets/icon.png",
        cardUrl = "https://chsami.github.io/Microbot-Hub/AutoHerblorePlugin/assets/card.png",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class AutoHerblorePlugin extends Plugin {
    static final String version = "1.0.0";
    @Inject
    private AutoHerbloreConfig config;

    @Provides
    AutoHerbloreConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AutoHerbloreConfig.class);
    }

    @Inject
    private AutoHerbloreScript script;

    @Override
    protected void startUp() throws AWTException {
        script.run(config);
    }

    @Override
    protected void shutDown() {
        script.shutdown();
    }

    @Subscribe
    public void onChatMessage(ChatMessage chatMessage) {
        if (chatMessage.getType() != ChatMessageType.GAMEMESSAGE) {
            return;
        }

        String message = chatMessage.getMessage();
        if (message.contains("It then crumbles to dust.")) {
            script.setAmuletBroken(true);
        }
    }
}
