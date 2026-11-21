package net.runelite.client.plugins.microbot.droefficientwalker;

import java.util.List;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;

/** Recorded mine access and the native upper-level ladder approaches. */
public final class MotherlodeTransports
{
    public static final WorldPoint FALADOR_STAIRS = point(3061, 3377);
    public static final WorldPoint DWARVEN_STAIRS = point(3058, 9777);
    public static final WorldPoint CAVE_APPROACH = point(3060, 9766);
    public static final WorldPoint MINE_ENTRY = point(3728, 5692);
    public static final WorldPoint BANK_APPROACH = point(3760, 5666);
    public static final WorldPoint UPPER_LADDER_APPROACH = point(3755, 5675);
    public static final WorldPoint LOWER_LADDER_APPROACH = point(3754, 5672);
    public static final WorldPoint SKILLS_NECKLACE_LANDING = point(3049, 9762);
    public static final List<WorldPoint> ENTRANCE_ROCKFALLS = List.of(point(3733, 5680), point(3731, 5683));
    private MotherlodeTransports() { }

    public static boolean inMine(WorldPoint point)
    {
        return point != null && point.getPlane() == 0 && point.getX() >= 3710 && point.getX() <= 3789
            && point.getY() >= 5630 && point.getY() <= 5730;
    }

    public static List<Transport> create()
    {
        Transport guildDown = edge(point(3019, 3337), point(3019, 9737), "Climb-down", "Ladder", 30367);
        guildDown.getSkillLevels()[Skill.MINING.ordinal()] = 60;
        return List.of(
            edge(FALADOR_STAIRS, DWARVEN_STAIRS, "Climb-down", "Staircase", 16664),
            edge(CAVE_APPROACH, MINE_ENTRY, "Enter", "Cave", 26654),
            edge(MINE_ENTRY, CAVE_APPROACH, "Exit", "Tunnel", 26655),
            guildDown,
            edge(point(3019, 9737), point(3019, 3337), "Climb-up", "Ladder", 17385),
            // Both levels use plane 0. The ladder foot itself is not a walkable endpoint.
            edge(UPPER_LADDER_APPROACH, LOWER_LADDER_APPROACH,
                "Climb-down", "Ladder", ObjectID.MOTHERLODE_LADDER_TOP));
    }

    static boolean isUpperLadderDescent(Transport source)
    {
        return source != null && source.getType() == TransportType.TRANSPORT
            && source.getObjectId() == ObjectID.MOTHERLODE_LADDER_TOP
            && UPPER_LADDER_APPROACH.equals(source.getOrigin())
            && LOWER_LADDER_APPROACH.equals(source.getDestination())
            && "Climb-down".equals(source.getAction());
    }

    public static boolean replaces(Transport source)
    {
        if (source == null || source.getOrigin() == null) return false;
        return replaces(source.getObjectId(), source.getOrigin());
    }

    static boolean replaces(int id, WorldPoint p)
    {
        if (p == null) return false;
        return (id == 26654 || id == 26655)
            || (id == ObjectID.MOTHERLODE_LADDER_TOP && near(p, UPPER_LADDER_APPROACH, 4))
            || (id == 16664 && near(p, FALADOR_STAIRS, 4))
            || (id == 30367 && near(p, point(3019, 3337), 4))
            || (id == 17385 && near(p, point(3019, 9737), 4));
    }

    private static Transport edge(WorldPoint from, WorldPoint to, String action, String name, int id)
    {
        return new Transport(from, to, "Motherlode Mine access", TransportType.TRANSPORT, true, action, name, id);
    }

    private static boolean near(WorldPoint a, WorldPoint b, int radius)
    {
        return a.getPlane() == b.getPlane() && a.distanceTo2D(b) <= radius;
    }

    private static WorldPoint point(int x, int y) { return new WorldPoint(x, y, 0); }
}
