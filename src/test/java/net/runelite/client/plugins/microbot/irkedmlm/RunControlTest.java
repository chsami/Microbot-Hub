package net.runelite.client.plugins.microbot.irkedmlm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import java.util.List;
import net.runelite.client.plugins.microbot.irkedmlm.enums.MLMStatus;
import org.junit.jupiter.api.Test;

/** Breaks, stop goals, logging back in, session history and the log setting. */
class RunControlTest {

    @Test
    void aBreakWaitsForPayDirtOnTheFloorAndForTrips() {
        assertTrue(MlmRunControl.safeForBreak(false, false, true));
        assertFalse(MlmRunControl.safeForBreak(true, false, true), "dropping");
        assertFalse(MlmRunControl.safeForBreak(false, true, true), "pile waiting");
        assertFalse(MlmRunControl.safeForBreak(false, false, false), "trip under way");
    }

    @Test
    void goalsAreOffAtZeroAndCountPlayTime() {
        assertNull(MlmRunControl.goalReached(0, 99 * 3_600_000L, 0, 99));
        assertNull(MlmRunControl.goalReached(60, 59 * 60_000L, 0, 50));
        assertEquals("played for 60 minutes", MlmRunControl.goalReached(60, 60 * 60_000L, 0, 50));
        assertEquals("reached 70 Mining", MlmRunControl.goalReached(0, 0, 70, 70));
        assertNull(MlmRunControl.goalReached(0, 0, 70, 69));
    }

    @Test
    void reloginWaitsLikeAPlayerAndBacksOff() {
        assertEquals(5_000, MlmRunControl.reloginWaitMs(0));
        assertEquals(40_000, MlmRunControl.reloginWaitMs(1));
        assertTrue(MlmRunControl.reloginGapMs(3, 0) > MlmRunControl.reloginGapMs(1, 0));
        assertEquals(MlmRunControl.reloginGapMs(8, 0.5), MlmRunControl.reloginGapMs(20, 0.5), "capped");
    }

    @Test
    void aLogoutJustAfterThePlayersOwnInputIsTheirs() {
        assertTrue(MlmRunControl.playerLoggedOut(true, 10_000, 12_000, 5_000));
        assertFalse(MlmRunControl.playerLoggedOut(true, 10_000, 16_000, 5_000), "too long ago");
        assertFalse(MlmRunControl.playerLoggedOut(false, 10_000, 12_000, 5_000), "never in game this run");
        assertFalse(MlmRunControl.playerLoggedOut(true, 0, 12_000, 5_000), "no input seen");
    }

    @Test
    void recoveryStopsAfterTheLastHardReset() {
        assertFalse(MlmRunControl.recoveryExhausted(0));
        assertFalse(MlmRunControl.recoveryExhausted(MlmRunControl.MAX_HARD_RESETS - 1));
        assertTrue(MlmRunControl.recoveryExhausted(MlmRunControl.MAX_HARD_RESETS));
    }

    @Test
    void aRepairBackoffLeavesTimeForTheFrozenHop() {
        assertEquals(120_000, MlmRunControl.repairBackoffMs(false, 900, 0));
        assertEquals(240_000, MlmRunControl.repairBackoffMs(false, 900, 1));
        assertTrue(MlmRunControl.repairBackoffMs(true, 900, 0) > 900_000);
        assertEquals(240_000, MlmRunControl.repairBackoffMs(true, 10, 1));
    }

    @Test
    void aRepairEndingWithTheWaterStillFrozenFailed() {
        assertTrue(MlmRunControl.repairFailed(true, false, false));
        assertTrue(MlmRunControl.repairFailed(false, false, true), "ran out of time");
        assertFalse(MlmRunControl.repairFailed(false, true, true), "somebody else is on it");
        assertFalse(MlmRunControl.repairFailed(false, false, false), "fixed");
    }

    @Test
    void repairRoundsEnd() {
        assertFalse(MlmRunControl.repairRoundsExhausted(MlmRunControl.MAX_REPAIR_ROUNDS - 1));
        assertTrue(MlmRunControl.repairRoundsExhausted(MlmRunControl.MAX_REPAIR_ROUNDS));
    }

    @Test
    void onlyActiveTimeCountsTowardsTheNoProgressLimit() {
        assertEquals(600, MlmRunControl.addNoProgress(0, 1_000, 1_600, true));
        assertEquals(500, MlmRunControl.addNoProgress(500, 1_000, 1_600, false), "waiting for a wheel");
        assertEquals(500, MlmRunControl.addNoProgress(500, 0, 9_000_000, true), "first tick after a logout or pause");
        assertEquals(MlmRunControl.NO_PROGRESS_MAX_STEP_MS,
                MlmRunControl.addNoProgress(0, 1_000, 9_000_000, true), "client was suspended");
        long stuck = 0;
        long now = 1_000;
        while (!MlmRunControl.noProgressTooLong(stuck)) {
            stuck = MlmRunControl.addNoProgress(stuck, now, now + 600, true);
            now += 600;
        }
        assertEquals(20 * 60_000L, now - 1_000, 600);
    }

    @Test
    void payDirtMinedIsProgressWithoutXp() {
        assertTrue(MlmRunControl.minedMore(true, 5, 6), "200M XP");
        assertFalse(MlmRunControl.minedMore(false, 5, 6), "picked up off the floor");
        assertFalse(MlmRunControl.minedMore(true, 26, 0), "deposited");
        assertFalse(MlmRunControl.minedMore(true, -1, 20), "first reading");
    }

    @Test
    void payDirtInHandRoutesBySackFact() {
        assertEquals(MLMStatus.EMPTY_SACK, IrkedMLMScript.payDirtRoute(true, true));
        assertEquals(MLMStatus.DEPOSIT_HOPPER, IrkedMLMScript.payDirtRoute(false, false));
        assertEquals(MLMStatus.RECOVERY, IrkedMLMScript.payDirtRoute(false, true), "unreadable: never dropped or deposited blind");
    }

    @Test
    void aKnownEmptySackNeedsNoTripUnlessThereIsSomethingToBank() {
        assertFalse(IrkedMLMScript.sackTripNeeded(false, 0, true, false), "full of junk: clogged");
        assertTrue(IrkedMLMScript.sackTripNeeded(false, 0, true, true), "gems to bank");
        assertFalse(IrkedMLMScript.sackTripNeeded(false, 0, false, true));
        assertTrue(IrkedMLMScript.sackTripNeeded(true, 0, false, false), "ore to bank");
        assertTrue(IrkedMLMScript.sackTripNeeded(false, SackState.UNKNOWN, false, false), "unknown is not empty");
        assertTrue(IrkedMLMScript.sackTripNeeded(false, 40, false, false));
    }

    @Test
    void historyKeepsTheNewestAndReplacesTheRunningSession() {
        MlmHistory h = new MlmHistory();
        for (int i = 0; i < MlmHistory.KEEP + 5; i++) h.upsert(entry(i, 3_600_000L, 40_000));
        assertEquals(MlmHistory.KEEP, h.sessions.size());
        assertEquals(5, h.sessions.get(0).startMs);
        h.upsert(entry(34, 3_600_000L, 50_000));
        assertEquals(MlmHistory.KEEP, h.sessions.size(), "same start replaces");
        assertEquals(50_000, h.sessions.get(h.sessions.size() - 1).xp);
        assertEquals(MlmHistory.KEEP - 1, h.before(34).size());
    }

    @Test
    void historyLineShowsLastAndBestFromFairSessions() {
        assertNull(MlmHistory.line(List.of()));
        String line = MlmHistory.line(List.of(entry(1, 3_600_000L, 52_000), entry(2, 5 * 60_000L, 20_000)));
        assertEquals("Last 5m · 240.0K/hr · best 52.0K", line);
    }

    @Test
    void repeatedLinesAreCountedNotWritten() {
        MlmLogRepeats r = new MlmLogRepeats();
        assertEquals(0, r.offer("a"));
        assertEquals(0, r.offer("a"));
        assertEquals(0, r.offer("a"));
        assertEquals(-1, r.offer("a"));
        assertEquals(-1, r.offer("a"));
        assertEquals(2, r.offer("b"), "the count comes with the next new line");
        assertEquals(0, r.offer("a"));
    }

    @Test
    void logDetailMapsToTheLoggerLevel() {
        assertEquals(Level.WARN, IrkedMLMPlugin.levelFor(IrkedMLMConfig.LogDetail.QUIET));
        assertEquals(Level.INFO, IrkedMLMPlugin.levelFor(IrkedMLMConfig.LogDetail.NORMAL));
        assertEquals(Level.DEBUG, IrkedMLMPlugin.levelFor(IrkedMLMConfig.LogDetail.DETAILED));
    }

    private static MlmHistory.Entry entry(long start, long activeMs, long xp) {
        MlmHistory.Entry e = new MlmHistory.Entry();
        e.startMs = start;
        e.activeMs = activeMs;
        e.xp = xp;
        return e;
    }
}
