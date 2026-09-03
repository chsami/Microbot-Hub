package net.runelite.client.plugins.microbot.hsblackjack;

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
        name = PluginConstants.DEFAULT_PREFIX + "HSBlackJack",
        description = "Automates blackjacking Menaphite Thugs in Pollnivneach",
        tags = {"thieving", "blackjack", "pollnivneach"},
        authors = { "JouwNaam" },
        version = HSBlackJackPlugin.version,
        minClientVersion = "1.9.8",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class HSBlackJackPlugin extends Plugin {

    static final String version = "1.0.0";

    @Inject
    private HSBlackJackConfig config;

    @Provides
    HSBlackJackConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(HSBlackJackConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private HSBlackJackOverlay overlay;
    @Inject
    private HSBlackJackScript script;

    public HSBlackJackScript getScript() {
        return script;
    }

    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(overlay);
        }
        script.run();
    }

    protected void shutDown() {
        script.shutdown();
        overlayManager.remove(overlay);
    }

    /**
     * Listens for the exact "You can't do this during combat." game message,
     * and flags it on the script - this is the precise, guaranteed signal that
     * a Knock-Out or Pickpocket attempt was blocked by combat, rather than
     * inferring it indirectly from Rs2Player.isInCombat().
     */
    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM) {
            return;
        }
        String message = event.getMessage();
        if (message != null && message.toLowerCase().contains("can't do this during combat")) {
            script.onCombatBlockedMessage();
        }
    }
}
