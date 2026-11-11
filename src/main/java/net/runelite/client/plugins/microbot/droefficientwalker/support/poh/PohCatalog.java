package net.runelite.client.plugins.microbot.droefficientwalker.support.poh;

import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.shortestpath.*;
import net.runelite.client.plugins.microbot.droefficientwalker.support.TransportFactory;
import net.runelite.client.plugins.microbot.util.poh.PohTransport;
import net.runelite.client.plugins.microbot.util.poh.data.HouseLocation;
import java.util.*;

/** Repairs this walker's POH edges while retaining the standard POH settings panel. */
public final class PohCatalog {
    private PohCatalog() {}

    public static Map<WorldPoint, Set<Transport>> getTransportsToPoh() {
        Map<WorldPoint, Set<Transport>> result = new HashMap<>();
        WorldPoint inside = PohPanel.getExitPortalTile();
        HouseLocation house = HouseLocation.getHouseLocation();
        if (inside == null || house == null) return result;
        WorldPoint outside = house.getPortalLocation();
        // The standard parser retains consumability, unlike the legacy convenience constructor.
        Transport tablet = TransportFactory.row(Map.of("Destination", coordinate(inside),
                "Display info", "Teleport to House tablet: Inside", "Item IDs", "8013",
                "isMembers", "Y", "Consumable", "T", "Wilderness level", "19"), TransportType.TELEPORTATION_ITEM);
        add(result, null, tablet);
        for (int toggle = 0; toggle <= 1; toggle++) {
            WorldPoint landing = toggle == 0 ? inside : outside;
            Transport cape = new Transport(landing, "Construction cape: Tele to POH",
                    TransportType.TELEPORTATION_ITEM, true, 19, Set.of(Set.of(9789), Set.of(9790)));
            Transport spell = new Transport(landing, "Teleport to House", TransportType.TELEPORTATION_SPELL,
                    true, 19, Map.of(Skill.MAGIC, 40));
            for (Transport teleport : List.of(cape, spell)) {
                teleport.getVarbits().add(new TransportVarbit(VarbitID.POH_TELE_TOGGLE, toggle, TransportVarbit.Operator.EQUAL));
                teleport.getVarbits().add(new TransportVarbit(VarbitID.POH_HOUSE_LOCATION, house.getVarbitValue(), TransportVarbit.Operator.EQUAL));
                add(result, null, teleport);
            }
        }
        add(result, outside, new Transport(outside, inside, house.name() + " -> PoH", TransportType.POH,
                true, "Home", "Portal", house.getPortalId()));
        return result;
    }

    public static Map<WorldPoint, Set<Transport>> getAvailableTransports(
            Map<WorldPoint, Set<Transport>> all, boolean useNexus) {
        WorldPoint inside = PohPanel.getExitPortalTile();
        if (inside == null || PohPanel.instance == null) return all;
        Map<WorldPoint, Set<Transport>> supplied = PohPanel.getAvailableTransports(all, false);
        Map<WorldPoint, Set<Transport>> result = new HashMap<>();
        Set<TransportType> houseNetworks = new HashSet<>();
        for (Set<Transport> group : supplied.values()) for (Transport transport : group) {
            if (transport.getType() == TransportType.FAIRY_RING || transport.getType() == TransportType.SPIRIT_TREE) {
                houseNetworks.add(transport.getType());
            } else if (transport instanceof PohTransport) {
                WalkerPohTransport fixed = new WalkerPohTransport(inside, ((PohTransport) transport).getTeleport());
                if (fixed.getDestination() != null) add(result, inside, fixed);
            } else add(result, transport.getOrigin(), transport);
        }
        if (useNexus) for (NexusPortal nexus : NexusPortal.getAvailableTeleports())
            add(result, inside, new WalkerPohTransport(inside, nexus));
        HouseLocation house = HouseLocation.getHouseLocation();
        if (house != null) add(result, inside, new Transport(inside, house.getPortalLocation(),
                "PoH -> " + house.name(), TransportType.POH, true, "Enter", "Portal", ObjectID.POH_EXIT_PORTAL));
        for (TransportType type : houseNetworks) {
            Transport home = type == TransportType.FAIRY_RING
                    ? new Transport(inside, inside, "DIQ", type, true, 5)
                    : TransportFactory.row(Map.of("Origin", coordinate(inside), "Destination", coordinate(inside),
                            "menuOption menuTarget objectID", "Travel;Spirit tree;" + ObjectID.POH_SPIRIT_TREE,
                            "Display info", "C: Your house", "Duration", "5", "isMembers", "Y"), type);
            Map<WorldPoint, Transport> origins = new HashMap<>(), destinations = new HashMap<>();
            for (Set<Transport> group : all.values()) for (Transport transport : group) {
                if (transport.getType() != type) continue;
                Transport origin = TransportFactory.origin(transport), destination = TransportFactory.destination(transport);
                if (origin.getOrigin() != null) origins.putIfAbsent(origin.getOrigin(), origin);
                if (destination.getDestination() != null) destinations.putIfAbsent(destination.getDestination(), destination);
            }
            for (var origin : origins.entrySet()) if (!origin.getKey().equals(inside))
                add(result, origin.getKey(), TransportFactory.pair(origin.getValue(), home));
            for (var destination : destinations.entrySet()) if (!destination.getKey().equals(inside))
                add(result, inside, TransportFactory.pair(home, destination.getValue()));
        }
        return result;
    }

    private static String coordinate(WorldPoint point) { return point.getX() + " " + point.getY() + " " + point.getPlane(); }
    private static void add(Map<WorldPoint, Set<Transport>> map, WorldPoint origin, Transport transport) {
        map.computeIfAbsent(origin, ignored -> new HashSet<>()).add(transport);
    }
}
