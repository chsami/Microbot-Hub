package net.runelite.client.plugins.microbot.cluesolver;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("cluesolver")
public interface ClueSolverConfig extends Config {

    @ConfigItem(
            keyName = "enableClueSolver",
            name = "Run solver",
            description = "Runs the solver. Same as the Start/Stop button on the overlay. If left on, the solver starts automatically when the plugin is turned on.",
            position = 1
    )
    default boolean enableClueSolver() {
        return false;
    }
}
