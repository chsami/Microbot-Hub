package net.runelite.client.plugins.microbot.FourtifyWoodcutting;

import net.runelite.client.plugins.microbot.fourtify.core.activity.ActivityQueue;
import net.runelite.client.plugins.microbot.fourtify.core.client.TaskScript;
import net.runelite.client.plugins.microbot.FourtifyWoodcutting.tasks.TreePicker;
import net.runelite.client.plugins.microbot.FourtifyWoodcutting.tasks.Woodcutting;


/**
 * Chops the configured tree, or the best one the player's level allows, at one of its spots (SpotData) that
 * is safe for the player's combat level, the nearest or a random one as configured, then drops the logs or
 * banks them at the nearest bank and walks back.
 */
public class FourtifyWoodcuttingScript extends TaskScript {

    public boolean run(FourtifyWoodcuttingConfig config) {
        TreePicker tree = TreePicker.of(config.treeType());
        return run(ActivityQueue.of(Woodcutting.activity(tree, config.spotChoice(), config.fullInventory(), config.targetLevel())));
    }
}
