package net.runelite.client.plugins.microbot.droefficientwalker;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WildernessPresenceScopeTest {
    @Test void allPlayerPresenceIsExemptOnlyWhileLiveCoursePredicateConfirmsEntry() {
        var ownership=new WildernessThreatOwnership();var inCourse=new AtomicBoolean(false);
        var saved=new EfficientWalkerConfig(){};
        try(var lease=ownership.acquire("Dro-Wilderness-Agility")) {
            lease.suppressNearbyPlayerLogoutWhile(inCourse::get);
            assertFalse(ownership.suppressesNearbyPlayerLogout());
            inCourse.set(true);assertTrue(ownership.suppressesNearbyPlayerLogout());
            assertFalse(ownership.isCombatActive(),"Agility must not take over Slayer's combat escape policy");
            assertTrue(saved.wildernessThreatLogout(),"No saved preference is rewritten");
            inCourse.set(false);assertFalse(ownership.suppressesNearbyPlayerLogout());
            inCourse.set(true);
        }
        assertFalse(ownership.suppressesNearbyPlayerLogout());assertFalse(ownership.isOwned());
    }
    @Test void stoppedOrRestartedWalkerCannotRetainStaleSuppression() {
        var ownership=new WildernessThreatOwnership();var old=ownership.acquire("Dro-Wilderness-Agility");
        old.suppressNearbyPlayerLogoutWhile(()->true);assertTrue(ownership.suppressesNearbyPlayerLogout());
        ownership.clear();assertFalse(ownership.suppressesNearbyPlayerLogout());
        try(var next=ownership.acquire("DroSlayer-Krystilia")) {
            next.setCombatActive(true);old.close();
            assertFalse(ownership.suppressesNearbyPlayerLogout());assertTrue(ownership.isCombatActive());assertTrue(ownership.usesSouthEscape());
        }
    }
}
