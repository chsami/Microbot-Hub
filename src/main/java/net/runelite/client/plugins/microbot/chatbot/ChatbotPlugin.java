package net.runelite.client.plugins.microbot.chatbot;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GameState;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
	name = PluginConstants.DEFAULT_PREFIX + "Chatbot",
	description = "Chat replies with OpenAI, Gemini or a custom provider, safe previews and automatic rate-limit recovery.",
	tags = {"chatbot", "openai", "gemini", "ai", "chat", "gpt"},
	authors = { "Bender" },
	version = ChatbotPlugin.version,
	minClientVersion = "1.9.8",
	enabledByDefault = PluginConstants.DEFAULT_ENABLED,
	isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class ChatbotPlugin extends Plugin {

    static final String version = "1.1.2";

    @Inject
    private ChatbotConfig config;

    @Inject
    private ConfigManager configManager;

    @Provides
    ChatbotConfig provideConfig(ConfigManager configManager) {
        // The client fills default config values before startUp. Migrate while
        // an absent new setting still means the player has not chosen it.
        migrateCooldown(configManager);
        return configManager.getConfig(ChatbotConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private ChatbotOverlay chatbotOverlay;

    @Inject
    private ChatbotScript chatbotScript;

    @Override
    protected void startUp() throws AWTException {
        migrateCooldown(configManager);
        if (overlayManager != null) {
            overlayManager.add(chatbotOverlay);
        }
        chatbotScript.run();
        log.info("[Chatbot] Plugin started");
    }

    @Override
    protected void shutDown() {
        chatbotScript.shutdown();
        if (overlayManager != null) {
            overlayManager.remove(chatbotOverlay);
        }
        log.info("[Chatbot] Plugin stopped");
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        chatbotScript.onLoginStateChanged(event.getGameState() == GameState.LOGGED_IN);
    }

    @Subscribe
    public void onChatMessage(ChatMessage chatMessage) {
        ChatMessageType type = chatMessage.getType();
        String sender = Text.removeTags(chatMessage.getName());
        String message = Text.removeTags(chatMessage.getMessage());

        // Determine if this chat type is enabled
        String chatType;
        switch (type) {
            case PUBLICCHAT:
            case MODCHAT:
                if (!config.listenPublicChat()) return;
                chatType = "public";
                break;
            case PRIVATECHAT:
            case PRIVATECHATOUT:
                return;
            case CLAN_CHAT:
            case CLAN_GIM_CHAT:
                if (!config.listenClanChat()) return;
                chatType = "clan";
                break;
            case CLAN_GUEST_CHAT:
                if (!config.listenClanChat()) return;
                chatType = "guestclan";
                break;
            case FRIENDSCHAT:
                if (!config.listenFriendsChat()) return;
                chatType = "friends";
                break;
            default:
                return; // Ignore game messages, spam, etc.
        }

        // Don't process empty messages
        if (sender == null || sender.isEmpty() || message == null || message.isEmpty()) {
            return;
        }

        // Enqueue for the script to process
        chatbotScript.enqueueMessage(sender, message, chatType);
    }

    private void migrateCooldown(ConfigManager configManager) {
        if (configManager.getConfiguration(ChatbotConfig.configGroup, "cooldownMinSeconds") != null) {
            return;
        }
        String savedCooldown = configManager.getConfiguration(ChatbotConfig.configGroup, "cooldownSeconds");
        if (savedCooldown == null) {
            return;
        }
        try {
            int seconds = Integer.parseInt(savedCooldown.trim());
            configManager.setConfiguration(ChatbotConfig.configGroup, "cooldownMinSeconds", Math.max(5, Math.min(120, seconds)));
            if (configManager.getConfiguration(ChatbotConfig.configGroup, "cooldownMaxSeconds") == null) {
                configManager.setConfiguration(ChatbotConfig.configGroup, "cooldownMaxSeconds", Math.max(15, Math.min(300, seconds)));
            }
        } catch (NumberFormatException ex) {
            log.debug("[Chatbot] Invalid saved cooldown; using default request pacing");
        }
    }
}
