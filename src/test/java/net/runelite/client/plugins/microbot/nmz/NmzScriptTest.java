package net.runelite.client.plugins.microbot.nmz;

import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NmzScriptTest {

    @Test
    void unknownPlayerLocationIsNotOutside() {
        assertFalse(NmzScript.isNearLobby(null));
    }

    @Test
    void locationNearLobbyIsOutside() {
        assertTrue(NmzScript.isNearLobby(new WorldPoint(2609, 3114, 0)));
    }

    @Test
    void locationInsideDreamIsNotOutside() {
        assertFalse(NmzScript.isNearLobby(new WorldPoint(2273, 4694, 0)));
    }

    @Test
    void overloadIsNotPendingWhenConfiguredButNoneInInventory() {
        assertFalse(NmzScript.shouldUseOverload(99, 99, 8, false));
    }

    @Test
    void overloadIsPendingWhenConfiguredCarriedAndNotActive() {
        assertTrue(NmzScript.shouldUseOverload(99, 99, 8, true));
    }

    @Test
    void overloadIsNotPendingWhileActive() {
        assertFalse(NmzScript.shouldUseOverload(120, 99, 8, true));
    }

    @Test
    void overloadIsNotPendingWhenNoneConfigured() {
        assertFalse(NmzScript.shouldUseOverload(99, 99, 0, true));
    }

    @Test
    void selfHarmsWithoutOverloadsInInventoryEvenWhenOverloadsAreConfigured() {
        boolean useOverload = NmzScript.shouldUseOverload(99, 99, 8, false);
        assertTrue(NmzScript.shouldSelfHarm(5, 2, useOverload));
    }

    @Test
    void doesNotSelfHarmWhileOverloadIsPending() {
        boolean useOverload = NmzScript.shouldUseOverload(99, 99, 8, true);
        assertFalse(NmzScript.shouldSelfHarm(75, 2, useOverload));
    }

    @Test
    void selfHarmsWhileOverloadIsActive() {
        boolean useOverload = NmzScript.shouldUseOverload(120, 99, 8, true);
        assertTrue(NmzScript.shouldSelfHarm(51, 1, useOverload));
    }

    @Test
    void doesNotSelfHarmBelowHealthThreshold() {
        assertFalse(NmzScript.shouldSelfHarm(1, 2, false));
    }

    @Test
    void unknownPlayerLocationIsNotAwayFromCenter() {
        assertFalse(NmzScript.isAwayFromCenter(new WorldPoint(2273, 4694, 0), null));
    }

    @Test
    void playerFarFromCenterIsAwayFromCenter() {
        assertTrue(NmzScript.isAwayFromCenter(new WorldPoint(2273, 4694, 0), new WorldPoint(2290, 4694, 0)));
    }

    @Test
    void playerNearCenterIsNotAwayFromCenter() {
        assertFalse(NmzScript.isAwayFromCenter(new WorldPoint(2273, 4694, 0), new WorldPoint(2275, 4694, 0)));
    }
}
