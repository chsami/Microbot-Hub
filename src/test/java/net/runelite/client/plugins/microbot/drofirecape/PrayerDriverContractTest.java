/* Copyright (c) 2026, DRO (droplugins). SPDX-License-Identifier: BSD-2-Clause */
package net.runelite.client.plugins.microbot.drofirecape;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.*;
import net.runelite.client.plugins.microbot.drofirecape.core.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.Tile;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class PrayerDriverContractTest {
    private static class Driver extends FcPrayers {
        Driver(FirecapeTestClient c,AtomicLong clock,FcInput input,BowPrayerClock attacks){super(c.client,s->{},clock::get,input,attacks);}
        @Override protected <T>T onClientThread(Callable<T> c,T fallback){try{return c.call();}catch(Exception e){throw new AssertionError(e);}}
        void select(){selectedMask=1<<3;}
        boolean offence(FcFrame f,boolean motion){return offenceNeeded(f,motion);}
    }
    private static class Fixture {
        final FirecapeTestClient state=new FirecapeTestClient();final AtomicLong clock=new AtomicLong(1_000_000_000L);
        final AtomicReference<FcFightInput> mode=new AtomicReference<>(FcFightInput.INVOKES);
        final BowPrayerClock attacks=new BowPrayerClock();
        final Driver driver;
        Fixture(){state.instance=true;driver=new Driver(state,clock,new FcInput(mode::get,null,()->true),attacks);}
        FcFrame frame(int tick,List<Mob> mobs)throws Exception {return state.frame(tick,mobs,Protection.MAGIC);}
        void begin(int tick)throws Exception{driver.gameTick(frame(tick,List.of()),Protection.MAGIC,clock.get());clock.addAndGet(30_000_000L);}
        void plan(){driver.clientTick(true,false);}
    }
    @Test public void invokeSendsOneGuardedProtectionOnAndReadinessNeedsTheNextTickObservation()throws Exception {
        Fixture f=new Fixture();f.begin(100);f.plan();
        assertEquals(1,f.state.menus.size());
        List<Object> sent=f.state.menus.get(0);
        assertEquals(Rs2PrayerEnum.PROTECT_MAGIC.getIndex(),sent.get(1));
        assertEquals(MenuAction.CC_OP,sent.get(2));assertEquals("Activate",sent.get(5));
        assertTrue(f.driver.protectionReady(), "An ON sent on this tick may be relied on");
        f.plan();assertEquals(1,f.state.menus.size(), "One flick, not a repeated toggle");
        f.clock.addAndGet(570_000_000L);f.begin(101);f.plan();
        assertFalse(f.driver.protectionReady());
        f.state.varbits.put(Rs2PrayerEnum.PROTECT_MAGIC.getVarbit(),1);f.plan();assertTrue(f.driver.protectionReady());
    }
    @Test public void naturalMouseQueuesForTheWorkerAndNeverPressesFromClientTick()throws Exception {
        Fixture f=new Fixture();f.mode.set(FcFightInput.NATURAL_MOUSE);f.begin(100);f.plan();
        assertTrue(f.state.menus.isEmpty());assertNotNull(f.driver.queued.get());
        assertFalse(f.driver.protectionReady());
    }
    @Test public void outsideTheCaveLeaseInvokesNeverReplaceTheMouse()throws Exception {
        FirecapeTestClient state=new FirecapeTestClient();state.instance=true;AtomicLong clock=new AtomicLong(1_000_000_000L);
        Driver d=new Driver(state,clock,new FcInput(()->FcFightInput.INVOKES,null,()->false),null);
        d.gameTick(state.frame(100,List.of(),Protection.MAGIC),Protection.MAGIC,clock.get());clock.addAndGet(30_000_000L);
        d.clientTick(true,false);assertTrue(state.menus.isEmpty());assertNotNull(d.queued.get());
    }
    @Test public void liveCheckDropsAnAlreadySatisfiedPrayerAtDelivery()throws Exception {
        FirecapeTestClient state=new FirecapeTestClient();
        assertFalse(FcInput.invokeNow(state.client,FcPrayers.entry(Rs2PrayerEnum.PROTECT_MAGIC,true,()->false)));
        assertTrue(state.menus.isEmpty());
        assertTrue(FcInput.invokeNow(state.client,FcPrayers.entry(Rs2PrayerEnum.PROTECT_MAGIC,true,()->true)));
        assertEquals(1,state.menus.size());
    }
    @Test public void detachedOwnerDropsQueuedAndFutureInput()throws Exception {
        Fixture f=new Fixture();f.begin(10);f.driver.detach();f.plan();
        f.driver.gameTick(f.frame(11,List.of()),Protection.MAGIC,f.clock.get());f.plan();f.driver.pulse();
        assertFalse(f.driver.ownsInput());assertFalse(f.driver.protectionReady());assertTrue(f.state.menus.isEmpty());
    }
    @Test public void zeroPrayerPermitsCombatAndRestorationRearmsProtection()throws Exception {
        Fixture f=new Fixture();f.state.prayer=0;f.begin(100);f.plan();
        assertFalse(f.driver.protectionReady());assertTrue(f.driver.exhaustedCombatReady());
        assertTrue(f.driver.attackInputWindow(100,180));assertEquals(Protection.NONE,f.driver.requested());
        f.driver.clientTick(false,false);assertFalse(f.driver.exhaustedCombatReady());
        f.state.prayer=1;f.clock.addAndGet(570_000_000L);f.begin(101);f.plan();
        assertFalse(f.driver.exhaustedCombatReady());assertEquals(Protection.MAGIC,f.driver.requested());
    }
    @Test public void offenceIsOnlyForLearnedShotsAtWorthwhileTargets()throws Exception {
        Fixture f=new Fixture();f.driver.select();
        Mob ranger=new Mob(1,Kind.RANGER,new Tile(22,28),3,10,10,-1,Protection.RANGE,true);
        Mob bat=new Mob(2,Kind.BAT,new Tile(28,27),1,10,10,-1,Protection.MELEE,true);
        FcFrame probe=f.frame(100,List.of(ranger,bat));int weapon=probe.weaponId,style=probe.attackStyle;
        FcFrame first=f.frame(104,List.of(ranger,bat));FirecapeTestClient.set(first,"interactingIndex",1);
        assertFalse(f.driver.offence(first,false), "No shot seen yet: the first shot is never boosted");
        for(int tick:new int[]{100,105})f.attacks.swing(tick,weapon,style,426,false);
        FcFrame frame=f.frame(109,List.of(ranger,bat));FirecapeTestClient.set(frame,"interactingIndex",1);
        assertTrue(f.driver.offence(frame,false));assertFalse(f.driver.offence(frame,true));
        FirecapeTestClient.set(frame,"interactingIndex",2);assertFalse(f.driver.offence(frame,false), "Bats never get offence");
        FirecapeTestClient.set(frame,"interactingIndex",-1);assertFalse(f.driver.offence(frame,false));
    }
    @Test public void offenceFlicksOnBeforeEachLearnedShotAndOffBetweenThem()throws Exception {
        Fixture f=new Fixture();f.driver.select();
        Mob ranger=new Mob(1,Kind.RANGER,new Tile(22,28),3,10,10,99,Protection.RANGE,true);
        FcFrame probe=f.frame(100,List.of(ranger));
        int weapon=probe.weaponId,style=probe.attackStyle;
        for(int tick:new int[]{100,105,110})f.attacks.swing(tick,weapon,style,426,false);
        Map<Integer,Boolean> on=new LinkedHashMap<>();
        for(int tick=110;tick<=115;tick++) {
            FcFrame frame=f.frame(tick,List.of(ranger));FirecapeTestClient.set(frame,"interactingIndex",1);
            on.put(tick,f.driver.offence(frame,false));
        }
        // Off on the observed shot tick, on the tick before the next (115), held while it is late.
        assertEquals(Map.of(110,false,111,false,112,false,113,false,114,true,115,true),on);
    }
    @Test public void movementPrimeRetainsMagicWhenAMagerIsPresent()throws Exception {
        Fixture f=new Fixture();
        Mob mager=new Mob(1,Kind.MAGER,new Tile(22,28),4,10,10,99,Protection.MAGIC,true);
        f.driver.gameTick(f.frame(100,List.of(mager)),Protection.NONE,f.clock.get());f.clock.addAndGet(30_000_000L);f.plan();
        f.driver.primeMovement(100,Protection.RANGE);
        assertTrue(f.driver.movementPrearm);
    }
}
