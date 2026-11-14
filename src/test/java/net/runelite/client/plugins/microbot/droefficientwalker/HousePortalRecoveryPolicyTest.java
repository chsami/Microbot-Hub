package net.runelite.client.plugins.microbot.droefficientwalker;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class HousePortalRecoveryPolicyTest {
    @Test public void doNotTerminateBeforeLateHouseExitOrWhileInputIsStillInFlight() {
        assertFalse(HousePortalRecoveryPolicy.expired(51, false));
        assertFalse(HousePortalRecoveryPolicy.expired(111, true));
        assertTrue(HousePortalRecoveryPolicy.expired(111, false));
        assertTrue(HousePortalRecoveryPolicy.expired(136, true));
    }

    @Test public void retryOnlyAfterCompletedActionWithBoundedBudget() {
        assertTrue(HousePortalRecoveryPolicy.shouldAttempt(0, 0));
        assertFalse(HousePortalRecoveryPolicy.shouldAttempt(1, 9));
        assertTrue(HousePortalRecoveryPolicy.shouldAttempt(1, 10));
        assertTrue(HousePortalRecoveryPolicy.shouldAttempt(5, 11));
        assertFalse(HousePortalRecoveryPolicy.shouldAttempt(6, 100));
    }
}
