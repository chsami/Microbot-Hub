package net.runelite.client.plugins.microbot.mmcaves;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;
import net.runelite.client.plugins.microbot.mmcaves.enums.CombatStyle;
import net.runelite.client.plugins.microbot.mmcaves.enums.MagicSpell;

@ConfigGroup("mmcaves")
public interface MmCavesConfig extends Config {


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
}