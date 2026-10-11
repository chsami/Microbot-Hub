/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape.core;

import java.util.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;

/** Bounded, committed pure-only spacing to a durable, usable firing position.
 * Route costs are conservative geometry-model costs, not predicted live damage. */
public final class PureSpacing {
    private static final int STALL_TICKS=10,RETRY_TICKS=10,SETTLE_TICKS=24;
    private Tile goal,lastPlayer;
    private int since=-1,lastSearch=-1,arrived=-1000,commitTicks=8;
    private int progressAt=-1,retryAfter=-1;

    public void reset(){goal=lastPlayer=null;since=lastSearch=progressAt=retryAfter=-1;arrived=-1000;commitTicks=8;}
    /** An intention is not an acknowledged move. The shared recovery ledger
     * separately observes actual, previously unvisited player/goal pairs. */
    public Tile destination(){return goal;}
    public boolean owns(Plan plan){return goal!=null&&plan!=null&&goal.equals(plan.destination());}
    /** Release a failed/expired leg without immediately selecting it again.
     * Keep the finite cooldown when the recovery controller takes ownership. */
    public void yieldToRecovery(int tick){goal=lastPlayer=null;since=progressAt=-1;retryAfter=tick+RETRY_TICKS;}

    public Plan decide(Snapshot s,int shotDelay) {
        if(s.meleeMode()||s.mobs().isEmpty()||s.mobs().stream().anyMatch(m->m.kind()==Kind.JAD||m.kind()==Kind.HEALER)) {
            reset();return null;
        }
        if(goal!=null) {
            if(s.tick()<since){reset();return null;}
            if(s.player().equals(goal)){goal=lastPlayer=null;arrived=s.tick();return null;}
            if(!s.player().equals(lastPlayer)){lastPlayer=s.player();progressAt=s.tick();}
            if(s.tick()-since<=commitTicks&&s.tick()-progressAt<STALL_TICKS
                &&PureSafety.routeAllowed(s,goal)&&forecast(s,MinimapMovement.path(s,goal))!=null) {
                Plan committed=route(s,goal);
                if(CombatPlanner.actionable(committed,false))return committed;
            }
            yieldToRecovery(s.tick());
            return null;
        }
        boolean simultaneous=synchronisedContact(s)||synchronisedShooters(s);
        if((!PureSafety.chipPressure(s)&&!simultaneous)||s.tick()==lastSearch
            ||s.tick()<retryAfter||s.tick()-arrived<2)return null;
        lastSearch=s.tick();
        int baseline=PureSafety.stationaryCost(s);
        if(baseline<=0)return null;
        // A fresh, observed co-tick mage/ranger or big-melee/shooter pair cannot
        // be covered by one overhead. Only that evidence enables the wider search.
        // Off-tick or unknown shooters must not acquire this wider-movement policy.
        int radius=simultaneous?18:6,maxPath=simultaneous?37:13;
        MinimapMovement.TerrainPaths paths=MinimapMovement.terrainPaths(s);
        List<Tile> candidates=new ArrayList<>();
        for(int dx=-radius;dx<=radius;dx++)for(int dy=-radius;dy<=radius;dy++) {
            Tile tile=s.player().add(dx,dy);
            if(s.grid().open(tile)&&!tile.equals(s.player())&&s.mobs().stream().noneMatch(m->m.occupies(tile)))candidates.add(tile);
        }
        candidates.sort(Comparator.comparingInt(t->t.distance(s.player())));
        long bestScore=Long.MAX_VALUE;Plan best=null;
        for(Tile tile:candidates) {
            List<Tile> path=paths.to(tile);
            if(path.isEmpty()||path.size()>maxPath)continue;
            Forecast result=forecast(s,path);
            if(result==null||result.cost>=baseline)continue;
            long score=result.cost*100L+path.size();
            if(score>=bestScore||!PureSafety.routeAllowed(s,tile))continue;
            Plan checked=route(s,tile);
            if(!CombatPlanner.actionable(checked,false))continue;
            // A clipped minimap command can have a different tie-broken path
            // from the final goal. Forecast the actual command plus remaining
            // route, rather than admitting a different diagonal-first preview.
            List<Tile> commandPath=commandPath(s,checked);
            int commandEnd=MinimapMovement.path(s,checked.nextStep()).size()-1;
            Forecast dispatched=forecast(s,commandPath,commandEnd);
            if(dispatched==null||dispatched.cost>=baseline)continue;
            score=dispatched.cost*100L+commandPath.size();
            if(score>=bestScore)continue;
            best=checked;bestScore=score;
        }
        if(best==null)return null;
        goal=best.destination();lastPlayer=s.player();since=progressAt=s.tick();
        commitTicks=simultaneous?24:8;
        return best;
    }

    /** Require a legal shot, not just geometric visibility of a body-blocking
     * blob which the final attack gate will refuse to kill. Check both arrival
     * and the settled formation, including every transient exposure on the way. */
    private static Forecast forecast(Snapshot s,List<Tile> path) {
        return forecast(s,path,path.size()-1);
    }
    private static Forecast forecast(Snapshot s,List<Tile> path,int commandEnd) {
        if(path.isEmpty())return null;
        Tile tile=path.get(path.size()-1);List<Mob> future=s.mobs();
        int stride=s.running()&&s.runEnergy()>0?2:1,cost=0,tick=s.tick();
        for(int i=0;i<path.size()-1;) {
            // A new click after an odd-length running leg starts next tick;
            // do not spend the first command's spare stride on the next leg.
            int limit=i<commandEnd?commandEnd:path.size()-1;
            i=Math.min(i+stride,limit);Tile next=path.get(i);
            future=CombatPlanner.advance(s.grid(),future,next,s.jadStyle());tick++;
            cost+=PureSafety.exposure(s,future,next,PureSafety.guard(s,future,next));
        }
        Snapshot arrival=at(s,tick,tile,future);
        if(!firingPosition(arrival))return null;
        for(int i=0;i<SETTLE_TICKS;i++) {
            cost+=PureSafety.exposure(arrival,future,tile,PureSafety.guard(arrival,future,tile));
            future=CombatPlanner.advance(s.grid(),future,tile,s.jadStyle());
        }
        Snapshot settled=at(s,tick+SETTLE_TICKS,tile,future);
        if(!firingPosition(settled)||PureSafety.stationaryCost(settled)!=0)return null;
        return new Forecast(cost);
    }
    private static boolean firingPosition(Snapshot s) {
        return s.mobs().stream().noneMatch(m->m.occupies(s.player())||m.kind()==Kind.RANGER&&m.distance(s.player())<2)
            &&CaveSafety.clearOfMagers(s,s.mobs(),s.player())
            &&PureCombatPolicy.preferredShot(s,PureSafety.guard(s,s.mobs(),s.player()))!=null;
    }
    private static Snapshot at(Snapshot s,int tick,Tile tile,List<Mob> mobs) {
        return new Snapshot(tick,tile,s.grid(),mobs,s.runEnergy(),s.running(),s.weaponRange(),s.jadStyle()).atWave(s.wave());
    }
    private static List<Tile> commandPath(Snapshot s,Plan plan) {
        List<Tile> first=MinimapMovement.path(s,plan.nextStep());
        if(first.isEmpty()||plan.nextStep().equals(plan.destination()))return first;
        List<Tile> second=MinimapMovement.path(at(s,s.tick(),plan.nextStep(),s.mobs()),plan.destination());
        if(second.isEmpty())return List.of();
        List<Tile> all=new ArrayList<>(first);all.addAll(second.subList(1,second.size()));return all;
    }
    private static boolean fresh(Snapshot s,Mob mob) {
        return mob.lastAttackTick()>=0&&s.tick()>=mob.lastAttackTick()&&s.tick()-mob.lastAttackTick()<=mob.kind().speed;
    }
    private static boolean synchronisedContact(Snapshot s) {
        for(Mob melee:s.mobs())if(melee.kind()==Kind.MELEER&&s.grid().melee(melee,s.player())&&fresh(s,melee))
            for(Mob shooter:s.mobs())if((shooter.kind()==Kind.MAGER||shooter.kind()==Kind.RANGER)
                &&shooter.lastAttackTick()==melee.lastAttackTick()
                &&(CombatPlanner.threats(s.grid(),shooter,s.player(),s.jadStyle())
                    &CombatPlanner.bit(shooter.kind().protection))!=0)return true;
        return false;
    }
    private static boolean synchronisedShooters(Snapshot s) {
        for(Mob mage:s.mobs())if(mage.kind()==Kind.MAGER&&fresh(s,mage)&&mage.attackingPlayer()
            &&mage.lastStyle()==Protection.MAGIC&&CaveSafety.active(s,mage))
            for(Mob ranger:s.mobs())if(ranger.kind()==Kind.RANGER&&ranger.lastAttackTick()==mage.lastAttackTick()
                &&ranger.attackingPlayer()&&ranger.lastStyle()==Protection.RANGE&&CaveSafety.active(s,ranger))return true;
        return false;
    }
    private static Plan route(Snapshot s,Tile goal) {
        Protection guard=MinimapMovement.routeProtection(s,goal,PureSafety.guard(s,s.mobs(),s.player()));
        return MinimapMovement.route(s,goal,goal,guard,false,"Pure: separate conflicting threats into a firing position");
    }
    private static final class Forecast {
        private final int cost;
        private Forecast(int cost){this.cost=cost;}
    }
}
