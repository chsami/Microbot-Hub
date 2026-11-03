package net.runelite.client.plugins.microbot.arceuuslibrary;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@PluginDescriptor(
        name = PluginConstants.PERT + "Arceuus Library",
        description = "Runs the Arceuus Library book-fetch loop using the upstream Kourend Library solver",
        authors = { "runsonmypc" },
        version = ArceuusLibraryPlugin.version,
        minClientVersion = "2.6.43",
        tags = {"arceuus", "library", "kourend", "magic", "runecraft", "minigame"},
        iconUrl = "https://chsami.github.io/Microbot-Hub/ArceuusLibraryPlugin/assets/card.jpg",
        cardUrl = "https://chsami.github.io/Microbot-Hub/ArceuusLibraryPlugin/assets/card.jpg",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class ArceuusLibraryPlugin extends Plugin
{
    public static final String version = "1.0.2";
    private static final String NOTHING_FOUND_MESSAGE = "You don't find anything useful here.";
    private static final String BOOKS_SHIFTED_MESSAGE =
            "You hear the shifting of books due to a mysterious force...or are you just hearing things?";

    @Getter
    @Inject
    private ArceuusLibraryConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private ArceuusLibraryOverlay overlay;

    @Inject
    private PluginManager pluginManager;

    @Getter
    ArceuusLibraryScript script;

    @Getter
    private KourendLibraryBridge bridge;

    @Provides
    ArceuusLibraryConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(ArceuusLibraryConfig.class);
    }

    @Override
    protected void startUp()
    {
        bridge = new KourendLibraryBridge(pluginManager);
        if (!bridge.ensureUpstreamEnabled())
        {
            log.warn("Kourend Library plugin could not be enabled — Arceuus Library will not run");
        }
        bridge.wire();

        script = new ArceuusLibraryScript(bridge);

        if (overlayManager != null)
        {
            overlayManager.add(overlay);
        }
        script.run(config);
    }

    @Override
    protected void shutDown()
    {
        if (script != null) script.shutdown();
        if (overlayManager != null) overlayManager.remove(overlay);
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        if (script == null || event.getType() != ChatMessageType.GAMEMESSAGE) return;
        String message = event.getMessage();
        if (NOTHING_FOUND_MESSAGE.equals(message))
        {
            script.onNothingFound();
        }
        else if (message != null && message.endsWith(BOOKS_SHIFTED_MESSAGE))
        {
            script.onBooksShifted();
        }
    }
}
