package net.runelite.client.plugins.microbot.drofirecape;
import java.util.*;
import net.runelite.client.plugins.microbot.drofirecape.core.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.lang.reflect.*;
public class SupplyPolicyTest {
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
        public FcActions.ItemResult drink(FcFrame.ItemSlot i){drinks++;return FcActions.ItemResult.SENT;}
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
    private void set(Object o,String name,Object value)throws Exception {Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}
    private FcFrame supplyFrame(int hp)throws Exception {
        Method factory=EntryStartupTest.class.getDeclaredMethod("frame",int.class,int.class);factory.setAccessible(true);
        FcFrame f=(FcFrame)factory.invoke(null,100,3024);set(f,"hp",hp);set(f,"interactingIndex",-1);
        set(f,"inventory",List.of(new FcFrame.ItemSlot(0,4561,50,"Purple sweets",List.of("Eat")),item(2,"Saradomin brew(4)")));
        return f;
    }
    private boolean invoke(DroFirecapeScript s,String name,FcFrame f)throws Exception {
        Field ownerField=DroFirecapeScript.class.getDeclaredField("tickPrayers");ownerField.setAccessible(true);
        if(ownerField.get(s)==null) {
            FcTickPrayers owner=mock(FcTickPrayers.class);
            when(owner.ownsInput()).thenReturn(true);when(owner.protectionReady()).thenReturn(true);
            when(owner.optionalInputWindow(anyInt(),anyLong())).thenReturn(true);
            ownerField.set(s,owner);
        }
        Method m=DroFirecapeScript.class.getDeclaredMethod(name,FcFrame.class,Protection.class);m.setAccessible(true);
        return (Boolean)m.invoke(s,f,Protection.NONE);
    }
    @Test public void sweetsCheckboxDefersNoncriticalBrewButNeverCriticalBrew()throws Exception {
        for(boolean enabled:List.of(false,true))for(int hp:List.of(55,20)) {
            DroFirecapeScript s=new DroFirecapeScript();FcActions a=mock(FcActions.class);DroFirecapeConfig c=mock(DroFirecapeConfig.class);
            when(c.eatPercent()).thenReturn(60);when(c.usePurpleSweets()).thenReturn(enabled);
            when(a.itemStep(any(),anyString())).thenReturn(FcActions.ItemResult.SENT);
            set(s,"actions",a);set(s,"config",c);FcFrame f=supplyFrame(hp);set(s,"frame",f);
            boolean sent=invoke(s,"supplies",f);assertEquals(!enabled||hp==20,sent);
            verify(a,never()).itemStep(any(),eq("Eat"));
            if(sent)verify(a).itemStep(argThat(i->i.name().equals("Saradomin brew(4)")),eq("Drink"));
        }
    }
    @Test public void sweetPauseClicksVisibleInventoryItemAndStopsAtFullOrNewThreat()throws Exception {
        DroFirecapeScript s=new DroFirecapeScript();FcActions a=mock(FcActions.class);DroFirecapeConfig c=mock(DroFirecapeConfig.class);
        when(c.usePurpleSweets()).thenReturn(true);when(a.itemStep(any(),eq("Eat"))).thenReturn(FcActions.ItemResult.SENT);
        set(s,"actions",a);set(s,"config",c);FcFrame f=supplyFrame(90);set(s,"frame",f);
        assertTrue(invoke(s,"healWithSweets",f));verify(a).itemStep(argThat(i->FcSupplyPolicy.sweet(i.name())),eq("Eat"));
        clearInvocations(a);set(f,"hp",99);assertFalse(invoke(s,"healWithSweets",f));verify(a,never()).itemStep(any(),anyString());
        set(f,"hp",90);set(f,"model",scene(new Mob(1,Kind.MELEER,new Tile(20,20),4,1,1,-1,Protection.MELEE,true)));
        assertFalse(invoke(s,"healWithSweets",f));verify(a,never()).itemStep(any(),anyString());
    }
    @Test public void unavailableSweetsDoNotHoldCombatIndefinitely()throws Exception {
        DroFirecapeScript s=new DroFirecapeScript();FcActions a=mock(FcActions.class);DroFirecapeConfig c=mock(DroFirecapeConfig.class);
        when(c.usePurpleSweets()).thenReturn(true);when(a.itemStep(any(),anyString())).thenReturn(FcActions.ItemResult.UNAVAILABLE);
        set(s,"actions",a);set(s,"config",c);FcFrame f=supplyFrame(90);set(s,"frame",f);
        assertTrue(invoke(s,"healWithSweets",f));set(f,"tick",113);assertFalse(invoke(s,"healWithSweets",f));
    }

    @Test public void rangeBoostWaitsForBrewBatchToSettle() {
        assertFalse(FcSupplyPolicy.rangedRecoveryReady(6885,6882,false));
        assertFalse(FcSupplyPolicy.rangedRecoveryReady(6900,6882,true));
        assertTrue(FcSupplyPolicy.rangedRecoveryReady(6900,6882,false));
    }
    @Test public void baseRangedAfterBrewWaitsThenBoostsWithoutWastingARestore()throws Exception {
        DroFirecapeScript s=new DroFirecapeScript();FcActions a=mock(FcActions.class);DroFirecapeConfig c=mock(DroFirecapeConfig.class);
        when(c.eatPercent()).thenReturn(60);when(c.rangingPotion()).thenReturn(true);
        when(a.itemStep(any(),anyString())).thenReturn(FcActions.ItemResult.SENT);
        set(s,"actions",a);set(s,"config",c);set(s,"brewDebt",1);set(s,"lastBrewTick",97);
        FcFrame f=supplyFrame(80);set(f,"ranged",99);set(f,"baseRanged",99);set(f,"prayer",50);
        set(f,"inventory",List.of(item(3024,"Super restore(4)"),item(2444,"Ranging potion(4)")));set(s,"frame",f);
        assertFalse(invoke(s,"supplies",f));verify(a,never()).itemStep(any(),anyString());
        set(f,"tick",106);assertTrue(invoke(s,"supplies",f));
        verify(a).itemStep(argThat(i->i.name().equals("Ranging potion(4)")),eq("Drink"));
    }
    @Test public void depletedBrewsDoNotLeaveDrainedRangedUnrestored()throws Exception {
        DroFirecapeScript s=new DroFirecapeScript();FcActions a=mock(FcActions.class);DroFirecapeConfig c=mock(DroFirecapeConfig.class);
        when(c.eatPercent()).thenReturn(60);when(a.itemStep(any(),anyString())).thenReturn(FcActions.ItemResult.SENT);
        set(s,"actions",a);set(s,"config",c);set(s,"brewDebt",1);
        FcFrame f=supplyFrame(50);set(f,"ranged",90);set(f,"baseRanged",99);set(f,"prayer",50);
        set(f,"inventory",List.of(item(3024,"Super restore(4)")));set(s,"frame",f);
        assertTrue(invoke(s,"supplies",f));verify(a).itemStep(argThat(i->i.name().equals("Super restore(4)")),eq("Drink"));
    }
    @Test public void exposed37And40HpAreCriticalRegardlessOfSweetsCheckbox()throws Exception {
        for(boolean sweets:List.of(false,true))for(int hp:List.of(37,40)) {
            DroFirecapeScript script=new DroFirecapeScript();FcActions actions=mock(FcActions.class);
            DroFirecapeConfig config=mock(DroFirecapeConfig.class);FcTickPrayers owner=mock(FcTickPrayers.class);
            when(config.eatPercent()).thenReturn(60);when(config.usePurpleSweets()).thenReturn(sweets);
            when(owner.ownsInput()).thenReturn(true);when(owner.protectionReady()).thenReturn(true);
            when(owner.optionalInputWindow(anyInt(),anyLong())).thenReturn(false);
            when(actions.itemStep(any(),anyString())).thenReturn(FcActions.ItemResult.SENT);
            set(script,"actions",actions);set(script,"config",config);set(script,"tickPrayers",owner);
            FcFrame f=supplyFrame(hp);set(f,"maxHp",97);set(f,"model",scene(new Mob(1,Kind.MAGER,new Tile(21,30),5,10,10,98,Protection.MAGIC,true)));
            set(script,"frame",f);assertTrue(invoke(script,"supplies",f));
            verify(actions).itemStep(argThat(i->i.name().startsWith("Saradomin brew(")),eq("Drink"));
        }
    }
    @Test public void preparedHealSurvivesRequiredPrayerAndUsesRemainingClickBudget()throws Exception {
        DroFirecapeScript script=new DroFirecapeScript();FcActions actions=mock(FcActions.class);
        DroFirecapeConfig config=mock(DroFirecapeConfig.class);FcTickPrayers owner=mock(FcTickPrayers.class);
        when(config.eatPercent()).thenReturn(60);when(owner.ownsInput()).thenReturn(true);when(owner.protectionReady()).thenReturn(true);
        when(owner.optionalInputWindow(anyInt(),eq(900L))).thenReturn(true);
        when(owner.optionalInputWindow(anyInt(),eq(300L))).thenReturn(true);
        when(actions.itemStep(any(),anyString())).thenReturn(FcActions.ItemResult.PREPARING,FcActions.ItemResult.SENT);
        set(script,"actions",actions);set(script,"config",config);set(script,"tickPrayers",owner);
        FcFrame f=supplyFrame(55);set(script,"frame",f);assertTrue(invoke(script,"supplies",f));
        when(owner.optionalInputWindow(anyInt(),eq(900L))).thenReturn(false);when(owner.protectionReady()).thenReturn(false);
        assertTrue(invoke(script,"supplies",f));verify(actions,times(1)).itemStep(any(),anyString());
        when(owner.protectionReady()).thenReturn(true);assertTrue(invoke(script,"supplies",f));
        verify(owner,atLeastOnce()).optionalInputWindow(anyInt(),eq(300L));verify(actions,times(2)).itemStep(any(),anyString());
        set(script,"lastSupplyAt",0L);assertTrue(invoke(script,"supplies",f));
        verify(actions,times(2)).itemStep(any(),anyString()); // Await consumption, never duplicate the dose.
    }

}
