package net.runelite.client.plugins.microbot.fourtifysbarrows.crypt;

import java.util.Arrays;
import java.util.function.IntPredicate;
import net.runelite.api.coords.WorldPoint;

public final class TunnelMap
{
    public static final int ROOM_COUNT = 9;
    public static final int CHEST_ROOM = 4;
    public static final int DOOR_COUNT = 16;

    private static final WorldPoint[] CENTERS = {
        new WorldPoint(3534, 9711, 0), new WorldPoint(3552, 9712, 0),
        new WorldPoint(3568, 9711, 0), new WorldPoint(3534, 9694, 0),
        new WorldPoint(3552, 9694, 0), new WorldPoint(3568, 9694, 0),
        new WorldPoint(3534, 9677, 0), new WorldPoint(3552, 9677, 0),
        new WorldPoint(3568, 9677, 0)
    };

    private static final int[][] DOORS = {
        {0, 2}, {0, 6}, {0, 3}, {0, 1}, {1, 4}, {1, 2}, {2, 5}, {2, 8},
        {3, 4}, {4, 5}, {6, 3}, {7, 4}, {8, 5}, {6, 7}, {8, 7}, {6, 8}
    };

    private TunnelMap()
    {
    }

    public static WorldPoint roomCenter(int room)
    {
        if (!validRoom(room))
        {
            throw new IllegalArgumentException("Unknown tunnel room: " + room);
        }
        return CENTERS[room];
    }

    public static int doorVarbit(int door)
    {
        if (door < 0 || door >= DOOR_COUNT)
        {
            throw new IllegalArgumentException("Unknown tunnel door: " + door);
        }
        return 469 + door;
    }

    public static int doorIndex(int objectId)
    {
        if (objectId >= 20681 && objectId <= 20696)
        {
            return objectId - 20681;
        }
        if (objectId >= 20700 && objectId <= 20715)
        {
            return objectId - 20700;
        }
        return -1;
    }

    public static int otherRoom(int door, int room)
    {
        if (door < 0 || door >= DOOR_COUNT || !validRoom(room))
        {
            return -1;
        }
        if (DOORS[door][0] == room)
        {
            return DOORS[door][1];
        }
        return DOORS[door][1] == room ? DOORS[door][0] : -1;
    }

    public static int firstDoorOnRoute(int from, int goal, IntPredicate openDoor)
    {
        if (!validRoom(from) || !validRoom(goal) || from == goal || openDoor == null)
        {
            return -1;
        }
        int[] queue = new int[ROOM_COUNT];
        int[] firstDoor = new int[ROOM_COUNT];
        Arrays.fill(firstDoor, -2);
        firstDoor[from] = -1;
        int head = 0;
        int tail = 0;
        queue[tail++] = from;
        while (head < tail)
        {
            int room = queue[head++];
            for (int door = 0; door < DOOR_COUNT; door++)
            {
                int next = otherRoom(door, room);
                if (next >= 0 && firstDoor[next] == -2 && openDoor.test(door))
                {
                    firstDoor[next] = room == from ? door : firstDoor[room];
                    if (next == goal)
                    {
                        return firstDoor[next];
                    }
                    queue[tail++] = next;
                }
            }
        }
        return -1;
    }

    public static boolean contains(WorldPoint point)
    {
        return point != null && point.getPlane() == 0
            && point.getX() >= 3517 && point.getX() <= 3597
            && point.getY() >= 9660 && point.getY() <= 9733;
    }

    public static int nearestRoom(WorldPoint point)
    {
        if (!contains(point))
        {
            return -1;
        }
        int nearest = 0;
        int distance = Integer.MAX_VALUE;
        for (int room = 0; room < ROOM_COUNT; room++)
        {
            int candidate = point.distanceTo(CENTERS[room]);
            if (candidate < distance)
            {
                nearest = room;
                distance = candidate;
            }
        }
        return nearest;
    }

    private static boolean validRoom(int room)
    {
        return room >= 0 && room < ROOM_COUNT;
    }
}
