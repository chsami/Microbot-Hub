/* Copyright (c) 2026, DRO (droplugins). SPDX-License-Identifier: BSD-2-Clause */
package net.runelite.client.plugins.microbot.drofirecape;

import java.util.List;
import net.runelite.client.plugins.microbot.drofirecape.core.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OffenceTargetsTest {
    private static Mob mob(int index,Kind kind,int x,int lastAttack) {
        return new Mob(index,kind,new Tile(x,28),kind.size,10,10,lastAttack,kind.protection,true);
    }
    private static Snapshot scene(Mob... mobs) {
        return new Snapshot(100,new Tile(30,28),new CollisionGrid(new int[104][104]),List.of(mobs),100,false,12,Protection.MAGIC);
    }
    @Test void onlyBigThreatsJadAndHealersAreBoosted() {
        for(Kind kind:Kind.values()) {
            Snapshot s=scene(mob(1,kind,23,-1));
            boolean expected=kind==Kind.MELEER||kind==Kind.RANGER||kind==Kind.MAGER||kind==Kind.JAD||kind==Kind.HEALER;
            assertEquals(expected,OffenceTargets.offence(1,s),kind.name());
        }
        assertFalse(OffenceTargets.offence(9,scene(mob(1,Kind.RANGER,23,-1))),"Missing target");
        assertFalse(OffenceTargets.offence(1,null));
    }
    @Test void magerIsBoostedOnlyInAFightItHasToItself() {
        assertTrue(OffenceTargets.offence(1,scene(mob(1,Kind.MAGER,20,96),mob(2,Kind.RANGER,24,80))),"Ranger idle for 20 ticks");
        assertFalse(OffenceTargets.offence(1,scene(mob(1,Kind.MAGER,20,96),mob(2,Kind.RANGER,24,95))),"Ranger attacked 5 ticks ago");
        assertFalse(OffenceTargets.offence(1,scene(mob(1,Kind.MAGER,20,96),mob(2,Kind.BAT,29,99))),"A bat attack counts too");
        assertTrue(OffenceTargets.offence(2,scene(mob(1,Kind.MAGER,20,96),mob(2,Kind.RANGER,24,95))),"Other targets keep offence");
    }
}
