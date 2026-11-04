/*
 * DroSulliuscep - Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Created by DRO (droplugins). Free and open source.
 * Reuse is permitted only under LICENSE.txt: every copy, fork or excerpt of this
 * file must keep this notice, the author credit and the license text. See CREDITS.txt.
 */
package net.runelite.client.plugins.microbot.drosulliuscep;

import net.runelite.api.events.HitsplatApplied;
import net.runelite.client.eventbus.Subscribe;

import com.google.inject.Provides;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@PluginDescriptor(
        name = PluginConstants.DRO + "Sulliuscep Run",
        description = "Sulliuscep runner: direct pendant/Mushtree travel with built-in WebWalk approaches, exact recording-derived six-tree/obstacle routing, direct tree/vine interaction, Ferox service, and BaseProfileDro.",
        tags = {"dro", "sulliuscep", "sullicep", "woodcutting", "fossil island", "microbot"},
        authors = {"Dro"},
        version = DroSulliuscepPlugin.VERSION,
        minClientVersion = "2.1.32",
        iconUrl = "https://chsami.github.io/Microbot-Hub/DroSulliuscepPlugin/assets/icon.png",
        cardUrl = "https://chsami.github.io/Microbot-Hub/DroSulliuscepPlugin/assets/card.png",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class DroSulliuscepPlugin extends Plugin
{
    public static final String VERSION = "1.4.15.19";

    @Inject private DroSulliuscepScript script;
    @Inject private DroSulliuscepConfig config;
    @Inject private DroSulliuscepOverlay overlay;
    @Inject private OverlayManager overlayManager;

    @Provides
    DroSulliuscepConfig provideConfig(ConfigManager manager)
    {
        return manager.getConfig(DroSulliuscepConfig.class);
    }

    @Override
    protected void startUp()
    {
        { overlayManager.add(overlay); overlay.enableDropDownInput(); }
        script.run(config);
    }

    @Subscribe
    public void onHitsplatApplied(HitsplatApplied event)
    {
        if (script != null) script.onPlayerHitsplat(event);
    }

    @Override
    protected void shutDown()
    {
        if (script != null) script.shutdown();
        if (overlayManager != null && overlay != null) { overlayManager.remove(overlay); overlay.disableDropDownInput(); }
    }
}
