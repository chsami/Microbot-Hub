package net.runelite.client.plugins.microbot.AmmoniteCrabs.enums;

import lombok.Getter;
import net.runelite.api.Skill;

public enum OffensivePotions {
    RANGED_POTION("Ranging potion", Skill.RANGED),
    SUPER_COMBAT_POTION("Super combat potion", Skill.STRENGTH),
    COMBAT_POTION("Combat potion", Skill.STRENGTH),
    STRENGTH_POTION("Strength potion", Skill.STRENGTH),
    SUPER_STRENGTH_POTION("Super strength", Skill.STRENGTH),
    DIVINE_SUPER_COMBAT_POTION("Divine super combat", Skill.STRENGTH),
    BASTION_POTION("Bastion potion", Skill.RANGED),
    BATTLEMAGE_POTION("Battlemage potion", Skill.MAGIC),
    MAGIC_POTION("Magic potion", Skill.MAGIC),
    ANCIENT_BREW_POTION("Ancient brew", Skill.MAGIC),
    FORGOTTEN_BREW_POTION("Forgotten brew", Skill.MAGIC);
    @Getter
    private final String PotionName;
    @Getter
    private final Skill BoostedSkill;

    OffensivePotions(String name, Skill boostedSkill) {
        PotionName = name;
        BoostedSkill = boostedSkill;
    }
}
