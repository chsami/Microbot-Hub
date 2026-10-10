/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape.core;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.Tile;
/** A proposed/failed pull is not movement progress. Dead goals must expire. */
public final class HealerPullLease {
    private Tile goal,last;
    private int began=-1,progress=-1,invalid=-1;
    public void reset(){goal=last=null;began=progress=invalid=-1;}
    public boolean allow(int tick,Tile player,Tile requested,boolean actionable) {
        if(requested==null)return false;
        if(goal==null||!goal.equals(requested)){goal=requested;last=player;began=progress=tick;invalid=-1;}
        if(!player.equals(last)){last=player;progress=tick;}
        if(actionable)invalid=-1;else if(invalid<0)invalid=tick;
        return tick>=began&&tick-began<40&&tick-progress<8&&(invalid<0||tick-invalid<3);
    }
}
