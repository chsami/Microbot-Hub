/* Copyright (c) 2026, DRO (droplugins). SPDX-License-Identifier: BSD-2-Clause */
package net.runelite.client.plugins.microbot.drofirecape;
import java.util.*;
import net.runelite.client.plugins.microbot.drofirecape.core.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ConservationTargetContractTest {
    private Snapshot trapped(Kind kind) {
        int[][] flags=new int[104][104];Tile origin=new Tile(18,28);
        for(int x=17;x<=18+kind.size;x++)for(int y=27;y<=28+kind.size;y++)
            if(x==17||x==18+kind.size||y==27||y==28+kind.size)flags[x][y]=CollisionGrid.OBJECT|(kind==Kind.RANGER?CollisionGrid.PROJECTILE_OBJECT:0);
        Mob mob=new Mob(1,kind,origin,kind.size,10,10,-1,kind.protection,false);
        return new Snapshot(100,new Tile(30,28),new CollisionGrid(flags),List.of(mob),100,false,12,Protection.NONE);
    }
    @Test void shelteredRangerAndShootableTrappedBigMeleeDoNotSpendOffence() {
        for(Kind kind:List.of(Kind.RANGER,Kind.MELEER)) {
            Snapshot s=trapped(kind);Mob target=s.mobs().get(0);
            assertEquals(kind==Kind.MELEER,CombatPlanner.playerCanAttack(s,s.player(),target));assertTrue(CaveSafety.trapped(s,target));assertFalse(CaveSafety.active(s,target));
            assertFalse(PrayerConservation.offence(true,true,1,s));assertTrue(PrayerConservation.offence(true,false,1,s));
        }
    }
    @Test void offPolicyIdentityIncludesMissingSceneTargetAndEarlyWave() {
        assertTrue(PrayerConservation.offence(true,false,-1,null));assertFalse(PrayerConservation.offence(false,false,-1,null));
        Snapshot s=trapped(Kind.RANGER).atWave(1);assertTrue(PrayerConservation.offence(true,false,999,s));assertFalse(PrayerConservation.offence(true,true,999,s));
    }
    @Test void jadAndUntrappedThreatsRemainEligibleAtAnyWave() {
        for(Kind kind:List.of(Kind.RANGER,Kind.MELEER,Kind.JAD)) {
            Mob m=new Mob(1,kind,new Tile(23,28),kind.size,10,10,99,kind.protection,true);
            Snapshot s=new Snapshot(100,new Tile(30,28),new CollisionGrid(new int[104][104]),List.of(m),100,false,12,Protection.MAGIC).atWave(1);
            assertTrue(PrayerConservation.offence(true,true,1,s));
        }
    }
}
