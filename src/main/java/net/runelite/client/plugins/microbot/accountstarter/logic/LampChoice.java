package net.runelite.client.plugins.microbot.accountstarter.logic;

import java.util.Map;
import java.util.Optional;
import net.runelite.api.Skill;

public enum LampChoice {
    AUTO("Lowest chosen skill", null),
    FISHING("Fishing", Skill.FISHING),
    WOODCUTTING("Woodcutting", Skill.WOODCUTTING),
    MINING("Mining", Skill.MINING);

    private final String label;
    private final Skill skill;

    LampChoice(String label, Skill skill) {
        this.label = label;
        this.skill = skill;
    }

    public Optional<Skill> resolve(Choices c, Map<Skill, Integer> levels) {
        if (skill != null && !c.getProtectedSkills().contains(skill)) return Optional.of(skill);
        return Planner.lampSkill(c, levels);
    }

    @Override
    public String toString() {
        return label;
    }
}
