package net.runelite.client.plugins.microbot.woodcutting.Forestry;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EntlingsEventSortTest {

    private static final WorldPoint PLAYER = new WorldPoint(3200, 3200, 0);

    private static Rs2NpcModel entlingAt(WorldPoint location) {
        Rs2NpcModel entling = mock(Rs2NpcModel.class);
        when(entling.getWorldLocation()).thenReturn(location);
        return entling;
    }

    @Test
    void sortsEntlingsNearestFirst() {
        Rs2NpcModel far = entlingAt(new WorldPoint(3205, 3200, 0));
        Rs2NpcModel near = entlingAt(new WorldPoint(3201, 3200, 0));
        Rs2NpcModel middle = entlingAt(new WorldPoint(3203, 3200, 0));

        List<Rs2NpcModel> sorted = EntlingsEvent.sortByDistance(new ArrayList<>(Arrays.asList(far, near, middle)), PLAYER);

        assertEquals(Arrays.asList(near, middle, far), sorted);
    }

    @Test
    void skipsEntlingsWithoutWorldLocation() {
        Rs2NpcModel missing = entlingAt(null);
        Rs2NpcModel near = entlingAt(new WorldPoint(3201, 3200, 0));
        Rs2NpcModel far = entlingAt(new WorldPoint(3204, 3200, 0));

        List<Rs2NpcModel> sorted = EntlingsEvent.sortByDistance(new ArrayList<>(Arrays.asList(far, missing, near)), PLAYER);

        assertEquals(Arrays.asList(near, far), sorted);
    }
}
