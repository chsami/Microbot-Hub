package net.runelite.client.plugins.microbot.dailytasks;

import com.google.inject.Inject;
import com.google.inject.Provides;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

import static net.runelite.client.plugins.PluginDescriptor.Mocrosoft;

@PluginDescriptor(
        name = Mocrosoft + "Daily Tasks",
        description = "Microbot daily tasks plugin",
        tags = {"misc"},
        authors = {"Unknown"},
        version = DailyTasksPlugin.version,
        minClientVersion = "2.1.0",
        cardUrl = "https://chsami.github.io/Microbot-Hub/DailyTasksPlugin/assets/card.jpg",
        iconUrl = "https://chsami.github.io/Microbot-Hub/DailyTasksPlugin/assets/icon.jpg",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class DailyTasksPlugin extends Plugin {
    public static final String version = "1.1.0";
    static final String CONFIG_GROUP = "dailytasks";
    static String currentState = "";

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private DailyTasksOverlay dailyTasksOverlay;

    @Inject
    private DailyTasksScript dailyTasksScript;
    @Provides
    DailyTasksConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(DailyTasksConfig.class);
    }


    @Override
    protected void startUp() throws Exception {
        overlayManager.add(dailyTasksOverlay);
        dailyTasksScript.run();
    }

    @Override
    protected void shutDown() throws Exception {
        overlayManager.remove(dailyTasksOverlay);
        dailyTasksScript.shutdown();
    }

    /**
     * Backup max-favour detector for the Rake Miscellania task: Gardener Gunnhild tells the player to
     * stop ("don't trouble yourself...") once approval is maxed. The KINGDOM_APPROVAL varbit is the
     * authoritative signal; this is a safety net since the exact wording isn't documented.
     */
    @Subscribe
    public void onChatMessage(ChatMessage event) {
        ChatMessageType type = event.getType();
        if (type != ChatMessageType.GAMEMESSAGE && type != ChatMessageType.MESBOX && type != ChatMessageType.DIALOG) {
            return;
        }
        if (Text.removeTags(event.getMessage()).toLowerCase().contains("trouble")) {
            DailyTask.gunnhildMaxFavor = true;
        }
    }
}
