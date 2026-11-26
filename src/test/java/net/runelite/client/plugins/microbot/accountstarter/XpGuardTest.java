package net.runelite.client.plugins.microbot.accountstarter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.accountstarter.logic.Action;
import net.runelite.client.plugins.microbot.accountstarter.logic.Choices;
import net.runelite.client.plugins.microbot.accountstarter.logic.Preset;
import net.runelite.client.plugins.microbot.accountstarter.logic.XpGuard;
import org.junit.jupiter.api.Test;

class XpGuardTest {
    @Test
    void defenceProtectedRefusesControlled() {
        assertFalse(XpGuard.allowed(Action.MELEE_CONTROLLED, EnumSet.of(Skill.DEFENCE)));
    }

    @Test
    void skillerCannotFight() {
        Set<Skill> p = Choices.from(Preset.SKILLER, new HashMap<>(), true, true, true).getProtectedSkills();
        assertEquals(Optional.empty(), XpGuard.styleFor(Skill.ATTACK, p));
    }

    @Test
    void attackOnlyGetsAccurate() {
        Set<Skill> p = EnumSet.of(Skill.STRENGTH, Skill.DEFENCE);
        assertEquals(Optional.of(Action.MELEE_ACCURATE), XpGuard.styleFor(Skill.ATTACK, p));
    }

    @Test
    void rangedRapidFromFiveAccurateBefore() {
        Set<Skill> none = EnumSet.noneOf(Skill.class);
        assertEquals(Optional.of(Action.RANGED_ACCURATE), XpGuard.styleFor(Skill.RANGED, 4, none));
        assertEquals(Optional.of(Action.RANGED_RAPID), XpGuard.styleFor(Skill.RANGED, 5, none));
        assertEquals(Optional.of(Action.RANGED_RAPID), XpGuard.styleFor(Skill.RANGED, 19, none));
        assertEquals(Optional.empty(), XpGuard.styleFor(Skill.RANGED, 10, EnumSet.of(Skill.HITPOINTS)));
        assertEquals(EnumSet.of(Skill.RANGED, Skill.HITPOINTS), XpGuard.xpSkills(Action.RANGED_RAPID));
        assertEquals(Optional.of(Action.MELEE_ACCURATE), XpGuard.styleFor(Skill.ATTACK, 10, none));
    }

    @Test
    void xpDropInProtectedSkillIsCaught() {
        Map<Skill, Integer> base = new HashMap<>();
        base.put(Skill.DEFENCE, 0);
        base.put(Skill.ATTACK, 0);
        Map<Skill, Integer> now = new HashMap<>(base);
        now.put(Skill.DEFENCE, 4);
        Set<Skill> p = EnumSet.of(Skill.DEFENCE);
        assertTrue(XpGuard.protectedXpGained(base, now, p));
        now.put(Skill.DEFENCE, 0);
        now.put(Skill.ATTACK, 4);
        assertFalse(XpGuard.protectedXpGained(base, now, p));
    }

    @Test
    void missingBaselineFailsClosed() {
        Map<Skill, Integer> base = new HashMap<>();
        base.put(Skill.ATTACK, 0);
        Map<Skill, Integer> now = new HashMap<>(base);
        now.put(Skill.DEFENCE, 0);
        assertTrue(XpGuard.protectedXpGained(base, now, EnumSet.of(Skill.DEFENCE)));
    }

    @Test
    void missingNowFailsClosed() {
        Map<Skill, Integer> base = new HashMap<>();
        base.put(Skill.DEFENCE, 0);
        assertTrue(XpGuard.protectedXpGained(base, new HashMap<>(), EnumSet.of(Skill.DEFENCE)));
    }
}
