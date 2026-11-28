package net.runelite.client.plugins.microbot.accountstarter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.accountstarter.logic.AccountSnapshot;
import net.runelite.client.plugins.microbot.accountstarter.logic.Choices;
import net.runelite.client.plugins.microbot.accountstarter.logic.Decision;
import net.runelite.client.plugins.microbot.accountstarter.logic.ProgressReader;
import net.runelite.client.plugins.microbot.accountstarter.logic.GatherTarget;
import net.runelite.client.plugins.microbot.accountstarter.logic.LoopRules;
import net.runelite.client.plugins.microbot.accountstarter.logic.Preset;
import net.runelite.client.plugins.microbot.accountstarter.logic.Spot;
import net.runelite.client.plugins.microbot.accountstarter.logic.Target;
import net.runelite.client.plugins.microbot.accountstarter.model.ApTask;
import org.junit.jupiter.api.Test;

class LoopRulesTest {
    @Test
    void choicesFromConfigValues() {
        Map<Skill, Integer> t = LoopRules.targets(Target.LEVEL_10, Target.OFF, Target.OFF, Target.OFF, Target.OFF,
                GatherTarget.LEVEL_5, GatherTarget.OFF, GatherTarget.OFF);
        assertEquals(10, t.get(Skill.ATTACK));
        assertEquals(0, t.get(Skill.STRENGTH));
        Choices c = Choices.from(Preset.CUSTOM, t, true, false, true);
        assertEquals(EnumSet.of(ApTask.ATTACK_5, ApTask.ATTACK_10, ApTask.FISHING_5), c.getChosen());
        assertTrue(c.getProtectedSkills().containsAll(EnumSet.of(Skill.STRENGTH, Skill.MINING, Skill.FIREMAKING)));
    }

    @Test
    void withoutParkedKeepsProtectionAndFlags() {
        Choices c = Choices.from(Preset.EVERYTHING, Collections.emptyMap(), true, false, true);
        Choices w = LoopRules.withoutParked(c, Set.of(ApTask.MINING_5, ApTask.RANGED_20));
        assertFalse(w.getChosen().contains(ApTask.MINING_5));
        assertFalse(w.getChosen().contains(ApTask.RANGED_20));
        assertEquals(16, w.getChosen().size());
        assertEquals(c.getProtectedSkills(), w.getProtectedSkills());
        assertFalse(w.getProtectedSkills().contains(Skill.MINING));
        assertEquals(c.isFires(), w.isFires());
        assertSame(c, LoopRules.withoutParked(c, Collections.emptySet()));
    }

    @Test
    void tilesToNearestSpotIgnoresPlane() {
        Spot near = new Spot("a", new WorldPoint(3210, 3220, 0), 3, Set.of("x"), 1, 4, false, 0, 0);
        Spot far = new Spot("b", new WorldPoint(3250, 3220, 0), 3, Set.of("x"), 1, 4, false, 0, 0);
        assertEquals(5, LoopRules.tilesTo(new WorldPoint(3205, 3220, 2), List.of(far, near)));
        assertEquals(0, LoopRules.tilesTo(new WorldPoint(3205, 3220, 0), List.of()));
        assertEquals(0, LoopRules.tilesTo(null, List.of(near)));
    }

    @Test
    void noBowWornWhenRangedProtected() {
        AccountSnapshot s = AccountSnapshot.builder().inventory(Map.of(841, 1)).levels(Map.of(Skill.RANGED, 1)).build();
        Choices open = new Choices(Set.of(ApTask.RANGED_5), EnumSet.noneOf(Skill.class), true, true, true);
        Choices prot = new Choices(Set.of(ApTask.RANGED_5), EnumSet.of(Skill.RANGED), true, true, true);
        assertEquals(List.of(841), LoopRules.gearPicks(ApTask.RANGED_5, s, open));
        assertTrue(LoopRules.gearPicks(ApTask.RANGED_5, s, prot).isEmpty());
    }

    @Test
    void gatheringWearsNothing() {
        AccountSnapshot s = AccountSnapshot.builder().inventory(Map.of(1293, 1, 1137, 1)).build();
        Choices c = Choices.from(Preset.EVERYTHING, Collections.emptyMap(), true, true, true);
        assertTrue(LoopRules.gearPicks(ApTask.MINING_5, s, c).isEmpty());
        assertEquals(List.of(1293, 1137), LoopRules.gearPicks(ApTask.ATTACK_5, s, c));
    }

    @Test
    void notSettledUntilThreeTicksAndRealStats() {
        AccountSnapshot real = AccountSnapshot.builder().levels(Map.of(Skill.ATTACK, 1, Skill.HITPOINTS, 10)).build();
        AccountSnapshot zeroed = AccountSnapshot.builder().levels(Map.of(Skill.ATTACK, 0, Skill.HITPOINTS, 0)).build();
        AccountSnapshot empty = AccountSnapshot.builder().build();
        assertFalse(LoopRules.settled(100, -1, real));
        assertFalse(LoopRules.settled(102, 100, real));
        assertTrue(LoopRules.settled(103, 100, real));
        assertFalse(LoopRules.settled(110, 100, zeroed));
        assertFalse(LoopRules.settled(110, 100, empty));
    }

    @Test
    void sameStepTwiceWithNoChangeRepeats() {
        AccountSnapshot a = AccountSnapshot.builder().progress(ProgressReader.read(v -> 0)).build();
        AccountSnapshot b = AccountSnapshot.builder().progress(ProgressReader.read(v -> v == ApTask.ATTACK_5.f2pVarbit() ? 1 : 0)).build();
        String fa = LoopRules.fingerprint(a);
        assertEquals(fa, LoopRules.fingerprint(AccountSnapshot.builder().progress(ProgressReader.read(v -> 0)).build()));
        assertFalse(LoopRules.repeatsWithoutProgress(null, null, Decision.Kind.CLAIM_AT_JON, fa));
        assertTrue(LoopRules.repeatsWithoutProgress(Decision.Kind.CLAIM_AT_JON, fa, Decision.Kind.CLAIM_AT_JON, fa));
        assertFalse(LoopRules.repeatsWithoutProgress(Decision.Kind.CLAIM_AT_JON, fa, Decision.Kind.CLAIM_AT_JON,
                LoopRules.fingerprint(b)));
        assertFalse(LoopRules.repeatsWithoutProgress(Decision.Kind.STARTER_KIT, fa, Decision.Kind.CLAIM_AT_JON, fa));
    }
}
