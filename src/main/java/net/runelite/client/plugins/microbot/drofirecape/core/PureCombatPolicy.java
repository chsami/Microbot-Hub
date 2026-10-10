/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape.core;

import java.util.*;
import static net.runelite.client.plugins.microbot.drofirecape.core.CaveSafety.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;

/** Pure mode only adjusts preferences inside the original combat and recorded lure controllers. */
public final class PureCombatPolicy {
    private PureCombatPolicy() { }
    public static boolean rangerBeforeBat(Snapshot s,Mob mob) {
        return mob!=null&&mob.kind()==Kind.RANGER&&CaveSafety.active(s,mob)
            &&s.mobs().stream().noneMatch(m->(m.kind()==Kind.BAT||m.kind()==Kind.BABY&&s.grid().melee(m,s.player()))
                &&CombatPlanner.playerCanAttack(s,s.player(),m));
    }
    public static int targetPriority(Snapshot s,Mob mob,Protection prayer) {
        // A nearby ten-HP nuisance should not remain alive through a long mage
        // or ranger kill. Do not chase a hidden tiny monster out of rock cover.
        boolean contact=s.grid().melee(mob,s.player());
        if(mob.kind()==Kind.BAT&&mob.distance(s.player())<=2)return 1250;
        if(mob.kind()==Kind.BABY&&contact)return 1200;
        if(mob.kind()==Kind.BAT)return 1150;
        if(rangerBeforeBat(s,mob))return 1100;
        if(mob.kind()==Kind.BLOB&&contact&&prayer!=Protection.MELEE)return 1000;
        return CaveSafety.targetPriority(s,mob,prayer);
    }
    public static Mob preferredShot(Snapshot s,Protection prayer) {
        return s.mobs().stream().filter(m->CombatPlanner.playerCanAttack(s,s.player(),m)
            &&PureSafety.releaseSafe(s,m,prayer))
            .max(Comparator.comparingInt((Mob m)->targetPriority(s,m,prayer))
                .thenComparingInt(m->-m.distance(s.player()))).orElse(null);
    }
    public static Mob rangedTarget(Snapshot s) {
        return s.mobs().stream().filter(m->m.kind()==Kind.RANGER||m.kind()==Kind.MAGER)
            .max(Comparator.comparingInt((Mob m)->targetPriority(s,m,Protection.MAGIC))
                .thenComparingInt(m->CombatPlanner.playerCanAttack(s,s.player(),m)?1:0)
                .thenComparingInt(m->-m.distance(s.player()))).orElse(null);
    }
    public static boolean crowded(Snapshot s) {
        boolean ranged=s.mobs().stream().anyMatch(m->m.kind()==Kind.RANGER||m.kind()==Kind.MAGER);
        long followers=s.mobs().stream().filter(m->(m.kind()==Kind.MELEER||m.kind()==Kind.BLOB)
            &&!rockTrapped(s,m)).count();
        return ranged&&followers>0;
    }
    public static boolean coveredMelee(Snapshot s) {
        List<Mob> settled=s.mobs();
        for(int tick=0;tick<12;tick++)settled=CombatPlanner.advance(s.grid(),settled,s.player(),s.jadStyle());
        if(settled.stream().anyMatch(m->s.grid().melee(m,s.player())))return false;
        return s.mobs().stream().anyMatch(m->m.kind().range==1&&rockTrapped(s,m));
    }
    /** A follower may need its last step to the wall before the shot opens.
     * Judge the stationary result before starting another release excursion. */
    public static Mob settlingWallShot(Snapshot s) {
        if(s.meleeMode()||s.mobs().isEmpty()||s.mobs().stream().anyMatch(m->
            m.kind()!=Kind.MELEER&&m.kind()!=Kind.BLOB&&m.kind()!=Kind.BABY)
            ||!coveredMelee(s))return null;
        List<Mob> settled=s.mobs();
        for(int tick=0;tick<12;tick++)settled=CombatPlanner.advance(s.grid(),settled,s.player(),s.jadStyle());
        Snapshot end=new Snapshot(s.tick(),s.player(),s.grid(),settled,s.runEnergy(),s.running(),
            s.weaponRange(),s.jadStyle());
        Mob shot=CaveSafety.preferredShot(end,CombatPlanner.protectionForNextTick(end,end.player()));
        if(shot==null||!CaveSafety.trapped(end,shot))return null;
        int index=shot.index();
        return s.mobs().stream().filter(m->m.index()==index).findFirst().orElse(null);
    }
    /** Preserve rock geometry even when the current tile cannot yet shoot. */
    public static Mob establishedMeleeTrap(Snapshot s) {
        List<Mob> followers=new ArrayList<>();
        for(Mob mob:s.mobs())if(mob.kind()==Kind.MELEER||mob.kind()==Kind.BLOB)followers.add(mob);
        if(followers.isEmpty()||followers.stream().anyMatch(m->!CaveSafety.trapped(s,m)||!rockTrapped(s,m)))return null;
        return followers.stream().min(Comparator.comparingInt(m->m.distance(s.player()))).orElse(null);
    }
    /** Early-wave wall cleanup is rechecked after every movement, split or death. */
    public static boolean shelteredRangedAtWall(Snapshot s) {
        if(s.meleeMode()||s.mobs().stream().anyMatch(m->m.kind()==Kind.JAD||m.kind()==Kind.HEALER
            ||m.kind()==Kind.RANGER&&s.wave()>=53))return false;
        List<Mob> future=s.mobs();
        for(int tick=0;tick<=12;tick++) {
            for(Mob mob:future)if(mob.kind()==Kind.RANGER
                &&CombatPlanner.threats(s.grid(),mob,s.player(),s.jadStyle())!=0)return false;
            future=CombatPlanner.advance(s.grid(),future,s.player(),s.jadStyle());
        }
        return true;
    }
    private static boolean rockTrapped(Snapshot s,Mob original) {
        // A ranger's body is not permanent rock cover: killing it must not release a follower.
        List<Mob> alone=List.of(original);
        for(int tick=0;tick<24;tick++)alone=CombatPlanner.advance(s.grid(),alone,s.player(),s.jadStyle());
        Mob endMob=alone.get(0);
        Snapshot end=new Snapshot(s.tick(),s.player(),s.grid(),alone,s.runEnergy(),s.running(),s.weaponRange(),s.jadStyle());
        return !s.grid().melee(endMob,s.player())&&CaveSafety.trapped(end,endMob);
    }
    /** Strongly prefer arrival without melee contact or multiple styles; do not replace baseline recovery. */
    public static long penalty(Snapshot s,Tile tile,List<Mob> arrivals) {
        if(s.meleeMode()||s.mobs().size()<2)return 0;
        int styles=0,contacts=0,released=0;
        for(Mob m:arrivals) {
            styles|=CombatPlanner.threats(s.grid(),m,tile,s.jadStyle());
            if(s.grid().melee(m,tile))contacts++;
            if(m.kind().range==1) {
                Mob original=s.mobs().stream().filter(a->a.index()==m.index()).findFirst().orElse(null);
                if(original!=null&&CaveSafety.trapped(s,original)) {
                    Snapshot end=new Snapshot(s.tick(),tile,s.grid(),arrivals,s.runEnergy(),s.running(),
                        s.weaponRange(),s.jadStyle());
                    if(!CaveSafety.trapped(end,m))released++;
                }
            }
        }
        return (Math.max(0,Integer.bitCount(styles)-1)*2L+contacts+released*2L)*10_000_000L;
    }
    public static long approachPenalty(Snapshot s,Tile tile,List<Tile> path) {
        if(s.mobs().size()<2)return 0;
        List<Mob> arrivals=s.mobs();int stride=s.running()&&s.runEnergy()>0?2:1;
        for(int i=stride;i<path.size();i+=stride)
            arrivals=CombatPlanner.advance(s.grid(),arrivals,path.get(i),s.jadStyle());
        for(int i=0;i<12;i++)arrivals=CombatPlanner.advance(s.grid(),arrivals,tile,s.jadStyle());
        return penalty(s,tile,arrivals);
    }
    public static Plan approach(Snapshot s,Mob target,Tile main,String reason) {
        if(target==null)return null;
        if(target.kind()==Kind.MELEER&&main!=null&&!s.meleeMode()&&CaveSafety.trapped(s,target)
            &&!CombatPlanner.playerCanAttack(s,s.player(),target)) {
            // Demonstrated Italy/Dragon firing holds, checked with actual weapon
            // range. Approach the trapped side once; never restart its lure.
            Tile best=null;int distance=Integer.MAX_VALUE;
            for(Tile tile:List.of(main.add(-1,-7),main.add(-1,-9),main.add(-1,-5),
                main.add(-1,-4),main.add(-14,4),main.add(-23,8))) {
                List<Tile> path=MinimapMovement.path(s,tile);
                if(path.isEmpty()||!s.grid().open(tile)||!CombatPlanner.playerCanAttack(s,tile,target)
                    ||s.grid().melee(target,tile)||!clearOfMagers(s,s.mobs(),tile)
                    ||!preservesMageCover(s,tile)||approachPenalty(s,tile,path)!=0)continue;
                List<Mob> alone=List.of(target);
                for(int tick=0;tick<24;tick++)alone=CombatPlanner.advance(s.grid(),alone,tile,s.jadStyle());
                if(s.grid().melee(alone.get(0),tile)||!CaveSafety.trapped(
                    new Snapshot(s.tick(),tile,s.grid(),alone,s.runEnergy(),s.running(),s.weaponRange(),s.jadStyle()),alone.get(0)))continue;
                if(path.size()<distance){best=tile;distance=path.size();}
            }
            if(best!=null)return MinimapMovement.route(s,best,null,
                CombatPlanner.bestProtection(s.grid(),s.mobs(),s.player(),s.jadStyle()),false,
                "Approach recorded rock wall without releasing trapped melee");
        }
        if(target.kind()==Kind.MAGER&&main!=null&&!s.meleeMode()) {
            Tile sole=main.add(-1,-5);
            if(!sole.equals(s.player())&&CombatPlanner.playerCanAttack(s,sole,target)
                &&clearOfMagers(s,s.mobs(),sole)&&approachPenalty(s,sole,MinimapMovement.path(s,sole))==0)
                return MinimapMovement.route(s,sole,null,Protection.MAGIC,false,"Range mage from usable Italy sole");
        }
        CollisionGrid.PathTree paths=s.grid().pathsFrom(s.player(),s.mobs(),t->
            TacticalMovement.clearOfJad(s,s.mobs(),t)&&clearOfMagers(s,s.mobs(),t)&&pocketAllowed(s,main,t));
        List<Tile> bestPath=null;Tile best=null;long score=Long.MAX_VALUE;
        int range=s.weaponRange();
        for(int x=target.tile().x()-range;x<target.tile().x()+target.size()+range;x++)
            for(int y=target.tile().y()-range;y<target.tile().y()+target.size()+range;y++) {
                Tile tile=new Tile(x,y);
                if(!s.grid().open(tile)||!CombatPlanner.playerCanAttack(s,tile,target))continue;
                if(target.kind()==Kind.RANGER&&target.distance(tile)<3)continue;
                List<Tile> path=paths.to(tile);if(path.isEmpty())continue;
                // Last-melee cleanup must fire from a wall, not deliberately stand in contact.
                if(!s.meleeMode()&&s.grid().melee(target,tile))continue;
                Protection p=CombatPlanner.bestProtection(s.grid(),s.mobs(),tile,s.jadStyle());
                long cost=CombatPlanner.immediateExposure(s,tile,p)*1000L+path.size()*10L
                    +approachPenalty(s,tile,path);
                if(cost<score){score=cost;best=tile;bestPath=path;}
            }
        if(best==null)return null;
        if(best.equals(s.player())) {
            Protection p=contactProtection(s,CombatPlanner.protectionForNextTick(s,s.player()));
            int risk=CombatPlanner.immediateExposure(s,s.player(),p);
            return new Plan(best,best,p,target.index(),risk==0,0,0,risk,reason);
        }
        // Keep the checked pocket path, rather than routing a long click across its boundary.
        return (target.kind()==Kind.MAGER||preservesMageCover(s,best))?MinimapMovement.route(s,best,null,
            CombatPlanner.bestProtection(s.grid(),s.mobs(),s.player(),s.jadStyle()),false,reason):null;
    }
}
