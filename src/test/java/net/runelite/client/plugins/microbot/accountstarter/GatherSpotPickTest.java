package net.runelite.client.plugins.microbot.accountstarter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.accountstarter.logic.Spot;
import net.runelite.client.plugins.microbot.accountstarter.logic.Spots;
import net.runelite.client.plugins.microbot.accountstarter.model.ApTask;
import net.runelite.client.plugins.microbot.accountstarter.run.GatherActivity;
import org.junit.jupiter.api.Test;

class GatherSpotPickTest {
    private static final List<Spot> TREES = Spots.usable(ApTask.WOODCUTTING_5, 1, true);

    @Test
    void prefersOneSpotButNotAlways() {
        Map<String, Integer> counts = new HashMap<>();
        Random r = new Random(7);
        for (int i = 0; i < 1000; i++) {
            counts.merge(GatherActivity.pickSpot(TREES, 42L, r, Set.of()).getName(), 1, Integer::sum);
        }
        assertEquals(2, counts.size());
        int top = counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        assertTrue(top > 700 && top < 950, "favourite share " + top);
    }

    @Test
    void favouriteDependsOnTheAccount() {
        String a = favourite(1L);
        boolean differs = false;
        for (long seed = 2; seed < 40 && !differs; seed++) differs = !favourite(seed).equals(a);
        assertTrue(differs);
    }

    @Test
    void triedSpotsAreSkippedThenNone() {
        String first = TREES.get(0).getName();
        for (int i = 0; i < 50; i++) {
            assertNotEquals(first, GatherActivity.pickSpot(TREES, 3L, new Random(i), Set.of(first)).getName());
        }
        assertNull(GatherActivity.pickSpot(TREES, 3L, new Random(1), Set.of(TREES.get(0).getName(), TREES.get(1).getName())));
    }

    private static String favourite(long seed) {
        Map<String, Integer> counts = new HashMap<>();
        Random r = new Random(5);
        for (int i = 0; i < 200; i++) counts.merge(GatherActivity.pickSpot(TREES, seed, r, Set.of()).getName(), 1, Integer::sum);
        return counts.entrySet().stream().max(Map.Entry.comparingByValue()).get().getKey();
    }

    @Test
    void aRockWithAMinerNextToItIsTaken() {
        List<WorldPoint> miners = List.of(new WorldPoint(3229, 3146, 0));
        assertTrue(GatherActivity.occupied(new WorldPoint(3229, 3145, 0), miners));
        assertTrue(GatherActivity.occupied(new WorldPoint(3230, 3145, 0), miners));
        assertFalse(GatherActivity.occupied(new WorldPoint(3224, 3146, 0), miners));
        assertFalse(GatherActivity.occupied(new WorldPoint(3229, 3145, 1), miners));
    }

    @Test
    void standsWhereThreeTinRocksTouch() {
        List<WorldPoint> tin = List.of(new WorldPoint(3222, 3147, 0), new WorldPoint(3223, 3146, 0),
                new WorldPoint(3223, 3148, 0), new WorldPoint(3224, 3146, 0), new WorldPoint(3225, 3148, 0));
        WorldPoint me = new WorldPoint(3226, 3150, 0);
        assertEquals(new WorldPoint(3223, 3147, 0), GatherActivity.bestAnchor(tin, t -> true, List.of(), me, new Random(1)));
    }

    @Test
    void neverStandsOnAnotherPlayersTile() {
        List<WorldPoint> tin = List.of(new WorldPoint(3222, 3147, 0), new WorldPoint(3223, 3146, 0), new WorldPoint(3223, 3148, 0));
        WorldPoint taken = new WorldPoint(3223, 3147, 0);
        WorldPoint a = GatherActivity.bestAnchor(tin, t -> true, List.of(taken), new WorldPoint(3226, 3150, 0), new Random(1));
        assertNotEquals(taken, a);
    }

    @Test
    void sidesOnlyNotDiagonals() {
        assertTrue(GatherActivity.adjacent(new WorldPoint(3223, 3147, 0), new WorldPoint(3223, 3148, 0)));
        assertFalse(GatherActivity.adjacent(new WorldPoint(3223, 3147, 0), new WorldPoint(3224, 3148, 0)));
    }
}
