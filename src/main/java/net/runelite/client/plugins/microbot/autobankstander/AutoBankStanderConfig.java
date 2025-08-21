package net.runelite.client.plugins.microbot.autobankstander;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
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
}