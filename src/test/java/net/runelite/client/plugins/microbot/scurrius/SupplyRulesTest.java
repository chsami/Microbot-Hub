package net.runelite.client.plugins.microbot.scurrius;

import net.runelite.client.plugins.microbot.scurrius.enums.State;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupplyRulesTest {
    private static State next(boolean inRoom, boolean boss, boolean los, boolean loot, int food, boolean potions, boolean tab,
                              int prayer, int maxPrayer, boolean full) {
        return SupplyRules.nextState(inRoom, boss, los, loot, food, potions, tab, prayer, maxPrayer, full);
    }

    @Test
    void outOfFoodMidFightLeavesAtOnceByTabOrOnFoot() {
        assertEquals(State.TELEPORT_AWAY, next(true, true, true, false, 0, true, true, 90, 99, false));
        assertEquals(State.BANKING, next(true, true, true, false, 0, true, false, 90, 99, false));
    }

    @Test
    void theReportedStuckCaseGoesBanking() {
        // outside the room, 0 food, 8 potions, 1 tab, boss in view: used to sit in FIGHTING doing nothing
        assertEquals(State.BANKING, next(false, true, true, true, 0, true, true, 90, 99, false));
        assertEquals(State.BANKING, next(false, false, false, false, 0, true, true, 90, 99, false));
    }

    @Test
    void outOfPotionsKeepsFightingOnlyWhilePrayerIsHighEnough() {
        assertEquals(State.FIGHTING, next(true, true, true, false, 5, false, true, 30, 99, false)); // 30% >= 25%
        assertEquals(State.TELEPORT_AWAY, next(true, true, true, false, 5, false, true, 20, 99, false));
        assertEquals(State.BANKING, next(true, true, true, false, 5, false, false, 20, 99, false));
    }

    @Test
    void betweenKillsLootComesFirstThenAnyShortageLeaves() {
        assertEquals(State.WAITING_FOR_BOSS, next(true, false, false, true, 5, false, true, 50, 99, false));
        assertEquals(State.TELEPORT_AWAY, next(true, false, false, false, 5, false, true, 50, 99, false));
        assertEquals(State.TELEPORT_AWAY, next(true, false, false, false, 0, true, true, 50, 99, false));
        assertEquals(State.WAITING_FOR_BOSS, next(true, false, false, false, 5, true, true, 50, 99, false));
    }

    @Test
    void outsideTheRoomBelowTheConfiguredAmountsAlwaysBanksAndFullWalksIn() {
        assertEquals(State.BANKING, next(false, false, false, false, 7, true, true, 99, 99, false));
        assertEquals(State.WALK_TO_BOSS, next(false, false, false, false, 20, true, true, 99, 99, true));
        assertEquals(State.FIGHTING, next(false, true, true, false, 20, true, true, 99, 99, true));
        assertEquals(State.WALK_TO_BOSS, next(false, true, false, false, 20, true, true, 99, 99, true)); // seen but not reachable yet
    }

    @Test
    void inTheRoomWithSuppliesFightsOrWaits() {
        assertEquals(State.FIGHTING, next(true, true, false, false, 3, true, false, 99, 99, false));
        assertEquals(State.WAITING_FOR_BOSS, next(true, false, false, false, 3, true, false, 99, 99, false));
    }

    @Test
    void noCombinationLeavesTheScriptIdle() {
        boolean[] both = {false, true};
        int[] foods = {0, 1, 2, 20};
        int[] prayers = {0, 24, 25, 99};
        int checked = 0;
        for (boolean inRoom : both) for (boolean boss : both) for (boolean los : both) for (boolean loot : both)
            for (int food : foods) for (boolean potions : both) for (boolean tab : both)
                for (int prayer : prayers) for (boolean full : both) {
                    State state = next(inRoom, boss, los, loot, food, potions, tab, prayer, 99, full);
                    assertNotNull(state);
                    // every result is a state with an action; and the leave states are reachable only by a way out
                    if (state == State.TELEPORT_AWAY) {
                        assertTrue(tab && inRoom, "teleport needs a tab and the room");
                    }
                    if (food == 0 && inRoom) {
                        assertTrue(state == State.TELEPORT_AWAY || state == State.BANKING || (state == State.WAITING_FOR_BOSS && loot && !boss),
                                "out of food in the room must leave: " + state);
                    }
                    if (!inRoom && !full) {
                        assertEquals(State.BANKING, state);
                    }
                    checked++;
                }
        assertEquals(2 * 2 * 2 * 2 * 4 * 2 * 2 * 4 * 2, checked);
    }

    @Test
    void zeroMaxPrayerNeverBlocksTheFight() {
        assertTrue(SupplyRules.prayerPointsEnough(0, 0));
        assertTrue(SupplyRules.prayerPointsEnough(25, 100));
        assertTrue(!SupplyRules.prayerPointsEnough(24, 100));
    }
}
