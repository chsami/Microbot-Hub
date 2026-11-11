/* Copyright (c) 2026, DRO (droplugins). SPDX-License-Identifier: BSD-2-Clause */
package net.runelite.client.plugins.microbot.drofirecape.core;

import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;

/**
 * "Manual movement" targets: the player walks, so only a monster in range and sight of the player's own tile is ever
 * chosen (an attack on it never walks the player). With Jad, an untagged healer in reach is tagged first, then Jad.
 * Otherwise the current target is kept while it stays in reach, else the planner's priority minus distance picks.
 */
public final class ManualTargets {
    private ManualTargets(){}
    public static Mob choose(Snapshot s,int interacting,Protection prayer) {
        if(s==null||s.player()==null)return null;
        Tile p=s.player();
        boolean jad=false;Mob untagged=null,jadMob=null;
        for(Mob m:s.mobs()) {
            if(m.kind()!=Kind.JAD&&m.kind()!=Kind.HEALER)continue;
            if(m.kind()==Kind.JAD)jad=true;
            if(!CombatPlanner.playerCanAttack(s,p,m))continue;
            if(m.kind()==Kind.JAD)jadMob=m;
            else if(!m.attackingPlayer()&&(untagged==null||m.distance(p)<untagged.distance(p)))untagged=m;
        }
        if(jad) {
            if(untagged!=null)return untagged;
            if(jadMob!=null)return jadMob;
        }
        Mob best=null;int max=Integer.MIN_VALUE;
        for(Mob m:s.mobs()) {
            if(!CombatPlanner.playerCanAttack(s,p,m))continue;
            if(m.index()==interacting)return m;
            int score=CombatPlanner.priority(m,prayer)-m.distance(p);
            if(score>max){max=score;best=m;}
        }
        return best;
    }
}
