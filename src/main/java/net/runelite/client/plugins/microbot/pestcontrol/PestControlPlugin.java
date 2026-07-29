package net.runelite.client.plugins.microbot.pestcontrol;

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
import net.runelite.client.plugins.pestcontrol.Portal;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import java.awt.*;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

	static final String version = "2.5.6";

    @Inject
    PestControlScript pestControlScript;

    String getRuntimeStatus() {
        return pestControlScript.getRuntimeStatus();
    }

    PestControlScript.OverlaySnapshot getOverlaySnapshot() {
        return pestControlScript.getOverlaySnapshot();
    }

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

    private static final Pattern SHIELD_DROP = Pattern.compile(
            "The\\s+(purple|blue|yellow|red)\\s*,.*?portal shield has dropped!",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern POINTS_AWARDED = Pattern.compile(
            "(?:awarded|received|gained)\\s+([\\d,]+)\\s+(?:Void Knight\\s+)?commendation points?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TOTAL_POINTS = Pattern.compile(
            "(?:you (?:now )?have|commendation (?:point )?count is now)\\s+([\\d,]+)"
                    + "\\s+(?:Void Knight\\s+)?commendation points?",
            Pattern.CASE_INSENSITIVE);


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
        if (chatMessage.getType() != ChatMessageType.GAMEMESSAGE) {
            return;
        }

        String message = Text.removeTags(chatMessage.getMessage());
        Matcher matcher = SHIELD_DROP.matcher(message);
        if (matcher.find()) {
            Portal portal = Portal.valueOf(matcher.group(1).toUpperCase(Locale.ROOT));
            pestControlScript.noteShieldDrop(portal);
            Microbot.log("Pest Control shield dropped: " + portal + " portal");
        }

        String normalizedMessage = message.toLowerCase(Locale.ROOT);
        if (normalizedMessage.contains("successfully defended the island")) {
            pestControlScript.noteRoundOutcome(true);
        } else if (normalizedMessage.contains("alas, the void knight has died")) {
            pestControlScript.noteRoundOutcome(false);
        }

        Matcher awardedMatcher = POINTS_AWARDED.matcher(message);
        if (awardedMatcher.find()) {
            pestControlScript.recordAwardedPoints(parseCount(awardedMatcher.group(1)));
        }

        Matcher totalMatcher = TOTAL_POINTS.matcher(message);
        if (totalMatcher.find()) {
            pestControlScript.recordTotalPoints(parseCount(totalMatcher.group(1)));
        }
    }

    private static int parseCount(String value) {
        try {
            return Integer.parseInt(value.replace(",", ""));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }
}
