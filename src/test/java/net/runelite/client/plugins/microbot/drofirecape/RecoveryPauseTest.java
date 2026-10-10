/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape;

import net.runelite.client.plugins.microbot.drofirecape.core.EnergyPause;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class RecoveryPauseTest {
    private void clear(EnergyPause p,int wave,int tick) {
        p.observe(wave,true,tick,-1000,1000);p.observe(wave,true,tick+3,-1000,2800);
    }
    @Test public void selectedWaveDoesNotDependOnZeroEnergyAndRequestsOnlyOnce() {
        EnergyPause p=new EnergyPause();
        assertFalse(p.shouldRequest(false,56,56,true));assertFalse(p.shouldRequest(true,56,55,true));
        assertFalse(p.shouldRequest(true,56,63,true));assertFalse(p.shouldRequest(true,56,56,false));
        assertTrue(p.shouldRequest(true,56,56,true));p.requested(56,307,1000);
        assertFalse(p.shouldRequest(true,56,56,true));assertTrue(p.requestExpired(181000));
        assertFalse(p.shouldRequest(true,56,56,true));
    }
    @Test public void acknowledgementClearStreakAndLastFlightAreAllRequired() {
        EnergyPause p=new EnergyPause();p.requested(56,307,1000);clear(p,56,10);
        assertEquals(EnergyPause.State.REQUESTED,p.state());p.confirmation();
        p.observe(56,false,14,14,2000);p.observe(56,true,15,14,2600);
        p.observe(56,true,18,14,4400);assertEquals(EnergyPause.State.REQUESTED,p.state());
        p.observe(56,true,22,14,6800);assertEquals(EnergyPause.State.RESTING,p.state());
    }
    @Test public void oneEmptyFrameAndSplitBabiesCannotStartRecovery() {
        EnergyPause p=new EnergyPause();p.requested(58,307,1000);p.confirmation();
        p.observe(58,true,100,80,1000);p.observe(58,true,100,80,1500);
        assertEquals(EnergyPause.State.REQUESTED,p.state());
        p.observe(58,false,101,80,1600);p.observe(58,true,102,80,2200);
        p.observe(58,true,104,80,3400);assertEquals(EnergyPause.State.REQUESTED,p.state());
        p.observe(58,true,105,80,4000);assertEquals(EnergyPause.State.RESTING,p.state());
    }
    @Test public void unacknowledgedRequestNeverStopsTheNextWaveOrRepeatsSameWave() {
        EnergyPause p=new EnergyPause();p.requested(56,307,1000);p.observe(57,false,200,200,2000);
        assertEquals(EnergyPause.State.OFF,p.state());assertFalse(p.shouldRequest(true,56,56,true));
        assertTrue(p.shouldRequest(true,56,57,true));
    }
    @Test public void rejectedAndTimedOutHopHaveBackoffAndThreeAttemptLimit() {
        EnergyPause p=new EnergyPause();p.requested(56,307,1000);p.confirmation();clear(p,56,100);p.arm();
        for(int attempt=1;attempt<=3;attempt++) {
            long at=attempt*100000L;assertTrue(p.canHop(at));p.hopping(308,at);
            assertEquals(attempt,p.hopAttempts());
            if(attempt==2){assertFalse(p.hopExpired(at+34999));assertTrue(p.hopExpired(at+35000));}
            p.hopFailed(at+35000);assertEquals(EnergyPause.State.ARMING,p.state());
            assertFalse(p.canHop(at+35001));
        }
        assertTrue(p.hopBlocked());assertFalse(p.canHop(9999999));
    }
    @Test public void worldNumberAloneOrLoadingCannotCompleteAHop() {
        EnergyPause p=new EnergyPause();p.requested(62,307,1000);p.confirmation();clear(p,62,100);p.arm();p.hopping(308,5000);
        assertFalse(p.resumedScene(true,308,200,4999));assertFalse(p.resumedScene(false,308,200,5001));
        assertFalse(p.resumedScene(true,307,200,5001));assertFalse(p.resumedScene(true,308,200,5001));
        assertFalse(p.resumedScene(true,308,200,5600));assertFalse(p.resumedScene(true,308,201,5601));
        assertTrue(p.resumedScene(true,308,202,6201));assertEquals(63,p.nextWave());
    }
    @Test public void readinessInterruptionRestartsTheFreshSceneStreak() {
        EnergyPause p=new EnergyPause();p.requested(56,307,1000);p.confirmation();clear(p,56,100);p.arm();p.hopping(308,5000);
        assertFalse(p.resumedScene(true,308,200,5001));assertFalse(p.resumedScene(false,308,201,5601));
        assertFalse(p.resumedScene(true,308,202,6201));assertTrue(p.resumedScene(true,308,204,7401));
    }
    @Test public void twoRecoveryCyclesKeepDistinctCompletedAndNextWaves() {
        EnergyPause p=new EnergyPause();
        for(int wave=56;wave<=57;wave++) {
            assertTrue(p.shouldRequest(true,56,wave,true));p.requested(wave,307,1000);p.confirmation();clear(p,wave,100);
            assertEquals(wave,p.requestedWave());assertEquals(wave+1,p.nextWave());p.arm();p.hopping(308,5000);
            p.resumedScene(true,308,200,5001);assertTrue(p.resumedScene(true,308,202,6201));p.resumed();
            assertEquals(EnergyPause.State.OFF,p.state());assertFalse(p.shouldRequest(true,56,wave,true));
        }
    }
    @Test public void restartRestoresPausedOwnershipAndRechecksClearScene() {
        EnergyPause p=new EnergyPause();p.requested(62,307,1000);p.confirmation();clear(p,62,100);p.arm();
        EnergyPause restarted=new EnergyPause();assertTrue(restarted.restore(p.saved(),20000));
        assertEquals(EnergyPause.State.RESTING,restarted.state());assertEquals(62,restarted.requestedWave());
        assertEquals(63,restarted.nextWave());assertFalse(restarted.clear(100,80));assertFalse(restarted.shouldRequest(true,56,62,true));
        clear(restarted,62,101);assertTrue(restarted.clear(104,80));
    }
    @Test public void restartRetainsSentHopAndRequestWithoutSecondLogout() {
        EnergyPause p=new EnergyPause();p.requested(56,307,1000);
        EnergyPause restarted=new EnergyPause();assertTrue(restarted.restore(p.saved(),2000));
        assertEquals(EnergyPause.State.REQUESTED,restarted.state());assertFalse(restarted.shouldRequest(true,56,56,true));
        p.confirmation();clear(p,56,100);p.arm();p.hopping(308,5000);
        assertTrue(restarted.restore(p.saved(),6000));assertEquals(EnergyPause.State.HOPPING,restarted.state());assertEquals(308,restarted.targetWorld());
        assertFalse(restarted.shouldRequest(true,56,56,true));
    }
    @Test public void delayedAcceptedHopAfterReportedFailureRetainsExpectedWorld() {
        EnergyPause p=new EnergyPause();p.requested(62,307,1000);p.confirmation();clear(p,62,100);p.arm();p.hopping(308,5000);
        p.hopFailed(6000);assertEquals(EnergyPause.State.ARMING,p.state());assertTrue(p.arrivedWorld(308));
        assertFalse(p.resumedScene(true,308,200,6500));assertTrue(p.resumedScene(true,308,202,7700));
        assertEquals(1,p.hopAttempts());assertEquals(63,p.nextWave());
    }

}
