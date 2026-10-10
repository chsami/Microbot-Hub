/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape;

import java.util.Optional;
import java.util.concurrent.Callable;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.drofirecape.core.WaveBook;
import net.runelite.client.plugins.microbot.drofirecape.core.WaveTracker;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

public class EmbeddedPredictorTest {
    @Test public void upstreamSixteenMinuteCycleIncludesRepeatedRotationFour() {
        // Upstream StartLocations.rotationResolver, including the repeated first column.
        int[] expected={4,2,9,11,13,1,6,15,10,8,5,3,12,14,7,4};
        FcSpawnPredictor p=new FcSpawnPredictor();
        for(int minute=0;minute<1440;minute++) {
            FcPredictorGate.Sample s=p.sample(307,minute,20,true,1000);
            assertTrue(s.ready);assertEquals(expected[minute%16],s.rotation);
            assertEquals(WaveBook.rotationAtMinute(minute),s.rotation);
        }
        assertEquals(1,FcSpawnPredictor.rotationColumn(15));assertEquals(1,FcSpawnPredictor.rotationColumn(16));
    }
    @Test public void embeddedClockNeedsAdvancementAndHonorsConfiguredRotation() {
        FcSpawnPredictor p=new FcSpawnPredictor();FcPredictorGate gate=new FcPredictorGate();
        assertFalse(gate.entryReady(p.sample(307,10,20,true,1000),1000,5));
        assertFalse(gate.entryReady(p.sample(307,10,21,true,2000),2000,5));
        FcPredictorGate.Sample ready=p.sample(307,10,22,true,3000);
        assertTrue(gate.entryReady(ready,3000,5));assertTrue(gate.entryReady(ready,3000,0));
        assertFalse(gate.entryReady(ready,3000,4));
        assertFalse(gate.entryReady(p.sample(307,10,22,true,8000),8000,0));
    }
    @Test public void worldChangeAndMinuteBoundaryRequireFreshSafeEvidence() {
        FcSpawnPredictor p=new FcSpawnPredictor();FcPredictorGate gate=new FcPredictorGate();
        for(int second=20;second<=22;second++)gate.entryReady(p.sample(307,10,second,true,second*1000L),second*1000L,0);
        assertFalse(gate.entryReady(p.sample(308,10,23,true,23000),23000,0));
        assertFalse(gate.entryReady(p.sample(308,10,24,true,24000),24000,0));
        assertTrue(gate.entryReady(p.sample(308,10,25,true,25000),25000,0));
        for(int second:new int[]{0,1,7,44,49,50,59}) {
            FcPredictorGate.Sample s=p.sample(308,10,second,true,26000);
            assertFalse(gate.entryReady(s,26000,0));
        }
    }
    @Test public void unavailableClockAndNonTzhaarCannotAuthorizeEntry() {
        FcSpawnPredictor p=new FcSpawnPredictor();
        assertFalse(p.sample(307,-1,20,true,0).ready);assertFalse(p.sample(307,10,-1,true,0).ready);
        assertFalse(p.sample(307,10,60,true,0).ready);assertFalse(p.sample(307,10,20,false,0).ready);
    }
    @Test public void actionAdapterReadsServerClockWithoutConsultingInstalledPlugins() {
        try(MockedStatic<Microbot> microbot=mockStatic(Microbot.class)) {
            Client c=mock(Client.class);Player player=mock(Player.class);WorldView view=mock(WorldView.class);ClientThread thread=mock(ClientThread.class);
            microbot.when(Microbot::getClient).thenReturn(c);microbot.when(Microbot::getClientThread).thenReturn(thread);
            when(thread.runOnClientThreadOptional(any())).thenAnswer(i->Optional.ofNullable(((Callable<?>)i.getArgument(0)).call()));
            when(c.getGameState()).thenReturn(GameState.LOGGED_IN);when(c.getWorld()).thenReturn(307);when(c.getLocalPlayer()).thenReturn(player);
            when(c.getTopLevelWorldView()).thenReturn(view);when(player.getWorldLocation()).thenReturn(new WorldPoint(2445,5178,0));
            when(c.getVarpValue(VarPlayerID.DATE_MINUTES)).thenReturn(10);when(c.getVarbitValue(VarbitID.DATE_SECONDS_PAST_MINUTE)).thenReturn(20);
            FcPredictorGate.Sample s=new FcActions().predictor(false);assertTrue(s.ready);assertEquals(5,s.rotation);
            assertTrue(s.source instanceof FcSpawnPredictor);microbot.verify(Microbot::getPluginManager,never());
            when(view.isInstance()).thenReturn(true);assertFalse(new FcActions().predictor(false).ready);
        }
    }
    @Test public void internallyArmedRunTracksWavesWithoutExternalPredictor() {
        FcSpawnPredictor p=new FcSpawnPredictor();WaveTracker waves=new WaveTracker();
        waves.begin(p.sample(307,10,20,true,0).rotation);
        for(int wave=1;wave<=63;wave++) {
            int tick=wave*20;assertTrue(waves.chat("Wave: "+wave,tick));int index=0;
            for(WaveBook.Spawned spawn:WaveBook.wave(5,wave))waves.spawned(new WaveTracker.SpawnEvidence(tick,index++,spawn.kind(),
                WaveBook.BASE_X+spawn.location().x,WaveBook.BASE_Y+spawn.location().y));
            waves.verify(tick+3);assertEquals(wave,waves.wave());assertEquals(5,waves.rotation());assertTrue(waves.predictionReady());
        }
        assertEquals(63,waves.confirmations());
    }
}
