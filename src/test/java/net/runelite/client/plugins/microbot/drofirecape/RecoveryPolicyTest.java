/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape;

import java.lang.reflect.*;
import java.util.*;
import net.runelite.client.plugins.microbot.drofirecape.core.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class RecoveryPolicyTest {
    static void set(Object o,String name,Object value)throws Exception {Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}
    static FcFrame.ItemSlot item(int id,String name){return new FcFrame.ItemSlot(0,id,1,name,List.of(name.equals("Purple sweets")?"Eat":"Drink"));}
    static FcFrame frame(int tick,int hp,int energy,FcFrame.ItemSlot...items)throws Exception {
        Method factory=FirecapeTestClient.class.getDeclaredMethod("frame",int.class,int.class);factory.setAccessible(true);
        FcFrame f=(FcFrame)factory.invoke(null,tick,3024);set(f,"cave",true);set(f,"containersReady",true);
        set(f,"hp",hp);set(f,"rawEnergy",energy*100);set(f,"inventory",List.of(items));
        set(f,"model",new Snapshot(tick,new Tile(30,30),new CollisionGrid(new int[104][104]),List.of(),energy,false,5,Protection.MAGIC));
        return f;
    }
    private FcRecoveryPolicy.Decision choose(FcFrame f,int debt,boolean overbrew,boolean pending) {
        return FcRecoveryPolicy.choose(f,false,true,overbrew,40,90,true,true,62,debt,pending);
    }
    private final FcFrame.ItemSlot sweet=item(4561,"Purple sweets"),brew=item(6685,"Saradomin brew(4)"),
        restore=item(3024,"Super restore(4)"),range=item(2444,"Ranging potion(4)");
    @Test public void fullHpLowRunUsesSweetsButReadyRunLowHpStillHeals()throws Exception {
        assertEquals(sweet,choose(frame(100,99,5,sweet,brew,restore,range),0,false,false).item);
        assertEquals(brew,choose(frame(100,40,100,brew,restore,range),0,false,false).item);
    }
    @Test public void runRecoveryFinishesBeforeFinalOverbrew()throws Exception {
        FcRecoveryPolicy.Decision wait=choose(frame(100,99,5,brew,restore,range),0,true,false);
        assertNull(wait.item);assertFalse(wait.ready);assertTrue(wait.status.contains("run energy"));
        assertEquals(brew,choose(frame(100,99,40,brew,restore,range),0,true,false).item);
        assertEquals(115,FcRecoveryPolicy.hpTarget(99,true));
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
        assertFalse(choose(f,0,false,false).ready);assertNull(choose(f,0,false,false).item);
        assertNotNull(FcRecoveryPolicy.choose(f,false,false,false,0,90,true,true,63,0,false).item);
    }
    @Test public void oldBrewDebtAloneDoesNotConsumeRestoreWhenActualTargetsAreReady()throws Exception {
        FcFrame f=frame(100,99,100,restore,range);set(f,"ranged",110);
        assertTrue(choose(f,3,false,false).ready);
    }
    @Test public void zeroRunTargetDoesNotRequireEnergyOrAnotherSweet()throws Exception {
        FcFrame f=frame(100,99,0,sweet);set(f,"ranged",110);
        assertTrue(FcRecoveryPolicy.choose(f,false,true,false,0,90,true,true,62,0,false).ready);
    }
}
