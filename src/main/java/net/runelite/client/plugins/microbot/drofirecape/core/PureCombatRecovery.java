/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape.core;

import java.util.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;

/** Combat progress bounds a stalled Pure plan; a progressing committed lure gets a finite completion window. */
public final class PureCombatRecovery {
    private static final int STALL_TICKS=24,ROUTE_STALL_TICKS=10,MAX_ROUTE_TILES=40,MAX_LURE_TICKS=64;
    private final Map<Integer,Integer> health=new HashMap<>();
    private final Map<Integer,Integer> priorityApproaches=new HashMap<>();
    private final Map<Tile,Set<Tile>> lureVisited=new HashMap<>();
    private final Set<Tile> failed=new HashSet<>();
    private int since=-1,lastTick=-1,searchTick=-1,target=-1,routeAt=-1,lureAt=-1,lureProgressAt=-1;
    private Tile goal,lastPlayer;
    private long mobGeometry=Long.MIN_VALUE;
    private boolean active,pursuitGoal;
    public void reset(){health.clear();priorityApproaches.clear();failed.clear();clearLureProgress();since=lastTick=searchTick=target=routeAt=-1;goal=lastPlayer=null;mobGeometry=Long.MIN_VALUE;active=pursuitGoal=false;}
    public boolean active(){return active;}
    public Tile destination(){return goal;}
    public void observe(Snapshot s) {
        observe(s,null);
    }
    public void observe(Snapshot s,Tile committedLure) {
        if(s.meleeMode()||s.mobs().isEmpty()||s.mobs().stream().anyMatch(m->m.kind()==Kind.JAD||m.kind()==Kind.HEALER)) {reset();return;}
        if(s.tick()==lastTick)return;
        Map<Integer,Integer> now=new HashMap<>();for(Mob m:s.mobs())now.put(m.index(),m.healthRatio());
        boolean rosterChanged=lastTick<0||s.tick()<lastTick||!now.keySet().equals(health.keySet());
        boolean progress=rosterChanged;
        for(Mob m:s.mobs())if(health.containsKey(m.index())&&m.healthRatio()>=0
            &&(health.get(m.index())<0||m.healthRatio()<health.get(m.index())))progress=true;
        if(progress){since=s.tick();clearLureProgress();}
        // Reconsider a failed endpoint only when the monsters actually changed
        // position, not because another click or player step occurred.
        observeGeometry(s);
        if(rosterChanged){active=pursuitGoal=false;goal=null;target=-1;failed.clear();priorityApproaches.clear();}
        health.clear();health.putAll(now);lastTick=s.tick();
        if(!active&&committedLure!=null) {
            if(lureAt<0)lureAt=s.tick();
            // Each leg may revisit its outward tiles on the way home. Repeating
            // the same tile/goal pair or merely dispatching clicks is not progress.
            if(lureVisited.computeIfAbsent(committedLure,k->new HashSet<>()).add(s.player()))lureProgressAt=s.tick();
        }
        boolean finishingLure=lureAt>=0&&s.tick()-lureAt<MAX_LURE_TICKS
            &&s.tick()-lureProgressAt<(committedLure!=null?ROUTE_STALL_TICKS:6);
        if(s.tick()-since>=STALL_TICKS&&!finishingLure)active=true;
    }
    private void clearLureProgress(){lureAt=lureProgressAt=-1;lureVisited.clear();}
    private void observeGeometry(Snapshot s) {
        long geometry=0;for(Mob m:s.mobs())geometry+=31L*m.index()+10816L*m.tile().x()+m.tile().y();
        if(geometry!=mobGeometry){failed.clear();mobGeometry=geometry;}
    }
    /** Final dispatch may see a longer or newer route than the planner preview. */
    public void rejected(Snapshot s,Plan route) {
        if(route==null)return;
        observeGeometry(s);
        if(route.destination()!=null&&!route.destination().equals(s.player()))failed.add(route.destination());
        if(route.nextStep()!=null&&!route.nextStep().equals(s.player()))failed.add(route.nextStep());
        if(goal!=null&&failed.contains(goal)){goal=null;target=-1;pursuitGoal=false;}
    }
    public boolean owns(Plan plan) {
        return active&&plan!=null&&goal!=null&&goal.equals(plan.destination());
    }
    public boolean routeAllowed(Snapshot s,Tile destination) {
        return active&&goal!=null&&PureSafety.acquisitionRouteAllowed(s,destination);
    }
    public Plan plan(Snapshot s) {
        if(!active)return null;
        if(CaveSafety.mageContact(s)) {
            Plan escape=CaveSafety.escapeMage(s);
            if(escape!=null){goal=escape.destination();target=-1;}
            return escape;
        }
        Protection prayer=PureSafety.guard(s,s.mobs(),s.player());
        Mob shot=PureCombatPolicy.preferredShot(s,prayer);
        // A live ranger one tile beyond our weapon range must not shoot through
        // a full mage kill. Acquire its checked firing tile before a lower-priority
        // stationary shot, while leaving genuinely sheltered rangers alone.
        Mob ranger=s.mobs().stream().filter(m->m.kind()==Kind.RANGER&&CaveSafety.active(s,m)
            &&s.tick()-priorityApproaches.getOrDefault(m.index(),s.tick())<ROUTE_STALL_TICKS
            &&shot!=null&&PureCombatPolicy.targetPriority(s,m,prayer)>PureCombatPolicy.targetPriority(s,shot,prayer))
            .min(Comparator.comparingInt(m->m.distance(s.player()))).orElse(null);
        if(ranger!=null) {
            Plan route=nearbyRangerShot(s,ranger);
            if(route!=null&&!failed.contains(route.destination())){priorityApproaches.putIfAbsent(ranger.index(),s.tick());
                pursuitGoal=false;
                goal=route.destination();target=ranger.index();lastPlayer=s.player();routeAt=s.tick();return route;}
        }
        if(shot!=null) {
            goal=s.player();target=shot.index();pursuitGoal=false;
            int risk=PureSafety.exposure(s,s.mobs(),s.player(),prayer);
            return new Plan(goal,goal,prayer,target,risk==0,0,0,risk,"Pure recovery: attack reachable threat");
        }
        if(goal!=null) {
            Mob mob=find(s,target);
            if(!s.player().equals(lastPlayer)){lastPlayer=s.player();routeAt=s.tick();}
            if(mob!=null&&s.tick()-routeAt<ROUTE_STALL_TICKS&&!failed.contains(goal)
                &&(pursuitGoal?firingAfterRoute(s,MinimapMovement.path(s,goal),target)!=null:
                    CombatPlanner.playerCanAttack(s,goal,mob))) {
                Plan route=command(s,goal);
                if(CombatPlanner.actionable(route,false)&&(!pursuitGoal||route.nextStep().equals(goal)))return route;
            }
            failed.add(goal);goal=null;target=-1;pursuitGoal=false;
        }
        if(searchTick==s.tick())return null;
        searchTick=s.tick();
        Plan best=null;Mob selected=null;long score=Long.MAX_VALUE;
        // First preserve cover, then consider mixed projectile exposure. If both
        // fail, acquiring the sole shooter can release a melee trap: killing it
        // leaves one protection style. Permanent refusal would leave its attacks
        // unanswered. Never open mage contact or substitute an adjacent retry.
        for(int pass=0;pass<3&&best==null;pass++) {
            for(Mob mob:s.mobs()) {
                if(pass==2&&!soleShooter(s,mob))continue;
                int range=s.weaponRange();
                for(int x=mob.tile().x()-range;x<mob.tile().x()+mob.size()+range;x++)
                    for(int y=mob.tile().y()-range;y<mob.tile().y()+mob.size()+range;y++) {
                        Tile tile=new Tile(x,y);
                        if(tile.equals(s.player())||failed.contains(tile)||!s.grid().open(tile)
                            ||!CombatPlanner.playerCanAttack(s,tile,mob)||!CaveSafety.clearOfMagers(s,s.mobs(),tile)
                            ||s.mobs().stream().anyMatch(m->m.occupies(tile)||m.kind()==Kind.RANGER&&m.distance(tile)<2))continue;
                        List<Tile> path=MinimapMovement.path(s,tile);
                        if(path.isEmpty()||path.size()>MAX_ROUTE_TILES)continue;
                        if(!(pass==0?PureSafety.routeAllowed(s,tile):PureSafety.acquisitionRouteAllowed(s,tile)))continue;
                        Protection guard=MinimapMovement.routeProtection(s,tile,prayer);
                        Plan route=command(s,tile);
                        if(!CombatPlanner.actionable(route,false))continue;
                        Snapshot end=new Snapshot(s.tick(),tile,s.grid(),s.mobs(),s.runEnergy(),s.running(),s.weaponRange(),s.jadStyle()).atWave(s.wave());
                        if(!PureSafety.releaseSafe(end,mob,guard)||(pass<2&&!preservesTrappedMelee(s,tile)))continue;
                        long cost=route.risk()*1000L+path.size()*20L-PureCombatPolicy.targetPriority(s,mob,guard)*5L;
                        if(cost<score){score=cost;best=route;selected=mob;}
                    }
            }
        }
        if(best==null) {
            for(Mob mob:s.mobs())if(soleShooter(s,mob)) {
                best=pursuitShot(s,mob);
                if(best!=null){selected=mob;pursuitGoal=true;break;}
            }
        }
        if(best==null)return null;
        goal=best.destination();target=selected.index();lastPlayer=s.player();routeAt=s.tick();return best;
    }
    /** A covered shooter can follow the approach before a shot opens. Search a
     * complete minimap destination against that future position, never a sequence
     * of one-tile retries or an unchecked rush toward its current footprint. */
    private Plan pursuitShot(Snapshot s,Mob shooter) {
        MinimapMovement.TerrainPaths paths=MinimapMovement.terrainPaths(s);
        Plan best=null;long score=Long.MAX_VALUE;
        for(int dx=-15;dx<=15;dx++)for(int dy=-15;dy<=15;dy++) {
            if(dx*dx+dy*dy>225||Math.max(Math.abs(dx),Math.abs(dy))<4)continue;
            Tile tile=s.player().add(dx,dy);
            if(failed.contains(tile)||!s.grid().open(tile))continue;
            List<Tile> path=paths.to(tile);
            if(path.isEmpty()||path.size()>MAX_ROUTE_TILES)continue;
            Snapshot end=firingAfterRoute(s,path,shooter.index());
            if(end==null||!preservesTrappedMelee(s,tile)||PureSafety.stationaryCost(end)>0)continue;
            Protection guard=MinimapMovement.routeProtection(s,tile,PureSafety.guard(s,s.mobs(),s.player()));
            if(!PureSafety.acquisitionRouteAllowed(s,tile)||!PureSafety.releaseSafe(end,find(end,shooter.index()),guard))continue;
            Plan route=MinimapMovement.checked(s,tile,tile,guard,"Pure recovery: acquire shooter after checked pursuit");
            if(!CombatPlanner.actionable(route,false))continue;
            long cost=route.risk()*1000L+path.size()*20L;
            if(cost<score){best=route;score=cost;}
        }
        return best;
    }
    private static Snapshot firingAfterRoute(Snapshot s,List<Tile> path,int target) {
        if(path.isEmpty())return null;
        List<Mob> future=s.mobs();int stride=s.running()&&s.runEnergy()>0?2:1;
        for(int i=1;i<path.size();i++)if(i%stride==0||i==path.size()-1)
            future=CombatPlanner.advance(s.grid(),future,path.get(i),s.jadStyle());
        Tile tile=path.get(path.size()-1);
        for(Mob mob:future)if(mob.occupies(tile)
            ||(mob.kind()==Kind.RANGER||mob.kind()==Kind.MAGER)&&mob.distance(tile)<2)return null;
        Snapshot end=new Snapshot(s.tick()+(path.size()-1+stride-1)/stride,tile,s.grid(),future,
            s.runEnergy(),s.running(),s.weaponRange(),s.jadStyle()).atWave(s.wave());
        return CombatPlanner.playerCanAttack(end,tile,find(end,target))?end:null;
    }
    private static Plan nearbyRangerShot(Snapshot s,Mob ranger) {
        Plan best=null;long score=Long.MAX_VALUE;
        // This is one move into weapon range, not a new cross-cave lure. Once
        // that shot opens, preferredShot selects the ranger on the next frame.
        for(int dx=-6;dx<=6;dx++)for(int dy=-6;dy<=6;dy++) {
            Tile tile=s.player().add(dx,dy);
            if(tile.equals(s.player())||!s.grid().open(tile)||ranger.distance(tile)<2
                ||!CombatPlanner.playerCanAttack(s,tile,ranger)||!CaveSafety.clearOfMagers(s,s.mobs(),tile))continue;
            List<Tile> path=MinimapMovement.path(s,tile);
            if(path.isEmpty()||path.size()>7||!preservesTrappedMelee(s,tile))continue;
            Plan route=command(s,tile,"Pure recovery: acquire attacking ranger first");
            if(!CombatPlanner.actionable(route,false)||!route.nextStep().equals(tile))continue;
            Snapshot end=new Snapshot(s.tick(),tile,s.grid(),s.mobs(),s.runEnergy(),s.running(),s.weaponRange(),s.jadStyle()).atWave(s.wave());
            if(!PureSafety.releaseSafe(end,ranger,route.protection()))continue;
            long cost=route.risk()*1000L+path.size();
            if(cost<score){best=route;score=cost;}
        }
        return best;
    }
    private static boolean soleShooter(Snapshot s,Mob target) {
        return (target.kind()==Kind.RANGER||target.kind()==Kind.MAGER)
            &&s.mobs().stream().allMatch(m->m.index()==target.index()||CaveSafety.meleeFollower(m));
    }
    private static boolean preservesTrappedMelee(Snapshot s,Tile tile) {
        for(Mob m:s.mobs())if(m.kind()==Kind.MELEER&&CaveSafety.trapped(s,m)) {
            List<Mob> future=List.of(m);
            for(int i=0;i<24;i++)future=CombatPlanner.advance(s.grid(),future,tile,s.jadStyle());
            if(s.grid().melee(future.get(0),tile))return false;
        }
        return true;
    }
    private static Mob find(Snapshot s,int index){return s.mobs().stream().filter(m->m.index()==index).findFirst().orElse(null);}
    private static Plan command(Snapshot s,Tile destination) {
        return command(s,destination,"Pure recovery: commit to firing range");
    }
    private static Plan command(Snapshot s,Tile destination,String reason) {
        List<Tile> path=MinimapMovement.path(s,destination);
        for(int i=Math.min(path.size()-1,MAX_ROUTE_TILES);i>0;i--) {
            Tile step=path.get(i);int dx=step.x()-s.player().x(),dy=step.y()-s.player().y();
            if(dx*dx+dy*dy>225)continue;
            Protection guard=MinimapMovement.routeProtection(s,step,PureSafety.guard(s,s.mobs(),s.player()));
            Plan plan=MinimapMovement.checked(s,destination,step,guard,reason);
            if(CombatPlanner.actionable(plan,false)&&PureSafety.acquisitionRouteAllowed(s,step))return plan;
        }
        return TacticalMovement.invalid(s,destination,"Pure recovery: no checked firing route");
    }
}
