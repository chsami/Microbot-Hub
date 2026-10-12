/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape.core;

import java.util.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;

/** Spatial checks shared by pure planning, fast shots and final dispatch.
 * Scores are conservative model costs, not promises about actual hit amounts. */
public final class PureSafety {
    private PureSafety() { }
    public static boolean tiny(Mob m) {
        return m.kind()==Kind.BAT||m.kind()==Kind.BLOB||m.kind()==Kind.BABY;
    }
    public static int mask(Snapshot s,List<Mob> mobs,Tile at) {
        int result=0;
        for(Mob mob:mobs)result|=CombatPlanner.threats(s.grid(),mob,at,s.jadStyle());
        return result;
    }
    public static Protection guard(Snapshot s,List<Mob> mobs,Tile at) {
        int styles=mask(s,mobs,at);
        if((styles&CombatPlanner.bit(Protection.MAGIC))!=0)return Protection.MAGIC;
        if((styles&CombatPlanner.bit(Protection.MELEE))!=0&&mobs.stream().anyMatch(m->
            m.kind()==Kind.MELEER&&s.grid().melee(m,at)))return Protection.MELEE;
        if((styles&CombatPlanner.bit(Protection.RANGE))!=0)return Protection.RANGE;
        return (styles&CombatPlanner.bit(Protection.MELEE))!=0?Protection.MELEE:Protection.NONE;
    }
    public static int exposure(Snapshot s,List<Mob> mobs,Tile at,Protection guard) {
        int result=0;
        for(Mob mob:mobs)if((CombatPlanner.threats(s.grid(),mob,at,s.jadStyle())
            &~CombatPlanner.bit(guard))!=0)result+=mob.kind().maxHit;
        return result;
    }
    public static boolean chipPressure(Snapshot s) {
        List<Mob> next=CombatPlanner.advance(s.grid(),s.mobs(),s.player(),s.jadStyle());
        boolean contact=next.stream().anyMatch(m->CaveSafety.meleeFollower(m)&&s.grid().melee(m,s.player()));
        int styles=mask(s,next,s.player());
        return contact&&(styles&(CombatPlanner.bit(Protection.RANGE)|CombatPlanner.bit(Protection.MAGIC)))!=0;
    }
    /** Do not finish a blocker and reveal a previously sheltered mage/large
     * melee, or split a safely trapped blob into unprotected contact babies. */
    public static boolean releaseSafe(Snapshot s,Mob target,Protection prayer) {
        if(s.meleeMode()||s.mobs().stream().anyMatch(m->m.kind()==Kind.JAD))return true;
        List<Mob> before=CombatPlanner.advance(s.grid(),s.mobs(),s.player(),s.jadStyle());
        List<Mob> after=CombatPlanner.advance(s.grid(),CombatPlanner.afterDefeat(s.mobs(),target),s.player(),s.jadStyle());
        for(Mob released:after) {
            int exposed=CombatPlanner.threats(s.grid(),released,s.player(),s.jadStyle())&~CombatPlanner.bit(prayer);
            if(exposed==0)continue;
            Mob original=null;for(Mob old:before)if(old.index()==released.index()){original=old;break;}
            if(released.kind()==Kind.MAGER||released.kind()==Kind.MELEER) {
                // Killing an active ranger may release a melee body-blocked by it.
                // If that leaves only melee threats, refusing the shot deadlocks
                // combat: the ranger cannot move and cannot be killed. The live
                // prayer owner observes the death and protects the remaining style.
                boolean removesLastShooter=target.kind()==Kind.RANGER
                    &&(CombatPlanner.threats(s.grid(),target,s.player(),s.jadStyle())
                        &CombatPlanner.bit(Protection.RANGE))!=0
                    &&(mask(s,after,s.player())&~CombatPlanner.bit(Protection.MELEE))==0;
                if(released.kind()==Kind.MELEER&&removesLastShooter)continue;
                if(original!=null&&(CombatPlanner.threats(s.grid(),original,s.player(),s.jadStyle())
                    &~CombatPlanner.bit(prayer))==0)return false;
            }
            if(target.kind()==Kind.BLOB&&released.kind()==Kind.BABY&&original==null
                &&before.stream().noneMatch(m->m.index()==target.index()&&s.grid().melee(m,s.player())))return false;
        }
        return true;
    }
    /** A real minimap command must not acquire new mage/ranger melee contact,
     * cross a newly exposed magic lane, or stop inside any living footprint. */
    public static boolean routeAllowed(Snapshot s,Tile destination) {
        return routeAllowed(s,destination,false);
    }
    /** Only for a committed firing approach after combat has stopped progressing.
     * Mixed projectile lanes may be unavoidable; contact and rock-trap guards remain. */
    public static boolean acquisitionRouteAllowed(Snapshot s,Tile destination) {
        return routeAllowed(s,destination,true);
    }
    private static boolean routeAllowed(Snapshot s,Tile destination,boolean acquisition) {
        if(destination==null)return false;
        if(destination.equals(s.player())||s.meleeMode())return true;
        if(s.mobs().stream().anyMatch(m->m.occupies(destination)))return false;
        List<Tile> path=MinimapMovement.path(s,destination);
        if(path.isEmpty())return false;
        List<Mob> future=s.mobs();int stride=s.running()&&s.runEnergy()>0?2:1;
        Set<Integer> hiddenMages=new HashSet<>();
        for(Mob m:s.mobs())if(m.kind()==Kind.MAGER&&
            (CombatPlanner.threats(s.grid(),m,s.player(),s.jadStyle())&CombatPlanner.bit(Protection.MAGIC))==0)
            hiddenMages.add(m.index());
        for(int i=1;i<path.size();i++) {
            Tile tile=path.get(i);
            if(!CaveSafety.mageStep(s,future,path.get(i-1),tile)||!TacticalMovement.clearOfJad(s,future,tile))return false;
            for(Mob m:future) {
                if(hiddenMages.contains(m.index())&&CombatPlanner.threats(s.grid(),m,tile,s.jadStyle())!=0) {
                    // Intentionally acquire a sheltered/remote mage only when
                    // Magic can cover the resulting position by itself. A ban
                    // on every new mage lane would strand the last monster.
                    List<Mob> next=CombatPlanner.advance(s.grid(),future,tile,s.jadStyle());
                    if(exposure(s,future,tile,Protection.MAGIC)>0
                        ||exposure(s,next,tile,Protection.MAGIC)>0) {
                        if(!acquisition||(mask(s,future,tile)&CombatPlanner.bit(Protection.MELEE))!=0
                            ||(mask(s,next,tile)&CombatPlanner.bit(Protection.MELEE))!=0)return false;
                    }
                }
                if(m.kind()==Kind.RANGER&&m.distance(s.player())>=2&&m.distance(tile)<2)return false;
            }
            if(i%stride==0||i==path.size()-1)future=CombatPlanner.advance(s.grid(),future,tile,s.jadStyle());
        }
        return future.stream().noneMatch(m->m.occupies(destination));
    }
    /** Four-tick pressure is used only to compare spatial alternatives. Clocks
     * are deliberately not extrapolated here; the live prayer owner owns timing. */
    public static int stationaryCost(Snapshot s) {
        List<Mob> future=s.mobs();int total=0;
        for(int i=0;i<4;i++) {
            future=CombatPlanner.advance(s.grid(),future,s.player(),s.jadStyle());
            total+=exposure(s,future,s.player(),guard(s,future,s.player()));
        }
        return total;
    }
}
