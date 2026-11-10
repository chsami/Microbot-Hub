package net.runelite.client.plugins.microbot.droslayer.walker;

import java.util.Arrays;
import java.util.List;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;

/** Inbound route observed in the user's 2026-10-07 screenshots. */
public final class WildernessGodWarsTransports {
    private WildernessGodWarsTransports() {}

    public static List<Transport> create() {
        Transport cave = edge(3017, 3740, 0, 3065, 10159, 3, "Enter", "Cave", 26766);
        Transport boulder = new Transport(new WorldPoint(3056, 10165, 3), new WorldPoint(3052, 10165, 3),
            "Wilderness God Wars: Move Boulder", TransportType.NPC,
            true, "Move", "Boulder", 6621, 6);
        boulder.getSkillLevels()[Skill.STRENGTH.ordinal()] = 60;
        Transport crevice = edge(3049, 10165, 3, 3034, 10158, 0, "Use", "Crevice", 26767);
        return Arrays.asList(cave, boulder, crevice);
    }

    static boolean isCrevice(Transport transport) {
        return transport != null && "Use".equals(transport.getAction())
            && new WorldPoint(3049, 10165, 3).equals(transport.getOrigin())
            && new WorldPoint(3034, 10158, 0).equals(transport.getDestination());
    }

    static boolean isBoulder(Transport transport) {
        return transport != null && "Move".equals(transport.getAction())
            && new WorldPoint(3056, 10165, 3).equals(transport.getOrigin())
            && new WorldPoint(3052, 10165, 3).equals(transport.getDestination());
    }

    private static Transport edge(int x, int y, int plane, int endX, int endY, int endPlane,
                                  String action, String name, int id) {
        return new Transport(new WorldPoint(x, y, plane), new WorldPoint(endX, endY, endPlane),
            "Wilderness God Wars: " + action + " " + name, TransportType.TRANSPORT,
            true, action, name, id, 6);
    }
}
