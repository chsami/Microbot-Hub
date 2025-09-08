package net.runelite.client.plugins.microbot.vorkath;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginConstants.MOCROSOFT + "Vorkath",
        description = "Microbot Vorkath plugin",
        authors = { "Mocrosoft" },
        version = VorkathPlugin.version,
        minClientVersion = "1.9.9.1",
        tags = {"vorkath", "microbot"},
        iconUrl = "https://chsami.github.io/Microbot-Hub/VorkathPlugin/assets/icon.png",
        cardUrl = "https://chsami.github.io/Microbot-Hub/VorkathPlugin/assets/card.png",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class VorkathPlugin extends Plugin {

    public static final String version = "1.3.9";

    @Inject
    Client client;
    @Inject
    private VorkathConfig config;
    @Provides
    VorkathConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(VorkathConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private VorkathOverlay exampleOverlay;

    @Inject
    public VorkathScript vorkathScript;

    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(exampleOverlay);
        }
        vorkathScript.run();
    }

    protected void shutDown() {
        vorkathScript.shutdown();
        overlayManager.remove(exampleOverlay);
    }

    @Subscribe
    public void onProjectileMoved(ProjectileMoved e)
    {
        if (e.getProjectile().getId() == vorkathScript.getAcidProjectileId()) {
            vorkathScript.getAcidPools().add(WorldPoint.fromLocal(client, e.getPosition()));
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (event.getType() == ChatMessageType.GAMEMESSAGE && event.getMessage().equalsIgnoreCase("oh dear, you are dead!")) {
            vorkathScript.state = State.DEAD_WALK;
        }
    }
}
