package net.runelite.client.plugins.microbot.mining;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public final class RockSelectorTest {
    @Test
    void picksNearestReachableRock() {
        List<Integer> rocks = Arrays.asList(7, 3, 5);
        assertEquals(3, RockSelector.nearestReachable(rocks, r -> r, r -> true));
    }

    @Test
    void skipsUnreachableNearerRock() {
        List<Integer> rocks = Arrays.asList(7, 3, 5);
        assertEquals(5, RockSelector.nearestReachable(rocks, r -> r, r -> r != 3));
    }

    @Test
    void checksReachabilityOnlyUntilFirstReachableRock() {
        List<Integer> rocks = Arrays.asList(9, 1, 4, 2, 6);
        List<Integer> checked = new ArrayList<>();
        Integer rock = RockSelector.nearestReachable(rocks, r -> r, r -> {
            checked.add(r);
            return r >= 2;
        });
        assertEquals(2, rock);
        assertEquals(Arrays.asList(1, 2), checked);
    }

    @Test
    void returnsNullWhenNoRockIsReachable() {
        assertNull(RockSelector.nearestReachable(Arrays.asList(1, 2), r -> r, r -> false));
    }

    @Test
    void returnsNullForEmptyOrMissingCandidates() {
        assertNull(RockSelector.nearestReachable(Collections.<Integer>emptyList(), r -> r, r -> true));
        assertNull(RockSelector.nearestReachable(null, (Integer r) -> r, r -> true));
        assertNull(RockSelector.nearestReachable(Arrays.asList((Integer) null), r -> r, r -> true));
    }
}
