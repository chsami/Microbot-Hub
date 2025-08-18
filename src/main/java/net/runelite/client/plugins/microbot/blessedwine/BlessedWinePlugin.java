package net.runelite.client.plugins.microbot.blessedwine;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.pluginscheduler.api.SchedulablePlugin;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.AndCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LogicalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.event.PluginScheduleEntrySoftStopEvent;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@Slf4j
@PluginDescriptor(
        name = PluginConstants.MOCROSOFT + "Blessed Wine",
        description = "Automating Prayer Training using blessed wines at Cam Torum",
        tags = {"blessed", "wine", "ralos", "prayer", "libation", "microbot", "automation"},
        authors = { "Hal" },
        version = BlessedWinePlugin.version,
		minClientVersion = "1.9.6",
        iconUrl = "https://oldschool.runescape.wiki/images/Jug_of_wine.png",
		enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class BlessedWinePlugin extends Plugin implements SchedulablePlugin {

	static final String version = "1.0.1";

    @Inject
    private BlessedWineConfig config;
    @Provides
    BlessedWineConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(BlessedWineConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private BlessedWineOverlay blessedWineOverlay;

    @Inject
    BlessedWineScript blessedWineScript;
    static String status = "Initializing...";
    static int loopCount = 0;
    static int expectedXp = 0;
    static int startingXp = 0;
    static int totalWinesToBless = 0;
    static int totalLoops = 0;
    static int endingXp = 0;
    private LogicalCondition stopCondition = new AndCondition();

    @Override
    public LogicalCondition getStartCondition() {
        // Create conditions that determine when your plugin can start
        // Return null if the plugin can start anytime
        return null;
    }

    @Override
    public LogicalCondition getStopCondition() {
        return this.stopCondition;
    }

    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(blessedWineOverlay);
        }
        blessedWineScript.run();
    }

    @Override
    protected void shutDown() {
        blessedWineScript.shutdown();
        overlayManager.remove(blessedWineOverlay);
    }

    @Subscribe
    public void onPluginScheduleEntrySoftStopEvent(PluginScheduleEntrySoftStopEvent event) {
        if (event.getPlugin() == this) {
            Microbot.stopPlugin(this);
        }
    }
}
