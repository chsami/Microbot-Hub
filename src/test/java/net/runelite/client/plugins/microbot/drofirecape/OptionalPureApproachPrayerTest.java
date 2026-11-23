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

public class OptionalPureApproachPrayerTest {
    private Snapshot scene(boolean mage, Kind follower) {
        Mob melee=new Mob(1,follower,new Tile(24,20),follower==Kind.MELEER?4:1,
            -1,-1,-1,Protection.MELEE,true);
        Mob ranger=new Mob(2,Kind.RANGER,new Tile(30,20),3,-1,-1,-1,Protection.RANGE,true);
        Mob mager=new Mob(3,Kind.MAGER,new Tile(20,30),5,-1,-1,-1,Protection.MAGIC,true);
        return new Snapshot(1,new Tile(20,20),new CollisionGrid(new int[64][64]),
            mage?List.of(melee,ranger,mager):List.of(melee,ranger),100,true,5,Protection.NONE);
    }
    @Test public void approachingBigMeleeKeepsNextTickGuardBeforeCurrentContact() {
        Snapshot s=scene(false,Kind.MELEER);TickProtection clock=new TickProtection(true);clock.beginTick(s.tick(),s.mobs());
        assertFalse(s.grid().melee(s.mobs().get(0),s.player()));
        assertEquals(Protection.MELEE,clock.chooseNative(s,true,Protection.MELEE,Protection.NONE).protection);
        TickProtection.Decision pure=BetaTickProtection.choose(clock,s,true,Protection.MELEE,Protection.NONE,true);
        assertEquals(3,pure.dueMask);assertEquals(Protection.MELEE,pure.protection);
        assertEquals(Protection.RANGE,BetaTickProtection.choose(clock,s,true,Protection.MELEE,Protection.NONE,false).protection,"Other optional mode retains its existing choice");
    }
    @Test public void incomingMagicStillWinsSimultaneousContact() {
        Snapshot s=scene(true,Kind.MELEER);TickProtection clock=new TickProtection(true);clock.beginTick(s.tick(),s.mobs());
        assertEquals(Protection.MAGIC,BetaTickProtection.choose(clock,s,true,Protection.MELEE,Protection.NONE,true).protection);
    }
    @Test public void approachingBatCannotDisplaceIncomingRanger() {
        Snapshot s=scene(false,Kind.BAT);TickProtection clock=new TickProtection(true);clock.beginTick(s.tick(),s.mobs());
        assertEquals(Protection.RANGE,BetaTickProtection.choose(clock,s,true,Protection.MELEE,Protection.NONE,true).protection);
    }
}
