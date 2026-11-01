/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drodriftnet;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.AWTException;

@PluginDescriptor(
        name = PluginConstants.DRO + "Drift Net",
        description = "Automated Drift Net fishing and hunting in Fossil Island underwater cavern",
        tags = {"fishing", "hunter", "drift net", "driftnet", "microbot", "dro"},
        authors = {"droplugins"},
        version = DroDriftNetPlugin.version,
        minClientVersion = "2.6.32",
        iconUrl = "https://chsami.github.io/Microbot-Hub/DroDriftNetPlugin/assets/icon.png",
        cardUrl = "https://chsami.github.io/Microbot-Hub/DroDriftNetPlugin/assets/card.png",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class DroDriftNetPlugin extends Plugin
{
    public static final String version = "1.0.0";

    @Inject
    private DroDriftNetConfig config;

    @Inject
    private DroDriftNetScript script;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private DroDriftNetOverlay overlay;

    @Provides
    DroDriftNetConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(DroDriftNetConfig.class);
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

    public DroDriftNetScript getScript()
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
