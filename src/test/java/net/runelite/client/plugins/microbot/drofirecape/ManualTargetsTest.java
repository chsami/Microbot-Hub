/* Copyright (c) 2026, DRO (droplugins). SPDX-License-Identifier: BSD-2-Clause */
package net.runelite.client.plugins.microbot.drofirecape;

import java.util.*;
import net.runelite.client.plugins.microbot.drofirecape.core.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Manual movement attacks only from the player's own tile. */
class ManualTargetsTest {
    private static final Tile PLAYER=new Tile(30,30);
    private static Mob mob(int index,Kind kind,int x,int y,boolean attacking) {
        return new Mob(index,kind,new Tile(x,y),kind.size,10,10,-1,kind.protection,attacking);
    }
    private static Snapshot scene(Mob... mobs) {
        return new Snapshot(100,PLAYER,new CollisionGrid(new int[104][104]),List.of(mobs),100,false,7,Protection.NONE);
    }
    @Test void onlyAMonsterInRangeOfTheCurrentTileIsChosen() {
        assertNull(ManualTargets.choose(scene(mob(1,Kind.RANGER,45,30,true)),-1,Protection.RANGE),"Out of range: never walk to it");
        assertEquals(2,ManualTargets.choose(scene(mob(1,Kind.RANGER,45,30,true),mob(2,Kind.BLOB,34,30,true)),-1,Protection.NONE).index());
    }
    @Test void theCurrentTargetIsKeptWhileInReach() {
        assertEquals(2,ManualTargets.choose(scene(mob(1,Kind.RANGER,33,30,true),mob(2,Kind.BLOB,34,30,true)),2,Protection.NONE).index());
        assertEquals(1,ManualTargets.choose(scene(mob(1,Kind.RANGER,33,30,true),mob(2,Kind.BLOB,34,30,true)),-1,Protection.NONE).index(),
            "Otherwise the planner's priority: ranger before blob");
    }
    @Test void untaggedHealersInReachComeBeforeJad() {
        Mob jad=mob(1,Kind.JAD,22,30,true);
        assertEquals(3,ManualTargets.choose(scene(jad,mob(2,Kind.HEALER,34,30,true),mob(3,Kind.HEALER,35,30,false)),-1,Protection.MAGIC).index());
        assertEquals(1,ManualTargets.choose(scene(jad,mob(2,Kind.HEALER,34,30,true)),-1,Protection.MAGIC).index(),"Every healer in reach is tagged");
    }
}
