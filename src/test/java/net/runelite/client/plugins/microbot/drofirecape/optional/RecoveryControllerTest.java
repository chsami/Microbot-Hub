package net.runelite.client.plugins.microbot.drofirecape.optional;
import net.runelite.client.plugins.microbot.drofirecape.DroFirecapeConfig;

import java.lang.reflect.*;
import java.util.List;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.drofirecape.optional.core.*;
import net.runelite.client.plugins.microbot.drofirecape.optional.core.FcModel.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Controller-level evidence: neither helper returns nor ready energy alone complete recovery. */
public class RecoveryControllerTest {
    static Object get(Object o,String name)throws Exception {Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    static Object call(DroFirecapeScript s,String name,FcFrame f)throws Exception {
        Method m=DroFirecapeScript.class.getDeclaredMethod(name,FcFrame.class);m.setAccessible(true);return m.invoke(s,f);
    }
    private static final class Fixture {
        final DroFirecapeScript script=new DroFirecapeScript();
        final FcActions actions=mock(FcActions.class);
        final FcTickPrayers driver=mock(FcTickPrayers.class);
        final DroFirecapeConfig config=mock(DroFirecapeConfig.class);
        final ConfigManager saved=mock(ConfigManager.class);
        final EnergyPause pause;
        Fixture()throws Exception {
            RecoveryPolicyTest.set(script,"actions",actions);RecoveryPolicyTest.set(script,"config",config);
            RecoveryPolicyTest.set(script,"tickPrayers",driver);
            when(driver.ownsInput()).thenReturn(true);when(driver.protectionReady()).thenReturn(true);
            when(driver.optionalInputWindow(anyInt(),anyLong())).thenReturn(true);
            RecoveryPolicyTest.set(script,"configManager",saved);RecoveryPolicyTest.set(script,"enabled",true);
            RecoveryPolicyTest.set(script,"state",DroFirecapeScript.State.FIGHTING);
            when(config.recoveryPrayerPercent()).thenReturn(90);when(config.recoveryStartWave()).thenReturn(56);
            when(config.energyPause()).thenReturn(true);when(config.eatPercent()).thenReturn(60);
            when(actions.prayersObservedOff()).thenReturn(true);when(actions.overheadActive(any())).thenReturn(true);
            when(actions.resumeWorld(anyInt())).thenReturn(308);when(actions.resumePausedWave(308)).thenReturn(true);
            pause=(EnergyPause)get(script,"energyPause");pause.requested(62,307,1000);pause.confirmation();
            pause.observe(62,true,100,-1000,1000);pause.observe(62,true,103,-1000,2800);
            ((WaveTracker)get(script,"waves")).restore(1,62);
        }
        FcFrame frame(int tick,int world)throws Exception {
            FcFrame f=RecoveryPolicyTest.frame(tick,99,100);RecoveryPolicyTest.set(f,"world",world);
            RecoveryPolicyTest.set(f,"capturedAt",System.currentTimeMillis()+1);RecoveryPolicyTest.set(script,"frame",f);return f;
        }
        void rest(FcFrame f)throws Exception {call(script,"rest",f);}
    }
    @Test public void prayersMustBeObservedOffBeforeAnyRecoverySupply()throws Exception {
        Fixture t=new Fixture();FcFrame f=t.frame(103,307);RecoveryPolicyTest.set(f,"hp",40);
        RecoveryPolicyTest.set(f,"inventory",List.of(RecoveryPolicyTest.item(6685,"Saradomin brew(4)")));
        when(t.actions.prayersObservedOff()).thenReturn(false);t.rest(f);
        verify(t.driver).recoveryPause(true,Protection.NONE);
        verify(t.actions,never()).prayersOff();verify(t.actions,never()).itemStep(any(),anyString(),any());
        verify(t.actions,never()).resumePausedWave(anyInt());assertEquals(EnergyPause.State.RESTING,t.pause.state());
    }
    @Test public void helperTrueStillNeedsFreshExpectedWorldAndJadGuardBeforeContinue()throws Exception {
        Fixture t=new Fixture();t.rest(t.frame(103,307));assertEquals(EnergyPause.State.ARMING,t.pause.state());
        t.rest(t.frame(104,307));assertEquals(EnergyPause.State.HOPPING,t.pause.state());
        verify(t.actions).resumePausedWave(308);clearInvocations(t.actions);
        t.rest(t.frame(105,307));assertEquals(EnergyPause.State.HOPPING,t.pause.state());
        FcFrame loading=t.frame(106,308);RecoveryPolicyTest.set(loading,"containersReady",false);t.rest(loading);
        t.rest(t.frame(107,308));t.rest(t.frame(108,308));
        when(t.actions.overheadActive(Protection.MAGIC)).thenReturn(false);t.rest(t.frame(109,308));
        assertEquals(EnergyPause.State.HOPPING,t.pause.state());verify(t.actions,never()).continueCaveDialogue();
        when(t.actions.overheadActive(Protection.MAGIC)).thenReturn(true);t.rest(t.frame(110,308));
        verify(t.actions).continueCaveDialogue();assertEquals(EnergyPause.State.OFF,t.pause.state());
        assertEquals(63,t.script.wave());verify(t.actions,never()).requestWavePause(anyInt(),any());
    }
    @Test public void rejectedHelperDoesNotStayInHoppingOrImmediatelyRetry()throws Exception {
        Fixture t=new Fixture();when(t.actions.resumePausedWave(308)).thenReturn(false);
        t.rest(t.frame(103,307));t.rest(t.frame(104,307));assertEquals(EnergyPause.State.ARMING,t.pause.state());
        t.rest(t.frame(105,307));verify(t.actions,times(1)).resumePausedWave(308);
        assertTrue(t.script.status().contains("retrying"));verify(t.actions,never()).requestWavePause(anyInt(),any());
    }
    @Test public void depletedAndUnacknowledgedSuppliesLeaveExplicitPausedBlockers()throws Exception {
        Fixture t=new Fixture();FcFrame f=t.frame(103,307);RecoveryPolicyTest.set(f,"hp",40);t.rest(f);
        assertTrue(t.script.status().contains("unavailable"));assertEquals(EnergyPause.State.RESTING,t.pause.state());
        RecoveryPolicyTest.set(t.script,"recoverySupplyMisses",3);
        RecoveryPolicyTest.set(f,"inventory",List.of(RecoveryPolicyTest.item(6685,"Saradomin brew(4)")));t.rest(f);
        assertTrue(t.script.warning().contains("not acknowledged"));verify(t.actions,never()).resumePausedWave(anyInt());
        verify(t.actions,never()).itemStep(any(),anyString(),any());
    }
    @Test public void unexpectedSpawnGuardCancelsInventoryPermission()throws Exception {
        Fixture t=new Fixture();FcFrame f=t.frame(103,307);RecoveryPolicyTest.set(f,"hp",40);
        RecoveryPolicyTest.set(f,"inventory",List.of(RecoveryPolicyTest.item(6685,"Saradomin brew(4)")));
        when(t.actions.itemStep(any(),anyString(),any())).thenAnswer(i->{
            java.util.function.BooleanSupplier permit=i.getArgument(2);assertTrue(permit.getAsBoolean());
            RecoveryPolicyTest.set(f,"model",new Snapshot(103,new Tile(30,30),new CollisionGrid(new int[104][104]),
                List.of(new Mob(9,Kind.MELEER,new Tile(26,30),4,10,10,-1,Protection.MELEE,true)),100,false,5,Protection.MAGIC));
            assertFalse(permit.getAsBoolean());return FcActions.ItemResult.PREPARING;
        });t.rest(f);verify(t.actions).itemStep(any(),eq("Drink"),any());
        assertFalse(((SupplyAck)get(t.script,"supplyAck")).pending());verify(t.actions,never()).resumePausedWave(anyInt());
    }
    @Test public void pausedPersistenceKeepsCompletedWaveSeparateFromNextWaveHint()throws Exception {
        Fixture t=new Fixture();t.rest(t.frame(103,307));
        verify(t.saved).setRSProfileConfiguration(eq(DroFirecapeConfig.GROUP),eq("activeRun"),eq("1:62:true:0"));
        verify(t.saved).setRSProfileConfiguration(eq(DroFirecapeConfig.GROUP),eq("recoveryPause"),contains(":62:true:"));
        assertEquals(63,t.pause.nextWave());assertEquals(62,t.pause.requestedWave());
    }
    @Test public void lateAcceptedArrivalAfterRejectedHelperDoesNotHopBack()throws Exception {
        Fixture t=new Fixture();when(t.actions.resumePausedWave(308)).thenReturn(false);
        t.rest(t.frame(103,307));t.rest(t.frame(104,307));assertEquals(EnergyPause.State.ARMING,t.pause.state());
        t.rest(t.frame(105,308));t.rest(t.frame(106,308));t.rest(t.frame(107,308));
        assertEquals(EnergyPause.State.OFF,t.pause.state());assertEquals(63,t.script.wave());
        verify(t.actions,times(1)).resumePausedWave(anyInt());verify(t.actions,times(1)).resumeWorld(anyInt());
        verify(t.actions,never()).requestWavePause(anyInt(),any());
    }

    @Test public void spawnEventBeforeNextFrameCancelsPreparedRecoverySupply()throws Exception {
        Fixture t=new Fixture();FcFrame f=t.frame(103,307);RecoveryPolicyTest.set(f,"hp",40);
        RecoveryPolicyTest.set(f,"inventory",List.of(RecoveryPolicyTest.item(6685,"Saradomin brew(4)")));
        when(t.actions.itemStep(any(),anyString(),any())).thenAnswer(i->{
            java.util.function.BooleanSupplier permit=i.getArgument(2);assertTrue(permit.getAsBoolean());
            RecoveryPolicyTest.set(t.script,"recoverySpawnObserved",true);
            assertTrue(f.model.mobs().isEmpty());assertFalse(permit.getAsBoolean());
            return FcActions.ItemResult.PREPARING;
        });t.rest(f);assertFalse(((SupplyAck)get(t.script,"supplyAck")).pending());
        verify(t.actions,never()).resumePausedWave(anyInt());
    }

    @Test public void pureOverbrewDepletionArmsAndHopsWithoutAnotherLogout()throws Exception {
        Fixture t=new Fixture();when(t.config.pureMode()).thenReturn(true);when(t.config.recoveryOverbrew()).thenReturn(true);
        t.rest(t.frame(103,307));assertEquals(EnergyPause.State.ARMING,t.pause.state());
        t.rest(t.frame(104,307));assertEquals(EnergyPause.State.HOPPING,t.pause.state());
        t.rest(t.frame(105,308));t.rest(t.frame(106,308));t.rest(t.frame(107,308));
        assertEquals(EnergyPause.State.OFF,t.pause.state());verify(t.actions,times(1)).resumePausedWave(308);
        verify(t.actions,never()).requestWavePause(anyInt(),any());verify(t.actions,never()).itemStep(any(),anyString(),any());
    }
    @Test public void zeroPrayerPureResumeUsesExistingExhaustionPermissionAndFreshHopEvidence()throws Exception {
        Fixture t=new Fixture();when(t.config.pureMode()).thenReturn(true);
        when(t.actions.overheadActive(any())).thenReturn(false);when(t.driver.protectionReady()).thenReturn(false);
        FcFrame f=t.frame(103,307);RecoveryPolicyTest.set(f,"prayer",0);t.rest(f);
        assertEquals(EnergyPause.State.ARMING,t.pause.state());t.rest(f);
        verify(t.actions,never()).resumePausedWave(anyInt());
        when(t.driver.exhaustedCombatReady()).thenReturn(true);
        t.rest(f);assertEquals(EnergyPause.State.HOPPING,t.pause.state());
        for(int tick=105;tick<=107;tick++) {
            f=t.frame(tick,308);RecoveryPolicyTest.set(f,"prayer",0);t.rest(f);
        }
        assertEquals(EnergyPause.State.OFF,t.pause.state());assertTrue(t.script.status().contains("exhausted prayer"));
        verify(t.actions,times(1)).resumePausedWave(308);verify(t.actions,never()).requestWavePause(anyInt(),any());
    }
    @Test public void pureSupplyDepletionDoesNotBypassExistingPrayerWhenPointsRemain()throws Exception {
        Fixture t=new Fixture();when(t.config.pureMode()).thenReturn(true);when(t.config.recoveryOverbrew()).thenReturn(true);
        when(t.actions.overheadActive(any())).thenReturn(false);when(t.driver.protectionReady()).thenReturn(false);
        when(t.driver.exhaustedCombatReady()).thenReturn(true);
        t.rest(t.frame(103,307));t.rest(t.frame(104,307));
        assertEquals(EnergyPause.State.ARMING,t.pause.state());verify(t.actions,never()).resumePausedWave(anyInt());
    }

}
