package net.runelite.client.plugins.microbot.driftnet;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.AWTException;

import static net.runelite.client.plugins.PluginDescriptor.Mocrosoft;

@PluginDescriptor(
        name = Mocrosoft + "Drifnet",
        description = "Microbot drifnet plugin",
        tags = {"fishing", "microbot", "hunter", "drift", "net"},
        authors = {"Mocrosoft", "droplugins"},
        version = DriftNetPlugin.version,
        minClientVersion = "2.6.43",
        cardUrl = "https://chsami.github.io/Microbot-Hub/DriftNetPlugin/assets/card.jpg",
        iconUrl = "https://chsami.github.io/Microbot-Hub/DriftNetPlugin/assets/icon.jpg",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class DriftNetPlugin extends Plugin
{
    public static final String version = "1.1.0";

    @Inject
    private DriftNetConfig config;

    @Inject
    private DriftNetScript script;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private DriftNetOverlay overlay;

    @Provides
    DriftNetConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(DriftNetConfig.class);
    }

    @Override
    protected void startUp() throws AWTException
    {
        Microbot.pauseAllScripts.compareAndSet(true, false);
        if (overlayManager != null)
        {
            overlayManager.add(overlay);
            overlay.enableDropDownInput();
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
            overlay.disableDropDownInput();
        }
    }

    public DriftNetScript getScript()
    {
        return script;
    }

    @Subscribe
    public void onStatChanged(StatChanged event)
    {
        script.onStatChanged(event);
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        script.onChatMessage(event);
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event)
    {
        script.onVarbitChanged(event);
    }

    @Subscribe
    public void onNpcSpawned(NpcSpawned event)
    {
        script.onNpcSpawned(event);
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event)
    {
        script.onNpcDespawned(event);
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event)
    {
        script.onGameObjectSpawned(event);
    }

    @Subscribe
    public void onGameObjectDespawned(GameObjectDespawned event)
    {
        script.onGameObjectDespawned(event);
    }
}
