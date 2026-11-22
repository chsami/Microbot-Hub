/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape;
import net.runelite.client.plugins.microbot.drofirecape.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import net.runelite.client.plugins.microbot.drofirecape.core.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;

public class OptionalPureTargetPriorityTest {
    private Snapshot scene(int wave,boolean rangerAttacking) {
        Mob bat=new Mob(1,Kind.BAT,new Tile(21,20),1,10,10,-1,Protection.MELEE,true);
        Mob ranger=new Mob(2,Kind.RANGER,new Tile(25,20),3,10,10,-1,Protection.RANGE,rangerAttacking);
        return new Snapshot(100,new Tile(20,20),new CollisionGrid(new int[64][64]),
            List.of(bat,ranger),100,true,7,Protection.NONE).atWave(wave);
    }
    @Test public void pureLureAndImmediateSelectionClearNearbyBatAcrossWave53Boundary() {
        for(int wave:new int[]{7,24,39,52,53,54,55,56,60})for(boolean attacking:new boolean[]{false,true}) {
            Snapshot s=scene(wave,attacking);int expected=1;
            assertEquals(expected,PureCombatPolicy.preferredShot(s,Protection.RANGE).index());
            PureLureController controller=new PureLureController();
            Plan chosen=controller.decide(s,new PureCombatPlanner(),-1,List.of(s.player()),0,
                s.player(),new Tile(28,37),null,new Tile(19,20));
            assertEquals(expected,chosen.targetIndex(),"wave "+wave);
            assertEquals(s.player(),chosen.nextStep());
        }
    }
    @Test public void ordinaryAndPureTargetingBothClearAnAttackableNearbyBat() {
        Snapshot s=scene(54,true);
        assertEquals(1,CaveSafety.preferredShot(s,Protection.RANGE).index());
        assertEquals(1,PureCombatPolicy.preferredShot(s,Protection.RANGE).index());
        assertFalse(PureCombatPolicy.rangerBeforeBat(s,s.mobs().get(1)));
    }
    @Test public void liveRangerBecomesPreferredAfterTheNearbyBatIsCleared() {
        Snapshot mixed=scene(54,true);
        Snapshot cleared=new Snapshot(101,mixed.player(),mixed.grid(),List.of(mixed.mobs().get(1)),
            100,true,7,Protection.NONE).atWave(54);
        assertTrue(PureCombatPolicy.rangerBeforeBat(cleared,cleared.mobs().get(0)));
        assertEquals(2,PureCombatPolicy.preferredShot(cleared,Protection.RANGE).index());
    }
    @Test public void batBecomesFirstAgainAfterTheLateRangerDies() {
        Snapshot mixed=scene(54,true);
        Snapshot cleared=new Snapshot(101,mixed.player(),mixed.grid(),List.of(mixed.mobs().get(0)),
            100,true,7,Protection.NONE).atWave(54);
        assertEquals(1,PureCombatPolicy.preferredShot(cleared,Protection.MAGIC).index());
    }
}
