/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape;

import com.google.inject.Provides;
import javax.inject.Inject;
import net.runelite.api.events.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(name = PluginConstants.DRO + "Firecape",version=DroFirecapePlugin.version,minClientVersion="2.6.30",enabledByDefault=PluginConstants.DEFAULT_ENABLED,
    description="Fight Caves with preserved regular combat and isolated pure positioning / native tick prayers",
    authors={"droplugins"},
    iconUrl="https://chsami.github.io/Microbot-Hub/DroFirecapePlugin/assets/icon.png",
    cardUrl="https://chsami.github.io/Microbot-Hub/DroFirecapePlugin/assets/card.png",
    isExternal=PluginConstants.IS_EXTERNAL,
    tags={"microbot","dro","firecape","fight caves","jad","boss"})
public final class DroFirecapePlugin extends Plugin {
    public static final String version="0.3.70";
    @Inject private FcControllers script;
    @Inject private DroFirecapeConfig config;
    @Inject private DroFirecapeOverlay overlay;
    @Inject private OverlayManager overlayManager;
    @Provides DroFirecapeConfig provideConfig(ConfigManager manager){return manager.getConfig(DroFirecapeConfig.class);}
    @Override protected void startUp(){overlayManager.add(overlay);script.run(config);}
    @Override protected void shutDown(){script.shutdown();overlayManager.remove(overlay);}
    @Subscribe public void onGameTick(GameTick e){script.onGameTick();}
    @Subscribe public void onClientTick(ClientTick e){script.onClientTick();}
    @Subscribe public void onChatMessage(ChatMessage e){script.onChatMessage(e);}
    @Subscribe public void onNpcSpawned(NpcSpawned e){script.onNpcSpawned(e);}
    @Subscribe public void onNpcDespawned(NpcDespawned e){script.onNpcDespawned(e);}
    @Subscribe public void onAnimationChanged(AnimationChanged e){script.onAnimationChanged(e);}
    @Subscribe public void onActorDeath(ActorDeath e){script.onActorDeath(e);}
    @Subscribe public void onHitsplatApplied(HitsplatApplied e){script.onHitsplatApplied(e);}
    @Subscribe public void onGameStateChanged(GameStateChanged e){script.onGameStateChanged(e);}
}
