/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape.optional;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
import net.runelite.client.plugins.microbot.drofirecape.optional.core.*;
import net.runelite.client.plugins.microbot.drofirecape.optional.core.FcModel.*;

public class BetaTickProtectionTest {
    @Test public void blockedMageDoesNotOverrideExposedRangerDuringMovement() {
        int[][] flags=new int[64][64];
        for(int x=0;x<23;x++)flags[x][25]=CollisionGrid.PROJECTILE_OBJECT;
        CollisionGrid grid=new CollisionGrid(flags);Tile player=new Tile(20,24);
        Mob mage=new Mob(1,Kind.MAGER,new Tile(23,26),5,10,10,-1,Protection.MAGIC,true);
        Mob ranger=new Mob(2,Kind.RANGER,new Tile(15,24),3,10,10,-1,Protection.RANGE,true);
        assertFalse(grid.sight(mage,player));
        assertTrue("A hypothetical move around the wall would reveal the mage",grid.sight(mage,player.add(2,0)));
        Snapshot s=new Snapshot(1,player,grid,List.of(mage,ranger),100,true,5,Protection.NONE);
        TickProtection clock=new TickProtection();clock.beginTick(1,s.mobs());
        for(boolean pure:new boolean[]{false,true}) {
            TickProtection.Decision d=BetaTickProtection.choose(clock,s,true,Protection.MAGIC,Protection.NONE,pure);
            assertEquals(Protection.RANGE,d.protection);
            assertEquals(0,d.dueMask&CombatPlanner.bit(Protection.MAGIC));
        }
        assertEquals("Visible baseline retains its conservative movement envelope",Protection.MAGIC,
            clock.choose(s,true,Protection.MAGIC,Protection.NONE).protection);
    }
    @Test public void outOfRangeMageDoesNotOverrideLiveRangerButRearmsOnceInRange() {
        TickProtection clock=new TickProtection();
        Mob mage=new Mob(1,Kind.MAGER,new Tile(23,39),5,10,10,-1,Protection.MAGIC,true);
        Mob ranger=new Mob(2,Kind.RANGER,new Tile(15,23),3,10,10,-1,Protection.RANGE,true);
        CollisionGrid grid=new CollisionGrid(new int[64][64]);
        Snapshot far=new Snapshot(1,new Tile(20,23),grid,List.of(mage,ranger),100,true,5,Protection.NONE);
        clock.beginTick(1,far.mobs());
        assertEquals(Protection.RANGE,BetaTickProtection.choose(clock,far,true,Protection.MAGIC,Protection.NONE,true).protection);
        Snapshot near=new Snapshot(2,new Tile(20,24),grid,far.mobs(),100,true,5,Protection.NONE);
        clock.beginTick(2,near.mobs());
        assertEquals(Protection.MAGIC,BetaTickProtection.choose(clock,near,true,Protection.RANGE,Protection.NONE,true).protection);
    }
    @Test public void contactBatNeverOverridesCalibratedIncomingRangerShot() {
        TickProtection clock=new TickProtection();
        Mob ranger=new Mob(1,Kind.RANGER,new Tile(25,20),3,10,10,-1,Protection.RANGE,true);
        Mob bat=new Mob(2,Kind.BAT,new Tile(19,20),1,10,10,-1,Protection.MELEE,true);
        for(int tick=1;tick<=9;tick++) {
            if(tick==1||tick==5||tick==9)clock.animation(1,Kind.RANGER,2633);
            clock.beginTick(tick,List.of(ranger,bat));
        }
        for(int tick:new int[]{10,11,12}) {
            Snapshot s=new Snapshot(tick,new Tile(20,20),new CollisionGrid(new int[64][64]),
                List.of(ranger,bat),100,true,5,Protection.NONE);
            TickProtection.Decision d=BetaTickProtection.choose(clock,s,false,Protection.MELEE,Protection.NONE,true);
            assertEquals(tick==10?Protection.MELEE:Protection.RANGE,d.protection);
            if(tick>=11)assertNotEquals(0,d.dueMask&CombatPlanner.bit(Protection.RANGE));
        }
    }
    private Snapshot scene(int tick,Mob mob){return new Snapshot(tick,new Tile(20,20),
        new CollisionGrid(new int[64][64]),List.of(mob),100,true,7,Protection.NONE);}
    private Mob mage(){return new Mob(1,Kind.MAGER,new Tile(25,20),5,10,10,-1,Protection.MAGIC,true);}
    @Test public void nativeTurnsOffInVerifiedGapsAndArmsOneTickBeforeLaunch(){
        TickProtection clock=new TickProtection();Mob mage=mage();
        for(int tick=0;tick<=13;tick++) {
            if(tick==1||tick==5||tick==9||tick==13)clock.animation(1,Kind.MAGER,2647);
            clock.beginTick(tick,List.of(mage));
            Protection beta=BetaTickProtection.choose(clock,scene(tick,mage),true,Protection.MAGIC,Protection.NONE).protection;
            if(tick==10||tick==13)assertEquals(Protection.NONE,beta);
            if(tick==11||tick==12)assertEquals(Protection.MAGIC,beta);
            if(tick==10)assertEquals("Regular selector still holds protection",Protection.MAGIC,
                clock.choose(scene(tick,mage),true,Protection.MAGIC,Protection.NONE).protection);
        }
    }
    @Test public void queuedMeleeAnimationRearmsBeforeActualNextLaunch(){
        TickProtection clock=new TickProtection();
        Mob melee=new Mob(1,Kind.MELEER,new Tile(19,20),4,10,10,-1,Protection.MELEE,true);
        // Animation event ticks 0/4/8 are consumed by frames 1/5/9.
        for(int tick=1;tick<=11;tick++) {
            if(tick==1||tick==5||tick==9)clock.animation(1,Kind.MELEER,2637);
            clock.beginTick(tick,List.of(melee));
        }
        assertEquals(Protection.MELEE,BetaTickProtection.choose(clock,scene(11,melee),false,
            Protection.NONE,Protection.NONE).protection);
    }
    @Test public void unknownBabyWinsVerifiedRangerCooldownGap(){
        TickProtection clock=new TickProtection();
        Mob ranger=new Mob(1,Kind.RANGER,new Tile(25,20),3,10,10,-1,Protection.RANGE,true);
        for(int tick=1;tick<=10;tick++) {
            if(tick==1||tick==5||tick==9)clock.animation(1,Kind.RANGER,2633);
            clock.beginTick(tick,List.of(ranger));
        }
        Mob baby=new Mob(2,Kind.BABY,new Tile(19,20),1,10,10,-1,Protection.MELEE,true);
        Snapshot scene=new Snapshot(10,new Tile(20,20),new CollisionGrid(new int[64][64]),
            List.of(ranger,baby),100,true,7,Protection.NONE);
        assertEquals(Protection.MELEE,BetaTickProtection.choose(clock,scene,false,
            Protection.RANGE,Protection.NONE).protection);
        assertEquals(Protection.RANGE,clock.choose(scene,false,Protection.RANGE,Protection.NONE).protection);
    }
    @Test public void observedRangerPunchUsesMeleeInContact(){
        TickProtection clock=new TickProtection();
        Mob ranger=new Mob(1,Kind.RANGER,new Tile(19,20),1,10,10,9,Protection.MELEE,true);
        clock.beginTick(10,List.of(ranger));
        assertEquals(Protection.MELEE,BetaTickProtection.choose(clock,scene(10,ranger),false,
            Protection.RANGE,Protection.NONE).protection);
    }
    @Test public void unknownFirstContactRetainsProtectionUntilTimingIsKnown(){
        TickProtection clock=new TickProtection();Mob mage=mage();clock.beginTick(1,List.of(mage));
        TickProtection.Decision d=BetaTickProtection.choose(clock,scene(1,mage),false,Protection.NONE,Protection.NONE);
        assertEquals(Protection.MAGIC,d.protection);assertNotEquals(0,d.uncertainMask);
    }
    @Test public void jadKeepsExistingWindupAndHealerPriority(){
        TickProtection clock=new TickProtection();Mob jad=new Mob(1,Kind.JAD,new Tile(25,20),5,10,10,-1,Protection.MAGIC,true);
        clock.animation(1,Kind.JAD,2656);clock.beginTick(1,List.of(jad));
        TickProtection.Decision original=clock.chooseNative(scene(1,jad),false,Protection.MAGIC,Protection.NONE);
        assertEquals(original.protection,BetaTickProtection.choose(clock,scene(1,jad),false,Protection.MAGIC,Protection.NONE).protection);
    }
    @Test public void pureTinyUsesFirstObservedAttackAndInvalidatesContactBreak() {
        TickProtection clock=new TickProtection();
        Mob baby=new Mob(2,Kind.BABY,new Tile(19,20),1,10,10,-1,Protection.MELEE,true);
        clock.animation(2,Kind.BABY,2625);clock.beginTick(1,List.of(baby));
        assertEquals(Protection.NONE,BetaTickProtection.choose(clock,scene(1,baby),false,Protection.MELEE,Protection.NONE,true).protection);
        assertEquals(Protection.MELEE,BetaTickProtection.choose(clock,scene(1,baby),false,Protection.MELEE,Protection.NONE).protection);
        clock.beginTick(3,List.of(baby));
        assertEquals(Protection.MELEE,BetaTickProtection.choose(clock,scene(3,baby),false,Protection.NONE,Protection.NONE,true).protection);
        Mob away=new Mob(2,Kind.BABY,new Tile(15,20),1,10,10,-1,Protection.MELEE,true);
        BetaTickProtection.choose(clock,scene(3,away),false,Protection.NONE,Protection.NONE,true);
        assertNotEquals(0,BetaTickProtection.choose(clock,scene(3,baby),false,Protection.NONE,Protection.NONE,true).uncertainMask);
    }
    @Test public void pureProtectsRangedLaunchBeforeUnknownBigMeleeThenUsesCooldownGap() {
        TickProtection clock=new TickProtection();
        Mob ranger=new Mob(1,Kind.RANGER,new Tile(25,20),3,10,10,-1,Protection.RANGE,true);
        Mob big=new Mob(2,Kind.MELEER,new Tile(16,20),4,10,10,-1,Protection.MELEE,true);
        for(int tick=1;tick<=9;tick++) {
            if(tick==1||tick==5||tick==9)clock.animation(1,Kind.RANGER,2633);
            clock.beginTick(tick,List.of(ranger,big));
        }
        for(int tick:new int[]{10,11}) {
            Snapshot s=new Snapshot(tick,new Tile(20,20),new CollisionGrid(new int[64][64]),List.of(ranger,big),100,true,7,Protection.NONE);
            assertEquals(tick==10?Protection.MELEE:Protection.RANGE,
                BetaTickProtection.choose(clock,s,false,Protection.MELEE,Protection.NONE,true).protection);
        }
    }
    @Test public void purePrearmsRangerContactBeforeFirstPunchAnimation() {
        TickProtection clock=new TickProtection();
        Mob ranger=new Mob(1,Kind.RANGER,new Tile(17,20),3,10,10,-1,Protection.RANGE,true);
        clock.beginTick(1,List.of(ranger));
        assertEquals(Protection.MELEE,BetaTickProtection.choose(clock,scene(1,ranger),true,Protection.RANGE,Protection.NONE,true).protection);
    }

}
