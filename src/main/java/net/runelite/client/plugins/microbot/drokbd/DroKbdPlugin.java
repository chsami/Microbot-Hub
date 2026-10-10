package net.runelite.client.plugins.microbot.drokbd;

import com.google.inject.Provides;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@PluginDescriptor(
        name = PluginConstants.DRO + "KBD",
        description = "King Black Dragon trips with Lava Maze travel, Wilderness safety, and death recovery.",
        tags = {"kbd", "king black dragon", "boss", "wilderness"},
        version = "1.1.7",
        authors = {"droplugins"},
        iconUrl = "https://chsami.github.io/Microbot-Hub/DroKbdPlugin/assets/icon.png",
        cardUrl = "https://chsami.github.io/Microbot-Hub/DroKbdPlugin/assets/card.png",
        minClientVersion = "2.6.25",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class DroKbdPlugin extends Plugin
{

    @Inject private DroKbdConfig config;
    @Inject private DroKbdScript script;
    @Inject private DroKbdOverlay overlay;
    @Inject private OverlayManager overlayManager;

    @Provides
    DroKbdConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(DroKbdConfig.class);
    }

    @Override
    protected void startUp()
    {
        overlayManager.add(overlay);
        script.run(config);
    }

    @Override
    protected void shutDown()
    {
        script.shutdown();
        overlayManager.remove(overlay);
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        if (event.getType() == ChatMessageType.GAMEMESSAGE || event.getType() == ChatMessageType.SPAM)
        {
            script.onChatMessage(event.getMessage());
        }
    }
}
