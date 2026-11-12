package net.runelite.client.plugins.microbot.tormenteddemons;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.tormenteddemons.TormentedDemonBombDodge.Outcome;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TormentedDemonBombDodgeTest {

    private static final int DEFAULT_DELAY = 800;
    private static final WorldPoint BOMB = new WorldPoint(4070, 4430, 0);
    private static final WorldPoint SAFE = new WorldPoint(4071, 4430, 0);

    @Test
    void noBombIsIdle() {
        TormentedDemonBombDodge dodge = new TormentedDemonBombDodge();

        assertEquals(Outcome.IDLE, dodge.onTick(BOMB, DEFAULT_DELAY));
        assertFalse(dodge.isExposed());
    }

    @Test
    void retriesEveryTickWhileBoundAndReportsDodgeOnlyAfterLeaving() {
        TormentedDemonBombDodge dodge = new TormentedDemonBombDodge();
        dodge.addBomb(BOMB);

        assertEquals(Outcome.WAITING, dodge.onTick(BOMB, DEFAULT_DELAY));
        assertEquals(Outcome.STEP, dodge.onTick(BOMB, DEFAULT_DELAY));
        assertEquals(Outcome.STEP, dodge.onTick(BOMB, DEFAULT_DELAY));
        assertTrue(dodge.isExposed());
        assertEquals(Outcome.DODGED, dodge.onTick(SAFE, DEFAULT_DELAY));
        assertFalse(dodge.isExposed());
        assertEquals(Outcome.IDLE, dodge.onTick(SAFE, DEFAULT_DELAY));
    }

    @Test
    void stayingOnTheTileUntilTheBombLandsIsAHit() {
        TormentedDemonBombDodge dodge = new TormentedDemonBombDodge();
        dodge.addBomb(BOMB);

        assertEquals(Outcome.WAITING, dodge.onTick(BOMB, DEFAULT_DELAY));
        assertEquals(Outcome.STEP, dodge.onTick(BOMB, DEFAULT_DELAY));
        assertEquals(Outcome.STEP, dodge.onTick(BOMB, DEFAULT_DELAY));
        assertEquals(Outcome.STEP, dodge.onTick(BOMB, DEFAULT_DELAY));
        assertEquals(Outcome.HIT, dodge.onTick(BOMB, DEFAULT_DELAY));
        assertFalse(dodge.isExposed());
        assertEquals(Outcome.IDLE, dodge.onTick(BOMB, DEFAULT_DELAY));
    }

    @Test
    void zeroDelayStepsOnTheFirstTick() {
        TormentedDemonBombDodge dodge = new TormentedDemonBombDodge();
        dodge.addBomb(BOMB);

        assertEquals(Outcome.STEP, dodge.onTick(BOMB, 0));
    }

    @Test
    void bombElsewhereNeverAsksForAStep() {
        TormentedDemonBombDodge dodge = new TormentedDemonBombDodge();
        dodge.addBomb(BOMB);

        for (int tick = 0; tick <= TormentedDemonBombDodge.BOMB_TICKS + 1; tick++) {
            assertEquals(Outcome.IDLE, dodge.onTick(SAFE, DEFAULT_DELAY));
        }
    }

    @Test
    void walkingBackOntoTheBombAsksForAnotherStep() {
        TormentedDemonBombDodge dodge = new TormentedDemonBombDodge();
        dodge.addBomb(BOMB);

        assertEquals(Outcome.WAITING, dodge.onTick(BOMB, DEFAULT_DELAY));
        assertEquals(Outcome.DODGED, dodge.onTick(SAFE, DEFAULT_DELAY));
        assertEquals(Outcome.STEP, dodge.onTick(BOMB, DEFAULT_DELAY));
    }

    @Test
    void resetClearsPendingBombs() {
        TormentedDemonBombDodge dodge = new TormentedDemonBombDodge();
        dodge.addBomb(BOMB);
        dodge.onTick(BOMB, 0);

        dodge.reset();

        assertFalse(dodge.isExposed());
        assertEquals(Outcome.IDLE, dodge.onTick(BOMB, 0));
    }

    @Test
    void safeTileIsTheNearestReachableTileThatIsNotABomb() {
        TormentedDemonBombDodge dodge = new TormentedDemonBombDodge();
        WorldPoint secondBomb = new WorldPoint(4069, 4430, 0);
        WorldPoint farTile = new WorldPoint(4072, 4430, 0);
        dodge.addBomb(BOMB);
        dodge.addBomb(secondBomb);

        Map<WorldPoint, Integer> reachable = new HashMap<>();
        reachable.put(BOMB, 0);
        reachable.put(secondBomb, 1);
        reachable.put(SAFE, 1);
        reachable.put(farTile, 2);

        assertEquals(SAFE, dodge.pickSafeTile(BOMB, reachable));
    }

    @Test
    void noSafeTileWhenEveryReachableTileIsABomb() {
        TormentedDemonBombDodge dodge = new TormentedDemonBombDodge();
        dodge.addBomb(BOMB);
        dodge.addBomb(SAFE);

        Map<WorldPoint, Integer> reachable = new HashMap<>();
        reachable.put(BOMB, 0);
        reachable.put(SAFE, 1);

        assertNull(dodge.pickSafeTile(BOMB, reachable));
        assertNull(dodge.pickSafeTile(null, reachable));
    }
}
