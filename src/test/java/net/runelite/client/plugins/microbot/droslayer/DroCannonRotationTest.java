package net.runelite.client.plugins.microbot.droslayer;

import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.AnimationID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DroCannonRotationTest {
    private final DroCannonRotation rotation = new DroCannonRotation();
    private final WorldPoint tile = new WorldPoint(3150, 3640, 0);
    private boolean sample(int ammo, int animation, int frame, int cycle, long now) {
        return rotation.observe(409, tile, ammo, animation, frame, cycle, now);
    }

    @Test void retainedAnimationExpiresEvenWithLoadedAmmunition() {
        assertTrue(sample(25, AnimationID.MCANNON_N_FIRE, 2, 10, 100));
        assertTrue(sample(25, AnimationID.MCANNON_N_FIRE, 2, 10, 6100));
        assertFalse(sample(25, AnimationID.MCANNON_N_FIRE, 2, 10, 6101));
        assertFalse(sample(25, AnimationID.MCANNON_N_FIRE, 2, 10, 60000));
    }

    @Test void turningWithoutTargetsStillCountsAsProgress() {
        assertTrue(sample(25, AnimationID.MCANNON_N_TURN, 0, 10, 0));
        assertTrue(sample(25, AnimationID.MCANNON_NE_TURN, 0, 10, 5000));
        assertTrue(sample(25, AnimationID.MCANNON_NE_TURN, 1, 10, 10000));
        assertTrue(sample(25, AnimationID.MCANNON_NE_TURN, 1, 11, 15000));
        assertTrue(sample(25, AnimationID.MCANNON_NE_TURN, 1, 11, 20000));
    }

    @Test void actualShotsRefreshProgressButLoadingAloneDoesNot() {
        assertTrue(sample(15, AnimationID.MCANNON_N_FIRE, 2, 10, 0));
        assertTrue(sample(14, AnimationID.MCANNON_N_FIRE, 2, 10, 5000));
        assertFalse(sample(30, AnimationID.MCANNON_N_FIRE, 2, 10, 12000));
    }

    @Test void newWorldOrDeploymentGetsItsOwnObservationWindow() {
        assertTrue(sample(25, AnimationID.MCANNON_N_FIRE, 2, 10, 0));
        assertFalse(sample(25, AnimationID.MCANNON_N_FIRE, 2, 10, 10000));
        assertTrue(rotation.observe(410, tile, 25, AnimationID.MCANNON_N_FIRE, 2, 10, 10001));
        assertFalse(rotation.observe(410, tile, 25, AnimationID.MCANNON_N_FIRE, 2, 10, 20000));
        assertTrue(rotation.observe(410, tile.dx(10), 25, AnimationID.MCANNON_N_FIRE, 2, 10, 20001));
    }

    @Test void missingInvalidOrEmptyCannonCannotSuppressOrdinaryCombat() {
        assertFalse(rotation.observe(409, null, 25, AnimationID.MCANNON_N_FIRE, 2, 10, 0));
        assertFalse(sample(0, AnimationID.MCANNON_N_FIRE, 2, 10, 0));
        assertFalse(sample(25, -1, 2, 10, 0));
        assertFalse(sample(25, AnimationID.MCANNON_HAMMER_ANIM, 2, 10, 0));
        assertTrue(sample(25, AnimationID.MCANNON_N_FIRE, 2, 10, 1));
        rotation.reset();
        assertTrue(sample(25, AnimationID.MCANNON_N_FIRE, 2, 10, 20000));
    }
}
