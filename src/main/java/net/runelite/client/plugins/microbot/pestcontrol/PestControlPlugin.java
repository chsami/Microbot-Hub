package net.runelite.client.plugins.microbot.pestcontrol;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.pestcontrol.Portal;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.runelite.client.plugins.microbot.pestcontrol.PestControlScript.portals;

@PluginDescriptor(
        name = PluginConstants.MOCROSOFT + "Pest Control",
        description = "Supports all boats, portals, and shields.",
        tags = {"pest control", "minigames"},
        authors = { "Mocrosoft" },
        version = PestControlPlugin.version,
        minClientVersion = "2.1.0",
		iconUrl = "https://chsami.github.io/Microbot-Hub/PestControlPlugin/assets/icon.png",
        cardUrl = "https://chsami.github.io/Microbot-Hub/PestControlPlugin/assets/card.png",
		enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class PestControlPlugin extends Plugin {

	static final String version = "2.3.0";

    @Inject
    PestControlScript pestControlScript;

    @Inject
    private PestControlConfig config;

    @Provides
    PestControlConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(PestControlConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private PestControlOverlay pestControlOverlay;

    private final Pattern SHIELD_DROP = Pattern.compile("The ([a-z]+), [^ ]+ portal shield has dropped!", Pattern.CASE_INSENSITIVE);


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(pestControlOverlay);
        }
        pestControlScript.initialise = true;
        pestControlScript.run(config);
    }

    protected void shutDown() {
        pestControlScript.shutdown();
        overlayManager.remove(pestControlOverlay);
    }

    @Subscribe
    public void onChatMessage(ChatMessage chatMessage) {
        if (chatMessage.getType() == ChatMessageType.GAMEMESSAGE) {
            Matcher matcher = SHIELD_DROP.matcher(chatMessage.getMessage());
            if (matcher.lookingAt()) {
                switch (matcher.group(1)) {
                    case "purple":
                        portals.stream().filter(x -> x == Portal.PURPLE).findFirst().get().setHasShield(false);
                        break;
                    case "blue":
                        portals.stream().filter(x -> x == Portal.BLUE).findFirst().get().setHasShield(false);
                        break;
                    case "red":
                        portals.stream().filter(x -> x == Portal.RED).findFirst().get().setHasShield(false);
                        break;
                    case "yellow":
                        portals.stream().filter(x -> x == Portal.YELLOW).findFirst().get().setHasShield(false);
                        break;
                }
            }
        }
    }
}
