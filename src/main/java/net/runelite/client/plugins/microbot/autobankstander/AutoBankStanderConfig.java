package net.runelite.client.plugins.microbot.autobankstander;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.plugins.microbot.autobankstander.processors.SkillType;
import net.runelite.client.plugins.microbot.autobankstander.skills.magic.MagicMethod;
import net.runelite.client.plugins.microbot.autobankstander.skills.magic.enchanting.BoltType;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.enums.CleanHerbMode;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.enums.HerblorePotion;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.enums.Mode;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.enums.UnfinishedPotionMode;

@ConfigGroup("AutoBankStander")
@ConfigInformation(
    "AIO Bank Standing plugin for various processing activities.<br>" +
    "Use the panel interface to configure options."
)
public interface AutoBankStanderConfig extends Config {

    @ConfigItem(
        keyName = "skill",
        name = "Skill",
        description = "Selected skill",
        hidden = true
    )
    default SkillType skill() {
        return SkillType.MAGIC;
    }

    @ConfigItem(
        keyName = "magicMethod",
        name = "Magic method",
        description = "Selected magic method",
        hidden = true
    )
    default MagicMethod magicMethod() {
        return MagicMethod.ENCHANTING;
    }

    @ConfigItem(
        keyName = "herbloreMode",
        name = "Herblore mode",
        description = "Selected herblore mode",
        hidden = true
    )
    default Mode herbloreMode() {
        return Mode.CLEAN_HERBS;
    }

    @ConfigItem(
        keyName = "boltType",
        name = "Bolt type",
        description = "Selected bolt type for enchanting",
        hidden = true
    )
    default BoltType boltType() {
        return BoltType.SAPPHIRE;
    }

    @ConfigItem(
        keyName = "cleanHerbMode",
        name = "Clean herb mode",
        description = "Selected clean herb mode",
        hidden = true
    )
    default CleanHerbMode cleanHerbMode() {
        return CleanHerbMode.ANY_AND_ALL;
    }

    @ConfigItem(
        keyName = "unfinishedPotionMode",
        name = "Unfinished potion mode",
        description = "Selected unfinished potion mode",
        hidden = true
    )
    default UnfinishedPotionMode unfinishedPotionMode() {
        return UnfinishedPotionMode.ANY_AND_ALL;
    }

    @ConfigItem(
        keyName = "finishedPotion",
        name = "Finished potion",
        description = "Selected finished potion type",
        hidden = true
    )
    default HerblorePotion finishedPotion() {
        return HerblorePotion.ANTIPOISON;
    }

    @ConfigItem(
        keyName = "useAmuletOfChemistry",
        name = "Use amulet of chemistry",
        description = "Whether to use amulet of chemistry",
        hidden = true
    )
    default boolean useAmuletOfChemistry() {
        return false;
    }

    @ConfigItem(
        keyName = "herbloreTurboMode",
        name = "Herblore turbo mode",
        description = "Rapid-click the entire grimy-herb inventory (cleaning only). Less human-like.",
        hidden = true
    )
    default boolean herbloreTurboMode() {
        return false;
    }

    @ConfigItem(
        keyName = "herbloreTurboLimit",
        name = "Herblore turbo limit",
        description = "Auto-disable turbo after this many herbs cleaned (0 = no limit).",
        hidden = true
    )
    @Range(min = 0, max = 10000)
    default int herbloreTurboLimit() {
        return 0;
    }

    @ConfigItem(
        keyName = "herbloreSleepMin",
        name = "Herblore sleep min (ms)",
        description = "Lower bound for Gaussian inter-batch sleep during cleaning.",
        hidden = true
    )
    @Range(min = 30, max = 1000)
    default int herbloreSleepMin() {
        return 60;
    }

    @ConfigItem(
        keyName = "herbloreSleepMax",
        name = "Herblore sleep max (ms)",
        description = "Upper bound for Gaussian inter-batch sleep during cleaning.",
        hidden = true
    )
    @Range(min = 100, max = 2000)
    default int herbloreSleepMax() {
        return 300;
    }

    @ConfigItem(
        keyName = "herbloreSleepTarget",
        name = "Herblore sleep target (ms)",
        description = "Target (mean anchor) for Gaussian inter-batch sleep during cleaning.",
        hidden = true
    )
    @Range(min = 50, max = 1500)
    default int herbloreSleepTarget() {
        return 150;
    }
}