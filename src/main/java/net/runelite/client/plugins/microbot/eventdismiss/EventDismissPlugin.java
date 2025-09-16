package net.runelite.client.plugins.microbot.eventdismiss;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.Default + "Event Dismiss",
        description = "Random Event Dismisser",
        tags = {"random", "events", "microbot"},
        authors = {"Unknown"},
        version = EventDismissPlugin.version,
        minClientVersion = "2.0.7",
        cardUrl = "https://chsami.github.io/Microbot-Hub/EventDismissPlugin/assets/card.jpg",
        iconUrl = "https://chsami.github.io/Microbot-Hub/EventDismissPlugin/assets/icon.jpg",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class EventDismissPlugin extends Plugin {
    public static final String version = "1.0.1";
    @Inject
    private ConfigManager configManager;
    @Inject
    private EventDismissConfig config;

    private DismissNpcEvent dismissNpcEvent;

    @Provides
    EventDismissConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(EventDismissConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {
        dismissNpcEvent = new DismissNpcEvent(config);
        Microbot.getBlockingEventManager().add(dismissNpcEvent);
    }

    protected void shutDown() {
        Microbot.getBlockingEventManager().remove(dismissNpcEvent);
        dismissNpcEvent = null;
    }
}
