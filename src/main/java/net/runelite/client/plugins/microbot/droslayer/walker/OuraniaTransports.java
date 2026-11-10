package net.runelite.client.plugins.microbot.droslayer.walker;

import java.util.List;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;

/** Live-confirmed Ourania entrance and interior, shared by every walker caller. */
public final class OuraniaTransports {
    public static final WorldPoint ENTRANCE = new WorldPoint(2452, 3232, 0);
    // The collision map isolates the exact ladder tile. This reachable approach
    // stays within interaction range of the same ladder, without overriding collision flags.
    public static final WorldPoint APPROACH = new WorldPoint(2455, 3233, 0);
    public static final WorldPoint CAVE_ENTRY = new WorldPoint(3015, 5629, 0);
    public static final WorldPoint ALTAR = new WorldPoint(3058, 5579, 0);
    private OuraniaTransports() { }
    public static List<Transport> create() {
        return List.of(
            new Transport(APPROACH, CAVE_ENTRY, "Ourania ladder approach", TransportType.TRANSPORT,
                true, "Climb", "Ladder", ObjectID.RC_ZMI_DUNGEON_ENTRANCE),
            new Transport(ENTRANCE, CAVE_ENTRY, "Ourania ladder entrance", TransportType.TRANSPORT,
                true, "Climb", "Ladder", ObjectID.RC_ZMI_DUNGEON_ENTRANCE),
            new Transport(CAVE_ENTRY, APPROACH, "Ourania ladder exit approach", TransportType.TRANSPORT,
                true, "Climb", "Ladder", 29636),
            new Transport(CAVE_ENTRY, ENTRANCE, "Ourania ladder exit", TransportType.TRANSPORT,
                true, "Climb", "Ladder", 29636));
    }
    static boolean replaces(Transport source) {
        return source.getObjectId() == ObjectID.RC_ZMI_DUNGEON_ENTRANCE || source.getObjectId() == 29636;
    }
}
