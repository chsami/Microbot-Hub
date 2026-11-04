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
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@PluginDescriptor(
        name = PluginConstants.DRO + "Sulliuscep",
        description = "Sulliuscep runs by DRO (droplugins): Digsite pendant and Magic Mushtree travel, the recorded six-tree and vine route, and Ferox Enclave pool and bank recovery.",
        tags = {"dro", "sulliuscep", "sullicep", "woodcutting", "fossil island", "microbot"},
        authors = {"droplugins"},
        version = DroSulliuscepPlugin.version,
        minClientVersion = "2.6.32",
        iconUrl = "https://chsami.github.io/Microbot-Hub/DroSulliuscepPlugin/assets/icon.png",
        cardUrl = "https://chsami.github.io/Microbot-Hub/DroSulliuscepPlugin/assets/card.png",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class DroSulliuscepPlugin extends Plugin
{
    static final String version = "1.0.0";

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
        log.info("{} v{} - {}", PluginConstants.DRO + "Sulliuscep", version, PluginConstants.CREDIT);
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
