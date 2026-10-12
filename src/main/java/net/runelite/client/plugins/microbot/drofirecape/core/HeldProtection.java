/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape.core;

import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;

/** Continuous overheads, deliberately independent of attack cooldown/projectile clocks. */
public final class HeldProtection {
    private HeldProtection() { }
    public static Protection choose(Snapshot scene,Protection requested,Protection held) {
        // Use the observed Jad style continuously, never flick it off between attacks.
        for(Mob mob:scene.mobs())if(mob.kind()==Kind.JAD)
            return scene.jadStyle()==Protection.NONE?Protection.MAGIC:scene.jadStyle();
        // Pre-arm while the threat is still approaching or behind cover. Movement may
        // expose it before the next snapshot; a launch animation is too late to arm.
        for(Mob mob:scene.mobs())if(mob.kind()==Kind.MAGER)return CaveSafety.contactProtection(scene,Protection.MAGIC);
        for(Mob mob:scene.mobs())if(mob.kind()==Kind.MELEER&&CaveSafety.active(scene,mob))return Protection.MELEE;
        for(Mob mob:scene.mobs())if(mob.kind()==Kind.RANGER)return Protection.RANGE;
        if(!scene.mobs().isEmpty())return Protection.MELEE;
        // Keep the pre-spawn guard, including across the worker/main-loop boundary.
        return requested!=Protection.NONE?requested:held;
    }
}
