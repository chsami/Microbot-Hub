/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape;

import net.runelite.client.plugins.microbot.drofirecape.core.SupplyPreparation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SupplyPreparationTest {
    @Test public void unacknowledgedTabCannotOwnCombatForever() {
        SupplyPreparation preparation=new SupplyPreparation();
        assertTrue(preparation.ready(2444,1000));assertTrue(preparation.waiting(1800));
        assertTrue(preparation.waiting(1900));assertFalse(preparation.waiting(7000));preparation.defer(7000);
        assertFalse(preparation.ready(2444,7100));assertTrue(preparation.ready(2444,7500));
    }
    @Test public void criticalRestoreCanPreemptDeferredOptionalBoost() {
        SupplyPreparation preparation=new SupplyPreparation();
        preparation.ready(2444,1000);preparation.defer(1100);
        assertTrue(preparation.ready(3024,1200));assertTrue(preparation.waiting(1200));
    }
    @Test public void pendingItemSurvivesPrayerPreemptionWithOnlyRemainingClickBudget() {
        SupplyPreparation preparation=new SupplyPreparation();assertEquals(900,preparation.inputBudgetMillis());
        preparation.ready(6685,1000);assertEquals(300,preparation.inputBudgetMillis());
        assertTrue(preparation.waiting(3000));assertEquals(6685,preparation.itemId());
        preparation.ready(6685,3100);assertEquals(300,preparation.inputBudgetMillis());
        preparation.reset();assertFalse(preparation.pending());assertEquals(900,preparation.inputBudgetMillis());
    }
    @Test public void consumedItemClearsPreparation() {
        SupplyPreparation preparation=new SupplyPreparation();
        preparation.ready(3024,1000);preparation.defer(1100);preparation.reset();
        assertTrue(preparation.ready(3024,1200));
    }
}
