package net.runelite.client.plugins.microbot.coordinatepicker;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

public class CoordinatePickerScript extends Script {
    private final CoordinatePickerPlugin plugin;
    private final CoordinatePickerConfig config;

    @Inject
    public CoordinatePickerScript(CoordinatePickerPlugin plugin, CoordinatePickerConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public boolean run() {
        Microbot.enableAutoRunOn = false;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;

                // Main loop logic - for now just a simple heartbeat
                // The actual coordinate picking is handled by menu events in the plugin

            } catch (Exception ex) {
                System.out.println("Exception in coordinate picker script: " + ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}
