/* Copyright (c) 2026, DRO (droplugins). SPDX-License-Identifier: BSD-2-Clause */
package net.runelite.client.plugins.microbot.drofirecape;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class FcAfterExitTest {
    @Test public void defaultStopsRatherThanRepeats() {
        DroFirecapeConfig config = new DroFirecapeConfig() { };
        assertEquals(DroFirecapeConfig.AfterExitBehavior.LOGOUT, config.afterExitBehavior());
        assertFalse(FcAfterExit.repeat(config.afterExitBehavior(), true, false, false));
    }

    @Test public void repeatRequiresAnActualCaveAttemptAndNoSupplyFailure() {
        DroFirecapeConfig.AfterExitBehavior repeat = DroFirecapeConfig.AfterExitBehavior.GET_ANOTHER_CAPE;
        assertTrue(FcAfterExit.repeat(repeat, true, false, false));
        assertFalse(FcAfterExit.repeat(repeat, false, false, false));
        assertFalse(FcAfterExit.repeat(repeat, true, true, false));
        assertFalse(FcAfterExit.repeat(repeat, true, false, true));
    }
}
