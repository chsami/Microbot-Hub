/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape.core;

import java.util.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;

/** Pure-only ranger contact avoidance with a committed firing destination. */
public final class PureRangerSpacing {
    private Tile goal;
    private int started=-1,retryAfter=-1;
    public void reset(){goal=null;started=retryAfter=-1;}
    public Plan decide(Snapshot s) {
        if(s.meleeMode()||s.mobs().stream().anyMatch(m->m.kind()==Kind.JAD||m.kind()==Kind.HEALER))return null;
        List<Mob> rangers=new ArrayList<>();
        for(Mob m:s.mobs())if(m.kind()==Kind.RANGER)rangers.add(m);
        if(goal!=null) {
            if(rangers.isEmpty()||s.player().equals(goal)){goal=null;return null;}
            if(s.tick()-started>12||!clear(s,rangers,goal)||!CaveSafety.preservesMageCover(s,goal)) {
                goal=null;retryAfter=s.tick()+8;return null;
            }
            Plan route=route(s,goal);
            if(CombatPlanner.actionable(route,false))return route;
            goal=null;retryAfter=s.tick()+8;return null;
        }
        if(s.tick()<retryAfter||rangers.stream().noneMatch(m->m.distance(s.player())<3))return null;
        CollisionGrid.PathTree paths=s.grid().pathsFrom(s.player(),s.mobs(),tile->
            TacticalMovement.clearOfJad(s,s.mobs(),tile)&&CaveSafety.clearOfMagers(s,s.mobs(),tile));
        long bestScore=Long.MAX_VALUE;Tile best=null;
        for(int dx=-6;dx<=6;dx++)for(int dy=-6;dy<=6;dy++) {
            Tile tile=s.player().add(dx,dy);
            if(s.player().distance(tile)<3||!s.grid().open(tile)||!clear(s,rangers,tile)
                ||!CaveSafety.preservesMageCover(s,tile))continue;
            List<Tile> path=paths.to(tile);if(path.isEmpty())continue;
            List<Mob> settled=s.mobs();
            for(int i=0;i<6;i++)settled=CombatPlanner.advance(s.grid(),settled,tile,s.jadStyle());
            Snapshot end=new Snapshot(s.tick(),tile,s.grid(),settled,s.runEnergy(),s.running(),s.weaponRange(),s.jadStyle());
            if(settled.stream().anyMatch(m->m.kind()==Kind.RANGER&&m.distance(tile)<3)
                ||settled.stream().noneMatch(m->m.kind()==Kind.RANGER&&CombatPlanner.playerCanAttack(end,tile,m)))continue;
            Plan route=route(s,tile);if(!CombatPlanner.actionable(route,false))continue;
            Protection guard=CombatPlanner.bestProtection(s.grid(),settled,tile,s.jadStyle());
            long score=PureCombatPolicy.approachPenalty(s,tile,path)
                +CombatPlanner.immediateExposure(end,tile,guard)*1000L+path.size()*10L;
            if(score<bestScore){bestScore=score;best=tile;}
        }
        if(best==null){retryAfter=s.tick()+8;return null;}
        goal=best;started=s.tick();return route(s,goal);
    }
    private static boolean clear(Snapshot s,List<Mob> rangers,Tile tile) {
        return CaveSafety.clearOfMagers(s,s.mobs(),tile)&&rangers.stream().allMatch(m->m.distance(tile)>=3);
    }
    private static Plan route(Snapshot s,Tile destination) {
        Plan plan=MinimapMovement.route(s,destination,null,
            CombatPlanner.protectionForNextTick(s,s.player()),false,"Pure: move to ranger firing distance; prevent punches");
        return CombatPlanner.actionable(plan,false)&&PureSafety.routeAllowed(s,plan.nextStep())?plan:
            TacticalMovement.invalid(s,destination,"Pure: ranger spacing would open another attack lane");
    }
}
