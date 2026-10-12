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

public class JadHealerProtectionTest {
    private static Snapshot scene(int tick,boolean tagged,boolean contact) {
        Mob jad=new Mob(1,Kind.JAD,new Tile(35,30),5,20,30,17,Protection.MAGIC,true);
        Mob healer=new Mob(2,Kind.HEALER,contact?new Tile(29,30):new Tile(45,42),1,10,10,-1,Protection.MELEE,tagged);
        return new Snapshot(tick,new Tile(30,30),new CollisionGrid(new int[64][64]),List.of(jad,healer),100,false,7,Protection.MAGIC);
    }
    private static TickProtection calibrated() {
        TickProtection clock=new TickProtection();
        for(int tick=1;tick<=22;tick++) {
            if(tick==1||tick==17)clock.animation(1,Kind.JAD,2656);
            if(tick==9)clock.animation(1,Kind.JAD,2652);
            clock.beginTick(tick,scene(tick,true,true).mobs());
        }
        return clock;
    }
    @Test public void alternatingRandomStylesCalibrateOnlyTheIntervalAndKeepFullWindupGuard() {
        TickProtection clock=calibrated();
        assertEquals(Protection.MAGIC,clock.choose(scene(22,true,true),false,Protection.MAGIC,Protection.NONE).protection);
        clock.beginTick(23,scene(23,true,true).mobs());
        TickProtection.Decision gap=clock.choose(scene(23,true,true),false,Protection.MAGIC,Protection.NONE);
        assertEquals(Protection.MELEE,gap.protection);assertEquals(Protection.NONE,gap.jadProtection);
        assertEquals(Protection.MAGIC,gap.jadReturnProtection);
        assertEquals(1,clock.inputWindowTicks(scene(23,true,true),false,Protection.MELEE));
    }
    @Test public void rearmOneTickBeforeNextWindupAndNeverRepeatGapThroughAMissingAttack() {
        TickProtection clock=calibrated();
        for(int tick=23;tick<=31;tick++) {
            clock.beginTick(tick,scene(tick,true,true).mobs());
            Protection chosen=clock.choose(scene(tick,true,true),false,Protection.MELEE,Protection.NONE).protection;
            assertEquals(tick==23?Protection.MELEE:Protection.MAGIC,chosen);
        }
    }
    @Test public void currentJadAnimationAlwaysWinsEvenWhenTheOldClockExpectedAGap() {
        TickProtection clock=calibrated();clock.animation(1,Kind.JAD,2652);
        clock.beginTick(23,scene(23,true,true).mobs());
        TickProtection.Decision decision=clock.choose(scene(23,true,true),false,Protection.MELEE,Protection.NONE);
        assertEquals(Protection.RANGE,decision.protection);assertEquals(Protection.RANGE,decision.jadProtection);
        assertEquals(Protection.NONE,decision.jadReturnProtection);
    }
    @Test public void unknownOrBrokenCadenceCannotBorrowHealerGap() {
        TickProtection unknown=new TickProtection();unknown.animation(1,Kind.JAD,2656);
        for(int tick=17;tick<=23;tick++)unknown.beginTick(tick,scene(tick,true,true).mobs());
        assertEquals(Protection.MAGIC,unknown.choose(scene(23,true,true),false,Protection.MAGIC,Protection.NONE).protection);
        TickProtection broken=calibrated();broken.invalidateCadence();
        broken.beginTick(23,scene(23,true,true).mobs());
        assertEquals(Protection.MAGIC,broken.choose(scene(23,true,true),false,Protection.MAGIC,Protection.NONE).protection);
    }
    @Test public void movementAndUntaggedOrDistantHealersDoNotRequestTheGapSwitch() {
        for(int scenario=0;scenario<3;scenario++) {
            TickProtection clock=calibrated();Snapshot scene=scene(23,scenario!=1,scenario!=2);
            clock.beginTick(23,scene.mobs());
            assertEquals(Protection.MAGIC,clock.choose(scene,scenario==0,Protection.MAGIC,Protection.NONE).protection);
        }
    }
    @Test public void missedFrameOrLosBreakInvalidatesJadCadence() {
        TickProtection skipped=calibrated();skipped.beginTick(24,scene(24,true,true).mobs());
        assertEquals(Protection.NONE,skipped.choose(scene(23,true,true),false,Protection.MAGIC,Protection.NONE).jadReturnProtection);
        TickProtection hidden=calibrated();Snapshot base=scene(22,true,true);
        Mob distant=base.mobs().get(0).at(new Tile(55,55));
        Snapshot unseen=new Snapshot(22,base.player(),base.grid(),List.of(distant,base.mobs().get(1)),100,false,7,Protection.MAGIC);
        hidden.choose(unseen,false,Protection.MAGIC,Protection.NONE);
        hidden.beginTick(23,scene(23,true,true).mobs());
        assertEquals(Protection.MAGIC,hidden.choose(scene(23,true,true),false,Protection.MAGIC,Protection.NONE).protection);
    }
    @Test public void recordedDelayedIntervalsNeedTwoNewMatchingIntervalsBeforeAnotherGap() {
        for(int interval:new int[]{9,10,11,24,37}) {
            TickProtection clock=calibrated();int delayed=17+interval;
            for(int tick=23;tick<=delayed+6;tick++) {
                if(tick==delayed)clock.animation(1,Kind.JAD,2656);
                clock.beginTick(tick,scene(tick,true,true).mobs());
            }
            assertEquals(Protection.MAGIC,clock.choose(scene(delayed+6,true,true),false,Protection.MAGIC,Protection.NONE).protection,"Delayed interval "+interval);
        }
    }
    @Test public void jadMeleeContactCannotOpenTheHealerGap() {
        TickProtection clock=calibrated();Snapshot base=scene(23,true,true);
        Snapshot contact=new Snapshot(23,base.player(),base.grid(),
            List.of(base.mobs().get(0).at(new Tile(31,30)),base.mobs().get(1)),100,false,7,Protection.MAGIC);
        clock.beginTick(23,contact.mobs());
        assertEquals(Protection.NONE,clock.choose(contact,false,Protection.MAGIC,Protection.NONE).jadReturnProtection);
    }
    @Test public void taggedHealerBehindRockCannotDemandAMeleeGap() {
        TickProtection clock=calibrated();Snapshot base=scene(23,true,true);
        int[][] flags=new int[64][64];
        for(int y=0;y<64;y++)flags[29][y]=CollisionGrid.FULL|CollisionGrid.PROJECTILE_OBJECT;
        Mob trapped=base.mobs().get(1).at(new Tile(28,30));
        Snapshot blocked=new Snapshot(23,base.player(),new CollisionGrid(flags),
            List.of(base.mobs().get(0),trapped),100,false,7,Protection.MAGIC);
        clock.beginTick(23,blocked.mobs());
        TickProtection.Decision guard=clock.choose(blocked,false,Protection.MAGIC,Protection.NONE);
        assertEquals(Protection.MAGIC,guard.protection);
        assertEquals(Protection.NONE,guard.jadReturnProtection);
    }
}
