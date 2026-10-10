/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
/* Attack catalogue reference: OreoCupcakes/kotori-plugins @ 8904ec22adef20cdf198fe583387e3cd4428dca2.
 * Copyright (c) 2018 Jordan Atwood; (c) 2019 Ganom and Lucas.
 * BSD-2-Clause terms and disclaimer are retained in THIRD-PARTY-NOTICES.txt, section 4.
 */
package net.runelite.client.plugins.microbot.drofirecape.core;

import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;

/**
 * Attack animation catalogue cross-checked against the Ganom/Lucas Fight Caves
 * helper (Kotori port). Four-tick ordinary attacks are NOT Jad-style reactive
 * prayers: their overhead must be selected before launch. See THIRD-PARTY-NOTICES.
 * A stale/unknown clock is ready, never extrapolated through an unseen LOS break.
 */
public final class AttackClock {
    private AttackClock() { }
    public static Protection animation(Kind kind, int animation) {
        switch (kind) {
            case RANGER: return animation == 2633 ? Protection.RANGE : animation == 2628 ? Protection.MELEE : null;
            case MAGER: return animation == 2647 ? Protection.MAGIC : animation == 2644 ? Protection.MELEE : null;
            case MELEER: return animation == 2637 ? Protection.MELEE : null; // 2639 heals; not a swing
            case JAD: return animation == 2652 ? Protection.RANGE : animation == 2656 ? Protection.MAGIC : animation == 2655 ? Protection.MELEE : null;
            // Unverified blob/healer animation IDs deliberately retain an unknown clock.
            default: return null;
        }
    }
    public static boolean due(Mob mob, int serverTick) {
        return mob.lastAttackTick() < 0 || serverTick >= mob.lastAttackTick() + mob.kind().speed;
    }
    public static int remaining(Mob mob, int tick) {
        return mob.lastAttackTick() < 0 ? -1 : Math.max(0, mob.lastAttackTick() + mob.kind().speed - tick);
    }
    public static Mob launched(Mob mob, int tick, Protection style) {
        return new Mob(mob.index(),mob.kind(),mob.tile(),mob.size(),mob.healthRatio(),mob.healthScale(),tick,style,mob.attackingPlayer());
    }
    public static Protection animationWithTiny(Kind kind,int animation) {
        if(kind==Kind.BAT)return animation==2621?Protection.MELEE:null;
        if(kind==Kind.BLOB||kind==Kind.BABY)return animation==2625?Protection.MELEE:null;
        return animation(kind,animation);
    }
}
