package net.runelite.client.plugins.microbot.nmz;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginConstants.MOCROSOFT + "Nmz",
        description = "Plays the nightmare zone minigame",
        authors = { "Mocrosoft" },
        version = NmzPlugin.version,
        minClientVersion = "2.1.0",
        cardUrl = "https://chsami.github.io/Microbot-Hub/NmzPlugin/assets/card.png",
        iconUrl = "https://chsami.github.io/Microbot-Hub/NmzPlugin/assets/icon.png",
        tags = {"nmz", "microbot"},
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class NmzPlugin extends Plugin {
    final static String version = "2.4.0";
    @Inject
    private NmzConfig config;

    @Provides
    NmzConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(NmzConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private NmzOverlay nmzOverlay;

    @Inject
    NmzScript nmzScript;
    @Inject
    PrayerPotionScript prayerPotionScript;

    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(nmzOverlay);
        }
        nmzScript.run();
        if (config.togglePrayerPotions()) {
            prayerPotionScript.run(config);
        }
    }

    protected void shutDown() {
        nmzScript.shutdown();
        overlayManager.remove(nmzOverlay);
        NmzScript.setHasSurge(false);
    }

    @Subscribe
    public void onActorDeath(ActorDeath actorDeath) {
        if (config.stopAfterDeath() && actorDeath.getActor() == Microbot.getClient().getLocalPlayer()) {
            Microbot.getClientThread().runOnSeperateThread(() -> {
                Global.sleepUntil(nmzScript::isOutside, 10000);
                Microbot.stopPlugin(this);
                return true;
            });
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (event.getType() == ChatMessageType.GAMEMESSAGE) {
            if (event.getMessage().equalsIgnoreCase("you feel a surge of special attack power!")) {
                NmzScript.setHasSurge(true);
            } else if (event.getMessage().equalsIgnoreCase("your surge of special attack power has ended.")) {
                NmzScript.setHasSurge(false);
            }
        }
    }
}
