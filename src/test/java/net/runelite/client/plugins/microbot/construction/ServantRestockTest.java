package net.runelite.client.plugins.microbot.construction;

import net.runelite.client.plugins.microbot.construction.ServantRestock.Problem;
import net.runelite.client.plugins.microbot.construction.ServantRestock.Servant;
import net.runelite.client.plugins.microbot.construction.enums.ConstructionState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServantRestockTest {

    @Test
    void stateFollowsHotspotAndPlanks() {
        assertEquals(ConstructionState.Remove, ConstructionState.next(true, true, false));
        assertEquals(ConstructionState.Build, ConstructionState.next(true, false, true));
        assertEquals(ConstructionState.Butler, ConstructionState.next(true, false, false));
        assertEquals(ConstructionState.ReturnToHouse, ConstructionState.next(false, false, true));
        assertEquals(ConstructionState.ReturnToHouse, ConstructionState.next(false, false, false));
    }

    @Test
    void repeatedRestockCyclesNeverStop() {
        ServantRestock restock = new ServantRestock();
        long now = 0;
        int planks = 0;
        for (int cycle = 0; cycle < 10; cycle++) {
            restock.observe(planks, true, now);
            restock.onSent(now);
            now += 600;
            restock.observe(planks, false, now);
            assertTrue(restock.isAway());
            now += 600;
            restock.observe(planks, true, now);
            assertTrue(restock.hasReturned(true, false));
            restock.onReturned();
            planks = 24;
            restock.observe(planks, true, now);
            planks = 0;
            assertEquals(Problem.NONE, restock.problem(Servant.DEMON_BUTLER, 100, false, 50_000, true));
        }
        assertEquals(0, restock.getFailedAttempts());
    }

    @Test
    void servantStillVisibleRightAfterSendingIsNotAReturn() {
        ServantRestock restock = new ServantRestock();
        restock.onSent(0);
        restock.observe(0, true, 600);
        assertFalse(restock.hasReturned(true, true));
        restock.observe(0, false, 1200);
        assertTrue(restock.hasReturned(false, true));
    }

    @Test
    void planksDeliveredWithoutReturnDialogueEndWaiting() {
        ServantRestock restock = new ServantRestock();
        restock.observe(2, true, 0);
        restock.onSent(0);
        restock.onFailedAttempt();
        restock.observe(26, true, 3000);
        assertFalse(restock.isAway());
        assertEquals(0, restock.getFailedAttempts());
    }

    @Test
    void servantThatNeverReturnsTimesOutAndStopsAfterMaxAttempts() {
        ServantRestock restock = new ServantRestock();
        long now = 0;
        for (int attempt = 0; attempt < ServantRestock.MAX_FAILED_ATTEMPTS; attempt++) {
            assertEquals(Problem.NONE, restock.problem(Servant.DEMON_BUTLER, 100, false, 50_000, true));
            restock.onSent(now);
            now += ServantRestock.RETURN_TIMEOUT_MS;
            restock.observe(0, false, now);
            assertFalse(restock.isAway());
        }
        assertEquals(Problem.SERVANT_UNRESPONSIVE, restock.problem(Servant.DEMON_BUTLER, 100, false, 50_000, true));
    }

    @Test
    void missingServantStopsAfterFailedCalls() {
        ServantRestock restock = new ServantRestock();
        for (int attempt = 0; attempt < ServantRestock.MAX_FAILED_ATTEMPTS; attempt++) {
            assertEquals(Problem.NONE, restock.problem(Servant.NONE, 100, false, 50_000, true));
            restock.onFailedAttempt();
        }
        assertEquals(Problem.NO_SERVANT, restock.problem(Servant.NONE, 100, false, 50_000, true));
    }

    @Test
    void outOfNotedPlanksStopsAfterFailedAttempts() {
        ServantRestock restock = new ServantRestock();
        for (int attempt = 0; attempt < ServantRestock.MAX_FAILED_ATTEMPTS; attempt++) {
            restock.onFailedAttempt();
        }
        assertEquals(Problem.NO_NOTED_PLANKS, restock.problem(Servant.DEMON_BUTLER, 0, false, 50_000, true));
    }

    @Test
    void failuresDoNotStopWhileInventoryStillHasPlanksToBuild() {
        ServantRestock restock = new ServantRestock();
        for (int attempt = 0; attempt < ServantRestock.MAX_FAILED_ATTEMPTS; attempt++) {
            restock.onFailedAttempt();
        }
        assertFalse(restock.canAttempt());
        assertEquals(Problem.NONE, restock.problem(Servant.NONE, 0, false, 0, false));
    }

    @Test
    void unsupportedServantStopsImmediately() {
        ServantRestock restock = new ServantRestock();
        assertEquals(Problem.UNSUPPORTED_SERVANT, restock.problem(Servant.OTHER, 100, false, 50_000, false));
    }

    @Test
    void wageDueWithoutCoinsStopsImmediately() {
        ServantRestock restock = new ServantRestock();
        assertEquals(Problem.NOT_ENOUGH_COINS, restock.problem(Servant.DEMON_BUTLER, 100, true, 9_999, false));
        assertEquals(Problem.NONE, restock.problem(Servant.DEMON_BUTLER, 100, true, 10_000, false));
    }

    @Test
    void everyProblemHasAMessage() {
        for (Problem problem : Problem.values()) {
            assertEquals(problem == Problem.NONE, problem.getMessage().isEmpty());
        }
    }

    @Test
    void resetClearsAttemptsAndWaiting() {
        ServantRestock restock = new ServantRestock();
        restock.onSent(0);
        restock.onFailedAttempt();
        restock.reset();
        assertFalse(restock.isAway());
        assertEquals(0, restock.getFailedAttempts());
    }
}
