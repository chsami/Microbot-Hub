package net.runelite.client.plugins.microbot.cooking.enums;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CookingLocationNearestTest {
    private static final List<CookingLocation> CANDIDATES = Arrays.asList(
            CookingLocation.BARBARBIAN_VILLAGE,
            CookingLocation.VARROCK_WEST,
            CookingLocation.CATHERBY);

    private static Map<CookingLocation, Integer> distances(int barbarianVillage, int varrockWest, int catherby) {
        Map<CookingLocation, Integer> distances = new EnumMap<>(CookingLocation.class);
        distances.put(CookingLocation.BARBARBIAN_VILLAGE, barbarianVillage);
        distances.put(CookingLocation.VARROCK_WEST, varrockWest);
        distances.put(CookingLocation.CATHERBY, catherby);
        return distances;
    }

    @Test
    void picksTheShortestDistance() {
        Map<CookingLocation, Integer> distances = distances(89, 120, 8);

        Map.Entry<CookingLocation, Integer> nearest = CookingLocation.nearest(CANDIDATES, distances::get);

        assertEquals(CookingLocation.CATHERBY, nearest.getKey());
        assertEquals(8, nearest.getValue());
    }

    @Test
    void unknownPlayerLocationDuringTheScanGivesNoResult() {
        Map<CookingLocation, Integer> distances = distances(89, 120, Integer.MAX_VALUE);

        assertNull(CookingLocation.nearest(CANDIDATES, distances::get));
    }

    @Test
    void equalDistancesKeepTheFirstCandidate() {
        Map<CookingLocation, Integer> distances = distances(40, 40, 40);

        Map.Entry<CookingLocation, Integer> nearest = CookingLocation.nearest(CANDIDATES, distances::get);

        assertEquals(CookingLocation.BARBARBIAN_VILLAGE, nearest.getKey());
        assertEquals(40, nearest.getValue());
    }

    @Test
    void noCandidatesGivesNoResult() {
        assertNull(CookingLocation.nearest(Collections.emptyList(), location -> 1));
    }
}
