package net.runelite.client.plugins.microbot.irkedmlm.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.irkedmlm.enums.MLMMiningSpot;
import org.junit.jupiter.api.Test;

/** Unknown floors, walls inside the reachable area and sack clicks that do nothing. */
class SessionGuardsTest {

    @Test
    void anUnknownFloorIsNeitherFloor() {
        assertTrue(Session.onFloor(Session.Floor.UPPER, true));
        assertTrue(Session.onFloor(Session.Floor.LOWER, false));
        assertFalse(Session.onFloor(Session.Floor.LOWER, true));
        assertFalse(Session.onFloor(Session.Floor.UNKNOWN, true));
        assertFalse(Session.onFloor(Session.Floor.UNKNOWN, false), "unknown must not read as the lower floor");
    }

    @Test
    void theAreaSearchDoesNotStepThroughAWall() {
        WorldPoint from = new WorldPoint(3731, 5659, 0);
        WorldPoint open = new WorldPoint(3732, 5659, 0);
        WorldPoint walled = new WorldPoint(3730, 5659, 0);
        Set<WorldPoint> live = Set.of(from, open, walled);
        assertEquals(live, MiningSession.confineToSpot(MLMMiningSpot.WEST_LOWER, from, live, (a, b) -> true));
        assertEquals(Set.of(from, open), MiningSession.confineToSpot(MLMMiningSpot.WEST_LOWER, from, live,
                (a, b) -> !b.equals(walled)));
    }

    @Test
    void progressRecordedWhileMovingHoldsOffASecondLadderClick() {
        // Clicked at 0, walked until 8000, now standing a moment before the climb animation.
        assertTrue(Session.climbStillPending(1, 8_000, 8_600, 1_900, false, false));
        assertFalse(Session.climbStillPending(1, 1, 8_600, 1_900, false, false), "what an unrecorded walk used to allow");
    }

    @Test
    void theReachableTilesAreOnlyReusedForTheSameSpotAndSide() {
        WorldPoint in = new WorldPoint(3731, 5659, 0);
        WorldPoint in2 = new WorldPoint(3732, 5660, 0);
        WorldPoint out = new WorldPoint(3738, 5659, 0);
        MLMMiningSpot spot = MLMMiningSpot.WEST_LOWER;
        assertTrue(MiningSession.reachableCacheUsable(spot, in, 1_000, spot, in2));
        assertFalse(MiningSession.reachableCacheUsable(spot, in, 3_000, spot, in2), "too old, or a rockfall changed");
        assertFalse(MiningSession.reachableCacheUsable(MLMMiningSpot.WEST_MID, in, 1_000, spot, in2), "another spot");
        assertFalse(MiningSession.reachableCacheUsable(spot, out, 1_000, spot, new WorldPoint(3735, 5659, 0)),
                "searched from outside, with the approach margin");
        assertFalse(MiningSession.reachableCacheUsable(spot, null, 1_000, spot, in));
        assertFalse(MiningSession.reachableCacheUsable(spot, in, 1_000, spot, new WorldPoint(3731, 5666, 0)), "too far");
    }

    @Test
    void gemDropsAreCappedPerTickAndSkipEverythingElse() {
        int ruby = ItemID.UNCUT_RUBY;
        assertEquals(List.of(ruby, ruby, ruby, ruby), MiningSession.gemsToDrop(
                Stream.of(ItemID.PAYDIRT, ruby, ruby, ruby, ItemID.COAL, ruby, ruby, ruby), i -> i));
        assertEquals(4, MiningSession.MAX_GEM_DROPS_PER_TICK);
        assertTrue(MiningSession.gemsToDrop(Stream.of(ItemID.PAYDIRT, ItemID.RUNE_PICKAXE), i -> i).isEmpty());
    }

    @Test
    void aVeinThatWillNotClickIsGivenUpOnButNotDuringAPause() {
        MiningSession m = new MiningSession(null, null);
        m.subStateEnteredMs = System.currentTimeMillis() - MiningSession.CLICK_GIVE_UP_MS - 1_000;
        assertTrue(m.isStalled(MiningSession.CLICK_GIVE_UP_MS));
        m.excludePause(5_000);
        assertFalse(m.isStalled(MiningSession.CLICK_GIVE_UP_MS));
    }

    @Test
    void aSackThatStaysUnreadableFailsTheTrip() {
        SackSession s = new SackSession(null, null);
        assertFalse(s.unknownTooLong(false, 1_000));
        assertFalse(s.unknownTooLong(false, 1_000 + SackSession.UNKNOWN_LIMIT_MS));
        assertTrue(s.unknownTooLong(false, 1_001 + SackSession.UNKNOWN_LIMIT_MS));
        assertFalse(s.unknownTooLong(true, 40_000));
        assertFalse(s.unknownTooLong(false, 50_000), "counted afresh after a reading");
    }

    @Test
    void onlyAnEmptierSackRestartsTheWithdrawLimit() {
        SackSession s = new SackSession(null, null);
        s.noteSackCount(100);
        s.withdrawsWithoutProgress = SackSession.MAX_WITHDRAWS_WITHOUT_PROGRESS;
        s.noteSackCount(100);
        s.noteSackCount(105);
        assertEquals(SackSession.MAX_WITHDRAWS_WITHOUT_PROGRESS, s.withdrawsWithoutProgress, "filling is not progress");
        assertFalse(s.consumeProgress());
        s.noteSackCount(80);
        assertEquals(0, s.withdrawsWithoutProgress);
        assertTrue(s.consumeProgress());
        assertFalse(s.consumeProgress(), "reported once");
    }
}
