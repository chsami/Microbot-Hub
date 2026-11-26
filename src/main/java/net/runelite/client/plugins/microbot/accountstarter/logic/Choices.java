package net.runelite.client.plugins.microbot.accountstarter.logic;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import lombok.Value;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.accountstarter.model.ApPath;
import net.runelite.client.plugins.microbot.accountstarter.model.ApTask;

@Value
public class Choices {
    Set<ApTask> chosen;
    Set<Skill> protectedSkills;
    boolean bury;
    boolean fires;
    boolean cook;

    public static Choices questOnly() {
        return new Choices(Collections.emptySet(), Collections.emptySet(), false, false, false);
    }

    public static Choices from(Preset preset, Map<Skill, Integer> customTargets, boolean bury, boolean fires, boolean cook) {
        Set<ApTask> chosen = EnumSet.noneOf(ApTask.class);
        for (ApTask t : ApTask.values()) {
            if (t.target() <= targetFor(preset, customTargets, t)) {
                chosen.add(t);
            }
        }
        Set<Skill> prot = EnumSet.noneOf(Skill.class);
        for (ApTask t : ApTask.values()) {
            prot.add(t.skill());
        }
        chosen.forEach(t -> prot.remove(t.skill()));
        if (!bury) prot.add(Skill.PRAYER);
        if (!fires) prot.add(Skill.FIREMAKING);
        if (!cook) prot.add(Skill.COOKING);
        if (chosen.stream().noneMatch(t -> t.path() == ApPath.COMBAT)) prot.add(Skill.HITPOINTS);
        return new Choices(Collections.unmodifiableSet(chosen), Collections.unmodifiableSet(prot), bury, fires, cook);
    }

    private static int targetFor(Preset preset, Map<Skill, Integer> custom, ApTask t) {
        switch (preset) {
            case EVERYTHING: return 20;
            case SKILLER: return t.path() == ApPath.GATHERER ? 20 : 0;
            case COMBAT_ONLY: return t.path() == ApPath.COMBAT ? 20 : 0;
            default: return custom.getOrDefault(t.skill(), 0);
        }
    }
}
