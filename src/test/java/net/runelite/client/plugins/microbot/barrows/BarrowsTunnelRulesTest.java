package net.runelite.client.plugins.microbot.barrows;

import net.runelite.client.plugins.microbot.util.walker.WalkerState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void tunnelMonstersCountAsCombatLockAggressors()
    {
        assertTrue(BarrowsTunnelRules.isCombatLockAggressorName("Skeleton"));
        assertTrue(BarrowsTunnelRules.isCombatLockAggressorName("Giant crypt rat"));
        assertTrue(BarrowsTunnelRules.isCombatLockAggressorName("Bloodworm"));
        assertFalse(BarrowsTunnelRules.isCombatLockAggressorName("Dharok the Wretched"));
        assertFalse(BarrowsTunnelRules.isCombatLockAggressorName(null));
    }
}
