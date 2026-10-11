/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape;
import net.runelite.client.plugins.microbot.drofirecape.*;

import java.util.List;
import net.runelite.client.plugins.microbot.drofirecape.core.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class OptionalPureWaveThreatsTest {
    private final Tile player=new Tile(62,60);
    private Snapshot scene(int tick,Mob... mobs){return new Snapshot(tick,player,new CollisionGrid(new int[104][104]),
        List.of(mobs),100,true,5,Protection.NONE);}
    private Mob mob(int index,Kind kind,Tile tile){return new Mob(index,kind,tile,kind.size,-1,-1,-1,kind.protection,true);}
    @Test public void recordedWave10SplitKeepsDeadRangerOutOfPrearm() {
        PureWaveThreats ledger=new PureWaveThreats();
        Mob ranger=mob(1,Kind.RANGER,new Tile(53,58)),blob=mob(2,Kind.BLOB,new Tile(60,60));
        ledger.observe(10,scene(249,ranger,blob));ledger.observe(10,scene(250,blob));
        assertTrue(ledger.defeated(Kind.RANGER));
        ledger.observe(10,scene(263,blob));
        for(int tick=264;tick<=267;tick++) {
            Snapshot gap=scene(tick);ledger.observe(10,gap);
            assertEquals(Protection.MELEE,ledger.spawnGuard(gap,Protection.RANGE));
        }
        Snapshot babies=scene(268,mob(3,Kind.BABY,new Tile(61,60)),mob(4,Kind.BABY,new Tile(62,61)));
        ledger.observe(10,babies);
        assertTrue(ledger.defeated(Kind.RANGER));assertTrue(ledger.defeated(Kind.BLOB));
    }
    @Test public void remoteSplitDoesNotHoldUnnecessaryPrayerAndWaitIsBounded() {
        PureWaveThreats ledger=new PureWaveThreats();ledger.observe(30,scene(1,mob(1,Kind.BLOB,new Tile(40,40))));
        Snapshot gap=scene(2);ledger.observe(30,gap);
        assertEquals(Protection.NONE,ledger.spawnGuard(gap,Protection.MAGIC));
        gap=scene(7);ledger.observe(30,gap);
        assertEquals(Protection.MAGIC,ledger.spawnGuard(gap,Protection.MAGIC));
    }
    @Test public void nextWaveClearsDefeatedKindsAndAllowsRealRangerOrMage() {
        PureWaveThreats ledger=new PureWaveThreats();
        ledger.observe(38,scene(1,mob(1,Kind.RANGER,new Tile(53,58)),mob(2,Kind.MAGER,new Tile(51,50))));
        ledger.observe(38,scene(2));assertTrue(ledger.defeated(Kind.RANGER));assertTrue(ledger.defeated(Kind.MAGER));
        ledger.observe(39,scene(3,mob(1,Kind.RANGER,new Tile(53,58))));
        assertFalse(ledger.defeated(Kind.RANGER));assertFalse(ledger.defeated(Kind.MAGER));
    }
    @Test public void existingRangedAttackerAlwaysOverridesSplitGapGuard() {
        PureWaveThreats ledger=new PureWaveThreats();Mob ranger=mob(1,Kind.RANGER,new Tile(53,58));
        ledger.observe(10,scene(1,ranger,mob(2,Kind.BLOB,new Tile(60,60))));
        Snapshot live=scene(2,ranger);ledger.observe(10,live);
        assertEquals(Protection.RANGE,ledger.spawnGuard(live,Protection.RANGE));
    }
}
