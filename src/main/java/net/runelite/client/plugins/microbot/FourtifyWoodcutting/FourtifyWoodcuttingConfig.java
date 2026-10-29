package net.runelite.client.plugins.microbot.FourtifyWoodcutting;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;
import net.runelite.client.plugins.microbot.fourtify.core.tasks.movement.SpotPicker;
import net.runelite.client.plugins.microbot.FourtifyWoodcutting.data.TreeType;

@ConfigGroup("fourtifywoodcutting")
public interface FourtifyWoodcuttingConfig extends Config {
    /** Best available first, so it is the default and the top of the list. */
    @Getter
    @RequiredArgsConstructor
    enum TreeChoice {
        BEST_AVAILABLE(null),
        TREE(TreeType.TREE),
        OAK(TreeType.OAK),
        WILLOW(TreeType.WILLOW),
        TEAK(TreeType.TEAK),
        MAPLE(TreeType.MAPLE),
        MAHOGANY(TreeType.MAHOGANY),
        ARCTIC_PINE(TreeType.ARCTIC_PINE),
        YEW(TreeType.YEW),
        CAMPHOR(TreeType.CAMPHOR),
        MAGIC(TreeType.MAGIC),
        IRONWOOD(TreeType.IRONWOOD),
        REDWOOD(TreeType.REDWOOD),
        ROSEWOOD(TreeType.ROSEWOOD);

        /** Null for the best available tree. */
        private final TreeType tree;

        @Override
        public String toString() {
            return tree == null ? "Best available" : tree.toString();
        }
    }

    enum FullInventory {
        DROP,
        BANK
    }

    @ConfigItem(
            position = 0,
            keyName = "treeType",
            name = "Tree",
            description = "Tree to chop, at a spot that is safe for your combat level. Best available: the tree"
                    + " that gives the most Woodcutting XP per hour with your level, world and best axe, moving on as you level"
    )
    default TreeChoice treeType() {
        return TreeChoice.BEST_AVAILABLE;
    }

    @ConfigItem(
            position = 1,
            keyName = "spotChoice",
            name = "Spot",
            description = "Nearest: the closest safe spot, so the walk is short. Random: any safe spot on the map;"
                    + " only the spot is checked for aggressive monsters, not the way there"
    )
    default SpotPicker.Choice spotChoice() {
        return SpotPicker.Choice.NEAREST;
    }

    @ConfigItem(
            position = 2,
            keyName = "fullInventory",
            name = "Full inventory",
            description = "Drop the logs, or bank them at the nearest bank and walk back"
    )
    default FullInventory fullInventory() {
        return FullInventory.DROP;
    }

    @Range(min = 2, max = 99)
    @ConfigItem(
            position = 3,
            keyName = "targetLevel",
            name = "Target level",
            description = "Stop when Woodcutting reaches this level"
    )
    default int targetLevel() {
        return 30;
    }
}
