/* Copyright (c) 2026, DRO (droplugins). SPDX-License-Identifier: BSD-2-Clause */
package net.runelite.client.plugins.microbot.drofirecape;

import java.util.List;
import net.runelite.client.plugins.microbot.drofirecape.core.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Wave 12, 2026-10-20 0.3.82 run: the last Tz-Kek was trapped two tiles away and shootable, but the
 * controller held "let melee settle into existing wall shot" until a watchdog released it. */
class TrappedBlobShotTest {
    @Test void aLoneTrappedBlobIsShotFromTheTrap()throws Exception {
        for(int tick:new int[]{1175,1190}) {
            Snapshot s=PureWave38And41ReplayTest.scene("pure-076",tick);Mob kek=s.mobs().get(0);
            assertEquals(Kind.BLOB,kek.kind());
            assertTrue(CombatPlanner.playerCanAttack(s,s.player(),kek),"Client and model agree: in range with line of sight");
            assertTrue(CaveSafety.trapped(s,kek));
            assertTrue(PureSafety.releaseSafe(s,kek,Protection.NONE),"Its babies are melee only; one prayer covers them");
            PureLureController lure=new PureLureController();
            Plan shot=new Plan(s.player(),s.player(),Protection.MELEE,kek.index(),true,0,0,0,"replay");
            assertTrue(lure.allowsImmediateShot(s,kek,shot),"tick "+tick+": shoot from the trap instead of waiting");
        }
    }
    @Test void aTrappedBlobIsStillNotSplitWhileATokXilCanAlsoReach()throws Exception {
        Snapshot trapped=PureWave38And41ReplayTest.scene("pure-076",1175);Mob kek=trapped.mobs().get(0);
        Mob ranger=new Mob(99,Kind.RANGER,new Tile(44,33),3,-1,-1,-1,Protection.RANGE,true);
        Snapshot s=new Snapshot(trapped.tick(),trapped.player(),trapped.grid(),List.of(kek,ranger),100,true,5,Protection.NONE);
        assertNotEquals(0,CombatPlanner.threats(s.grid(),ranger,s.player(),s.jadStyle())&CombatPlanner.bit(Protection.RANGE),"Precondition: a Tok-Xil lane");
        assertFalse(PureSafety.releaseSafe(s,kek,Protection.RANGE),"Babies in contact plus a Tok-Xil lane: no single prayer covers both");
    }
}
