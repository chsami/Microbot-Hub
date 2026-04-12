package net.runelite.client.plugins.microbot.chaosaltar;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@PluginDescriptor(
        name = PluginDescriptor.Bee + "Chaos Altar",
        description = "Automates bone offering at the Chaos Altar",
        tags = {"prayer", "bones", "altar"},
        authors = {"Bee"},
        version = ChaosAltarPlugin.version,
        minClientVersion = "2.1.0",
        cardUrl = "https://chsami.github.io/Microbot-Hub/ChaosAltarPlugin/assets/card.jpg",
        iconUrl = "https://chsami.github.io/Microbot-Hub/ChaosAltarPlugin/assets/icon.jpg",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class ChaosAltarPlugin extends Plugin  {
    final static String version = "1.1.1";
    @Inject
    private ChaosAltarScript chaosAltarScript;
    @Inject
    private ChaosAltarConfig config;

    @Provides
    ChaosAltarConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(ChaosAltarConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    ChaosAltarOverlay chaosAltarOverlay;


    @Override
    protected void startUp() {
        if (overlayManager != null) {
            overlayManager.add(chaosAltarOverlay);
        }
        chaosAltarScript.run(config, this);
    }

    protected void shutDown() {
        chaosAltarScript.shutdown();

        overlayManager.remove(chaosAltarOverlay);
    }

    @Subscribe
    public void onChatMessage(ChatMessage chatMessage) {
        if (chatMessage.getType() != ChatMessageType.GAMEMESSAGE) {
            return;
        }

        String msg = chatMessage.getMessage();
        //need to add the chat message we get when we try to attack an NPC with an empty staff.

        if (msg.contains("Oh dear, you are dead!")) {
            ChaosAltarScript.didWeDie = true;
        }


    }
}
