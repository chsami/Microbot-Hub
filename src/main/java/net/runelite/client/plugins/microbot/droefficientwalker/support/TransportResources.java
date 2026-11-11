package net.runelite.client.plugins.microbot.droefficientwalker.support;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * This class represents a travel point between two WorldPoints.
 */
@Slf4j
public class TransportResources {
    public static java.io.InputStream openResource(String name) {
        if ("charter_ships.tsv".equals(name))
            return net.runelite.client.plugins.microbot.droefficientwalker.CharterTransportCatalog.open();
        java.io.InputStream local = TransportResources.class.getResourceAsStream(name);
        if (local == null) local = TransportResources.class.getResourceAsStream(
                "/net/runelite/client/plugins/microbot/shortestpath/" + name);
        return "collision-map.zip".equals(name)
            ? net.runelite.client.plugins.microbot.droefficientwalker.MotherlodeCollisionMap.open(local) : local;
    }

    // Haunted Woods (ALQ): hostile area; never route into or out of it.
    private static final WorldPoint BLOCKED_RING = new WorldPoint(3597, 3495, 0);
    private static final WorldPoint LOCATION_PERMUTATION = new WorldPoint(-1, -1, -1);

    private static void addTransports(Map<WorldPoint, Set<Transport>> transports, String path, TransportType transportType) {
        addTransports(transports, path, transportType, 0);
    }

    private static void addTransports(Map<WorldPoint, Set<Transport>> transports, String path, TransportType transportType, int radiusThreshold) {
        final String DELIM_COLUMN = "\t";
        final String PREFIX_COMMENT = "#";

        try {
            java.io.InputStream stream = openResource(path);
            if (stream == null) {
                log.warn("Transport resource missing, skipping: {}", path);
                return;
            }
            String s = new String(Util.readAllBytes(stream), StandardCharsets.UTF_8);
            Scanner scanner = new Scanner(s);
            if (!scanner.hasNextLine()) {
                scanner.close();
                log.warn("Transport resource empty, skipping: {}", path);
                return;
            }

            // Header line is the first line in the file and will start with either '#' or '# '
            String headerLine = scanner.nextLine();
            if (headerLine.endsWith("\r")) {
                headerLine = headerLine.substring(0, headerLine.length() - 1);
            }
            headerLine = headerLine.startsWith(PREFIX_COMMENT + " ") ? headerLine.replace(PREFIX_COMMENT + " ", PREFIX_COMMENT) : headerLine;
            headerLine = headerLine.startsWith(PREFIX_COMMENT) ? headerLine.replace(PREFIX_COMMENT, "") : headerLine;
            String[] headers = headerLine.split(DELIM_COLUMN);

            Set<Transport> newTransports = new HashSet<>();

            int lineNumber = 1;
            while (scanner.hasNextLine()) {
                lineNumber++;
                String line = scanner.nextLine();
                if (line.endsWith("\r")) {
                    line = line.substring(0, line.length() - 1);
                }

                if (line.startsWith(PREFIX_COMMENT) || line.isBlank()) {
                    continue;
                }

                String[] fields = line.split(DELIM_COLUMN);
                Map<String, String> fieldMap = new HashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    if (i < fields.length) {
                        fieldMap.put(headers[i], fields[i]);
                    }
                }

                try {
                    Transport transport = TransportFactory.row(fieldMap, transportType);
                    newTransports.add(transport);
                } catch (RuntimeException e) {
                    log.warn("Skipping transport row {} in {}: {}", lineNumber, path, e.getMessage());
                }
            }
            scanner.close();

            /*
             * A transport with origin A and destination B is one-way and must
             * be duplicated as origin B and destination A to become two-way.
             * Example: key-locked doors
             *
             * A transport with origin A and a missing destination is one-way,
             * but can go from origin A to all destinations with a missing origin.
             * Example: fairy ring AIQ -> <blank>
             *
             * A transport with a missing origin and destination B is one-way,
             * but can go from all origins with a missing destination to destination B.
             * Example: fairy ring <blank> -> AIQ
             *
             * Identical transports from origin A to destination A are skipped, and
             * non-identical transports from origin A to destination A can be skipped
             * by specifying a radius threshold to ignore almost identical coordinates.
             * Example: fairy ring AIQ -> AIQ
             */
            Set<Transport> transportOrigins = new HashSet<>();
            Set<Transport> transportDestinations = new HashSet<>();
            for (Transport transport : newTransports) {
                WorldPoint origin = transport.getOrigin();
                WorldPoint destination = transport.getDestination();
                // Logic to determine ordinary transport vs teleport vs permutation (e.g. fairy ring)
                if ((origin == null && destination == null)
                        || (LOCATION_PERMUTATION.equals(origin) && LOCATION_PERMUTATION.equals(destination))) {
                    continue;
                } else if (!LOCATION_PERMUTATION.equals(origin) && origin != null
                        && LOCATION_PERMUTATION.equals(destination)) {
                    transportOrigins.add(transport);
                } else if (LOCATION_PERMUTATION.equals(origin)
                        && !LOCATION_PERMUTATION.equals(destination) && destination != null) {
                    transportDestinations.add(transport);
                }
                if (!LOCATION_PERMUTATION.equals(origin)
                        && destination != null && !LOCATION_PERMUTATION.equals(destination)
                        && (origin == null || !origin.equals(destination))) {
                    transports.computeIfAbsent(origin, k -> new HashSet<>()).add(transport);
                }
            }
            for (Transport origin : transportOrigins) {
                if (transportType == TransportType.FAIRY_RING && BLOCKED_RING.equals(origin.getOrigin())) {
                    continue;
                }
                for (Transport destination : transportDestinations) {
                    if (transportType == TransportType.FAIRY_RING && BLOCKED_RING.equals(destination.getDestination())) {
                        continue;
                    }
                    if (origin.getOrigin().distanceTo2D(destination.getDestination()) > radiusThreshold) {
                        transports.computeIfAbsent(origin.getOrigin(), k -> new HashSet<>())
                                .add(TransportFactory.pair(origin, destination));
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Failed to read transport file {}: {}", path, e.getMessage());
        }
    }

    private static void appendStandardTransportFiles(HashMap<WorldPoint, Set<Transport>> transports) {
        addTransports(transports, "transports.tsv", TransportType.TRANSPORT);
        addTransports(transports, "agility_shortcuts.tsv", TransportType.AGILITY_SHORTCUT);
        addTransports(transports, "boats.tsv", TransportType.BOAT);
        addTransports(transports, "canoes.tsv", TransportType.CANOE);
        addTransports(transports, "charter_ships.tsv", TransportType.CHARTER_SHIP);
        addTransports(transports, "ships.tsv", TransportType.SHIP);
        addTransports(transports, "fairy_rings.tsv", TransportType.FAIRY_RING);
        addTransports(transports, "gnome_gliders.tsv", TransportType.GNOME_GLIDER, 6);
        addTransports(transports, "minecarts.tsv", TransportType.MINECART);
        addTransports(transports, "spirit_trees.tsv", TransportType.SPIRIT_TREE, 5);
        addTransports(transports, "quetzals.tsv", TransportType.QUETZAL, 6);
        addTransports(transports, "teleportation_items.tsv", TransportType.TELEPORTATION_ITEM);
        addTransports(transports, "teleportation_minigames.tsv", TransportType.TELEPORTATION_MINIGAME);
        addTransports(transports, "teleportation_levers.tsv", TransportType.TELEPORTATION_LEVER);
        addTransports(transports, "teleportation_portals.tsv", TransportType.TELEPORTATION_PORTAL);
        addTransports(transports, "teleportation_spells.tsv", TransportType.TELEPORTATION_SPELL);
        addTransports(transports, "wilderness_obelisks.tsv", TransportType.WILDERNESS_OBELISK);
        addTransports(transports, "magic_carpets.tsv", TransportType.MAGIC_CARPET);
        addTransports(transports, "hot_air_balloons.tsv", TransportType.HOT_AIR_BALLOON, 7);
        addTransports(transports, "magic_mushtrees.tsv", TransportType.MAGIC_MUSHTREE, 5);
        addTransports(transports, "seasonal_transports.tsv", TransportType.SEASONAL_TRANSPORT);
        addTransports(transports, "npcs.tsv", TransportType.NPC);
    }

    public static HashMap<WorldPoint, Set<Transport>> loadAllFromResources() {
        HashMap<WorldPoint, Set<Transport>> transports = new HashMap<>();
        appendStandardTransportFiles(transports);
        for (Set<Transport> group : transports.values())
            group.removeIf(edge -> net.runelite.client.plugins.microbot.droefficientwalker.SlayerDungeonTransports.isSupersededTransport(edge.getObjectId()));
        for (Transport edge : net.runelite.client.plugins.microbot.droefficientwalker.SlayerDungeonTransports.create())
            transports.computeIfAbsent(edge.getOrigin(), point -> new HashSet<>()).add(edge);
        for (Transport edge : net.runelite.client.plugins.microbot.droefficientwalker.MagicAxesHutTransports.create())
            transports.computeIfAbsent(edge.getOrigin(), point -> new HashSet<>()).add(edge);
        for (Transport edge : net.runelite.client.plugins.microbot.droefficientwalker.WildernessGodWarsTransports.create())
            transports.computeIfAbsent(edge.getOrigin(), point -> new HashSet<>()).add(edge);
        for (Set<Transport> group : transports.values())
            group.removeIf(edge -> net.runelite.client.plugins.microbot.droefficientwalker.ApeAtollTransports.replacesTravel(edge.getObjectId(), edge.getOrigin()));
        for (Transport edge : net.runelite.client.plugins.microbot.droefficientwalker.ApeAtollTransports.create())
            transports.computeIfAbsent(edge.getOrigin(), point -> new HashSet<>()).add(edge);
        for (Transport edge : net.runelite.client.plugins.microbot.droefficientwalker.CharterTransportCatalog.deckExits())
            transports.computeIfAbsent(edge.getOrigin(), point -> new HashSet<>()).add(edge);
        for (Transport edge : net.runelite.client.plugins.microbot.droefficientwalker.SailorsAmuletTransports.additional())
            transports.computeIfAbsent(edge.getOrigin(), point -> new HashSet<>()).add(edge);
        for (Set<Transport> group : transports.values())
            group.removeIf(edge -> net.runelite.client.plugins.microbot.droefficientwalker.MotherlodeTransports.replaces(edge));
        for (Transport edge : net.runelite.client.plugins.microbot.droefficientwalker.MotherlodeTransports.create())
            transports.computeIfAbsent(edge.getOrigin(), point -> new HashSet<>()).add(edge);
        log.info("Loaded {} transports", transports.size());
        return transports;
    }

    /**
     * Reload transport TSVs from the plugin classpath (same as {@link #loadAllFromResources()}).
     * Apply to {@link PathfinderConfig} via {@link ShortestPathPlugin} config hot-reload.
     */
    public static HashMap<WorldPoint, Set<Transport>> reloadFromResources() {
        return loadAllFromResources();
    }


    public static List<Restriction> loadRestrictions() {
        List<Restriction> restrictions = new ArrayList<>();
        final String DELIM_COLUMN = "\t";
        final String PREFIX_COMMENT = "#";

        try {
            String s = new String(Util.readAllBytes(openResource("restrictions.tsv")), StandardCharsets.UTF_8);
            Scanner scanner = new Scanner(s);
            String headerLine = scanner.nextLine();
            headerLine = headerLine.startsWith(PREFIX_COMMENT + " ") ? headerLine.replace(PREFIX_COMMENT + " ", PREFIX_COMMENT) : headerLine;
            headerLine = headerLine.startsWith(PREFIX_COMMENT) ? headerLine.replace(PREFIX_COMMENT, "") : headerLine;
            String[] headers = headerLine.split(DELIM_COLUMN);
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();

                if (line.startsWith(PREFIX_COMMENT) || line.isBlank()) {
                    continue;
                }
                String[] fields = line.split(DELIM_COLUMN);
                Map<String, String> fieldMap = new HashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    if (i < fields.length) {
                        fieldMap.put(headers[i], fields[i]);
                    }
                }

                Restriction restriction = new Restriction(fieldMap);
                restrictions.add(restriction);
            }
            scanner.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return restrictions;
    }
}
