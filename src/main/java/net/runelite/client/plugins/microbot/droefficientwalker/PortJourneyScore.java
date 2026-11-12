package net.runelite.client.plugins.microbot.droefficientwalker;

/** Bounded-port ranking only: geometric sea distance does not establish a navigable sea route. */
final class PortJourneyScore {
    private PortJourneyScore() { }

    static double score(EfficientSailingConfig.PortMode mode, double landCost, double seaLowerBound,
                        double seaTilesPerTick, double relocationSeconds) {
        if (mode == EfficientSailingConfig.PortMode.DESTINATION_NEAREST) return seaLowerBound;
        if (mode == EfficientSailingConfig.PortMode.TOTAL_JOURNEY) {
            // Efficient Walker RouteTimeCost explicitly uses half ticks (300 ms).
            // Sea speed is in tiles per full game tick (600 ms).
            return landCost * .3 + seaLowerBound * .6 / Math.max(1.0, seaTilesPerTick)
                    + Math.max(0, relocationSeconds);
        }
        return landCost; // Preserve the user's earlier AUTO nearest-by-Walker-cost behavior.
    }
}
