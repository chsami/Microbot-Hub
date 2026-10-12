/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape.core;

import java.util.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;

/** Opt-in waves 1–2 only: acquire a bat at real weapon range instead of
 * running into it. A blocked approach eventually yields to the existing lure.
 * This does not promise a kill before contact or alter late-wave priorities. */
public final class OpeningBatPolicy {
    private int wave=-1,began=-1,progress=-1,lastTick=-1;
    private boolean peeked;
    public void reset(){wave=began=progress=lastTick=-1;lastGeometry="";peeked=false;}
    public static boolean eligible(Snapshot s){return !s.meleeMode()&&s.weaponRange()>1
        &&s.wave()>=1&&s.wave()<=2&&!s.mobs().isEmpty()
        &&s.mobs().stream().allMatch(m->m.kind()==Kind.BAT);}
    private static boolean contactSoon(List<Mob> mobs,Tile tile) {
        for(Mob m:mobs)if(m.distance(tile)<=2)return true;
        return false;
    }
    public Plan decide(Snapshot s,Tile home,Tile committed) {
        if(!eligible(s)||home==null)return null;
        if(wave!=s.wave()){reset();wave=s.wave();began=progress=s.tick();}
        // An animation/clock alone must not renew a stalled waiting period.
        StringBuilder positions=new StringBuilder(s.player().toString());
        for(Mob m:s.mobs())positions.append('/').append(m.index()).append('@').append(m.tile()).append(':').append(m.healthRatio());
        String geometry=positions.toString();
        // Geometry ledger is independent of current attack-tick metadata.
        if(lastTick!=s.tick()) {
            if(!geometry.equals(lastGeometry)){lastGeometry=geometry;progress=s.tick();}
            lastTick=s.tick();
        }
        if(s.tick()<began||s.tick()-began>=80||s.tick()-progress>=16)return null;
        Protection guard=CombatPlanner.protectionForNextTick(s,s.player());
        Mob target=s.mobs().stream().filter(m->CombatPlanner.playerCanAttack(s,s.player(),m))
            .min(Comparator.comparingInt((Mob m)->m.distance(s.player())).thenComparingInt(Mob::index)).orElse(null);
        if(target!=null)return hold(s,guard,target.index(),"Shoot approaching bat at actual weapon range");
        // A southern bat can stop below Italy while we wait north of it.
        // The existing one-tile west peek opens that approach without a long
        // southward chase. Fifteen tiles is an observation trigger, NOT range.
        if(!peeked&&s.player().equals(home)&&s.mobs().stream().anyMatch(m->m.distance(home)<=15)
            &&s.grid().step(home,home.add(-1,0)))peeked=true;
        Tile camp=peeked?home.add(-1,0):home;
        if(s.player().equals(camp))return hold(s,guard,-1,"Wait at cover; do not chase a distant bat");
        Plan route=committed!=null?MinimapMovement.checked(s,camp,committed,Protection.NONE,"Opening bat: reach cover"):
            MinimapMovement.route(s,camp,null,Protection.NONE,false,"Opening bat: reach cover");
        if(!CombatPlanner.actionable(route,false))return hold(s,guard,-1,"Wait for bat access before resuming the existing lure");
        List<Tile> path=MinimapMovement.path(s,route.nextStep());List<Mob> future=s.mobs();
        int stride=s.running()&&s.runEnergy()>0?2:1;
        for(int i=1;i<path.size();i++) {
            if(contactSoon(future,path.get(i)))return hold(s,guard,-1,"Do not run into the approaching bat");
            if(i%stride==0||i==path.size()-1) {
                future=CombatPlanner.advance(s.grid(),future,path.get(i),s.jadStyle());
                if(contactSoon(future,path.get(i)))return hold(s,guard,-1,"Let the bat enter firing range");
            }
        }
        return route;
    }
    private String lastGeometry="";
    private static Plan hold(Snapshot s,Protection guard,int target,String reason) {
        int risk=CombatPlanner.immediateExposure(s,s.player(),guard);
        return new Plan(s.player(),s.player(),guard,target,risk==0,0,0,risk,"Opening bat: "+reason);
    }
}
