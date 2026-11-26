package net.runelite.client.plugins.microbot.accountstarter.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SupplyShareTest {
    private static Need arrows(int quantity) {
        return new Need("arrows", Set.of(882), quantity, null, 882, "bronze arrows");
    }

    private static AccountSnapshot banked(int arrows) {
        return AccountSnapshot.builder().bank(Map.of(882, arrows)).bankKnown(true).build();
    }

    @Test
    void bankedSuppliesCountTowardsTheShare() {
        assertEquals(0.5, Planner.supplyShare(List.of(arrows(100)), banked(50)), 0.001);
    }

    @Test
    void theScarcestNeedDecides() {
        AccountSnapshot s = AccountSnapshot.builder().bank(Map.of(882, 100)).bankKnown(true).build();
        assertEquals(0, Planner.supplyShare(List.of(arrows(100), new Need("runes", Set.of(558), 10, null, 558, "mind runes")), s), 0.001);
    }

    @Test
    void aWellStockedStyleBeatsAnEmptyOne() {
        assertTrue(Planner.supplyShare(List.of(arrows(50)), banked(50)) > Planner.supplyShare(List.of(arrows(50)), banked(5)));
    }
}
