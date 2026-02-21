package net.runelite.client.plugins.microbot.chatbot;

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
import net.runelite.client.util.Text;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
	name = PluginConstants.DEFAULT_PREFIX + "Chatbot",
	description = "AI-powered chatbot using OpenAI. Reads in-game chat and responds intelligently.",
	tags = {"chatbot", "openai", "ai", "chat", "gpt"},
	authors = { "Mocrosoft" },
	version = ChatbotPlugin.version,
	minClientVersion = "1.9.8",
	enabledByDefault = PluginConstants.DEFAULT_ENABLED,
	isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class ChatbotPlugin extends Plugin {

    static final String version = "1.2.3";

    @Inject
    private ChatbotConfig config;

    @Provides
    ChatbotConfig provideConfig(ConfigManager configManager) {
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
            case CLAN_GUEST_CHAT:
                if (!config.listenClanChat()) return;
                chatType = "clan";
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
}
