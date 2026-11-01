package net.runelite.client.plugins.microbot.barbarianfishing;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.StatChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.See1Duck + " Barbarian Fisher",
        description = "Automated Barbarian Fishing at Otto's Grotto and Mount Quidamortem (CoX)",
        tags = {"Fishing", "barbarian", "skilling", "cox", "fish barrel"},
        authors = {"See1Duck", "Dro"},
        version = BarbarianFishingPlugin.version,
        minClientVersion = "2.6.32",
        cardUrl = "https://chsami.github.io/Microbot-Hub/BarbarianFishingPlugin/assets/card.jpg",
        iconUrl = "https://chsami.github.io/Microbot-Hub/BarbarianFishingPlugin/assets/icon.jpg",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class BarbarianFishingPlugin extends Plugin
{
    public static final String version = "1.2.0";

    @Inject
    private BarbarianFishingScript script;

    @Inject
    private BarbarianFishingConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private BarbarianFishingOverlay overlay;

    @Provides
    BarbarianFishingConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(BarbarianFishingConfig.class);
    }

    @Override
    protected void startUp() throws AWTException
    {
        Microbot.pauseAllScripts.compareAndSet(true, false);
        if (overlayManager != null)
        {
            overlayManager.add(overlay);
        }
        script.run(config);
    }

    @Override
    protected void shutDown()
    {
        script.shutdown();
        if (overlayManager != null)
        {
            overlayManager.remove(overlay);
        }
    }

    public BarbarianFishingScript getScript()
    {
        return script;
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        script.onChatMessage(event);
    }

    @Subscribe
    public void onStatChanged(StatChanged event)
    {
        script.onStatChanged(event);
    }
}
