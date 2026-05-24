package net.runelite.client.plugins.microbot.eventdismiss;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.Default + "Event Dismiss",
        description = "Dismisses random events and optionally accepts lamps from Genie/Count Check",
        tags = {"random", "events", "microbot", "lamp", "genie"},
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
    public static final String version = "1.1.0";

    @Inject
    private EventDismissConfig config;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private EventDismissOverlay overlay;

    private DismissNpcEvent dismissNpcEvent;
    private UseLampEvent useLampEvent;

    @Provides
    EventDismissConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(EventDismissConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {
        dismissNpcEvent = new DismissNpcEvent(config);
        useLampEvent = new UseLampEvent(config);
        Microbot.getBlockingEventManager().add(dismissNpcEvent);
        Microbot.getBlockingEventManager().add(useLampEvent);
        if (overlayManager != null) {
            overlayManager.add(overlay);
        }
        LampUtility.reset();
    }

    @Override
    protected void shutDown() {
        Microbot.getBlockingEventManager().remove(dismissNpcEvent);
        Microbot.getBlockingEventManager().remove(useLampEvent);
        if (overlayManager != null) {
            overlayManager.remove(overlay);
        }
        dismissNpcEvent = null;
        useLampEvent = null;
    }
}
