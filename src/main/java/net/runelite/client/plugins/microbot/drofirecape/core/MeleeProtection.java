/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape.core;

import java.util.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;

/** Held melee-mode overheads, not ordinary-NPC attack-cycle flicking.
 * Jad defaults to MELEE before contact; a recent observed range/magic wind-up
 * temporarily overrides it. Mixed-style contact can still cause damage.
 */
public final class MeleeProtection {
    private MeleeProtection() { }
    public static Protection choose(Snapshot s,Tile destination,Protection held) {
        Tile to=destination==null?s.player():destination;
        return choose(s.grid(),s.mobs(),CombatPlanner.advance(s.grid(),s.mobs(),to,s.jadStyle()),
            s.player(),to,s.tick(),s.jadStyle(),held);
    }
    static Protection choose(CollisionGrid grid,List<Mob> before,List<Mob> after,
                             Tile from,Tile to,int tick,Protection jadStyle,Protection held) {
        for(Mob mob:before)if(mob.kind()==Kind.JAD) {
            int age=mob.lastAttackTick()<0?Integer.MAX_VALUE:tick-mob.lastAttackTick();
            if(age>=0&&age<=5&&jadStyle!=Protection.NONE)return jadStyle;
            boolean contact=mob.distance(from)<=1||mob.distance(to)<=1;
            for(Mob future:after)if(future.kind()==Kind.JAD&&future.distance(to)<=1)contact=true;
            if(contact)return Protection.MELEE;
            // Outside melee reach there is no reason to return to melee prayer.
            return jadStyle==Protection.NONE?Protection.MAGIC:jadStyle;
        }
        int[] weights=new int[4];
        for(int i=0;i<before.size();i++) {
            Mob a=before.get(i),b=after.get(i);
            int mask=CombatPlanner.threats(grid,a,from,jadStyle)|CombatPlanner.threats(grid,b,to,jadStyle);
            for(Protection p:Protection.values())if((mask&CombatPlanner.bit(p))!=0)
                weights[p.ordinal()]+=a.kind().maxHit;
        }
        Protection best=held==null?Protection.NONE:held;
        int highest=weights[best.ordinal()];
        // Hold the present style on equal-risk ordinary attacks, avoiding churn.
        // With no prior protection, favor mage/range over a contact-style tie.
        for(Protection candidate:new Protection[]{Protection.MAGIC,Protection.RANGE,Protection.MELEE})
            if(weights[candidate.ordinal()]>highest){highest=weights[candidate.ordinal()];best=candidate;}
        if(highest>0)return best;
        if(before.isEmpty())return best;
        // Approaching the first monster: arm before entering its attack envelope.
        if(best!=Protection.NONE)return best;
        for(Mob mob:before)if(mob.kind()==Kind.MAGER)return Protection.MAGIC;
        for(Mob mob:before)if(mob.kind()==Kind.RANGER)return Protection.RANGE;
        return Protection.MELEE;
    }
}
