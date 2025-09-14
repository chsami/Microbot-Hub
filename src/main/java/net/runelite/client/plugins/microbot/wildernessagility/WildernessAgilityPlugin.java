package net.runelite.client.plugins.microbot.wildernessagility;

import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.config.ConfigManager;
import com.google.inject.Provides;
import javax.inject.Inject;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.ChatMessageType;
import net.runelite.client.util.Text;
import net.runelite.client.eventbus.Subscribe;

@PluginDescriptor(
    name = PluginConstants.CRANNY + "Wilderness Agility",
    description = "Automated wilderness agility training with banking and ticket collection",
    version = WildernessAgilityPlugin.version,
    minClientVersion = "1.9.8",
    tags = {"agility", "skilling", "solo", "mass", "MoneyMaking"},
    enabledByDefault = PluginConstants.DEFAULT_ENABLED,
    isExternal = PluginConstants.IS_EXTERNAL,
    iconUrl = "https://chsami.github.io/Microbot-Hub/WildernessAgilityPlugin/assets/icon.png",
    cardUrl = "https://chsami.github.io/Microbot-Hub/WildernessAgilityPlugin/assets/card.png"
)
public class WildernessAgilityPlugin extends Plugin {

    static final String version = "1.5.1";
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private WildernessAgilityOverlay overlay;
    @Inject
    private WildernessAgilityConfig config;
    @Inject
    private WildernessAgilityScript script;

    @Provides
    WildernessAgilityConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(WildernessAgilityConfig.class);
    }

    @Override
    protected void startUp() throws Exception {
        Microbot.log("WildernessAgilityPlugin: startUp called");
        if (overlayManager != null) {
            overlayManager.add(overlay);
        }
        overlay.setScript(script);
        overlay.setActive(true);
        script.setPlugin(this);
        script.run(config);
    }

    @Override
    protected void shutDown() throws Exception {
        Microbot.log("WildernessAgilityPlugin: shutDown called");
        if (script != null) {
            script.shutdown();
        }
        if (overlayManager != null) {
            overlayManager.remove(overlay);
        }
        overlay.setActive(false);
    }

    @Subscribe
    public void onChatMessage(ChatMessage chatMessage) {
        String msg = Text.removeTags(chatMessage.getMessage());
        if (chatMessage.getType() == ChatMessageType.FRIENDSCHATNOTIFICATION && msg.startsWith("Now talking in chat-channel")) {
            script.setLastFcJoinMessageTime(System.currentTimeMillis());
        }
    }
} 