/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape;
import net.runelite.client.plugins.microbot.drofirecape.*;
import net.runelite.client.plugins.microbot.drofirecape.core.BrewHealing;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
public class OptionalBrewHealingTest {
    @Test public void optionalFullOverbrewStartsOnlyFrom53AndFinishesBoostedTargetOncePerWave(){
        BrewHealing h=new BrewHealing();
        assertFalse(h.needed(88,92,65,false,true,true,52));
        assertTrue(h.needed(88,92,65,false,true,true,53));
        h.confirmed();
        assertTrue(h.needed(103,92,65,false,true,true,53),"One sip to 103 is not the full 107 boosted target");
        h.confirmed();assertFalse(h.needed(107,92,65,false,true,true,53));
        assertFalse(h.needed(88,92,65,false,true,true,53));
        assertTrue(h.needed(88,92,65,false,true,true,54));
    }
    @Test public void failedOptionalClickDoesNotSpendTheWaveAllowance(){
        BrewHealing h=new BrewHealing();assertTrue(h.needed(88,92,65,false,true,true,53));
        h.cancel();assertTrue(h.needed(88,92,65,false,true,true,53));
    }
    @Test public void lowPrayerAndEmergencyBatchesStillWorkBefore53WithoutOverbrew(){
        BrewHealing h=new BrewHealing();assertTrue(h.needed(75,100,60,true,true,false,20));
        assertFalse(h.needed(102,100,60,false,true,false,20));
        assertTrue(h.needed(55,100,60,false,true,true,20));
        assertTrue(h.needed(79,100,60,false,true,true,20));
        assertFalse(h.needed(80,100,60,false,true,true,20));
    }
    @Test public void thresholdStartsABatchThatStopsAtTheRecoveryBudget(){
        BrewHealing h=new BrewHealing();
        assertFalse(h.needed(61,100,60,false,true));
        assertTrue(h.needed(60,100,60,false,true));
        assertTrue(h.needed(75,100,60,false,true));
        assertTrue(h.needed(79,100,60,false,true));
        assertFalse(h.needed(80,100,60,false,true));
        assertFalse(h.needed(79,100,60,false,true),"A completed batch must not restart above the trigger");
    }
    @Test public void topUpBatchContinuesEvenAfterPrayerWasRestored(){
        BrewHealing h=new BrewHealing();
        assertTrue(h.needed(75,100,60,true,true));
        assertTrue(h.needed(79,100,60,false,true));
        assertFalse(h.needed(80,100,60,false,true));
    }
    @Test public void lowPrayerTopUpMustFitAWholeDoseAndStayBelowRecoveryBudget(){
        BrewHealing h=new BrewHealing();
        // A 100-HP brew heals 17. Threshold 70 gives a 90-HP recovery target.
        assertFalse(h.needed(84,100,70,true,true));
        assertTrue(h.needed(83,100,70,true,true));
        assertTrue(h.needed(89,100,70,false,true));
        assertFalse(h.needed(90,100,70,false,true));
    }
    @Test public void exhaustedBrewsAndRestartClearTheBatch(){
        BrewHealing h=new BrewHealing();
        assertTrue(h.needed(40,100,60,false,true));
        assertFalse(h.needed(55,100,60,false,false));
        assertFalse(h.needed(85,100,60,false,true));
        assertTrue(h.needed(40,100,60,false,true));h.reset();
        assertFalse(h.needed(85,100,60,false,true));
    }
}
