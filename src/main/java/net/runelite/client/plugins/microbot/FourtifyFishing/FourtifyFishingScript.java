package net.runelite.client.plugins.microbot.FourtifyFishing;

import net.runelite.client.plugins.microbot.fourtify.core.client.TaskScript;
import net.runelite.client.plugins.microbot.FourtifyFishing.tasks.Fishing;

/**
 * Fishes with the configured method, or the best one the player's level and gear allow, at the nearest or a random
 * spot (SpotData) that is safe for the player's combat level, then drops the raw fish or banks them and walks back.
 */
public class FourtifyFishingScript extends TaskScript {

    public boolean run(FourtifyFishingConfig config) {
        return run(Fishing.queue(config));
    }
}
