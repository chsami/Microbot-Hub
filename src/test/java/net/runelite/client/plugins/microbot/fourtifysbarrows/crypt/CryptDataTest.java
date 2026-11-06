package net.runelite.client.plugins.microbot.fourtifysbarrows.crypt;

import java.util.HashSet;
import java.util.Set;
import net.runelite.api.Prayer;
import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CryptDataTest
{
    @Test
    public void visitsTheFourDangerousBrothersBeforeGuthanAndTorag()
    {
        assertArrayEquals(new Brother[] {Brother.DHAROK, Brother.AHRIM, Brother.KARIL,
            Brother.VERAC, Brother.GUTHAN, Brother.TORAG}, Brother.values());
    }

    @Test
    public void identifiesAllCryptsAndRejectsOtherPlanes()
    {
        Set<Integer> killedBits = new HashSet<>();
        for (Brother brother : Brother.values())
        {
            assertTrue(killedBits.add(brother.getKilledVarbit()));
            assertEquals(brother, Brother.inTomb(brother.getSarcophagus()));
            assertEquals(brother, Brother.inTomb(brother.getStairs()));
            assertNull(Brother.inTomb(brother.getMound()));
            assertEquals(brother, Brother.fromNpcName(brother.getNpcName().toUpperCase()));
            WorldPoint coffin = brother.getSarcophagus();
            assertFalse(brother.containsTomb(new WorldPoint(coffin.getX(), coffin.getY(), 0)));
        }
        assertEquals(6, killedBits.size());
        assertNull(Brother.inTomb(null));
        assertNull(Brother.fromNpcName(null));
        assertNull(Brother.fromNpcName("Skeleton"));
        assertEquals(Prayer.PROTECT_FROM_MAGIC, Brother.AHRIM.getPrayer());
        assertEquals(Prayer.PROTECT_FROM_MISSILES, Brother.KARIL.getPrayer());
        assertEquals(Prayer.PROTECT_FROM_MELEE, Brother.DHAROK.getPrayer());
    }

    @Test
    public void puzzleSelectsEachPositionAndRefusesAmbiguousOrUnloadedModels()
    {
        assertEquals(0, PuzzleModels.answerIndex(123, 120, 121, 122));
        assertEquals(1, PuzzleModels.answerIndex(123, 121, 120, 122));
        assertEquals(2, PuzzleModels.answerIndex(123, 121, 122, 120));
        assertEquals(-1, PuzzleModels.answerIndex(-1, -1, -1, -1));
        assertEquals(-1, PuzzleModels.answerIndex(123, 120, 120, 122));
        assertEquals(-1, PuzzleModels.answerIndex(123, 121, 122, 124));
        assertEquals((25 << 16) | 12, PuzzleModels.buttonId(0));
        assertEquals((25 << 16) | 14, PuzzleModels.buttonId(1));
        assertEquals((25 << 16) | 16, PuzzleModels.buttonId(2));
        assertEquals((25 << 16) | 17, PuzzleModels.modelId(2));
    }

    @Test
    public void mazeReroutesAroundBlockedDoorsAndDetectsIsolation()
    {
        assertEquals(8, TunnelMap.firstDoorOnRoute(3, 4, door -> true));
        int detour = TunnelMap.firstDoorOnRoute(3, 4, door -> door != 8);
        assertTrue(detour == 2 || detour == 10);
        assertEquals(-1, TunnelMap.firstDoorOnRoute(3, 4, door -> false));
        assertEquals(-1, TunnelMap.firstDoorOnRoute(3, 4, door -> door != 4 && door != 8 && door != 9 && door != 11));
        assertEquals(-1, TunnelMap.firstDoorOnRoute(4, 4, door -> true));
        assertEquals(-1, TunnelMap.firstDoorOnRoute(-1, 4, door -> true));
        assertEquals(-1, TunnelMap.firstDoorOnRoute(3, 9, door -> true));
    }

    @Test
    public void routeReachesEveryGoalWithoutRepeatingRooms()
    {
        for (int from = 0; from < TunnelMap.ROOM_COUNT; from++)
        {
            for (int goal = 0; goal < TunnelMap.ROOM_COUNT; goal++)
            {
                Set<Integer> visited = new HashSet<>();
                int room = from;
                while (room != goal)
                {
                    assertTrue(visited.add(room), "Route loops");
                    int door = TunnelMap.firstDoorOnRoute(room, goal, candidate -> true);
                    assertTrue(door >= 0, "No route");
                    room = TunnelMap.otherRoom(door, room);
                    assertTrue(room >= 0);
                }
            }
        }
    }

    @Test
    public void mapsBothDoorFormsAndRejectsNearbyUnrelatedObjects()
    {
        for (int door = 0; door < TunnelMap.DOOR_COUNT; door++)
        {
            assertEquals(door, TunnelMap.doorIndex(20681 + door));
            assertEquals(door, TunnelMap.doorIndex(20700 + door));
            assertEquals(469 + door, TunnelMap.doorVarbit(door));
        }
        assertEquals(-1, TunnelMap.doorIndex(20680));
        assertEquals(-1, TunnelMap.doorIndex(20697));
        assertEquals(-1, TunnelMap.doorIndex(20716));
        assertEquals(-1, TunnelMap.otherRoom(16, 0));
        assertEquals(-1, TunnelMap.otherRoom(0, 4));
        assertEquals(TunnelMap.CHEST_ROOM, TunnelMap.nearestRoom(new WorldPoint(3551, 9695, 0)));
        assertFalse(TunnelMap.contains(new WorldPoint(3551, 9695, 3)));
        assertEquals(-1, TunnelMap.nearestRoom(null));
    }
}
