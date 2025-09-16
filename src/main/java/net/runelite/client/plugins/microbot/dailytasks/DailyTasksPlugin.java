package net.runelite.client.plugins.microbot.dailytasks;

import com.google.inject.Inject;
import com.google.inject.Provides;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.pluginscheduler.api.SchedulablePlugin;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.AndCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LogicalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.event.PluginScheduleEntryPostScheduleTaskEvent;
import net.runelite.client.ui.overlay.OverlayManager;

import static net.runelite.client.plugins.PluginDescriptor.Mocrosoft;

@PluginDescriptor(
        name = Mocrosoft + "Daily Tasks",
        description = "Microbot daily tasks plugin",
        tags = {"misc"},
        authors = {"Unknown"},
        version = DailyTasksPlugin.version,
        minClientVersion = "2.0.7",
        cardUrl = "https://chsami.github.io/Microbot-Hub/DailyTasksPlugin/assets/card.jpg",
        iconUrl = "https://chsami.github.io/Microbot-Hub/DailyTasksPlugin/assets/icon.jpg",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class DailyTasksPlugin extends Plugin implements SchedulablePlugin {
    public static final String version = "1.0.0";
    static final String CONFIG_GROUP = "dailytasks";
    static String currentState = "";

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private DailyTasksOverlay dailyTasksOverlay;

    @Inject
    private DailyTasksScript dailyTasksScript;
    private LogicalCondition stopCondition = new AndCondition();
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

    @Subscribe
    public void onPluginScheduleEntryPostScheduleTaskEvent(PluginScheduleEntryPostScheduleTaskEvent event) {
        if (event.getPlugin() == this) {
            Microbot.stopPlugin(this);
        }
    }
    @Override     
    public LogicalCondition getStopCondition() {
        // Create a new stop condition        
        return this.stopCondition;
    }
}
