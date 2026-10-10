/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape;
import net.runelite.client.plugins.microbot.drofirecape.*;
import net.runelite.client.plugins.microbot.drofirecape.DroFirecapeConfig;
import java.util.*;
import net.runelite.client.plugins.microbot.drofirecape.core.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.*;
public class OptionalSupplyPolicyTest {
    private FcFrame.ItemSlot item(int id,String name){return new FcFrame.ItemSlot(0,id,1,name,List.of("Drink"));}
    @Test public void fullPotionsCountEveryDoseAndReserveThreeUntil53ThenOneUntilJad() {
        int doses=FcSupplyPolicy.rangedDoses(List.of(item(1,"Ranging potion(4)"),item(1,"Ranging potion(4)"),item(2,"Saradomin brew(4)")));
        assertEquals(8,doses);
        assertTrue(FcSupplyPolicy.rangedDoseAllowed(52,99,99,4));assertFalse(FcSupplyPolicy.rangedDoseAllowed(52,99,99,3));
        assertTrue(FcSupplyPolicy.rangedDoseAllowed(53,99,99,3));assertFalse(FcSupplyPolicy.rangedDoseAllowed(62,99,99,1));
        assertTrue(FcSupplyPolicy.rangedDoseAllowed(63,99,99,1));assertFalse(FcSupplyPolicy.rangedDoseAllowed(63,100,99,4));
        assertFalse(FcSupplyPolicy.rangedDoseAllowed(0,99,99,3));
    }
    private Snapshot scene(Mob...m){return new Snapshot(100,new Tile(30,30),new CollisionGrid(new int[64][64]),List.of(m),100,true,7,Protection.NONE);}
    @Test public void sweetsNeedAnActuallySafePauseNotAProtectedOrDistantApproachingEnemy() {
        assertTrue(FcSupplyPolicy.sweetPauseSafe(scene()));
        assertFalse(FcSupplyPolicy.sweetPauseSafe(scene(new Mob(1,Kind.MELEER,new Tile(20,20),4,1,1,-1,Protection.MELEE,true))));
        assertFalse(FcSupplyPolicy.sweetPauseSafe(scene(new Mob(1,Kind.MAGER,new Tile(21,30),5,1,1,99,Protection.MAGIC,true))));
        assertFalse(FcSupplyPolicy.sweetPauseSafe(scene(new Mob(1,Kind.JAD,new Tile(2,2),5,1,1,-1,Protection.MAGIC,true))));
    }
    @Test public void brewThresholdAccountsForCriticalHealthAndActiveMaximumHit() {
        assertFalse(FcSupplyPolicy.criticalHealth(scene(),55,99));assertTrue(FcSupplyPolicy.criticalHealth(scene(),30,99));
        assertTrue(FcSupplyPolicy.criticalHealth(scene(new Mob(1,Kind.MAGER,new Tile(21,30),5,1,1,98,Protection.MAGIC,true)),50,99));
    }
    private static class Bank implements FcRangePrepot.Input {
        boolean open=true,stocked=true;int drinks,deposits,withdraws;String name;
        public boolean bankOpen(){return open;}public void closeBank(){open=false;}public void openBank(){open=true;}
        public OptionalActions.ItemResult drink(FcFrame.ItemSlot i){drinks++;return OptionalActions.ItemResult.SENT;}
        public boolean stocked(String n){return stocked;}public void deposit(int id){deposits++;}
        public void withdraw(String n){withdraws++;name=n;}
    }
    @Test public void preDoseRequiresConsumptionDepositAndFullRefillAcknowledgements() {
        FcRangePrepot p=new FcRangePrepot();Bank b=new Bank();
        List<FcFrame.ItemSlot> full=List.of(item(1,"Ranging potion(4)"),item(1,"Ranging potion(4)"));
        List<FcFrame.ItemSlot> used=List.of(item(1,"Ranging potion(4)"),item(2,"Ranging potion(3)"));
        assertFalse(p.step(1,99,99,full,b));assertFalse(b.open);
        assertFalse(p.step(2,99,99,full,b));assertEquals(1,b.drinks);
        assertFalse(p.step(3,99,99,full,b));assertEquals(0,b.deposits);
        p.step(4,112,99,used,b);p.step(5,112,99,used,b);p.step(6,112,99,used,b);p.step(7,112,99,used,b);
        assertEquals(1,b.deposits);assertEquals(0,b.withdraws);
        p.step(8,112,99,used,b);assertEquals(0,b.withdraws);
        List<FcFrame.ItemSlot> deposited=List.of(item(1,"Ranging potion(4)"));
        p.step(9,112,99,deposited,b);p.step(10,112,99,deposited,b);assertEquals(1,b.withdraws);
        assertEquals("Ranging potion(4)",b.name);assertFalse(p.step(11,112,99,deposited,b));
        assertTrue(p.step(12,112,99,full,b));assertFalse(p.failed());assertEquals(1,b.drinks);
    }
    @Test public void missingRefillFailsClearlyAndPreexistingBoostDoesNotConsumeAnotherDose() {
        FcRangePrepot p=new FcRangePrepot();Bank b=new Bank();List<FcFrame.ItemSlot> full=List.of(item(1,"Ranging potion(4)"));
        assertTrue(p.step(1,110,99,full,b));assertEquals(0,b.drinks);
        p.reset();p.step(1,99,99,full,b);p.step(2,99,99,full,b);
        List<FcFrame.ItemSlot> used=List.of(item(2,"Ranging potion(3)"));
        p.step(3,110,99,used,b);p.step(4,110,99,used,b);b.stocked=false;p.step(5,110,99,used,b);
        assertTrue(p.failed());assertEquals(0,b.deposits);
    }
    @Test public void unobservedDoseIsNeverRepeatedOrInventedAsConsumed() {
        FcRangePrepot p=new FcRangePrepot();Bank b=new Bank();List<FcFrame.ItemSlot> full=List.of(item(1,"Bastion potion(4)"));
        p.step(1,99,99,full,b);p.step(2,99,99,full,b);p.step(33,99,99,full,b);
        assertTrue(p.failed());assertEquals(1,b.drinks);assertEquals(0,b.deposits);
    }
}
