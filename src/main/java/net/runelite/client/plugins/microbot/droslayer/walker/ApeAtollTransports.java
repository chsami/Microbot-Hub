package net.runelite.client.plugins.microbot.droslayer.walker;

import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.shortestpath.TransportVarbit;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Verified Ape Atoll crossings shared by native planning and the comparison graph. */
public final class ApeAtollTransports
{
    public static final Set<Integer> GREEGREES = Set.of(4024,4025,4026,4027,4028,4029,4030,4031,19525);
    public static final WorldPoint CRASH_ISLAND = new WorldPoint(2896,2726,0);
    public static final WorldPoint GRAND_TREE_LANDING = new WorldPoint(2393,3465,0);
    public static final WorldPoint APE_ATOLL_LUMDO = new WorldPoint(2803,2706,0);
    private ApeAtollTransports() { }

    public static boolean crashIsland(WorldPoint point)
    {
        return point != null && point.getPlane() == 0 && point.getRegionID() == 11562;
    }

    public static boolean teakReturnArea(WorldPoint point)
    {
        return ApeAtollGreegree.onIsland(point) || crashIsland(point);
    }

    /** Supersede the legacy scenery rows with executable NPC Travel connections. */
    public static boolean replacesTravel(int id, WorldPoint origin)
    {
        if (origin == null) return false;
        return id == 1446 && (crashIsland(origin) || origin.distanceTo(new WorldPoint(2649,4518,0)) <= 8)
            || id == 1454 && (crashIsland(origin) || origin.distanceTo(APE_ATOLL_LUMDO) <= 8)
            || id == 1445 && origin.distanceTo(new WorldPoint(2484,3486,1)) <= 8;
    }

    public static List<Transport> create()
    {
        List<Transport> routes = new ArrayList<>();
        // Live Crash Island standing tile (2896,2726), 2026-10-15. Waydar flies
        // to the Grand Tree; Lumdo ferries between Crash Island and Ape Atoll.
        routes.add(new Transport(CRASH_ISLAND, GRAND_TREE_LANDING, "Grand Tree", TransportType.NPC,
            true, "Travel", "Waydar", 1446, 4));
        routes.add(new Transport(new WorldPoint(2649,4518,0), new WorldPoint(2894,2726,0),
            "Crash Island", TransportType.NPC, true, "Travel", "Waydar", 1446, 4));
        routes.add(new Transport(CRASH_ISLAND, APE_ATOLL_LUMDO,
            "Ape Atoll", TransportType.NPC, true, "Travel", "Lumdo", 1454, 5));
        routes.add(new Transport(APE_ATOLL_LUMDO, new WorldPoint(2894,2726,0),
            "Crash Island", TransportType.NPC, true, "Travel", "Lumdo", 1454, 5));
        routes.add(new Transport(new WorldPoint(2484,3486,1), new WorldPoint(2649,4516,0),
            "Gnome hangar", TransportType.NPC, true, "Travel", "Daero", 1445, 6));
        // User's live gate: object row y=2766; standable approaches on both sides.
        WorldPoint south = new WorldPoint(2720,2765,0), north = new WorldPoint(2720,2767,0);
        for (boolean reverse : new boolean[]{false,true})
        {
            for (boolean postQuest : new boolean[]{false,true})
            {
                Transport gate = new Transport(reverse ? north : south, reverse ? south : north,
                    "Ape Atoll bamboo gate", TransportType.TRANSPORT, true, "Open", "Bamboo Gate", postQuest ? 4787 : 4788, 6);
                if (postQuest) gate.getQuests().put(Quest.MONKEY_MADNESS_II, QuestState.FINISHED);
                else gate.setItemIdRequirements(Set.of(GREEGREES));
                routes.add(gate);
            }
        }
        // Live user-confirmed entrance and standable interior landing, 2026-09-29.
        Transport entrance = new Transport(new WorldPoint(2714,2787,0), new WorldPoint(2509,9173,1),
            "Kruk's Dungeon entrance", TransportType.TRANSPORT, true, "Investigate", "Jungle Grass", 28810, 8);
        entrance.getQuests().put(Quest.MONKEY_MADNESS_II, QuestState.IN_PROGRESS);
        entrance.getVarbits().add(new TransportVarbit(5027,34,TransportVarbit.Operator.GREATER_THAN));
        routes.add(entrance);
        routes.add(new Transport(new WorldPoint(2509,9173,1), new WorldPoint(2714,2787,0),
            "Kruk's Dungeon exit", TransportType.TRANSPORT, true, "Climb-up", "Bamboo Ladder", 28676, 4));
        // The embedded table has these stairs without object IDs, so its parser omits them.
        for (int x : new int[]{2795,2796,2799})
        {
            WorldPoint ground = new WorldPoint(x,2793,0), upper = new WorldPoint(x,2797,1);
            routes.add(new Transport(ground, upper, "Marim temple stairs", TransportType.TRANSPORT,
                true, "Climb-up", "Stairs", 4756, 4));
            routes.add(new Transport(upper, ground, "Marim temple stairs", TransportType.TRANSPORT,
                true, "Climb-down", "Stairs", 4755, 4));
        }
        // Live-confirmed second chin cave entrance; both areas use plane 1.
        WorldPoint chinEntrance = new WorldPoint(2572,9168,1);
        WorldPoint chinLanding = new WorldPoint(2380,9168,1);
        routes.add(new Transport(chinEntrance, chinLanding, "MM2 chin cave hole", TransportType.TRANSPORT,
            true, "Enter", "Hole", 28772, 4));
        routes.add(new Transport(chinLanding, chinEntrance, "MM2 chin cave rope", TransportType.TRANSPORT,
            true, "Climb-up", "Rope", 28775, 4));
        return routes;
    }

    public static boolean isEntrance(Transport edge)
    {
        return edge != null && edge.getObjectId() == 28810;
    }

    public static boolean matchesObject(Transport edge, int id)
    {
        if (isEntrance(edge)) return id == 28810 || id == 28673 || id == 28674;
        if (isGate(edge)) return id == 4787 || id == 4788;
        return edge.getObjectId() == id;
    }

    public static boolean isGate(Transport edge)
    {
        return edge != null && (edge.getObjectId() == 4787 || edge.getObjectId() == 4788)
            && edge.getOrigin() != null && edge.getOrigin().getPlane() == 0
            && edge.getOrigin().getX() == 2720
            && (edge.getOrigin().getY() == 2765 || edge.getOrigin().getY() == 2767);
    }
}
