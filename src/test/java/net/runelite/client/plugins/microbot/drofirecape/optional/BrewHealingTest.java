package net.runelite.client.plugins.microbot.drofirecape.optional;
import net.runelite.client.plugins.microbot.drofirecape.optional.core.BrewHealing;
import org.junit.Test;
import static org.junit.Assert.*;
public class BrewHealingTest {
    @Test public void optionalFullOverbrewStartsOnlyFrom53AndFinishesBoostedTargetOncePerWave(){
        BrewHealing h=new BrewHealing();
        assertFalse(h.needed(88,92,65,false,true,true,52));
        assertTrue(h.needed(88,92,65,false,true,true,53));
        h.confirmed();
        assertTrue("One sip to 103 is not the full 107 boosted target",h.needed(103,92,65,false,true,true,53));
        h.confirmed();assertFalse(h.needed(107,92,65,false,true,true,53));
        assertFalse(h.needed(88,92,65,false,true,true,53));
        assertTrue(h.needed(88,92,65,false,true,true,54));
    }
    @Test public void failedOptionalClickDoesNotSpendTheWaveAllowance(){
        BrewHealing h=new BrewHealing();assertTrue(h.needed(88,92,65,false,true,true,53));
        h.cancel();assertTrue(h.needed(88,92,65,false,true,true,53));
    }
    @Test public void lowPrayerAndEmergencyBatchesStillWorkBefore53WithoutOverbrew(){
        BrewHealing h=new BrewHealing();assertTrue(h.needed(85,100,60,true,true,false,20));
        assertFalse(h.needed(102,100,60,false,true,false,20));
        assertTrue(h.needed(55,100,60,false,true,true,20));
        assertTrue(h.needed(85,100,60,false,true,true,20));
        assertFalse(h.needed(100,100,60,false,true,true,20));
    }
    @Test public void thresholdStartsAFullBatchNotOneSip(){
        BrewHealing h=new BrewHealing();
        assertFalse(h.needed(61,100,60,false,true));
        assertTrue(h.needed(60,100,60,false,true));
        assertTrue(h.needed(75,100,60,false,true));
        assertTrue(h.needed(99,100,60,false,true));
        assertFalse(h.needed(100,100,60,false,true));
        assertFalse(h.needed(99,100,60,false,true));
    }
    @Test public void topUpBatchContinuesEvenAfterPrayerWasRestored(){
        BrewHealing h=new BrewHealing();
        assertTrue(h.needed(85,100,60,true,true));
        assertTrue(h.needed(95,100,60,false,true));
        assertFalse(h.needed(115,100,60,false,true));
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
