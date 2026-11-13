package net.runelite.client.plugins.microbot.barrows;

import net.runelite.client.plugins.microbot.util.walker.WalkerState;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BarrowsTunnelRulesTest
{
    @Test
    void failedChestWalkClearsTheRouteForAFreshPlan()
    {
        assertTrue(BarrowsTunnelRules.shouldReplanChestWalk(WalkerState.EXIT));
        assertTrue(BarrowsTunnelRules.shouldReplanChestWalk(WalkerState.UNREACHABLE));
        assertTrue(BarrowsTunnelRules.shouldReplanChestWalk(null));
    }

    @Test
    void arrivedOrMovingChestWalkKeepsTheRoute()
    {
        assertFalse(BarrowsTunnelRules.shouldReplanChestWalk(WalkerState.ARRIVED));
        assertFalse(BarrowsTunnelRules.shouldReplanChestWalk(WalkerState.MOVING));
    }

    @Test
    void tunnelKillsAreOnlyWantedWhileRewardPotentialIsNeededAndTheRoomCapIsOpen()
    {
        assertFalse(BarrowsTunnelRules.wantsAnotherTunnelKill(false, 0, 2));
        assertFalse(BarrowsTunnelRules.wantsAnotherTunnelKill(true, 2, 2));
        assertTrue(BarrowsTunnelRules.wantsAnotherTunnelKill(true, 1, 2));
    }

    @Test
    void onlyItemsWithAnEatOptionPauseTheWalkForFood()
    {
        assertTrue(BarrowsTunnelRules.hasEatOption(new String[]{"Eat", null, "Drop"}));
        assertFalse(BarrowsTunnelRules.hasEatOption(new String[]{"Drink", null, "Drop"}));
        assertFalse(BarrowsTunnelRules.hasEatOption(null));
    }

    @Test
    void refusedTargetIsNotAnAggressorEvenWhileThePlayerStillFacesIt()
    {
        Map<String, Integer> distances = new HashMap<>();
        distances.put("skeleton", 1);
        distances.put("crypt rat", 3);
        List<String> aggressors = Arrays.asList("skeleton", "crypt rat");

        assertEquals("skeleton", BarrowsTunnelRules.pickCombatLockAggressor(
                aggressors, "crypt rat"::equals, "crypt rat"::equals, distances::get, 8));
    }

    @Test
    void gameAnswersThatRefuseAnAttackAreRecognised()
    {
        assertTrue(BarrowsTunnelRules.isAttackRejectedMessage("I'm already under attack."));
        assertTrue(BarrowsTunnelRules.isAttackRejectedMessage("I can't reach that!"));
        assertFalse(BarrowsTunnelRules.isAttackRejectedMessage("Your prayers have been drained!"));
        assertFalse(BarrowsTunnelRules.isAttackRejectedMessage(null));
    }

    @Test
    void refusalOnlyCountsForARecentAttackClick()
    {
        assertTrue(BarrowsTunnelRules.isRejectionForAttack(10_000, 11_200));
        assertFalse(BarrowsTunnelRules.isRejectionForAttack(10_000,
                10_001 + BarrowsTunnelRules.ATTACK_REJECTION_WINDOW_MS));
        assertFalse(BarrowsTunnelRules.isRejectionForAttack(0, 1_000));
    }

    @Test
    void refusedTargetIsSkippedUntilTheCooldownEnds()
    {
        assertTrue(BarrowsTunnelRules.isAttackRejectionActive(20_000L, 19_999));
        assertFalse(BarrowsTunnelRules.isAttackRejectionActive(20_000L, 20_000));
        assertFalse(BarrowsTunnelRules.isAttackRejectionActive(null, 1_000));
    }

    @Test
    void aggressorPickSkipsRefusedTargetsAndKeepsTheCurrentFight()
    {
        Map<String, Integer> distances = new HashMap<>();
        distances.put("skeleton behind door", 1);
        distances.put("crypt rat", 3);
        distances.put("bloodworm", 5);
        distances.put("far skeleton", 12);
        List<String> aggressors = Arrays.asList("skeleton behind door", "crypt rat", "bloodworm", "far skeleton");

        assertEquals("skeleton behind door", BarrowsTunnelRules.pickCombatLockAggressor(
                aggressors, a -> false, a -> false, distances::get, 8));
        assertEquals("crypt rat", BarrowsTunnelRules.pickCombatLockAggressor(
                aggressors, a -> false, "skeleton behind door"::equals, distances::get, 8));
        assertEquals("bloodworm", BarrowsTunnelRules.pickCombatLockAggressor(
                aggressors, "bloodworm"::equals, a -> false, distances::get, 8));
        assertNull(BarrowsTunnelRules.pickCombatLockAggressor(
                aggressors, a -> false, a -> !a.equals("far skeleton"), distances::get, 8));
        assertNull(BarrowsTunnelRules.pickCombatLockAggressor(
                null, a -> false, a -> false, distances::get, 8));
    }

    @Test
    void tunnelMonstersCountAsCombatLockAggressors()
    {
        assertTrue(BarrowsTunnelRules.isCombatLockAggressorName("Skeleton"));
        assertTrue(BarrowsTunnelRules.isCombatLockAggressorName("Giant crypt rat"));
        assertTrue(BarrowsTunnelRules.isCombatLockAggressorName("Bloodworm"));
        assertFalse(BarrowsTunnelRules.isCombatLockAggressorName("Dharok the Wretched"));
        assertFalse(BarrowsTunnelRules.isCombatLockAggressorName(null));
    }
}
