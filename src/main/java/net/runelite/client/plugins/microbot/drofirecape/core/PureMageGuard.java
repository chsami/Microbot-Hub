/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape.core;

import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;

/** Release only a stale HOLD/pre-arm hint, never a live native deadline.
 * All actual mages remain in every snapshot, route and prayer forecast. */
public final class PureMageGuard {
    private PureMageGuard(){}
    public static boolean allTerrainCovered(Snapshot s) {
        if(s==null||s.meleeMode()||s.mobs().stream().anyMatch(m->m.kind()==Kind.JAD))return false;
        long count=s.mobs().stream().filter(m->m.kind()==Kind.MAGER).count();
        // Existing 64-step, single-mage pursuit test excludes temporary NPC
        // body blocks and both current projectile and player firing access.
        return count>0&&CaveSafety.coveredMages(s).size()==count;
    }
    public static Protection held(Snapshot s,Protection requested,Protection previous) {
        if(requested==Protection.MAGIC||!allTerrainCovered(s))return HeldProtection.choose(s,requested,previous);
        for(Mob m:s.mobs())if(m.kind()==Kind.MELEER&&CaveSafety.active(s,m))return Protection.MELEE;
        for(Mob m:s.mobs())if(m.kind()==Kind.RANGER)return Protection.RANGE;
        for(Mob m:s.mobs())if(m.kind()!=Kind.MAGER)return Protection.MELEE;
        return requested;
    }
}
