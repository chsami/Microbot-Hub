package net.runelite.client.plugins.microbot.sailing.features.trials;

import net.runelite.client.plugins.microbot.sailing.features.trials.data.TrialLocations;
import net.runelite.client.plugins.microbot.sailing.features.trials.data.TrialRanks;
import net.runelite.client.plugins.microbot.sailing.features.trials.data.TrialRoute;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrialRouteCoverageTest {

    @Test
    void everyRankWithRoutesCoversEveryKnownLocation() {
        for (TrialRanks rank : TrialRanks.values()) {
            if (!TrialRoute.hasRoutes(rank)) {
                continue;
            }
            for (TrialLocations location : TrialLocations.values()) {
                if (location == TrialLocations.Unknown) {
                    continue;
                }
                TrialRoute route = TrialRoute.find(location, rank);
                assertNotNull(route, location + " " + rank);
                assertEquals(location, route.Location);
                assertEquals(rank, route.Rank);
            }
        }
    }

    @Test
    void rankedTargetsHaveRoutes() {
        assertTrue(TrialRoute.hasRoutes(TrialRanks.Swordfish));
        assertTrue(TrialRoute.hasRoutes(TrialRanks.Shark));
        assertTrue(TrialRoute.hasRoutes(TrialRanks.Marlin));
    }

    @Test
    void unrankedAndUnknownHaveNoRoute() {
        assertFalse(TrialRoute.hasRoutes(TrialRanks.Unranked));
        assertFalse(TrialRoute.hasRoutes(TrialRanks.Unknown));
        assertNull(TrialRoute.find(TrialLocations.TemporTantrum, TrialRanks.Unranked));
    }

    @Test
    void missingRouteMessageNamesRankAndSupportedRanks() {
        String message = TrialsScript.missingRouteMessage(TrialRanks.Unranked);
        assertTrue(message.contains("Unranked"));
        assertTrue(message.contains("Swordfish, Shark or Marlin"));
    }
}
