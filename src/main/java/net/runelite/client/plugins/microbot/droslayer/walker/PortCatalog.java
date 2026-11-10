package net.runelite.client.plugins.microbot.droslayer.walker;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.api.boat.data.PortLocation;
import net.runelite.client.plugins.microbot.droslayer.walker.sailing.core.SeaPoint;

import java.util.*;

/** Native Sailing port coordinates, levels and gangplank IDs; never hardcoded as land tiles. */
final class PortCatalog {
    static final class Port {
        final String key, name;
        final int level, gangplankId;
        final SeaPoint sea;
        Port(PortLocation source) {
            key = source.name(); name = source.getName();
            level = source.getSailingLevelRequired() == null ? Integer.MAX_VALUE : source.getSailingLevelRequired();
            gangplankId = source.getGangplankObject();
            sea = new SeaPoint(source.getNavigationLocation().getX(), source.getNavigationLocation().getY());
        }
        @Override public String toString() { return name + " [" + key + "]"; }
    }
    static final class Candidate {
        final Port port;
        final WorldPoint approach;
        final double landCost, seaEstimate, total;
        final String chain;
        final boolean knownBoat;
        final EfficientSailingConfig.PortMode mode;
        Candidate(Port port, WorldPoint approach, double landCost, double seaEstimate, String chain, boolean knownBoat) {
            this(port, approach, landCost, seaEstimate, chain, knownBoat, EfficientSailingConfig.PortMode.AUTO, 3, 0);
        }
        Candidate(Port port, WorldPoint approach, double landCost, double seaEstimate, String chain, boolean knownBoat,
                  EfficientSailingConfig.PortMode mode, double seaTilesPerTick, double relocationSeconds) {
            this.port = port; this.approach = approach; this.landCost = landCost; this.seaEstimate = seaEstimate;
            this.chain = chain; this.knownBoat = knownBoat; this.mode = mode;
            total = PortJourneyScore.score(mode, landCost, seaEstimate, seaTilesPerTick, knownBoat ? 0 : relocationSeconds);
        }
        @Override public String toString() {
            return port.key + " land=" + Math.round(landCost) + " seaLowerBound=" + Math.round(seaEstimate)
                    + " rank=" + Math.round(total) + " unit="
                    + (mode == EfficientSailingConfig.PortMode.TOTAL_JOURNEY ? "estimated-seconds"
                    : mode == EfficientSailingConfig.PortMode.DESTINATION_NEAREST ? "sea-lower-bound-tiles" : "walker-half-ticks")
                    + " via " + chain;
        }
    }
    // Conservative initial NPC recovery registry. Boarding and tablet summoning do NOT
    // require a noticeboard or a shipwright. More recovery ports can be added independently.
    static boolean supportsNpcRecovery(String key) {
        return key.equals("PORT_KHAZARD") || key.equals("PORT_SARIM") || key.equals("PANDEMONIUM");
    }
    static String forcedPort(EfficientSailingConfig config) {
        return ""; // Retired selectors remain only for saved-config compatibility.
    }
    static EfficientSailingConfig.PortMode selectionMode(EfficientSailingConfig config) {
        return EfficientSailingConfig.PortMode.DESTINATION_NEAREST;
    }
    static boolean excluded(EfficientSailingConfig config, String key) {
        for (String token : config.excludedPorts().split(",")) if (normalize(token).equals(key)) return true;
        return false;
    }
    static Port byKey(String key) {
        for (PortLocation location : PortLocation.values()) if (location.name().equals(normalize(key))) return new Port(location);
        return null;
    }
    /** Observed Pest teleport landing, before approaching the loaded native Sailing dock.
     * The Squire's return-ship tile at 2659,2676 is not a Sailing arrival target. */
    static WorldPoint landGateway(String key) {
        return normalize(key).equals("VOID_KNIGHTS_OUTPOST") ? new WorldPoint(2658,2659,0) : null;
    }
    static List<Port> shortlist(EfficientSailingConfig config, int level, SeaPoint origin) {
        return shortlist(config, level, origin, false);
    }
    static List<Port> shortlist(EfficientSailingConfig config, int level, SeaPoint origin, boolean recoveryOnly) {
        return shortlist(config, level, origin, null, recoveryOnly);
    }
    static List<Port> shortlist(EfficientSailingConfig config, int level, SeaPoint origin, SeaPoint destination, boolean recoveryOnly) {
        return shortlist(config, level, origin, destination, recoveryOnly, false);
    }
    static List<Port> shortlist(EfficientSailingConfig config, int level, SeaPoint origin, SeaPoint destination,
                                boolean recoveryOnly, boolean boatAbsent) {
        List<Port> ports = new ArrayList<>();
        for (PortLocation location : PortLocation.values()) {
            Port p = new Port(location);
            if (p.level > level || p.gangplankId <= 0 || excluded(config, p.key)) continue;
            if (recoveryOnly && !supportsNpcRecovery(p.key)) continue;
            ports.add(p);
        }
        ports.sort(Comparator.comparingDouble(p -> shortlistScore(config, p, origin, destination)));
        // Check ports in destination-distance order until Walker verifies a reachable one.
        return ports;
    }
    private static double shortlistScore(EfficientSailingConfig config, Port port, SeaPoint origin, SeaPoint destination) {
        if (destination == null) return port.sea.distance(origin);
        return port.sea.distance(destination);
    }
    static Map<String, WorldPoint> overrides(String text) {
        Map<String, WorldPoint> result = new HashMap<>();
        if (text == null || text.trim().isEmpty()) return result;
        for (String line : text.split("[;\\r\\n]+")) {
            String[] f = line.trim().split(",");
            if (f.length != 3) throw new IllegalArgumentException("Invalid port override: " + line);
            try {
                int x = Integer.parseInt(f[1].trim()), y = Integer.parseInt(f[2].trim());
                if (x < 1 || y < 1 || x > 16383 || y > 16383) throw new IllegalArgumentException("Coordinate out of range");
                String key = normalize(f[0]); PortLocation.valueOf(key);
                result.put(key, new WorldPoint(x, y, 0));
            } catch (RuntimeException ex) { throw new IllegalArgumentException("Invalid port override: " + line, ex); }
        }
        return result;
    }
    static String normalize(String text) {
        String key = simple(text);
        if (key.equals("PORT_CAZAR") || key.equals("CAZAR") || key.equals("KHAZARD")) return "PORT_KHAZARD";
        if (key.equals("THE_PANDEMONIUM")) return "PANDEMONIUM";
        if (key.equals("THE_SUMMER_SHORE")) return "SUMMER_SHORE";
        if (key.equals("PEST_CONTROL") || key.equals("PEST_CONTROL_ISLAND") || key.equals("VOID_KNIGHT_OUTPOST") || key.equals("VOID_KNIGHTS_OUTPOST")) return "VOID_KNIGHTS_OUTPOST";
        if (key.equals("JATIZSO")) return "JATIZO"; // Native Microbot enum spelling.
        for (PortLocation port : PortLocation.values()) {
            if (key.equals(simple(port.name())) || key.equals(simple(port.getName()))) return port.name();
        }
        return key;
    }
    private static String simple(String text) {
        return text == null ? "" : text.trim().toUpperCase(Locale.ROOT)
                .replace("'", "").replace("’", "").replaceAll("[\\s_-]+", "_");
    }
}
