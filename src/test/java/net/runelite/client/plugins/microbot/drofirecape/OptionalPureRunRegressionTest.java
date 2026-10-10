/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape;
import net.runelite.client.plugins.microbot.drofirecape.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import net.runelite.client.plugins.microbot.drofirecape.DroFirecapeConfig;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.plugins.microbot.drofirecape.core.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;

public class OptionalPureRunRegressionTest {
    private Mob mob(int id,Kind kind,int x,int y) {
        return new Mob(id,kind,new Tile(x,y),kind.size,9,30,1825,kind.protection,true);
    }
    private Snapshot scene(Mob...mobs) {
        return new Snapshot(1825,new Tile(34,26),new CollisionGrid(new int[104][104]),
            List.of(mobs),88,true,5,Protection.NONE);
    }
    @Test public void activeRangerBodyBlockingLastMeleeMustBeKillable() {
        Mob ranger=mob(45054,Kind.RANGER,32,29),melee=mob(45053,Kind.MELEER,35,27);
        Snapshot s=scene(melee,ranger);
        assertTrue(CombatPlanner.playerCanAttack(s,s.player(),ranger));
        assertFalse(s.grid().melee(melee,s.player()));
        assertTrue(s.grid().melee(CombatPlanner.advance(s.grid(),List.of(melee),s.player(),Protection.NONE).get(0),s.player()));
        assertTrue(PureSafety.releaseSafe(s,ranger,Protection.RANGE));
        assertTrue(new PureCombatPlanner().attackAllowed(s,ranger,Protection.RANGE,false));
    }
    @Test public void anotherShooterKeepsReleaseGuard() {
        Mob ranger=mob(2,Kind.RANGER,32,29),melee=mob(1,Kind.MELEER,35,27);
        assertFalse(PureSafety.releaseSafe(scene(melee,ranger,mob(3,Kind.MAGER,40,26)),ranger,Protection.RANGE));
        assertFalse(PureSafety.releaseSafe(scene(melee,ranger,mob(3,Kind.RANGER,40,26)),ranger,Protection.RANGE));
    }
    @Test public void depletionCannotBlockRemainingPureBrewsButKeepsRestorePriority() {
        assertTrue(PureSupplyBudget.brewAllowed(0,4,true,false));
        assertTrue(PureSupplyBudget.brewAllowed(3,3,true,false));
        assertFalse(PureSupplyBudget.brewAllowed(0,0,true,true));
        assertFalse(PureSupplyBudget.brewAllowed(0,4,false,false));
        assertFalse(PureSupplyBudget.brewAllowed(40,3,true,true));
    }
}
