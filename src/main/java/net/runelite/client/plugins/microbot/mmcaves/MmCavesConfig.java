package net.runelite.client.plugins.microbot.mmcaves;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;
import net.runelite.client.plugins.microbot.mmcaves.enums.CombatStyle;
import net.runelite.client.plugins.microbot.mmcaves.enums.DungeonRoute;
import net.runelite.client.plugins.microbot.mmcaves.enums.MagicSpell;

@ConfigGroup("mmcaves")
public interface MmCavesConfig extends Config {

    @ConfigItem(
            keyName = "dungeonRoute",
            name = "Dungeon route",
            description = "Choose the numbered hole. Only Hole 2 has a recorded path so far."
    )
    default DungeonRoute dungeonRoute() {
        return DungeonRoute.HOLE_2;
    }


    @ConfigItem(
        keyName = "combatStyle",
        name = "Combat Style",
        description = "Choose between ranging (chinning) or maging (spells)"
    )
    default CombatStyle combatStyle() {
        return CombatStyle.RANGING;
    }

    @ConfigItem(
        keyName = "magicSpell",
        name = "Magic Spell",
        description = "If using magic, choose which spell to cast"
    )
    default MagicSpell magicSpell() {
        return MagicSpell.ICE_BURST;
    }

    @ConfigItem(
            keyName = "shouldAutoCast",
            name = "Enable Autocast",
            description = "Toggle whether to automatically set the selected magic spell as autocast"
    )
    default boolean shouldAutoCast() {
        return true;
    }

    @ConfigItem(
            keyName = "useCustomDelay",
            name = "Enable Custom Attack Delay",
            description = "Toggle whether to use the custom attack delay"
    )
    default boolean useCustomDelay() {
        return false;
    }

    @ConfigItem(
            keyName = "customAttackDelay",
            name = "Attack delay",
            description = "Delay (ms) between attacks. Suggestion: chinning >= 1800 | Magic >= 3000"
    )
    @Range(
            min = 1800,
            max = 4200
    )
    default int customAttackDelay() {
        return 1800;
    }

    @ConfigItem(
            keyName = "clickRangedAttackTargets",
            name = "Click ranged attack targets",
            description = "Click monkeys to attack while ranging. Leave off when using auto-retaliate."
    )
    default boolean clickRangedAttackTargets() {
        return false;
    }

    @ConfigItem(
            keyName = "minimumStackSize",
            name = "Minimum monkey stack",
            description = "Pause stacking clicks when at least this many monkeys fit in the 3x3 chinchompa area."
    )
    @Range(min = 1, max = 20)
    default int minimumStackSize() {
        return 8;
    }

    @ConfigItem(
            keyName = "maximumOutsideStack",
            name = "Maximum outside stack",
            description = "How many nearby monkeys may remain outside the 3x3 area before gathering resumes."
    )
    @Range(min = 0, max = 10)
    default int maximumOutsideStack() {
        return 2;
    }
}
