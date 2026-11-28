package net.runelite.client.plugins.microbot.accountstarter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.accountstarter.logic.Choices;
import net.runelite.client.plugins.microbot.accountstarter.logic.Preset;
import net.runelite.client.plugins.microbot.accountstarter.model.ApTask;
import org.junit.jupiter.api.Test;

class ChoicesTest {
    @Test
    void skillerProtectsHitpointsAndCombat() {
        Choices c = Choices.from(Preset.SKILLER, new HashMap<>(), true, true, true);
        assertTrue(c.getProtectedSkills().containsAll(EnumSet.of(Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE,
                Skill.RANGED, Skill.MAGIC, Skill.HITPOINTS)));
        assertEquals(3, c.getChosen().size());
    }

    @Test
    void attackAndStrengthOnly() {
        Map<Skill, Integer> t = new HashMap<>();
        t.put(Skill.ATTACK, 20);
        t.put(Skill.STRENGTH, 10);
        Choices c = Choices.from(Preset.CUSTOM, t, true, true, true);
        assertEquals(EnumSet.of(ApTask.ATTACK_5, ApTask.ATTACK_10, ApTask.ATTACK_20, ApTask.STRENGTH_5,
                ApTask.STRENGTH_10), c.getChosen());
        assertTrue(c.getProtectedSkills().contains(Skill.DEFENCE));
        assertFalse(c.getProtectedSkills().contains(Skill.HITPOINTS));
    }

    @Test
    void buryOffProtectsPrayer() {
        assertTrue(Choices.from(Preset.EVERYTHING, new HashMap<>(), false, true, true)
                .getProtectedSkills().contains(Skill.PRAYER));
        assertFalse(Choices.from(Preset.EVERYTHING, new HashMap<>(), true, true, true)
                .getProtectedSkills().contains(Skill.PRAYER));
    }

    @Test
    void presetSizes() {
        assertEquals(18, Choices.from(Preset.EVERYTHING, new HashMap<>(), true, true, true).getChosen().size());
        assertEquals(15, Choices.from(Preset.COMBAT_ONLY, new HashMap<>(), true, true, true).getChosen().size());
    }
}
