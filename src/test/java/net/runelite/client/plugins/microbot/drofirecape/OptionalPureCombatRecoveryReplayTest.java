/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape;
import net.runelite.client.plugins.microbot.drofirecape.*;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import net.runelite.client.plugins.microbot.drofirecape.core.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;

public class OptionalPureCombatRecoveryReplayTest {
    private Snapshot at(Snapshot s,int tick,Tile player,List<Mob> mobs) { return new Snapshot(tick,player,s.grid(),mobs,s.runEnergy(),s.running(),s.weaponRange(),s.jadStyle()).atWave(s.wave()); }
    @Test public void projectileFallbackNeverAllowsMageContact()throws Exception {
        Snapshot s=new Snapshot(1,new Tile(20,20),new CollisionGrid(new int[104][104]),
            List.of(new Mob(1,Kind.MAGER,new Tile(23,20),5,-1,-1,-1,Protection.MAGIC,true)),100,true,5,Protection.NONE);
        assertFalse(PureSafety.acquisitionRouteAllowed(s,new Tile(22,20)));
    }
    @Test public void stalledMageContactStillEscapesBeforeShooting() {
        Snapshot s=new Snapshot(1,new Tile(22,20),new CollisionGrid(new int[104][104]),
            List.of(new Mob(1,Kind.MAGER,new Tile(23,20),5,10,10,0,Protection.MELEE,true)),100,true,5,Protection.NONE);
        PureCombatRecovery recovery=new PureCombatRecovery();recovery.observe(s);
        s=at(s,25,s.player(),s.mobs());recovery.observe(s);Plan escape=recovery.plan(s);
        assertNotNull(escape);assertEquals(-1,escape.targetIndex());assertNotEquals(s.player(),escape.nextStep());
        assertTrue(CaveSafety.clearOfMagers(s,s.mobs(),escape.nextStep()));
        assertTrue(recovery.routeAllowed(s,escape.nextStep()));
    }
}
