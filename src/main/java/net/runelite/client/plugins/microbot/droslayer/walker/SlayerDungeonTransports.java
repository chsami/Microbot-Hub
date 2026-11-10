package net.runelite.client.plugins.microbot.droslayer.walker;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;

import java.util.ArrayList;
import java.util.List;

/** Missing dungeon links belong to the walker and are usable by every script. */
public final class SlayerDungeonTransports {
    private SlayerDungeonTransports() {}

    public static List<Transport> create() {
        // Surface object from ShadowsOfCustodia; interior exit from the confirmed Slayer run.
        List<Transport> routes = new ArrayList<>(List.of(
            new Transport(new WorldPoint(1296, 3375, 0), new WorldPoint(1298, 9757, 0),
                "Stalker Den entrance", TransportType.TRANSPORT, true, "Enter", "Cave entrance", 56439),
            new Transport(new WorldPoint(1298, 9757, 0), new WorldPoint(1296, 3375, 0),
                "Stalker Den exit", TransportType.TRANSPORT, true, "Exit", "Cave exit", 56440)));
        // Live verified Smoke Devil entrance and landing, 2026-09-25.
        routes.add(new Transport(new WorldPoint(2412, 3060, 0), new WorldPoint(2404, 9415, 0),
            "Smoke Devil Dungeon entrance", TransportType.TRANSPORT, true, "Enter", "Smoky cave", 30176));
        // Live verified Kraken Cove entry, 2026-09-25.
        routes.add(new Transport(new WorldPoint(2275, 3611, 0), new WorldPoint(2276, 9988, 0),
            "Kraken Cove entrance", TransportType.TRANSPORT, true, "Enter", "Cave Entrance", 30177));
        // Alternate ice entrance: user-confirmed outside/inside tiles. Keep the
        // existing western rock entrance (8929) as a separate route.
        routes.add(new Transport(new WorldPoint(2544, 3742, 0), new WorldPoint(2545, 10143, 0),
            "Waterbirth ice entrance", TransportType.TRANSPORT, true, "Enter", "Cave entrance", 8930));
        // Live verified Araxyte Cave landing, 2026-09-25.
        routes.add(new Transport(new WorldPoint(3660, 3411, 0), new WorldPoint(3679, 9797, 0),
            "Araxyte Cave entrance", TransportType.TRANSPORT, true, "Enter", "Cave", 42594));
        // Existing surface entrance with the live-confirmed upper floor (legacy plane 3 is obsolete).
        routes.add(new Transport(new WorldPoint(1435, 3671, 0), new WorldPoint(1435, 10076, 2),
            "Chasm of Fire entrance", TransportType.TRANSPORT, true, "Enter", "Chasm", 30236));
        // Chasm now occupies planes 2/1/0, rather than the legacy 3/2/1.
        addLift(routes, 1437, 10093, 2);
        addLift(routes, 1451, 10069, 2);
        addLift(routes, 1457, 10095, 1);
        addLift(routes, 1457, 10075, 1);
        // Cross the blocked object row, not onto it. Both sides are walkable in the
        // collision map; the user confirmed the northern Hydra door approach in-game.
        addRocks(routes, 1311, 10214, 10216, 34544);
        addRocks(routes, 1312, 10214, 10216, 34544);
        addRocks(routes, 1351, 10250, 10252, 34548);
        addRocks(routes, 1352, 10250, 10252, 34548);
        // Whirlpool arrives upstairs; live screenshots confirm plane 1 and the
        // downstairs landing. The old catalogue incorrectly landed on plane 0.
        routes.add(new Transport(new WorldPoint(2511, 3511, 0), new WorldPoint(1768, 5366, 1),
            "Ancient Cavern whirlpool", TransportType.TRANSPORT, true, "Jump-into", "Whirlpool", 25274));
        routes.add(new Transport(new WorldPoint(1769, 5366, 1), new WorldPoint(1774, 5366, 0),
            "Ancient Cavern main-floor stairs", TransportType.TRANSPORT, true, "Climb-down", "Stairs", 25338));
        // Lower-floor stair object is confirmed by the bundled quest helper;
        // the upper-floor approach was confirmed in-game by the user.
        routes.add(new Transport(new WorldPoint(1778, 5346, 0), new WorldPoint(1778, 5343, 1),
            "Ancient Cavern mithril stairs", TransportType.TRANSPORT, true, "Climb-up", "Stairs", 25339));
        // Account-confirmed Edgeville approach; the object can be closed or open.
        routes.add(new Transport(new WorldPoint(3094, 3469, 0), new WorldPoint(3096, 9867, 0),
            "Edgeville Dungeon trapdoor", TransportType.TRANSPORT, true, "Climb-down", "Trapdoor", 1581));
        return List.copyOf(routes);
    }

    private static void addRocks(List<Transport> routes, int x, int southY, int northY, int id) {
        WorldPoint south = new WorldPoint(x, southY, 0);
        WorldPoint north = new WorldPoint(x, northY, 0);
        routes.add(new Transport(south, north, "Karuulm rocks north", TransportType.TRANSPORT,
            true, "Climb", "Rocks", id));
        routes.add(new Transport(north, south, "Karuulm rocks south", TransportType.TRANSPORT,
            true, "Climb", "Rocks", id));
    }

    private static void addLift(List<Transport> routes, int x, int y, int upperPlane) {
        WorldPoint upper = new WorldPoint(x, y, upperPlane);
        WorldPoint lower = new WorldPoint(x, y, upperPlane - 1);
        routes.add(new Transport(upper, lower, "Chasm lift down", TransportType.TRANSPORT,
            true, "Enter", "Lift", 30258));
        routes.add(new Transport(lower, upper, "Chasm lift up", TransportType.TRANSPORT,
            true, "Enter", "Lift", 30259));
    }

    public static boolean isSupersededTransport(int objectId, WorldPoint origin) {
        return objectId == 1581 && new WorldPoint(3096, 3468, 0).equals(origin)
            || isSupersededTransport(objectId);
    }

    public static boolean isSupersededTransport(int objectId) {
        return objectId == 25274 || objectId == 30234 || objectId == 30236 || objectId == 30258 || objectId == 30259;
    }
}
