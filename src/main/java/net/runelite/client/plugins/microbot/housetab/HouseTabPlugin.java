package net.runelite.client.plugins.microbot.housetab;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.housetab.enums.HOUSETABS_CONFIG;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.Mocrosoft + "HouseTab",
        description = "Microbot HouseTab plugin",
        tags = {"microbot", "magic", "moneymaking"},
        version = HouseTabPlugin.version,
        minClientVersion = "2.0.13",
        cardUrl = "",
        iconUrl = "",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class HouseTabPlugin extends Plugin {
    public static final String version = "1.0.7";

    @Inject
    private HouseTabConfig config;

    @Provides
    HouseTabConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(HouseTabConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private HouseTabOverlay houseTabOverlay;

    private final HouseTabScript houseTabScript = new HouseTabScript(HOUSETABS_CONFIG.FRIENDS_HOUSE,
            new String[]{"xGrace", "workless", "Lego Batman", "Batman 321", "Batman Chest"});

    @Override
    protected void startUp() throws AWTException {
		Microbot.pauseAllScripts.compareAndSet(true, false);
        if (overlayManager != null) {
            overlayManager.add(houseTabOverlay);
        }
        houseTabScript.run(config);
    }

    protected void shutDown() {
        houseTabScript.shutdown();
        overlayManager.remove(houseTabOverlay);
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        if (event.getType() == ChatMessageType.GAMEMESSAGE && event.getMessage().contains("That player is offline")) {
            Microbot.showMessage("Player is offline.");
            houseTabScript.shutdown();
        }
    }
}
