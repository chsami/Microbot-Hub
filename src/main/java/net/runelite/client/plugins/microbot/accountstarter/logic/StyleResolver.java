package net.runelite.client.plugins.microbot.accountstarter.logic;

import java.util.EnumSet;
import java.util.Set;
import net.runelite.api.Skill;

/** xpTypes is indexed like VarPlayerID.COM_MODE; never pick by label alone, a longsword's "Lunge" is controlled. */
public final class StyleResolver {
    private StyleResolver() {}

    static Set<Skill> skillsOf(String xpType) {
        if (xpType == null) return EnumSet.noneOf(Skill.class);
        switch (xpType) {
            case "Accurate": return EnumSet.of(Skill.ATTACK, Skill.HITPOINTS);
            case "Aggressive": return EnumSet.of(Skill.STRENGTH, Skill.HITPOINTS);
            case "Defensive": return EnumSet.of(Skill.DEFENCE, Skill.HITPOINTS);
            case "Controlled": return EnumSet.of(Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.HITPOINTS);
            case "Ranging": return EnumSet.of(Skill.RANGED, Skill.HITPOINTS);
            case "Longrange": return EnumSet.of(Skill.RANGED, Skill.DEFENCE, Skill.HITPOINTS);
            case "Casting": return EnumSet.of(Skill.MAGIC, Skill.HITPOINTS);
            case "Defensive Casting": return EnumSet.of(Skill.MAGIC, Skill.DEFENCE, Skill.HITPOINTS);
            default: return EnumSet.noneOf(Skill.class);
        }
    }

    public static int indexFor(Action wanted, String[] labels, String[] xpTypes, Set<Skill> protectedSkills) {
        if (!XpGuard.allowed(wanted, protectedSkills)) return -1;
        Set<Skill> want = XpGuard.xpSkills(wanted);
        // Accurate and Rapid give the same XP type ("Ranging"): the button label tells them apart
        String label = wanted == Action.RANGED_RAPID ? "Rapid" : wanted == Action.RANGED_ACCURATE ? "Accurate" : null;
        int first = -1;
        for (int i = 0; i < Math.min(4, Math.min(labels.length, xpTypes.length)); i++) {
            if (labels[i] == null || labels[i].trim().isEmpty()) continue;
            if (!skillsOf(xpTypes[i]).equals(want)) continue;
            if (label == null || label.equalsIgnoreCase(labels[i].trim())) return i;
            if (first < 0) first = i;
        }
        return first;
    }

    public static boolean safe(int comMode, int defMode, String[] xpTypes, Action wanted, Set<Skill> protectedSkills) {
        if (!XpGuard.allowed(wanted, protectedSkills)) return false;
        // script4525: COM_MODE 4 is autocast, and defensive autocast is the struct after it
        int i = comMode == 4 ? 4 + defMode : comMode;
        if (i < 0 || i >= xpTypes.length) return false;
        String type = i == 5 && "Defensive".equals(xpTypes[i]) ? "Defensive Casting" : xpTypes[i];
        return skillsOf(type).equals(XpGuard.xpSkills(wanted));
    }
}
