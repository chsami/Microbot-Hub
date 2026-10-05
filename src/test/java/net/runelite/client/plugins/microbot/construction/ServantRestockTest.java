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
            restock.observe(planks, true, now, true);
            restock.onSent(now);
            now += 600;
            restock.observe(planks, false, now, true);
            assertTrue(restock.isAway());
            now += 600;
            restock.observe(planks, true, now, true);
            assertTrue(restock.hasReturned(true, false));
            restock.onReturned();
            planks = 24;
            restock.observe(planks, true, now, true);
            planks = 0;
            assertEquals(Problem.NONE, restock.problem(Servant.DEMON_BUTLER, 100, false, 50_000, true));
        }
        assertEquals(0, restock.getFailedAttempts());
    }

    @Test
    void servantStillVisibleRightAfterSendingIsNotAReturn() {
        ServantRestock restock = new ServantRestock();
        restock.onSent(0);
        restock.observe(0, true, 600, true);
        assertFalse(restock.hasReturned(true, true));
        restock.observe(0, false, 1200, true);
        assertTrue(restock.hasReturned(false, true));
    }

    @Test
    void planksDeliveredWithoutReturnDialogueEndWaiting() {
        ServantRestock restock = new ServantRestock();
        restock.observe(2, true, 0, true);
        restock.onSent(0);
        restock.onFailedAttempt();
        restock.observe(26, true, 3000, true);
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
            restock.observe(0, false, now, true);
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
    void buildStateFailuresDoNotStopButlerStateBeforeItTries() {
        ServantRestock restock = new ServantRestock();
        long now = 0;
        restock.observe(20, true, now, false);
        restock.onFailedAttempt();
        restock.onSent(now);
        now += ServantRestock.RETURN_TIMEOUT_MS;
        restock.observe(10, false, now, false);
        restock.onFailedAttempt();
        assertFalse(restock.canAttempt());

        restock.observe(4, true, now + 600, true);
        assertTrue(restock.canAttempt());
        assertEquals(Problem.NONE, restock.problem(Servant.DEMON_BUTLER, 100, false, 50_000, true));

        for (int attempt = 0; attempt < ServantRestock.MAX_FAILED_ATTEMPTS; attempt++) {
            restock.onFailedAttempt();
            restock.observe(4, true, now + 1200 + attempt * 600L, true);
        }
        assertEquals(Problem.SERVANT_UNRESPONSIVE, restock.problem(Servant.DEMON_BUTLER, 100, false, 50_000, true));
    }

    @Test
    void unsupportedServantStopsOnlyOncePlanksAreNeeded() {
        ServantRestock restock = new ServantRestock();
        assertEquals(Problem.NONE, restock.problem(Servant.OTHER, 100, false, 50_000, false));
        assertEquals(Problem.UNSUPPORTED_SERVANT, restock.problem(Servant.OTHER, 100, false, 50_000, true));
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
