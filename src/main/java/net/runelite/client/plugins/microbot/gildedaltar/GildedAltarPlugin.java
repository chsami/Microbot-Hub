package net.runelite.client.plugins.microbot.gildedaltar;

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
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.Mocrosoft + "Gilded Altar",
        description = "Gilded Altar plugin",
        tags = {"prayer", "microbot"},
        version = GildedAltarPlugin.version,
        minClientVersion = "2.0.13",
        cardUrl = "",
        iconUrl = "",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class GildedAltarPlugin extends Plugin {
    public static final String version = "1.0.0";
    @Inject
    private GildedAltarConfig config;

    @Provides
    GildedAltarConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(GildedAltarConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private GildedAltarOverlay gildedAltarOverlay;

    @Inject
    GildedAltarScript gildedAltarScript;

 
    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(gildedAltarOverlay);
        }
        gildedAltarScript.run(config);
    }


    @Override
    protected void shutDown() {
        overlayManager.remove(gildedAltarOverlay);
        gildedAltarScript.shutdown();
    }

    @Subscribe
    public void onChatMessage(ChatMessage chatMessage) {
        if (chatMessage.getType() != ChatMessageType.GAMEMESSAGE && chatMessage.getType() != ChatMessageType.SPAM) return;
        String chatMsg = chatMessage.getMessage().toLowerCase();
        if(chatMsg.contains("that player is offline") || chatMsg.contains("haven't visited anyone this session") || chatMsg.contains("house is no longer accessible")){
            // If we try to use Visit-Last unsuccessfully, these chat messages will appear, and we need to reset vars.
            gildedAltarScript.visitedOnce= false;
            gildedAltarScript.usePortal = null;
            gildedAltarScript.altarCoords = null;
            gildedAltarScript.portalCoords = null;
            gildedAltarScript.addNameToBlackList();
        }
    }
}
