package net.runelite.client.plugins.microbot.drofirecape.optional;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
import net.runelite.client.plugins.microbot.drofirecape.optional.core.*;
import net.runelite.client.plugins.microbot.drofirecape.optional.core.FcModel.*;

public class PureTargetPriorityTest {
    private Snapshot scene(int wave,boolean rangerAttacking) {
        Mob bat=new Mob(1,Kind.BAT,new Tile(21,20),1,10,10,-1,Protection.MELEE,true);
        Mob ranger=new Mob(2,Kind.RANGER,new Tile(25,20),3,10,10,-1,Protection.RANGE,rangerAttacking);
        return new Snapshot(100,new Tile(20,20),new CollisionGrid(new int[64][64]),
            List.of(bat,ranger),100,true,7,Protection.NONE).atWave(wave);
    }
    @Test public void pureLureAndImmediateSelectionAgreeAtWave53Boundary() {
        for(int wave:new int[]{7,24,39,52,53,54,55,56,60})for(boolean attacking:new boolean[]{false,true}) {
            Snapshot s=scene(wave,attacking);int expected=wave>=53?2:1;
            assertEquals(expected,PureCombatPolicy.preferredShot(s,Protection.RANGE).index());
            PureLureController controller=new PureLureController();
            Plan chosen=controller.decide(s,new PureCombatPlanner(),-1,List.of(s.player()),0,
                s.player(),new Tile(28,37),null,new Tile(19,20));
            assertEquals("wave "+wave,expected,chosen.targetIndex());
            assertEquals(s.player(),chosen.nextStep());
        }
    }
    @Test public void ordinaryTargetingKeepsItsExistingWave54BatPriority() {
        Snapshot s=scene(54,true);
        assertEquals(1,CaveSafety.preferredShot(s,Protection.RANGE).index());
        assertEquals(2,PureCombatPolicy.preferredShot(s,Protection.RANGE).index());
    }
    @Test public void batBecomesFirstAgainAfterTheLateRangerDies() {
        Snapshot mixed=scene(54,true);
        Snapshot cleared=new Snapshot(101,mixed.player(),mixed.grid(),List.of(mixed.mobs().get(0)),
            100,true,7,Protection.NONE).atWave(54);
        assertEquals(1,PureCombatPolicy.preferredShot(cleared,Protection.MAGIC).index());
    }
}
