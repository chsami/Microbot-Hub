/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape;

import java.util.List;
import net.runelite.client.plugins.microbot.drofirecape.core.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class RangedProtectionPriorityTest {
    private final Tile player=new Tile(50,50);
    private final CollisionGrid grid=new CollisionGrid(new int[104][104]);
    private Mob mob(int index,Kind kind,Tile tile) {
        return new Mob(index,kind,tile,kind.size,10,10,-1,kind.protection,true);
    }
    private Snapshot frame(int tick,Mob... mobs) {
        return new Snapshot(tick,player,grid,List.of(mobs),100,false,7,Protection.RANGE);
    }
    @Test public void rangerCooldownDoesNotGiveSmallBlobTheOverhead() {
        Mob ranger=mob(1,Kind.RANGER,new Tile(44,50));
        Mob baby=mob(2,Kind.BABY,new Tile(50,49));
        TickProtection clock=new TickProtection();
        for(int tick=1;tick<=10;tick++) {
            if(tick==1||tick==5||tick==9)clock.animation(1,Kind.RANGER,2633);
            clock.beginTick(tick,List.of(ranger,baby));
        }
        assertEquals(Protection.RANGE,clock.choose(frame(10,ranger,baby),false,Protection.MELEE,Protection.NONE).protection);
    }
    @Test public void exposedMagerTakesPriorityOverRangerAndBaby() {
        TickProtection clock=new TickProtection();
        assertEquals(Protection.MAGIC,clock.choose(frame(10,
            mob(1,Kind.MAGER,new Tile(43,48)),mob(2,Kind.RANGER,new Tile(50,43)),
            mob(3,Kind.BABY,new Tile(50,49))),false,Protection.RANGE,Protection.NONE).protection);
    }
    @Test public void observedJadWindupWinsOverOtherThreats() {
        TickProtection clock=new TickProtection();Mob jad=mob(1,Kind.JAD,new Tile(43,48));
        Mob mage=mob(2,Kind.MAGER,new Tile(50,43));
        clock.animation(1,Kind.JAD,2652);clock.beginTick(10,List.of(jad,mage));
        assertEquals(Protection.RANGE,clock.choose(frame(10,jad,mage),false,Protection.MAGIC,Protection.NONE).protection);
    }
    @Test public void knownBatCooldownKeepsProtectionSelectedForAttackDispatch() {
        TickProtection clock=new TickProtection();Mob bat=mob(1,Kind.BAT,new Tile(50,49));
        for(int tick=1;tick<=10;tick++) {
            if(tick==1||tick==5||tick==9)clock.animation(1,Kind.BAT,2621);
            clock.beginTick(tick,List.of(bat));
        }
        assertEquals(Protection.MELEE,clock.choose(frame(10,bat),false,Protection.MELEE,Protection.NONE).protection);
    }

    private void attacks(TickProtection clock,int tick,Mob... mobs) {
        for(Mob m:mobs) {
            int phase=m.kind()==Kind.MAGER?1:m.kind()==Kind.RANGER?2:3;
            if(tick>=phase&&(tick-phase)%4==0)
                clock.animation(m.index(),m.kind(),m.kind()==Kind.MAGER?2647:m.kind()==Kind.RANGER?2633:2637);
        }
        clock.beginTick(tick,List.of(mobs));
    }
    @Test public void calibratedRangerAndBigMeleeSwitchBeforeEachUpcomingLaunch() {
        TickProtection clock=new TickProtection();Mob ranger=mob(1,Kind.RANGER,new Tile(44,50));
        Mob melee=mob(2,Kind.MELEER,new Tile(46,50));
        for(int tick=1;tick<=14;tick++) {
            attacks(clock,tick,ranger,melee);
            if(tick==13)assertEquals(Protection.RANGE,clock.choose(frame(tick,ranger,melee),false,Protection.MELEE,Protection.NONE).protection);
            if(tick==14)assertEquals(Protection.MELEE,clock.choose(frame(tick,ranger,melee),false,Protection.RANGE,Protection.NONE).protection);
        }
    }
    @Test public void allThreeMajorStylesSwitchOnTheirSeparateObservedPhases() {
        TickProtection clock=new TickProtection();Mob mage=mob(1,Kind.MAGER,new Tile(43,48));
        Mob ranger=mob(2,Kind.RANGER,new Tile(50,43)),melee=mob(3,Kind.MELEER,new Tile(46,50));
        Protection held=Protection.MAGIC;
        for(int tick=1;tick<=18;tick++) {
            attacks(clock,tick,mage,ranger,melee);
            Protection next=clock.choose(frame(tick,mage,ranger,melee),false,held,Protection.NONE).protection;
            if(tick>=12) {
                Protection expected=tick%4==0?Protection.MAGIC:tick%4==1?Protection.RANGE:tick%4==2?Protection.MELEE:Protection.MAGIC;
                assertEquals(expected,next,"Pre-arm server tick "+(tick+1));
            }
            held=next;
        }
    }
    @Test public void uncertainOrBrokenMageCadenceCannotGiveAwayMagicProtection() {
        TickProtection clock=new TickProtection();Mob mage=mob(1,Kind.MAGER,new Tile(43,48));
        Mob ranger=mob(2,Kind.RANGER,new Tile(50,43)),melee=mob(3,Kind.MELEER,new Tile(46,50));
        for(int tick=1;tick<=14;tick++) {
            attacks(clock,tick,mage,ranger,melee);
            if(tick==14) {
                clock.invalidateCadence();
                assertEquals(Protection.MAGIC,clock.choose(frame(tick,mage,ranger,melee),false,Protection.MELEE,Protection.NONE).protection);
            }
        }
        assertEquals(Protection.MAGIC,clock.choose(frame(30,mage,ranger,melee),false,Protection.RANGE,Protection.NONE).protection);
    }
    @Test public void simultaneousMajorAttacksKeepMagicThenThreateningBigMeleePriority() {
        TickProtection clock=new TickProtection();Mob mage=mob(1,Kind.MAGER,new Tile(43,48));
        Mob ranger=mob(2,Kind.RANGER,new Tile(50,43)),melee=mob(3,Kind.MELEER,new Tile(46,50));
        assertEquals(Protection.MAGIC,clock.choose(frame(10,mage,ranger,melee),false,Protection.MELEE,Protection.NONE).protection);
        assertEquals(Protection.MELEE,clock.choose(frame(10,ranger,melee),false,Protection.MELEE,Protection.NONE).protection);
    }
    @Test public void movingWithUnchangedAttackStylesKeepsVerifiedTiming() {
        TickProtection clock=new TickProtection();Mob mage=mob(1,Kind.MAGER,new Tile(43,48));
        Mob ranger=mob(2,Kind.RANGER,new Tile(50,43)),melee=mob(3,Kind.MELEER,new Tile(46,50));
        for(int tick=1;tick<=14;tick++)attacks(clock,tick,mage,ranger,melee);
        assertEquals(Protection.MELEE,clock.choose(frame(14,mage,ranger,melee),true,Protection.MAGIC,Protection.NONE).protection);
    }

    @Test public void verifiedSmallBlobTickUsesMeleeOnlyInsideMagicCooldownGap() {
        for(Kind kind:List.of(Kind.BLOB,Kind.BABY)) {
            TickProtection clock=new TickProtection();Mob mage=mob(1,Kind.MAGER,new Tile(43,48));
            Mob blob=mob(2,kind,kind==Kind.BLOB?new Tile(48,49):new Tile(50,49));Protection held=Protection.MAGIC;
            for(int tick=1;tick<=18;tick++) {
                if((tick-1)%4==0)clock.animation(1,Kind.MAGER,2647);
                if(tick>=3&&(tick-3)%4==0)clock.animation(2,kind,2625);
                clock.beginTick(tick,List.of(mage,blob));
                Snapshot s=frame(tick,mage,blob);
                Protection next=clock.choose(s,false,held,Protection.NONE).protection;
                if(tick>=12)assertEquals(tick%4==2?Protection.MELEE:Protection.MAGIC,next,"Small blob "+kind+" pre-arm "+(tick+1));
                if(tick%4==0&&tick>=12)assertTrue(clock.inputWindowTicks(s,false,next)<=1);
                held=next;
            }
        }
    }
    @Test public void simultaneousVerifiedMagicAndSmallBlobAttackKeepsMagic() {
        TickProtection clock=new TickProtection();Mob mage=mob(1,Kind.MAGER,new Tile(43,48));
        Mob blob=mob(2,Kind.BABY,new Tile(50,49));
        for(int tick=1;tick<=12;tick++) {
            if((tick-1)%4==0){clock.animation(1,Kind.MAGER,2647);clock.animation(2,Kind.BABY,2625);}
            clock.beginTick(tick,List.of(mage,blob));clock.choose(frame(tick,mage,blob),false,Protection.MAGIC,Protection.NONE);
        }
        TickProtection.Decision next=clock.choose(frame(12,mage,blob),false,Protection.MAGIC,Protection.NONE);
        assertEquals(Protection.MAGIC,next.protection);assertTrue(next.conflict());
    }

    @Test public void movementEnvelopeWithPossibleMageContactKeepsMagic() {
        TickProtection clock=new TickProtection();Mob mage=mob(1,Kind.MAGER,new Tile(45,48));
        Mob ranger=mob(2,Kind.RANGER,new Tile(50,43)),melee=mob(3,Kind.MELEER,new Tile(46,50));
        for(int tick=1;tick<=14;tick++)attacks(clock,tick,mage,ranger,melee);
        assertEquals(Protection.MAGIC,clock.choose(frame(14,mage,ranger,melee),true,Protection.MELEE,Protection.NONE).protection);
    }

}
