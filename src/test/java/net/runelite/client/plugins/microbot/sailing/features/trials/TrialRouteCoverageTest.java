package net.runelite.client.plugins.microbot.sailing.features.trials;

import net.runelite.client.plugins.microbot.sailing.features.trials.TrialsScript.MissingRouteAction;
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
    void autoNavigateWithoutTrialStopsWhenTargetRankHasNoRoute() {
        assertEquals(MissingRouteAction.STOP_FOR_TARGET_RANK, TrialsScript.missingRouteAction(null, TrialRanks.Unranked, true));
        assertEquals(MissingRouteAction.STOP_FOR_TARGET_RANK, TrialsScript.missingRouteAction(null, TrialRanks.Unknown, true));
        assertEquals(MissingRouteAction.NONE, TrialsScript.missingRouteAction(null, TrialRanks.Swordfish, true));
    }

    @Test
    void withoutAutoNavigateTargetRankWithoutRouteDoesNotStop() {
        assertEquals(MissingRouteAction.NONE, TrialsScript.missingRouteAction(null, TrialRanks.Unranked, false));
    }

    @Test
    void unrankedTrialStopsOnlyWithAutoNavigate() {
        assertEquals(MissingRouteAction.STOP_FOR_TRIAL, TrialsScript.missingRouteAction(TrialRanks.Unranked, TrialRanks.Swordfish, true));
        assertEquals(MissingRouteAction.NOTIFY_TRIAL, TrialsScript.missingRouteAction(TrialRanks.Unranked, TrialRanks.Swordfish, false));
    }

    @Test
    void rankedOrUnknownTrialNeverStops() {
        assertEquals(MissingRouteAction.NONE, TrialsScript.missingRouteAction(TrialRanks.Shark, TrialRanks.Unranked, true));
        assertEquals(MissingRouteAction.NONE, TrialsScript.missingRouteAction(TrialRanks.Unknown, TrialRanks.Swordfish, true));
    }

    @Test
    void targetRankMessageNamesSettingAndTellsPlayerToTurnPluginOn() {
        String message = TrialsScript.missingTargetRouteMessage(TrialRanks.Unranked);
        assertTrue(message.contains("Target Rank Unranked"));
        assertTrue(message.contains("Swordfish, Shark or Marlin"));
        assertTrue(message.contains("turn the plugin on again"));
    }

    @Test
    void trialMessagesNameTheTrialNotTheSetting() {
        String stop = TrialsScript.missingTrialRouteStopMessage(TrialRanks.Unranked);
        assertTrue(stop.contains("this Unranked trial"));
        assertFalse(stop.contains("Target Rank"));
        String notice = TrialsScript.missingTrialRouteNotice(TrialRanks.Unranked);
        assertTrue(notice.contains("Unranked trials"));
        assertFalse(notice.contains("Target Rank"));
        assertFalse(notice.contains("stopped"));
    }
}
