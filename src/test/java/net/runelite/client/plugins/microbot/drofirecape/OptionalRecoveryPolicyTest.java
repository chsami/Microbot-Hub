/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape;
import net.runelite.client.plugins.microbot.drofirecape.*;
import net.runelite.client.plugins.microbot.drofirecape.DroFirecapeConfig;

import java.lang.reflect.*;
import java.util.*;
import net.runelite.client.plugins.microbot.drofirecape.core.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class OptionalRecoveryPolicyTest {
    static void set(Object o,String name,Object value)throws Exception {Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}
    static FcFrame.ItemSlot item(int id,String name){return new FcFrame.ItemSlot(0,id,1,name,List.of(name.equals("Purple sweets")?"Eat":"Drink"));}
    static FcFrame frame(int tick,int hp,int energy,FcFrame.ItemSlot...items)throws Exception {
        Method factory=FirecapeTestClient.class.getDeclaredMethod("frame",int.class,int.class);factory.setAccessible(true);
        FcFrame f=(FcFrame)factory.invoke(null,tick,3024);set(f,"cave",true);set(f,"containersReady",true);
        set(f,"hp",hp);set(f,"rawEnergy",energy*100);set(f,"inventory",List.of(items));
        set(f,"model",new Snapshot(tick,new Tile(30,30),new CollisionGrid(new int[104][104]),List.of(),energy,false,5,Protection.MAGIC));
        return f;
    }
    private OptionalRecoveryPolicy.Decision choose(FcFrame f,int debt,boolean overbrew,boolean pending) {
        return OptionalRecoveryPolicy.choose(f,false,true,overbrew,40,90,true,true,62,debt,pending);
    }
    private final FcFrame.ItemSlot sweet=item(4561,"Purple sweets"),brew=item(6685,"Saradomin brew(4)"),
        restore=item(3024,"Super restore(4)"),range=item(2444,"Ranging potion(4)");
    @Test public void fullHpLowRunUsesSweetsButReadyRunLowHpStillHeals()throws Exception {
        assertEquals(sweet,choose(frame(100,99,5,sweet,brew,restore,range),0,false,false).item);
        assertEquals(brew,choose(frame(100,40,100,brew,restore,range),0,false,false).item);
    }
    @Test public void runRecoveryFinishesBeforeFinalOverbrew()throws Exception {
        OptionalRecoveryPolicy.Decision wait=choose(frame(100,99,5,brew,restore,range),0,true,false);
        assertNull(wait.item);assertFalse(wait.ready);assertTrue(wait.status.contains("run energy"));
        assertEquals(brew,choose(frame(100,99,40,brew,restore,range),0,true,false).item);
        assertEquals(115,OptionalRecoveryPolicy.hpTarget(99,true));
    }
    @Test public void lowPrayerTakesOneBrewThenRestoreAndDoesNotBrewAtFullHp()throws Exception {
        FcFrame f=frame(100,50,100,brew,restore,range);set(f,"prayer",45);
        assertEquals(brew,choose(f,0,false,false).item);set(f,"hp",66);set(f,"magic",89);
        assertEquals(restore,choose(f,1,false,false).item);
        set(f,"hp",99);set(f,"magic",99);assertEquals(restore,choose(f,0,false,false).item);
    }
    @Test public void thirdAcknowledgedBrewRestoresBeforeAnotherHealingDose()throws Exception {
        FcFrame f=frame(100,60,100,brew,restore,range);set(f,"ranged",80);set(f,"magic",80);
        assertEquals(brew,choose(f,2,false,false).item);assertEquals(restore,choose(f,3,false,false).item);
    }
    @Test public void actualMagicAndPrayerDeficitsNeedFurtherRestorationDespiteZeroDebt()throws Exception {
        FcFrame f=frame(100,99,100,restore,range);set(f,"magic",98);
        assertEquals(restore,choose(f,0,false,false).item);set(f,"magic",99);set(f,"prayer",80);
        assertEquals(restore,choose(f,0,false,false).item);set(f,"prayer",90);
        assertEquals(range,choose(f,0,false,false).item);set(f,"ranged",110);
        assertTrue(choose(f,0,false,false).ready);
    }
    @Test public void pendingConsumptionAndDepletedSuppliesCannotWeakenTargets()throws Exception {
        FcFrame f=frame(100,99,100);set(f,"ranged",110);
        assertFalse(choose(f,0,false,true).ready);set(f,"hp",40);
        assertFalse(choose(f,0,false,false).ready);assertTrue(choose(f,0,false,false).status.contains("unavailable"));
        set(f,"hp",99);set(f,"ranged",99);assertTrue(choose(f,0,false,false).status.contains("reserve"));
    }
    @Test public void finalRangedDoseRetainsJadReserveUntilWave62HasCompleted()throws Exception {
        FcFrame f=frame(100,99,100,item(2444,"Ranging potion(1)"));
        assertTrue(choose(f,0,false,false).ready);assertNull(choose(f,0,false,false).item);
        assertTrue(choose(f,0,false,false).status.contains("reserve"));
        assertNotNull(OptionalRecoveryPolicy.choose(f,false,false,false,0,90,true,true,63,0,false).item);
    }
    @Test public void oldBrewDebtAloneDoesNotConsumeRestoreWhenActualTargetsAreReady()throws Exception {
        FcFrame f=frame(100,99,100,restore,range);set(f,"ranged",110);
        assertTrue(choose(f,3,false,false).ready);
    }
    @Test public void zeroRunTargetDoesNotRequireEnergyOrAnotherSweet()throws Exception {
        FcFrame f=frame(100,99,0,sweet);set(f,"ranged",110);
        assertTrue(OptionalRecoveryPolicy.choose(f,false,true,false,0,90,true,true,62,0,false).ready);
    }
    private OptionalRecoveryPolicy.Decision pure(FcFrame f,int debt,boolean pending) {
        return OptionalRecoveryPolicy.choose(f,false,true,true,40,90,true,true,54,debt,pending,true);
    }
    @Test public void recordedFullBaseHpDoesNotWaitForImpossibleOverbrew()throws Exception {
        FcFrame f=frame(100,74,100,sweet);set(f,"maxHp",74);set(f,"maxPrayer",45);set(f,"prayer",45);
        set(f,"ranged",80);set(f,"baseRanged",80);set(f,"magic",70);
        OptionalRecoveryPolicy.Decision result=pure(f,2,false);
        assertTrue(result.ready);assertNull(result.item);assertTrue(result.status.contains("depleted"));
    }
    @Test public void availableBrewAndRestoreStillCompleteOverbrew()throws Exception {
        FcFrame f=frame(100,74,100,brew,restore);set(f,"maxHp",74);
        assertEquals(brew,pure(f,0,false).item);
        set(f,"hp",87);set(f,"ranged",89);assertEquals(restore,pure(f,1,false).item);
        set(f,"ranged",99);assertTrue(pure(f,0,false).ready);
    }
    @Test public void runningOutMidBatchRestoresActualLevelsThenResumes()throws Exception {
        FcFrame f=frame(100,84,100,restore);set(f,"maxHp",74);set(f,"magic",80);
        assertEquals(restore,pure(f,2,false).item);
        set(f,"magic",99);assertTrue(pure(f,0,false).ready);
    }
    @Test public void exhaustedPureSuppliesCannotBlockFurtherWaves()throws Exception {
        FcFrame f=frame(100,40,100);set(f,"prayer",0);set(f,"ranged",70);set(f,"magic",70);
        assertTrue(pure(f,3,false).ready);
        assertFalse(choose(f,3,true,false).ready); // Other optional mode retains its prior policy.
    }
    @Test public void noRestoreSkipsOptionalOverbrewButNotNecessaryHealing()throws Exception {
        FcFrame f=frame(100,99,100,brew);assertTrue(pure(f,0,false).ready);
        set(f,"hp",40);assertEquals(brew,pure(f,3,false).item);
        set(f,"inventory",List.of(restore));set(f,"prayer",0);
        assertEquals(restore,pure(f,3,false).item);
    }
    @Test public void sweetsStillHealToBaseBeforeDepletedRecoveryResumes()throws Exception {
        FcFrame f=frame(100,70,100,sweet);set(f,"maxHp",74);
        assertEquals(sweet,pure(f,0,false).item);set(f,"hp",74);assertTrue(pure(f,0,false).ready);
    }
    @Test public void depletingSupplyRequiresFreshInventoryAndCompletedAcknowledgement()throws Exception {
        FcFrame f=frame(100,99,100);assertFalse(pure(f,1,true).ready);
        set(f,"containersReady",false);assertFalse(pure(f,1,false).ready);
        set(f,"containersReady",true);set(f,"capturedAt",System.currentTimeMillis()-10_000);
        assertFalse(pure(f,1,false).ready);
    }
}
