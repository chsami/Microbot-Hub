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

/** Prayer phase must not make a nearly dead/sticky baby outrank an accessible ranger. */
public class TargetPriorityTest {
    private final Tile player=new Tile(50,50);
    private final CollisionGrid grid=new CollisionGrid(new int[104][104]);
    private Mob mob(int index,Kind kind,Tile tile,int hp){return new Mob(index,kind,tile,kind.size,hp,100,-1,kind.protection,true);}
    private Snapshot frame(Mob... mobs){return new Snapshot(100,player,grid,List.of(mobs),100,false,7,Protection.NONE);}
    @Test public void reachableRangerOutranksNearlyDeadMeleeAndCurrentTargetBonusOnEveryPrayerPhase() {
        Mob ranger=mob(1,Kind.RANGER,new Tile(43,50),100);
        for(Kind kind:List.of(Kind.BABY,Kind.BLOB,Kind.MELEER)) {
            Mob melee=mob(2,kind,new Tile(50,51),1);
            for(Protection p:Protection.values())
                assertTrue(CombatPlanner.priority(ranger,p)-ranger.distance(player)>
                    CombatPlanner.priority(melee,p)+15-melee.distance(player));
        }
    }
    @Test public void recordedHoldLeavesCurrentBabyForAccessibleRanger() {
        Mob ranger=mob(1,Kind.RANGER,new Tile(43,50),100),baby=mob(2,Kind.BABY,new Tile(50,49),1);
        Plan plan=new LureController().decide(frame(ranger,baby),new CombatPlanner(),baby.index(),List.of(player),
            0,player,null,null,player.add(-2,0));
        assertNotNull(plan);assertEquals(ranger.index(),plan.targetIndex());assertEquals(player,plan.nextStep());
    }
    @Test public void legalBatShotStillWinsBeforeRanger() {
        Mob ranger=mob(1,Kind.RANGER,new Tile(43,50),1),bat=mob(2,Kind.BAT,new Tile(50,49),100);
        Plan plan=new LureController().decide(frame(ranger,bat),new CombatPlanner(),ranger.index(),List.of(player),
            0,player,null,null,player.add(-2,0));
        assertEquals(bat.index(),plan.targetIndex());
    }
    @Test public void blockedRangerIsApproachedRatherThanUsingAMeleePeek() {
        Mob ranger=mob(1,Kind.RANGER,new Tile(30,30),100),baby=mob(2,Kind.BABY,new Tile(50,49),1);
        Plan plan=new LureController().decide(frame(ranger,baby),new CombatPlanner(),-1,List.of(player),
            0,player,null,null,player.add(-2,0));
        assertEquals(-1,plan.targetIndex());
        assertNotEquals(player,plan.nextStep());
        assertTrue(plan.reason().contains("Approach blocked ranged"));
    }
}
