/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape.core;

import java.util.List;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;

public final class PureCombatPlanner extends CombatPlanner {
    @Override protected boolean releaseAllowed(Snapshot s,Mob target,Protection prayer){return PureSafety.releaseSafe(s,target,prayer);}
    @Override protected boolean avoidsMeleeKiting(Snapshot s){return false;}
    @Override protected boolean routeAllowed(Snapshot s,Tile immediate){return PureSafety.routeAllowed(s,immediate);}
    @Override protected long arrivalPenalty(Snapshot s,Tile p,List<Mob> arrivals){return PureCombatPolicy.penalty(s,p,arrivals);}
    @Override protected boolean rangerFirst(Snapshot s,Mob m){return PureCombatPolicy.rangerBeforeBat(s,m);}
    @Override protected int targetScore(Snapshot s,Tile p,List<Mob> mobs,Mob m,Protection prayer) {
        Snapshot positioned=new Snapshot(s.tick(),p,s.grid(),mobs,s.runEnergy(),s.running(),s.weaponRange(),s.jadStyle(),s.meleeMode()).atWave(s.wave());
        if(!PureSafety.releaseSafe(positioned,m,prayer))return SKIP_TARGET;
        return PureCombatPolicy.targetPriority(positioned,m,prayer);
    }
}
