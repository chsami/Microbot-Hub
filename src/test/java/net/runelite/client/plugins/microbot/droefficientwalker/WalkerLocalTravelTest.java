package net.runelite.client.plugins.microbot.droefficientwalker;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WalkerLocalTravelTest
{
    @Test void localCourseRestrictionsNeverChangeTheNextClientsSavedTravelPreferences()
    {
        EfficientWalkerConfig saved = new EfficientWalkerConfig() {};
        WalkerLocalTravel owner = new WalkerLocalTravel();
        EfficientWalkerConfig current = owner.effectiveConfig(saved);
        Runnable release = owner.acquire();
        assertFalse(current.useTeleportations());
        assertFalse(current.useItemsFromBank());
        assertTrue(saved.useTeleportations());
        // Closing/crashing a client while its route is active must not persist its override.
        EfficientWalkerConfig restarted = new WalkerLocalTravel().effectiveConfig(saved);
        assertTrue(restarted.useTeleportations());
        release.run();
        assertTrue(current.useTeleportations());
        assertEquals(saved.useItemsFromBank(), current.useItemsFromBank());
        assertEquals(saved.wildernessThreatLogout(), current.wildernessThreatLogout());
    }

    @Test void staleOrRepeatedCleanupCannotReleaseANewerRoute()
    {
        WalkerLocalTravel owner = new WalkerLocalTravel();
        EfficientWalkerConfig current = owner.effectiveConfig(new EfficientWalkerConfig() {});
        Runnable old = owner.acquire();
        owner.clear();
        assertTrue(current.useTeleportations());
        Runnable next = owner.acquire();
        old.run();old.run();
        assertFalse(current.useTeleportations());
        next.run();next.run();
        assertTrue(current.useTeleportations());
    }

    @Test void userPreferenceChangesAndCombatOwnershipSurviveRouteRelease()
    {
        AtomicBoolean teleport = new AtomicBoolean(true);
        EfficientWalkerConfig saved = new EfficientWalkerConfig() {
            @Override public boolean useTeleportations() { return teleport.get(); }
        };
        WildernessThreatOwnership combat = new WildernessThreatOwnership();
        WalkerLocalTravel owner = new WalkerLocalTravel();
        EfficientWalkerConfig current = owner.effectiveConfig(combat.effectiveConfig(saved));
        try (var scope = combat.acquire("Dro-Wilderness-Agility")) {
            scope.suppressNearbyPlayerLogoutWhile(() -> true);
            scope.allowQuietPvpResumeWhile(() -> true);
            Runnable release = owner.acquire();
            assertTrue(current.allowWildernessTravel());
            assertTrue(combat.suppressesNearbyPlayerLogout());
            assertTrue(combat.allowsQuietPvpResume());
            teleport.set(false);release.run();
            assertFalse(current.useTeleportations(), "A user's changed preference remains authoritative");
            teleport.set(true);assertTrue(current.useTeleportations());
        }
        assertFalse(combat.suppressesNearbyPlayerLogout());
    }
}
