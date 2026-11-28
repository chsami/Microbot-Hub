package net.runelite.client.plugins.microbot.accountstarter.logic;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.runelite.api.Skill;

public final class XpGuard {
    private XpGuard() {}

    public static Set<Skill> xpSkills(Action a) {
        switch (a) {
            case MELEE_ACCURATE: return EnumSet.of(Skill.ATTACK, Skill.HITPOINTS);
            case MELEE_AGGRESSIVE: return EnumSet.of(Skill.STRENGTH, Skill.HITPOINTS);
            case MELEE_DEFENSIVE: return EnumSet.of(Skill.DEFENCE, Skill.HITPOINTS);
            case MELEE_CONTROLLED: return EnumSet.of(Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.HITPOINTS);
            case RANGED_ACCURATE:
            case RANGED_RAPID: return EnumSet.of(Skill.RANGED, Skill.HITPOINTS);
            case RANGED_LONGRANGE: return EnumSet.of(Skill.RANGED, Skill.DEFENCE, Skill.HITPOINTS);
            case MAGIC_CAST: return EnumSet.of(Skill.MAGIC, Skill.HITPOINTS);
            case MAGIC_DEFENSIVE_CAST: return EnumSet.of(Skill.MAGIC, Skill.DEFENCE, Skill.HITPOINTS);
            case FISH:
            case LAMP_FISHING: return EnumSet.of(Skill.FISHING);
            case CHOP:
            case LAMP_WOODCUTTING: return EnumSet.of(Skill.WOODCUTTING);
            case MINE:
            case LAMP_MINING: return EnumSet.of(Skill.MINING);
            case BURY: return EnumSet.of(Skill.PRAYER);
            case LIGHT_FIRE: return EnumSet.of(Skill.FIREMAKING);
            case COOK: return EnumSet.of(Skill.COOKING);
            default: throw new IllegalArgumentException(a.name());
        }
    }

    public static boolean allowed(Action a, Set<Skill> protectedSkills) {
        return xpSkills(a).stream().noneMatch(protectedSkills::contains);
    }

    public static Optional<Action> styleFor(Skill training, Set<Skill> protectedSkills) {
        for (Action a : Action.values()) {
            if (a == Action.MELEE_CONTROLLED || a == Action.RANGED_LONGRANGE || a == Action.MAGIC_DEFENSIVE_CAST) continue;
            if (xpSkills(a).contains(training) && allowed(a, protectedSkills)) return Optional.of(a);
        }
        return Optional.empty();
    }

    public static final int RAPID_FROM = 5;

    public static Optional<Action> styleFor(Skill training, int level, Set<Skill> protectedSkills) {
        if (training == Skill.RANGED && level >= RAPID_FROM && allowed(Action.RANGED_RAPID, protectedSkills)) {
            return Optional.of(Action.RANGED_RAPID);
        }
        return styleFor(training, protectedSkills);
    }

    public static boolean protectedXpGained(Map<Skill, Integer> baselineXp, Map<Skill, Integer> nowXp, Set<Skill> protectedSkills) {
        return protectedSkillGained(baselineXp, nowXp, protectedSkills).isPresent();
    }

    public static Optional<Skill> protectedSkillGained(Map<Skill, Integer> baselineXp, Map<Skill, Integer> nowXp, Set<Skill> protectedSkills) {
        for (Skill s : protectedSkills) {
            Integer base = baselineXp.get(s);
            Integer now = nowXp.get(s);
            if (base == null || now == null || now > base) return Optional.of(s);
        }
        return Optional.empty();
    }
}
