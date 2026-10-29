package net.runelite.client.plugins.microbot.GemCrabKiller;

import net.runelite.api.Skill;

/**
 * Offensive potion families. Item names are listed best first, without the dose suffix, and are matched
 * exactly, so "Super combat potion" never matches "Divine super combat potion".
 */
public enum GemCrabKillerPotion {
    ANY("Any (best available)",
            new Boost(Skill.RANGED, "Divine ranging potion", "Divine bastion potion", "Bastion potion", "Ranging potion"),
            new Boost(Skill.MAGIC, "Divine magic potion", "Divine battlemage potion", "Battlemage potion", "Magic potion"),
            new Boost(Skill.STRENGTH, "Divine super combat potion", "Super combat potion", "Divine super strength potion",
                    "Super strength", "Combat potion", "Strength potion"),
            new Boost(Skill.ATTACK, "Divine super combat potion", "Super combat potion", "Divine super attack potion",
                    "Super attack", "Combat potion", "Attack potion"),
            new Boost(Skill.DEFENCE, "Divine super combat potion", "Super combat potion", "Divine super defence potion",
                    "Super defence", "Defence potion")),
    DIVINE_SUPER_COMBAT("Divine super combat",
            new Boost(Skill.ATTACK, "Divine super combat potion"),
            new Boost(Skill.STRENGTH, "Divine super combat potion"),
            new Boost(Skill.DEFENCE, "Divine super combat potion")),
    SUPER_COMBAT("Super combat",
            new Boost(Skill.ATTACK, "Super combat potion"),
            new Boost(Skill.STRENGTH, "Super combat potion"),
            new Boost(Skill.DEFENCE, "Super combat potion")),
    COMBAT("Combat",
            new Boost(Skill.ATTACK, "Combat potion"),
            new Boost(Skill.STRENGTH, "Combat potion")),
    SUPER_ATTACK_STRENGTH("Super attack + strength",
            new Boost(Skill.ATTACK, "Divine super attack potion", "Super attack"),
            new Boost(Skill.STRENGTH, "Divine super strength potion", "Super strength")),
    RANGING("Ranging",
            new Boost(Skill.RANGED, "Divine ranging potion", "Divine bastion potion", "Bastion potion", "Ranging potion")),
    MAGIC("Magic",
            new Boost(Skill.MAGIC, "Divine magic potion", "Divine battlemage potion", "Battlemage potion", "Magic potion"));

    public static final class Boost {
        final Skill skill;
        final String[] itemNames;

        Boost(Skill skill, String... itemNames) {
            this.skill = skill;
            this.itemNames = itemNames;
        }
    }

    private final String displayName;
    final Boost[] boosts;

    GemCrabKillerPotion(String displayName, Boost... boosts) {
        this.displayName = displayName;
        this.boosts = boosts;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
