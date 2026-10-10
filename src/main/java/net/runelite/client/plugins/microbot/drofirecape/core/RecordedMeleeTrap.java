/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape.core;

import java.util.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;

/** The two demonstrated Italy-rock routes. No generated neighbour or kite destinations. */
public final class RecordedMeleeTrap {
    private enum Phase { IDLE, HOME, WEST, RELEASE, EAST, NORTH, NORTH_WAIT, WALL, RETURN }
    private Phase phase=Phase.IDLE;
    private final Set<Integer> attempted=new HashSet<>();
    private Tile home,goal;
    private int target=-1,started=-1,arrived=-1,failures;
    public void reset(){phase=Phase.IDLE;attempted.clear();home=goal=null;target=started=arrived=-1;failures=0;}
    public boolean active(){return phase!=Phase.IDLE;}
    public void failed(){if(active()&&++failures>=2)phase=Phase.IDLE;}
    public void rebase(int dx,int dy){if(home!=null)home=home.add(dx,dy);if(goal!=null)goal=goal.add(dx,dy);}
    public boolean start(Snapshot s,Tile italy,int currentTarget) {return start(s,italy,currentTarget,true);}
    public boolean start(Snapshot s,Tile italy,int currentTarget,boolean allowNorthern) {
        if(active()||s.meleeMode()||italy==null||s.mobs().stream().anyMatch(m->
            m.kind()==Kind.BAT||m.kind()==Kind.RANGER||m.kind()==Kind.JAD||m.kind()==Kind.HEALER))return false;
        Mob big=s.mobs().stream().filter(m->m.kind()==Kind.MELEER&&!attempted.contains(m.index()))
            .min(Comparator.comparingInt(m->m.distance(s.player()))).orElse(null);
        if(big==null)return false;
        // A distant Dragon-rock trap needs a firing approach, not another Italy excursion.
        if(big.tile().x()+big.size()<italy.x()-10&&CaveSafety.trapped(s,big))return false;
        // Preserve a successful stationary fight and an already usable trap.
        boolean shot=CombatPlanner.playerCanAttack(s,s.player(),big);
        if(shot&&(currentTarget==big.index()||CaveSafety.trapped(s,big)))return false;
        if(s.mobs().stream().anyMatch(m->m.kind()==Kind.MAGER)
            &&(CaveSafety.trapped(s,big)||big.distance(s.player())>8))return false;
        boolean south=big.tile().y()+big.size()<=italy.y()-6;
        if(!south&&!allowNorthern)return false;
        Tile furthest=south?italy.add(-4,0):italy.add(8,17);
        if(!CaveSafety.pocketAllowed(s,italy,furthest)||!CaveSafety.preservesMageCover(s,furthest))return false;
        home=italy;target=big.index();attempted.add(target);started=s.tick();arrived=-1;failures=0;
        // South recording: four tiles west, observe release, then the same home.
        // Centre/north recording: east elbow, full north tile, then south wall.
        phase=south?Phase.HOME:Phase.EAST;goal=south?home:home.add(6,0);
        return true;
    }
    public Plan plan(Snapshot s) {
        if(!active())return null;
        Mob big=s.mobs().stream().filter(m->m.index()==target).findFirst().orElse(null);
        if(big==null||s.tick()-started>65||s.mobs().stream().anyMatch(m->m.kind()==Kind.BAT||m.kind()==Kind.RANGER)) {
            phase=Phase.IDLE;return stationary(s,"Recorded trap finished or unavailable; use protected combat");
        }
        if(s.player().equals(goal)) {
            // Reaching home may itself finish the trap. Do not execute the
            // next release leg after the target is already a usable wall shot.
            if(CaveSafety.trapped(s,big)&&CombatPlanner.playerCanAttack(s,s.player(),big)) {
                phase=Phase.IDLE;
                return stationary(s,"Recorded melee already trapped; keep current wall shot");
            }
            if(arrived<0)arrived=s.tick();
            switch(phase) {
                case HOME: return next(s,Phase.WEST,home.add(-4,0),"South melee: run four tiles west to release Italy edge");
                case WEST: phase=Phase.RELEASE;break;
                case EAST: return next(s,Phase.NORTH,home.add(8,17),"Centre/north melee: run to recorded northern rock tile");
                case NORTH: phase=Phase.NORTH_WAIT;break;
                case WALL: case RETURN:
                    // Observe the final position before yielding to shooting.
                    if(s.tick()>arrived){phase=Phase.IDLE;return stationary(s,"Recorded rock route complete; attack from its wall");}
                    break;
                default: break;
            }
        }
        if(phase==Phase.RELEASE&&(big.tile().y()+big.size()-1>=home.y()-7
            ||big.distance(s.player())<=2||s.tick()-arrived>=12))
            return next(s,Phase.RETURN,home,"South melee released: return directly to Italy safe tile");
        if(phase==Phase.NORTH_WAIT&&s.tick()-arrived>=3)
            return next(s,Phase.WALL,home.add(-1,-6),"Northern melee follows: run back along recorded Italy wall");
        if(s.player().equals(goal))return stationaryHold(s,"Hold recorded rock tile; observe melee release");
        Protection p=CombatPlanner.bestProtection(s.grid(),s.mobs(),s.player(),s.jadStyle());
        Plan route=MinimapMovement.route(s,goal,null,p,false,"Complete recorded melee rock route");
        if(!CombatPlanner.actionable(route,false)) {
            // A blocked route is not permission to invent one-tile escape steps.
            phase=Phase.IDLE;return stationary(s,"Recorded rock route blocked; attack reachable threat");
        }
        return route;
    }
    private Plan next(Snapshot s,Phase next,Tile destination,String reason) {
        phase=next;goal=destination;arrived=-1;
        Plan p=plan(s);
        return p==null?null:new Plan(p.destination(),p.nextStep(),p.protection(),p.targetIndex(),p.safe(),
            p.blockedMobs(),p.exposedStyles(),p.risk(),reason);
    }
    private static Plan stationary(Snapshot s,String reason) {
        Protection p=CombatPlanner.bestProtection(s.grid(),s.mobs(),s.player(),s.jadStyle());
        Mob shot=CaveSafety.preferredShot(s,p);int risk=CombatPlanner.immediateExposure(s,s.player(),p);
        return new Plan(s.player(),s.player(),p,shot==null?-1:shot.index(),risk==0,0,0,risk,reason);
    }
    private static Plan stationaryHold(Snapshot s,String reason) {
        Plan p=stationary(s,reason);return new Plan(p.destination(),p.nextStep(),p.protection(),-1,p.safe(),0,0,p.risk(),reason);
    }
}
