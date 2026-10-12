package net.runelite.client.plugins.microbot.microhunter.scripts;

import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoHunterPlannerTest {
    @Test
    void bootstrapsUntilEveryAllowedTrapHasAnOwnedTile() {
        assertTrue(AutoHunterPlanner.shouldBootstrap(0, 4));
        assertTrue(AutoHunterPlanner.shouldBootstrap(3, 4));
        assertFalse(AutoHunterPlanner.shouldBootstrap(4, 4));
        assertFalse(AutoHunterPlanner.shouldBootstrap(5, 4));
    }

    @Test
    void identifiesCompetingHuntersWithoutTreatingPassersByAsOccupied() {
        assertTrue(AutoHunterPlanner.isHuntingAreaOccupied(1, 1, false));
        assertTrue(AutoHunterPlanner.isHuntingAreaOccupied(0, 2, false));
        assertTrue(AutoHunterPlanner.isHuntingAreaOccupied(1, 0, true));
        assertFalse(AutoHunterPlanner.isHuntingAreaOccupied(1, 0, false));
        assertFalse(AutoHunterPlanner.isHuntingAreaOccupied(0, 1, false));
    }

    @Test
    void derivesNormalBoxTrapLimit() {
        assertEquals(1, AutoHunterPlanner.normalBoxTrapLimit(19));
        assertEquals(2, AutoHunterPlanner.normalBoxTrapLimit(20));
        assertEquals(3, AutoHunterPlanner.normalBoxTrapLimit(40));
        assertEquals(4, AutoHunterPlanner.normalBoxTrapLimit(64));
        assertEquals(5, AutoHunterPlanner.normalBoxTrapLimit(80));
    }

    @Test
    void classifiesTrapActionsWithoutObjectIds() {
        assertEquals(AutoHunterPlanner.TrapState.CAUGHT,
                AutoHunterPlanner.classifyActions(new String[]{"Check", null, "Dismantle"}));
        assertEquals(AutoHunterPlanner.TrapState.FAILED,
                AutoHunterPlanner.classifyActions(new String[]{"Reset", "Dismantle"}));
        assertEquals(AutoHunterPlanner.TrapState.ACTIVE,
                AutoHunterPlanner.classifyActions(new String[]{"Dismantle"}));
        assertEquals(AutoHunterPlanner.TrapState.UNKNOWN,
                AutoHunterPlanner.classifyActions(new String[]{"Examine"}));
    }

    @Test
    void producesEightUniqueRingTiles() {
        WorldPoint center = new WorldPoint(3200, 3200, 0);
        assertEquals(8, AutoHunterPlanner.ring(center).stream().distinct().count());
        assertTrue(AutoHunterPlanner.ring(center).stream().noneMatch(center::equals));
    }

    @Test
    void fourTrapLayoutUsesDieCornersWithOneTileBetweenThem() {
        WorldPoint center = new WorldPoint(3200, 3200, 0);
        assertEquals(List.of(
                new WorldPoint(3199, 3201, 0),
                new WorldPoint(3201, 3201, 0),
                new WorldPoint(3201, 3199, 0),
                new WorldPoint(3199, 3199, 0)
        ), AutoHunterPlanner.fiveDotLayout(center, 4));
    }

    @Test
    void fifthTrapOccupiesTheLayoutCenter() {
        WorldPoint center = new WorldPoint(3200, 3200, 0);
        assertEquals(center, AutoHunterPlanner.fiveDotLayout(center, 5).get(4));
        assertEquals(5, AutoHunterPlanner.fiveDotLayout(center, 5).stream().distinct().count());
    }

    @Test
    void recognisesLiveRedChinchompaIdentity() {
        assertTrue(AutoHunterPlanner.isRedChinchompaTarget(2911, "Carnivorous chinchompa"));
        assertTrue(AutoHunterPlanner.isRedChinchompaTarget(-1, "Red chinchompa"));
        assertFalse(AutoHunterPlanner.isRedChinchompaTarget(2910, "Chinchompa"));
    }

    @Test
    void producesNearbyPlacementGridIncludingAnchor() {
        WorldPoint center = new WorldPoint(1316, 3170, 0);
        assertEquals(25, AutoHunterPlanner.placementGrid(center, 2).stream().distinct().count());
        assertTrue(AutoHunterPlanner.placementGrid(center, 2).contains(center));
    }

    @Test
    void repeatedFreshSpawnsOutrankOldDistantSpawns() {
        assertTrue(AutoHunterPlanner.spawnScore(3, 1_000, 2)
                > AutoHunterPlanner.spawnScore(2, 500_000, 10));
    }
}
