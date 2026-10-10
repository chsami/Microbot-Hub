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
public class RecordedPolicyTest {
    private final Tile main=RecordedLureBook.ITALY,pull=RecordedLureBook.PULL,nw=RecordedLureBook.NORTHWEST;
    private Snapshot state(int tick,Tile player,Mob... mobs){return new Snapshot(tick,player,new CollisionGrid(new int[104][104]),List.of(mobs),100,true,7,Protection.NONE);}
    private Mob mob(int index,Kind kind,Tile tile){return new Mob(index,kind,tile,kind.size,10,10,-1,kind.protection,true);}
    private Plan decide(LureController c,Snapshot s,List<Tile> route){return c.decide(s,new CombatPlanner(),-1,route,4,main,pull,nw,null);}
    @Test public void visibleBatAlwaysGetsShotInsteadOfAnotherMovement(){
        LureController c=new LureController();Mob bat=mob(1,Kind.BAT,main.add(1,1));
        for(int tick=1;tick<100;tick++){Plan p=decide(c,state(tick,main,bat),List.of(main,pull));assertEquals(main,p.nextStep());assertEquals(1,p.targetIndex());assertEquals(Protection.MELEE,p.protection());}
    }
    @Test public void distantBatDoesNotCauseInventedNeighbourSteps(){
        LureController c=new LureController();Mob bat=mob(1,Kind.BAT,main.add(-20,0));
        for(int tick=1;tick<=6;tick++){Plan p=decide(c,state(tick,main,bat),List.of(main,pull));assertEquals(main,p.nextStep());}
    }
    @Test public void failedMovementImmediatelyUsesProtectedCombat(){
        LureController c=new LureController();c.movementFailed();Mob ranger=mob(2,Kind.RANGER,main.add(5,0));
        Plan p=decide(c,state(1,main,ranger),List.of(main,pull));assertEquals(main,p.nextStep());assertEquals(2,p.targetIndex());assertEquals(Protection.RANGE,p.protection());
    }
    @Test public void suppliedRecordingIncludesActualCenterPullAndReturns(){
        List<Tile> route=RecordedLureBook.candidates(5,13);assertTrue(route.contains(pull));assertEquals(main,route.get(route.size()-1));
        assertEquals(List.of(main,new Tile(44,44),main),RecordedLureBook.candidates(5,3));
        assertTrue(RecordedLureBook.candidates(5,24).contains(pull));
    }
    @Test public void magicWinsOverRangeAndStaysOnForLaunchedAttack(){
        Snapshot exposed=state(10,main,mob(1,Kind.MAGER,main.add(5,0)),mob(2,Kind.RANGER,main.add(-5,0)));
        assertEquals(Protection.MAGIC,MagicProtection.choose(exposed,main,-1,Protection.RANGE));
        assertEquals(Protection.MAGIC,MagicProtection.choose(state(12,main),main,14,Protection.RANGE));
        assertEquals(Protection.RANGE,MagicProtection.choose(state(15,main),main,14,Protection.RANGE));
    }
}
