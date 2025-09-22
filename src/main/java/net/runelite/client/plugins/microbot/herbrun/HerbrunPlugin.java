package net.runelite.client.plugins.microbot.herbrun;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigDescriptor;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.pluginscheduler.api.SchedulablePlugin;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.AndCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LockCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LogicalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.event.PluginScheduleEntryPostScheduleTaskEvent;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.Mocrosoft + "Herb runner",
        description = "Herb runner",
        tags = {"herb", "farming", "money making", "skilling"},
        authors = {"Mocrosoft"},
        version = HerbrunPlugin.version,
        minClientVersion = "2.0.7",
        iconUrl = "https://chsami.github.io/Microbot-Hub/HerbrunPlugin/assets/icon.jpg",
        cardUrl = "https://chsami.github.io/Microbot-Hub/HerbrunPlugin/assets/card.jpg",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j

public class HerbrunPlugin extends Plugin implements SchedulablePlugin {
    public static final String version = "1.0.1";
    @Inject
    private HerbrunConfig config;

    @Provides
    HerbrunConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(HerbrunConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private HerbrunOverlay HerbrunOverlay;

    @Inject
    HerbrunScript herbrunScript;

    static String status;
    private LockCondition lockCondition;
    private LogicalCondition stopCondition = null;


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(HerbrunOverlay);
        }
        herbrunScript.run();
    }

    protected void shutDown() {
        herbrunScript.shutdown();
        overlayManager.remove(HerbrunOverlay);
        status = null; // Reset status on shutdown
    }

    @Subscribe
    public void onPluginScheduleEntryPostScheduleTaskEvent(PluginScheduleEntryPostScheduleTaskEvent event) {
        try {
            if (event.getPlugin() == this) {
                // Check if lock is active before stopping
                if (lockCondition != null && lockCondition.isLocked()) {
                    log.info("Soft stop deferred - plugin is locked: {}", lockCondition.getReason());
                    // Defer the stop operation to respect the lock
                    Microbot.getClientThread().invokeLater(() -> {
                        // Re-check lock state when invokeLater executes
                        if (lockCondition == null || !lockCondition.isLocked()) {
                            log.info("Lock released, proceeding with deferred stop");
                            Microbot.stopPlugin(this);
                        } else {
                            log.warn("Lock still active, stop operation cancelled");
                        }
                        return true;
                    });
                } else {
                    log.info("Stopping plugin immediately - no lock active");
                    Microbot.stopPlugin(this);
                }
            }
        } catch (Exception e) {
            log.error("Error stopping plugin: ", e);
        }
    }

    @Override
    public LogicalCondition getStopCondition() {
        if (this.stopCondition == null) {
            this.lockCondition = new LockCondition("Herb run in progress");
            AndCondition andCondition = new AndCondition();
            andCondition.addCondition(lockCondition);
            this.stopCondition = andCondition;
        }
        return this.stopCondition;
    }

    @Override
    public ConfigDescriptor getConfigDescriptor() {
        if (Microbot.getConfigManager() == null) {
            return null;
        }
        HerbrunConfig conf = Microbot.getConfigManager().getConfig(HerbrunConfig.class);
        return Microbot.getConfigManager().getConfigDescriptor(conf);
    }

}
