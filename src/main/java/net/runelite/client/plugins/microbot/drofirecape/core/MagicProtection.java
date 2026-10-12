/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape.core;

import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;

/** Mager safety overrides range weighting and optional prayer flicks. */
public final class MagicProtection {
    private MagicProtection() {}
    public static Protection choose(Snapshot s,Tile destination,int inFlightUntil,Protection otherwise) {
        if(s.tick()<=inFlightUntil)return Protection.MAGIC;
        for(Mob mob:s.mobs())if(mob.kind()==Kind.MAGER) {
            if(exposed(s,mob,s.player())||destination!=null&&exposed(s,mob,destination))return Protection.MAGIC;
        }
        return otherwise;
    }
    private static boolean exposed(Snapshot s,Mob mage,Tile tile) {
        return mage.distance(tile)<=mage.kind().range&&s.grid().sight(mage,tile);
    }
}
