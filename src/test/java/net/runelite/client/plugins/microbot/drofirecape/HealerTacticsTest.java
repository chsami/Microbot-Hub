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

public class HealerTacticsTest {
    private static Mob healer(int index,int x,int y,boolean tagged) {
        return new Mob(index,Kind.HEALER,new Tile(x,y),1,tagged?1:10,10,-1,Protection.MELEE,tagged);
    }
    private static Snapshot scene(CollisionGrid grid,Mob...healers) {
        List<Mob> mobs=new ArrayList<>(Arrays.asList(healers));
        mobs.add(new Mob(45,Kind.JAD,new Tile(40,30),5,29,30,98,Protection.MAGIC,true));
        return new Snapshot(100,new Tile(30,30),grid,mobs,100,false,7,Protection.MAGIC);
    }
    @Test public void remainingTagOutranksAnAlreadyTaggedLowHealthHealer() {
        Snapshot scene=scene(new CollisionGrid(new int[64][64]),healer(1,28,30,true),healer(2,34,30,false));
        Plan tag=HealerTactics.tag(scene,false);assertNotNull(tag);assertEquals(2,tag.targetIndex());
        assertEquals(scene.player(),tag.nextStep());
    }
    @Test public void unreachableHealerRequiresApproachInsteadOfKillingCollectedHealer() {
        Snapshot scene=scene(new CollisionGrid(new int[64][64]),healer(1,28,30,true),healer(2,48,33,false));
        Plan tag=HealerTactics.tag(scene,false);assertNotNull(tag);assertEquals(2,tag.targetIndex());
        assertNotEquals(scene.player(),tag.nextStep());assertTrue(CombatPlanner.actionable(tag,false));
        assertTrue(TacticalMovement.clearOfJad(scene,scene.mobs(),tag.nextStep()));
        assertTrue(TacticalMovement.clearOfJad(scene,scene.mobs(),tag.destination()));
    }
    @Test public void impossibleRemainingTagNeverFallsBackToAnAlreadyTaggedKill() {
        int[][] flags=new int[64][64];
        for(int y=0;y<64;y++)flags[38][y]=CollisionGrid.FULL|CollisionGrid.PROJECTILE_OBJECT;
        Snapshot scene=scene(new CollisionGrid(flags),healer(1,28,30,true),healer(2,48,33,false));
        assertNull(HealerTactics.tag(scene,false));
    }
    @Test public void allTaggedMeansNoMoreTagProposal() {
        assertNull(HealerTactics.tag(scene(new CollisionGrid(new int[64][64]),healer(1,34,30,true)),false));
    }
    @Test public void meleeModeUsesItsContactTagWithoutRangedDistanceRule() {
        Snapshot ranged=scene(new CollisionGrid(new int[64][64]),healer(1,31,30,false));
        Snapshot melee=new Snapshot(ranged.tick(),ranged.player(),ranged.grid(),ranged.mobs(),100,false,1,Protection.MAGIC,true);
        Plan tag=HealerTactics.tag(melee,false);assertNotNull(tag);assertEquals(1,tag.targetIndex());
        assertEquals(melee.player(),tag.nextStep());
    }
}
