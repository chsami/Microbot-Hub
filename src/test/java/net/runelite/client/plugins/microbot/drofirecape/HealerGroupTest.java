/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape;

import java.util.*;
import net.runelite.client.plugins.microbot.drofirecape.core.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class HealerGroupTest {
    private static Mob healer(int index,boolean tagged) {
        return new Mob(index,Kind.HEALER,new Tile(34+index%4,30),1,10,10,-1,Protection.MELEE,tagged);
    }
    private static Snapshot scene(int tick,int...tagged) {
        Set<Integer> tags=new HashSet<>();for(int index:tagged)tags.add(index);
        List<Mob> mobs=new ArrayList<>();
        mobs.add(new Mob(45,Kind.JAD,new Tile(40,30),5,30,30,tick-2,Protection.MAGIC,true));
        for(int index=1;index<=4;index++)mobs.add(healer(index,tags.contains(index)));
        return new Snapshot(tick,new Tile(30,30),new CollisionGrid(new int[64][64]),mobs,100,false,7,Protection.MAGIC);
    }
    private static Snapshot without(Snapshot scene,int...indices) {
        Set<Integer> removed=new HashSet<>();for(int index:indices)removed.add(index);
        List<Mob> mobs=new ArrayList<>();for(Mob mob:scene.mobs())if(!removed.contains(mob.index()))mobs.add(mob);
        return new Snapshot(scene.tick(),scene.player(),scene.grid(),mobs,100,false,7,Protection.MAGIC);
    }
    private static HealerGroup allTagged() {
        HealerGroup group=new HealerGroup();group.observe(scene(100),Set.of());
        group.observe(scene(101,1,2,3,4),Set.of());group.observe(scene(102,1,2,3,4),Set.of());
        assertEquals(HealerGroup.Phase.LURING,group.phase());return group;
    }
    @Test public void clickDoesNotConfirmAggroOrPermitFirstHealerPull() {
        HealerGroup group=new HealerGroup();group.observe(scene(100),Set.of());group.tagRequested(1,100);
        assertFalse(group.confirmed(1));assertEquals(4,group.remaining());
        group.observe(scene(101),Set.of(1,2,3,4));
        assertEquals(1,group.pending());assertEquals(HealerGroup.Phase.TAGGING,group.phase());
        assertEquals(HealerGroup.Ack.CONFIRMED,group.observe(scene(102,1),Set.of(2,3,4)));
        assertEquals(-1,group.pending());assertEquals(3,group.remaining());
        assertEquals(HealerGroup.Phase.TAGGING,group.phase());
    }
    @Test public void chasingHealerWithNoMeleeAnimationAlreadyCountsAsTagged() {
        HealerGroup group=new HealerGroup();group.observe(scene(100),Set.of());group.tagRequested(1,100);
        Snapshot base=scene(101);List<Mob> mobs=new ArrayList<>(base.mobs());mobs.removeIf(m->m.index()==1);
        mobs.add(new Mob(1,Kind.HEALER,new Tile(50,48),1,10,10,-1,Protection.MELEE,true));
        Snapshot chasing=new Snapshot(101,base.player(),base.grid(),mobs,100,false,7,Protection.MAGIC);
        assertEquals(HealerGroup.Ack.CONFIRMED,group.observe(chasing,Set.of(2,3,4)));
        assertTrue(group.confirmed(1));assertEquals(-1,group.pending());assertEquals(3,group.remaining());
    }
    @Test public void everyTagMustBeObservedAndOneFreshCompleteFramePrecedesLure() {
        HealerGroup group=new HealerGroup();group.observe(scene(100),Set.of());
        for(int index=1;index<=4;index++) {
            group.tagRequested(index,100+index);
            int[] tags=new int[index];for(int i=0;i<index;i++)tags[i]=i+1;
            assertEquals(HealerGroup.Ack.CONFIRMED,group.observe(scene(100+index,tags),Set.of()));
            assertEquals(HealerGroup.Phase.TAGGING,group.phase());
        }
        group.observe(scene(104,1,2,3,4),Set.of());assertEquals(HealerGroup.Phase.TAGGING,group.phase());
        group.observe(scene(105,1,2,3,4),Set.of());assertEquals(HealerGroup.Phase.LURING,group.phase());
    }
    @Test public void returningHealerReopensCollectionDuringPullOrFight() {
        for(boolean fighting:new boolean[]{false,true}) {
            HealerGroup group=allTagged();if(fighting)group.lureComplete();
            group.observe(scene(103,1,2,3,4),Set.of(2));
            assertEquals(HealerGroup.Phase.TAGGING,group.phase());assertFalse(group.confirmed(2));
            assertTrue(group.confirmed(1));assertEquals(1,group.remaining());
        }
    }
    @Test public void confirmationPersistsThroughIdleInteractionButNotReturnToJad() {
        HealerGroup group=allTagged();group.observe(scene(103),Set.of());
        assertEquals(0,group.remaining());assertEquals(HealerGroup.Phase.LURING,group.phase());
        group.observe(scene(104),Set.of(3));assertEquals(1,group.remaining());
    }
    @Test public void spawnBeforeTheNextFrameInvalidatesGroupAndReusedIndex() {
        HealerGroup group=allTagged();group.lureComplete();group.spawned(2,103);
        assertEquals(HealerGroup.Phase.TAGGING,group.phase());assertFalse(group.confirmed(2));
        group.observe(scene(103,1,2,3,4),Set.of());
        assertFalse(group.confirmed(2));assertFalse(group.fresh(103));
        group.observe(scene(104,1,3,4),Set.of(2));assertEquals(1,group.remaining());
        assertEquals(HealerGroup.Phase.TAGGING,group.phase());
    }
    @Test public void fullyClearedGroupDoesNotSuppressASecondCohort() {
        HealerGroup group=allTagged();group.lureComplete();
        group.observe(without(scene(103),1,2,3,4),Set.of());assertEquals(HealerGroup.Phase.IDLE,group.phase());
        group.observe(scene(104),Set.of(1,2,3,4));
        assertEquals(HealerGroup.Phase.TAGGING,group.phase());assertEquals(4,group.remaining());
    }
    @Test public void newSpawnCancelsPendingTagForItsReusedIndex() {
        HealerGroup group=new HealerGroup();group.observe(scene(100),Set.of());group.tagRequested(1,100);
        group.spawned(1,101);assertEquals(-1,group.pending());assertFalse(group.confirmed(1));
    }
    @Test public void disappearedPendingTargetDoesNotInventAggroOrBlockOtherTags() {
        HealerGroup group=new HealerGroup();group.observe(scene(100),Set.of());group.tagRequested(1,100);
        assertEquals(HealerGroup.Ack.GONE,group.observe(without(scene(101),1),Set.of()));
        assertEquals(-1,group.pending());assertEquals(3,group.remaining());assertFalse(group.confirmed(1));
    }
    @Test public void missingAcknowledgementExpiresAfterEightTicks() {
        HealerGroup group=new HealerGroup();group.observe(scene(100),Set.of());group.tagRequested(1,100);
        assertEquals(HealerGroup.Ack.NONE,group.observe(scene(107),Set.of()));
        assertEquals(HealerGroup.Ack.TIMED_OUT,group.observe(scene(108),Set.of()));
        assertEquals(-1,group.pending());assertFalse(group.confirmed(1));assertEquals(4,group.remaining());
    }
    @Test public void killingOneCollectedHealerDoesNotRestartThePull() {
        HealerGroup group=allTagged();group.lureComplete();group.removed(1,103);
        group.observe(without(scene(104,2,3,4),1),Set.of());
        assertEquals(HealerGroup.Phase.FIGHTING,group.phase());assertEquals(0,group.remaining());
    }
    @Test public void oneLivingHealerNeedsNoHardcodedFourSpawnCount() {
        HealerGroup group=new HealerGroup();group.observe(without(scene(100),2,3,4),Set.of());
        group.observe(without(scene(101,1),2,3,4),Set.of());group.observe(without(scene(102,1),2,3,4),Set.of());
        assertEquals(HealerGroup.Phase.LURING,group.phase());assertEquals("1/1",group.progress());
    }
    @Test public void jadDepartureEndsCollectionAndResetDropsAllRememberedTags() {
        HealerGroup group=allTagged();group.observe(without(scene(103),45),Set.of());
        assertEquals(HealerGroup.Phase.IDLE,group.phase());assertEquals("0/0",group.progress());
        group.spawned(2,105);group.reset();assertEquals(HealerGroup.Phase.IDLE,group.phase());
        assertTrue(group.fresh(1));
    }
}
