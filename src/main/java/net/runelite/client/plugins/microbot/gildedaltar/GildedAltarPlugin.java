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
    public static final String version = "1.0.1";
    
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
        ChatMessageType type = chatMessage.getType();
        if (type != ChatMessageType.GAMEMESSAGE && type != ChatMessageType.SPAM) {
            return;
        }

        String message = chatMessage.getMessage().toLowerCase();
        
        // "You haven't visited anyone this session" - triggers on login/world hop
        if (message.contains("haven't visited anyone this session") || 
            message.contains("you haven't visited anyone")) {
            
            log.info("Detected 'haven't visited' message - resetting to initial setup");
            gildedAltarScript.handleHaventVisitedMessage();
        }
    }
}
package net.runelite.client.plugins.microbot.gildedaltar;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
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
    public static final String version = "1.0.1";
    
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
}
