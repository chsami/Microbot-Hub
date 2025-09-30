package net.runelite.client.plugins.microbot.zeroprayer;

import lombok.Getter;
import net.runelite.api.Skill;

@Getter
enum AttackStyle
{
    ACCURATE("Accurate", Skill.ATTACK),
    AGGRESSIVE("Aggressive", Skill.STRENGTH),
    DEFENSIVE("Defensive", Skill.DEFENCE),
    CONTROLLED("Controlled", Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE),
    RANGING("Ranging", Skill.RANGED),
    LONGRANGE("Longrange", Skill.RANGED, Skill.DEFENCE),
    CASTING("Casting", Skill.MAGIC),
    DEFENSIVE_CASTING("Defensive Casting", Skill.MAGIC, Skill.DEFENCE),
    OTHER("Other");

    private final String name;
    private final Skill[] skills;

    AttackStyle(String name, Skill... skills)
    {
        this.name = name;
        this.skills = skills;
    }
}