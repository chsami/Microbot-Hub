package net.runelite.client.plugins.microbot.accountstarter;

import net.runelite.api.Skill;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.accountstarter.logic.Spot;
import net.runelite.client.plugins.microbot.accountstarter.logic.Spots;
import net.runelite.client.plugins.microbot.accountstarter.logic.WildernessGuard;
import net.runelite.client.plugins.microbot.accountstarter.model.ApPath;
import net.runelite.client.plugins.microbot.accountstarter.model.ApTask;
import net.runelite.client.plugins.microbot.accountstarter.run.Reach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpotsTest {
    @Test
    void noSpotIsInTheWilderness() {
        List<WorldPoint> all = new ArrayList<>(Spots.TUTORS.values());
        all.addAll(Spots.VARROCK.values());
        all.add(Spots.LUMBRIDGE_BANK);
        for (ApTask t : ApTask.values()) {
            for (Spot s : Spots.forTask(t)) {
                all.add(s.getCenter());
            }
        }
        for (WorldPoint p : all) {
            assertTrue(p.getY() < 3520, p.toString());
            assertTrue(WildernessGuard.destinationSafe(p, WildernessGuardTest.WILD), p.toString());
        }
    }

    private static final Set<String> SAFE_THREE = Set.of("Lumbridge chickens", "Lumbridge goblins", "Lumbridge cows");

    private static Map<Skill, Integer> levels(int att, int str, int def, int hp) {
        Map<Skill, Integer> m = new EnumMap<>(Skill.class);
        m.put(Skill.ATTACK, att);
        m.put(Skill.STRENGTH, str);
        m.put(Skill.DEFENCE, def);
        m.put(Skill.HITPOINTS, hp);
        return m;
    }

    private static Set<String> names(List<Spot> spots) {
        return spots.stream().map(Spot::getName).collect(Collectors.toSet());
    }

    @Test
    void onlyChickensGoblinsAndCowsServeEveryCombatTask() {
        for (ApTask t : ApTask.of(ApPath.COMBAT)) assertEquals(SAFE_THREE, names(Spots.forTask(t)), t.name());
    }

    @Test
    void combatLevelFollowsTheWikiFormula() {
        assertEquals(3, Spots.combatLevel(Map.of()));
        assertEquals(126, Spots.combatLevel(Map.of(Skill.ATTACK, 99, Skill.STRENGTH, 99, Skill.DEFENCE, 99,
                Skill.HITPOINTS, 99, Skill.PRAYER, 99)));
        assertEquals(6, Spots.combatLevel(levels(2, 9, 1, 11)));
        assertEquals(7, Spots.combatLevel(Map.of(Skill.RANGED, 10, Skill.HITPOINTS, 10)));
    }

    @Test
    void theLiveAccountTrainsStrengthOnlyOnWeakTargets() {
        Map<Skill, Integer> live = levels(2, 9, 1, 11);
        assertEquals(SAFE_THREE, names(Spots.usable(ApTask.STRENGTH_10, live, false, 0)));
        Map<Skill, Integer> strong = levels(15, 15, 10, 18);
        assertEquals(SAFE_THREE, names(Spots.usable(ApTask.STRENGTH_20, strong, false, 5)));
    }

    @Test
    void safetyRuleChecksLevelMaxHitAndAttack() {
        Spot goblins = Spots.forTask(ApTask.ATTACK_5).stream().filter(s -> s.getName().equals("Lumbridge goblins")).findFirst().get();
        Spot lvl10 = new Spot("x", goblins.getCenter(), 4, Set.of("x"), 1, 99, false, 10, 2);
        assertTrue(Spots.safe(goblins, Skill.ATTACK, 3, 10, 1, 0));
        assertFalse(Spots.safe(lvl10, Skill.STRENGTH, 6, 11, 2, 5));
        assertFalse(Spots.safe(lvl10, Skill.RANGED, 7, 18, 1, 0));
        assertTrue(Spots.safe(lvl10, Skill.RANGED, 8, 18, 1, 0));
        assertFalse(Spots.safe(lvl10, Skill.STRENGTH, 12, 18, 3, 5));
        assertTrue(Spots.safe(lvl10, Skill.STRENGTH, 12, 18, 4, 5));
        assertFalse(Spots.safe(lvl10, Skill.STRENGTH, 12, 7, 4, 0));
        assertTrue(Spots.safe(lvl10, Skill.STRENGTH, 12, 7, 4, 1));
    }

    @Test
    void withNothingSafeTheSafestSpotIsKept() {
        assertEquals(Set.of("Lumbridge chickens"), names(Spots.usable(ApTask.ATTACK_5, levels(1, 1, 1, 1), false, 0)));
    }

    @Test
    void crowdedWhenOtherPlayersOutnumberFreeTargets() {
        assertFalse(Spots.crowded(0, 0));
        assertFalse(Spots.crowded(1, 0));
        assertFalse(Spots.crowded(3, 3));
        assertTrue(Spots.crowded(2, 1));
        assertTrue(Spots.crowded(5, 2));
    }

    @Test
    void reachTouchesOnlyFromASideTile() {
        Set<WorldPoint> reach = Set.of(new WorldPoint(3200, 3200, 0));
        assertTrue(Reach.touches(new WorldArea(new WorldPoint(3201, 3200, 0), 1, 1), reach));
        assertTrue(Reach.touches(new WorldArea(new WorldPoint(3199, 3199, 0), 1, 2), reach));
        assertFalse(Reach.touches(new WorldArea(new WorldPoint(3201, 3201, 0), 1, 1), reach));
        assertFalse(Reach.touches(new WorldArea(new WorldPoint(3210, 3200, 0), 1, 1), reach));
        assertFalse(Reach.touches(null, reach));
    }

    @Test
    void skillerSkipsAggressiveSpots() {
        for (ApTask t : ApTask.values()) {
            assertFalse(Spots.usable(t, Map.of(), true, 0).stream().anyMatch(Spot::isAggressive), t.name());
        }
    }

    @Test
    void varrockWalksFromLumbridgeSkirtTheDarkWizards() {
        WorldPoint lumbridge = new WorldPoint(3222, 3218, 0);
        for (WorldPoint npc : Spots.VARROCK.values()) {
            List<WorldPoint> via = Spots.waypoints(lumbridge, npc, 3);
            if (npc.getX() < 3180) {
                assertTrue(via.isEmpty(), npc.toString());
                continue;
            }
            assertFalse(via.isEmpty(), npc.toString());
            for (WorldPoint w : via) assertTrue(Math.max(Math.abs(w.getX() - 3227), Math.abs(w.getY() - 3369)) >= 24, w.toString());
        }
        assertTrue(Spots.waypoints(new WorldPoint(3180, 3430, 0), Spots.VARROCK.get("Aubury"), 3).isEmpty());
        assertTrue(Spots.waypoints(lumbridge, new WorldPoint(3082, 3425, 0), 3).isEmpty());
        assertTrue(Spots.waypoints(lumbridge, new WorldPoint(3110, 3161, 0), 3).isEmpty());
    }

    @Test
    void everyBandHasTwoSpots() {
        for (ApTask t : ApTask.values()) {
            assertTrue(Spots.forTask(t).size() >= 2, t.name());
        }
    }

    @Test
    void theSpotTableKeepsItsRows() {
        assertTrue(Spots.SURVEYED.stream().anyMatch(r -> Arrays.equals(r, new int[]{3205, 3214, 3207, 3201})));
        assertTrue(Spots.SURVEYED.stream().anyMatch(r -> Arrays.equals(r, new int[]{3200, 3177, 3165, 3184})));
    }

    @Test
    void gatheringSpotsOnlyHoldWhatALowLevelCanGather() {
        Map<ApTask, Set<String>> lowLevel = Map.of(
                ApTask.FISHING_5, Set.of("Fishing spot"),
                ApTask.WOODCUTTING_5, Set.of("Tree"),
                ApTask.MINING_5, Set.of("Copper rocks", "Tin rocks"));
        lowLevel.forEach((task, allowed) -> {
            for (Spot spot : Spots.forTask(task)) {
                assertTrue(allowed.containsAll(spot.getTargets()), spot.getName() + " " + spot.getTargets());
                assertFalse(spot.getName().toLowerCase().contains("west swamp mine"), spot.getName());
            }
        });
    }
}
