package net.runelite.client.plugins.microbot.WealthyCitizen;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;

import javax.inject.Inject;

@PluginDescriptor(
        name = PluginConstants.HALALSKILLER + "Wealthy Citizen",
        description = "Pickpockets Wealthy Citizens tijdens urchin-distractions",
        tags = {"microbot", "thieving", "wealthy citizen"},
        authors = { "HalalSkiller" },
        version = WealthyCitizenPlugin.version,
        minClientVersion = "2.0.13",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class WealthyCitizenPlugin extends Plugin {

    static final String version = "1.0.0";

    @Inject
    WealthyCitizenScript wealthyCitizenScript;

    private static final String DISTRACTION_MESSAGE = "You notice an urchin distract a wealthy citizen nearby.";

    @Override
    protected void startUp() {
        wealthyCitizenScript.run();
    }

    @Override
    protected void shutDown() {
        wealthyCitizenScript.shutdown();
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (event.getType() != ChatMessageType.GAMEMESSAGE) return;
        if (event.getMessage().contains(DISTRACTION_MESSAGE)) {
            wealthyCitizenScript.distractionActive.set(true);
        }
    }
}