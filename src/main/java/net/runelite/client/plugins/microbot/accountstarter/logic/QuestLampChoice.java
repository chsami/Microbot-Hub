package net.runelite.client.plugins.microbot.accountstarter.logic;

import java.util.Map;
import java.util.Optional;
import net.runelite.api.Skill;

public enum QuestLampChoice {
    AUTO("Lowest Ranged/Magic", null),
    ATTACK("Attack", Skill.ATTACK),
    STRENGTH("Strength", Skill.STRENGTH),
    DEFENCE("Defence", Skill.DEFENCE),
    RANGED("Ranged", Skill.RANGED),
    MAGIC("Magic", Skill.MAGIC),
    PRAYER("Prayer", Skill.PRAYER),
    HITPOINTS("Hitpoints", Skill.HITPOINTS);

    private final String label;
    private final Skill skill;

    QuestLampChoice(String label, Skill skill) {
        this.label = label;
        this.skill = skill;
    }

    public Optional<Skill> resolve(Choices c, Map<Skill, Integer> levels) {
        if (skill != null && !c.getProtectedSkills().contains(skill)) return Optional.of(skill);
        return Planner.questLampSkill(c, levels);
    }

    @Override
    public String toString() {
        return label;
    }
}
