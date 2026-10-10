/* Copyright (c) 2026, DRO (droplugins). SPDX-License-Identifier: BSD-2-Clause */
package net.runelite.client.plugins.microbot.drofirecape;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import net.runelite.api.*;
import net.runelite.client.plugins.microbot.drofirecape.core.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.Tile;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PrayerDriverContractTest {
    private static class Actions extends FcActions {
        final List<OneTickPrayerCycle.Command> clicks=new ArrayList<>();
        @Override FcPrayerUi.Result tickPrayer(OneTickPrayerCycle.Command c,Rs2PrayerEnum p,BooleanSupplier early,BooleanSupplier valid) {
            if(!valid.getAsBoolean())return FcPrayerUi.Result.SKIPPED;
            clicks.add(c);return FcPrayerUi.Result.SENT;
        }
        @Override boolean prepareTickPrayer(Rs2PrayerEnum p,BooleanSupplier valid){return valid.getAsBoolean();}
    }
    private static class Regular extends FcTickPrayers {
        Regular(FirecapeTestClient c,AtomicLong clock){super(c.client,false,s->{},clock::get);}
        @Override protected <T>T onClientThread(Callable<T> c,T fallback){try{return c.call();}catch(Exception e){throw new AssertionError(e);}}
    }
    private static class Pure extends OptionalTickPrayers {
        Pure(FirecapeTestClient c,AtomicLong clock){super(c.client,false,s->{},clock::get);}
        @Override protected <T>T onClientThread(Callable<T> c,T fallback){try{return c.call();}catch(Exception e){throw new AssertionError(e);}}
        void select(){selectedMask=1<<3;}
        boolean offence(FcFrame f,boolean motion){return offenceNeeded(f,true,motion);}
    }
    private static class Fixture {
        final FirecapeTestClient state=new FirecapeTestClient();final AtomicLong clock=new AtomicLong(1_000_000_000L);
        final Actions actions=new Actions();final FcTickPrayers driver;
        Fixture(boolean pure){state.instance=true;driver=pure?new Pure(state,clock):new Regular(state,clock);}
        FcFrame frame(int tick,List<Mob> mobs)throws Exception {return state.frame(tick,mobs,Protection.MAGIC);}
        void begin(int tick)throws Exception{driver.gameTick(frame(tick,List.of()),Protection.MAGIC,clock.get());clock.addAndGet(30_000_000L);}
        void plan(){driver.clientTick(true,false,false);}
    }
    @Test void visibleRequestNeedsObservedProtectionAndNeverDispatchesFromClientTick()throws Exception {
        for(boolean pure:List.of(false,true)) {
            Fixture f=new Fixture(pure);f.begin(10);f.plan();
            assertFalse(f.driver.protectionReady());assertTrue(f.actions.clicks.isEmpty());assertTrue(f.state.menus.isEmpty());
            f.driver.pulse(f.actions);assertEquals(1,f.actions.clicks.size());assertEquals(OneTickPrayerCycle.Type.ON,f.actions.clicks.get(0).type);
            assertFalse(f.driver.protectionReady());
            f.state.varbits.put(Rs2PrayerEnum.PROTECT_MAGIC.getVarbit(),1);f.plan();assertTrue(f.driver.protectionReady());
        }
    }
    @Test void detachedOwnerDropsQueuedAndFutureInput()throws Exception {
        for(boolean pure:List.of(false,true)) {
            Fixture f=new Fixture(pure);f.begin(10);f.plan();f.driver.detach();f.driver.pulse(f.actions);
            f.driver.gameTick(f.frame(11,List.of()),Protection.MAGIC,f.clock.get());f.plan();f.driver.pulse(f.actions);
            assertFalse(f.driver.ownsInput());assertFalse(f.driver.protectionReady());assertTrue(f.actions.clicks.isEmpty());
        }
    }
    @Test void pureNativeDispatchOccursOnceAndPermissionRequiresNextTickObservation()throws Exception {
        Fixture f=new Fixture(true);Pure p=(Pure)f.driver;p.nativeWidgets(true);f.begin(100);f.plan();p.pulse(f.actions);
        assertEquals(1,f.state.menus.size());assertEquals(MenuAction.CC_OP,f.state.menus.get(0).get(2));
        assertEquals("Activate",f.state.menus.get(0).get(5));assertTrue(f.actions.clicks.isEmpty());assertTrue(p.protectionReady());
        f.plan();assertEquals(1,f.state.menus.size());f.clock.addAndGet(570_000_000L);f.begin(101);f.plan();
        assertFalse(p.protectionReady());f.state.varbits.put(Rs2PrayerEnum.PROTECT_MAGIC.getVarbit(),1);f.plan();assertTrue(p.protectionReady());
    }
    @Test void zeroPrayerPermitsOnlyPureCombatAndRestorationRearmsProtection()throws Exception {
        Fixture regular=new Fixture(false);regular.state.prayer=0;regular.begin(100);regular.plan();assertFalse(regular.driver.attackInputWindow(100,180));
        Fixture f=new Fixture(true);Pure p=(Pure)f.driver;p.pureTinyPrayers(true);f.state.prayer=0;f.begin(100);f.plan();
        assertFalse(p.protectionReady());assertTrue(p.exhaustedCombatReady());assertTrue(p.attackInputWindow(100,180));assertEquals(Protection.NONE,p.requested());
        p.clientTick(false,false,false);assertFalse(p.exhaustedCombatReady());assertFalse(p.attackInputWindow(100,180));
        f.state.prayer=1;f.clock.addAndGet(570_000_000L);f.begin(101);f.plan();assertFalse(p.exhaustedCombatReady());assertEquals(Protection.MAGIC,p.requested());
    }
    @Test void conservationUsesActualOrPrimedTargetAndMovementDisarmsOffence()throws Exception {
        Fixture f=new Fixture(true);Pure p=(Pure)f.driver;p.select();p.conservation(true);
        Mob ranger=new Mob(1,Kind.RANGER,new Tile(22,28),3,10,10,99,Protection.RANGE,true);
        Mob bat=new Mob(2,Kind.BAT,new Tile(28,27),1,10,10,99,Protection.MELEE,true);
        FcFrame frame=f.frame(100,List.of(ranger,bat));FirecapeTestClient.set(frame,"interactingIndex",1);
        assertTrue(p.offence(frame,false));assertFalse(p.offence(frame,true));
        FirecapeTestClient.set(frame,"interactingIndex",2);assertFalse(p.offence(frame,false));
        FirecapeTestClient.set(frame,"interactingIndex",-1);p.primeAttack(100,1);assertTrue(p.offence(frame,false));
        p.primeAttack(100,2);assertFalse(p.offence(frame,false));
        p.conservation(false);assertTrue(p.offence(frame,false));
    }
    @Test void regularAndPurePrayerOwnersHaveIndependentState()throws Exception {
        Fixture r=new Fixture(false),p=new Fixture(true);r.begin(100);p.begin(100);r.plan();p.plan();
        r.driver.detach();assertFalse(r.driver.ownsInput());assertTrue(p.driver.ownsInput());
        p.driver.pulse(p.actions);assertEquals(1,p.actions.clicks.size());assertTrue(r.actions.clicks.isEmpty());
    }
}
