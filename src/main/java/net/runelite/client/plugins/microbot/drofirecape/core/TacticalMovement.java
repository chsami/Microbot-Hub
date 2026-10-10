/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape.core;

import java.util.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;

/**
 * Local, receding-horizon cave movement. A strategic destination is never a click.
 * Every command is one tile, or two collinear tiles while running, and is checked
 * again against the newest collision map and full NPC footprints before dispatch.
 * No world walker, teleport, camera operation, or client dependency lives here.
 */
public final class TacticalMovement {
    private TacticalMovement() { }
    private static final int[][] DIRECTIONS = {
        {0,1},{1,0},{0,-1},{-1,0},{1,1},{1,-1},{-1,1},{-1,-1}
    };

    /** Includes the diagonal buffer: do not acquire Jad's melee range on a path. */
    public static boolean clearOfJad(List<Mob> mobs, Tile tile) {
        for (Mob mob : mobs) if (mob.kind()==Kind.JAD && mob.distance(tile)<2) return false;
        return true;
    }
    public static boolean clearOfJad(Snapshot s,List<Mob> mobs,Tile tile) {
        if(!s.meleeMode())return clearOfJad(mobs,tile);
        for(Mob mob:mobs)if(mob.kind()==Kind.JAD&&mob.occupies(tile))return false;
        return true;
    }
    private static boolean occupied(List<Mob> mobs, Tile tile) {
        for (Mob mob : mobs) if (mob.occupies(tile)) return true;
        return false;
    }
    private static boolean walkable(Snapshot s, Tile tile) {
        return s.grid().open(tile) && !occupied(s.mobs(),tile) && clearOfJad(s,s.mobs(),tile);
    }
    private static boolean edge(Snapshot s, Tile from, Tile to) {
        if (!s.grid().step(from,to) || !walkable(s,to) || !CaveSafety.mageStep(s,s.mobs(),from,to)) return false;
        // Do not squeeze a diagonal click through two adjacent NPC footprints.
        if (from.x()!=to.x() && from.y()!=to.y()) {
            if (occupied(s.mobs(),new Tile(from.x(),to.y()))
                || occupied(s.mobs(),new Tile(to.x(),from.y()))) return false;
        }
        return true;
    }

    private static int overlapDepth(Mob mob,Tile tile) {
        if(!mob.occupies(tile))return 0;
        return Math.min(Math.min(tile.x()-mob.tile().x()+1,mob.tile().x()+mob.size()-tile.x()),
            Math.min(tile.y()-mob.tile().y()+1,mob.tile().y()+mob.size()-tile.y()));
    }
    private static int jadDanger(Mob mob,Tile tile) {
        return mob.occupies(tile)?overlapDepth(mob,tile)+1:Math.max(0,2-mob.distance(tile));
    }
    /** Recovery only: leave an existing overlap, never acquire a new one or go deeper. */
    private static boolean escapeEdge(Snapshot s,Tile from,Tile to) {
        if(!s.grid().step(from,to))return false;
        boolean escaping=false;
        for(Mob mob:s.mobs()) {
            if(mob.occupies(s.player()))escaping=true;
            if(mob.occupies(to)&&(!mob.occupies(s.player())||!mob.occupies(from)
                ||overlapDepth(mob,to)>overlapDepth(mob,from)))return false;
            if(!s.meleeMode()&&mob.kind()==Kind.JAD&&jadDanger(mob,to)>0
                &&(jadDanger(mob,s.player())==0||jadDanger(mob,to)>jadDanger(mob,from)))return false;
        }
        return escaping;
    }
    /** Used only if the observed player is already inside a full NPC footprint. */
    public static Plan escapeOverlap(Snapshot s) {
        boolean overlap=false;for(Mob mob:s.mobs())if(mob.occupies(s.player()))overlap=true;
        if(!overlap)return null;
        Plan best=null;long bestScore=Long.MAX_VALUE;
        int stride=s.running()&&s.runEnergy()>0?2:1;
        for(int[] d:DIRECTIONS)for(int length=1;length<=stride;length++) {
            Tile next=s.player().add(d[0]*length,d[1]*length);
            Plan p=checked(s,next,next,"Escape observed NPC overlap; do not route through monsters");
            if(!CombatPlanner.actionable(p,false))continue;
            int depth=0;for(Mob mob:s.mobs())depth+=overlapDepth(mob,next);
            long score=depth*100000L+p.risk()*1000L-length;
            if(score<bestScore){bestScore=score;best=p;}
        }
        return best==null?invalid(s,s.player(),"No legal escape from current NPC overlap"):best;
    }
    public static Plan invalid(Snapshot s, Tile goal, String reason) {
        Protection protection=CombatPlanner.bestProtection(s.grid(),s.mobs(),s.player(),s.jadStyle());
        return new Plan(goal==null?s.player():goal,s.player(),protection,-1,false,0,0,Integer.MAX_VALUE,reason);
    }

    /** Terrain/NPC route to the intent, with a short, protection-aware first command. */
    public static Plan route(Snapshot s, Tile goal, String reason) {
        if (goal==null || !s.grid().open(s.player())) return invalid(s,goal,"Missing local route: "+reason);
        if (goal.equals(s.player())) return checked(s,goal,goal,reason);
        if (!walkable(s,goal)) return invalid(s,goal,"Blocked local destination: "+reason);
        int w=s.grid().width,h=s.grid().height;
        int[][] distance=new int[w][h];
        for (int[] row:distance) Arrays.fill(row,-1);
        ArrayDeque<Tile> queue=new ArrayDeque<>();
        queue.add(goal); distance[goal.x()][goal.y()]=0;
        while (!queue.isEmpty()) {
            Tile at=queue.removeFirst();
            for (int[] d:DIRECTIONS) {
                Tile next=at.add(d[0],d[1]);
                if (!s.grid().open(next) || distance[next.x()][next.y()]>=0) continue;
                // Reverse search. Its origin may already be in danger; allow escape.
                if (!next.equals(s.player()) && !walkable(s,next)) continue;
                if (!edge(s,next,at)) continue;
                distance[next.x()][next.y()]=distance[at.x()][at.y()]+1;
                queue.addLast(next);
            }
        }
        int initial=distance[s.player().x()][s.player().y()];
        if (initial<0) return invalid(s,goal,"No connected local route: "+reason);
        Plan best=null; long bestScore=Long.MAX_VALUE;
        int stride=s.running() && s.runEnergy()>0?2:1;
        for (int[] d:DIRECTIONS) for (int count=1;count<=stride;count++) {
            Tile next=s.player().add(d[0]*count,d[1]*count);
            if (!s.grid().open(next)) continue;
            int remaining=distance[next.x()][next.y()];
            if (remaining<0 || remaining>=initial) continue;
            Plan candidate=checked(s,goal,next,reason);
            if (!CombatPlanner.actionable(candidate,false)) continue;
            long score=candidate.risk()*10000L+remaining*30L-count;
            if (score<bestScore) { best=candidate;bestScore=score; }
        }
        return best==null?invalid(s,goal,"No legal next step: "+reason):best;
    }

    /** Revalidate a previously selected short command; never substitute a long goal. */
    public static Plan checked(Snapshot s, Tile goal, Tile next, String reason) {
        if (next==null) return invalid(s,goal,"No next step: "+reason);
        int dx=next.x()-s.player().x(),dy=next.y()-s.player().y();
        int length=Math.max(Math.abs(dx),Math.abs(dy));
        if (length>2 || length>1 && (!s.running() || s.runEnergy()<=0)
            || dx!=0 && dy!=0 && Math.abs(dx)!=Math.abs(dy))
            return invalid(s,goal,"Not a one-tick straight command: "+reason);
        ArrayList<Tile> tiles=new ArrayList<>(); tiles.add(s.player());
        Tile last=s.player();
        for (int i=1;i<=length;i++) {
            Tile tile=s.player().add(Integer.signum(dx)*i,Integer.signum(dy)*i);
            if (!edge(s,last,tile)&&!escapeEdge(s,last,tile)) return invalid(s,goal,"Blocked local step: "+reason);
            tiles.add(tile);last=tile;
        }
        if (length>0) {
            List<Mob> future=CombatPlanner.advance(s.grid(),s.mobs(),next,s.jadStyle());
            for(Mob mob:future) {
                Mob original=null;for(Mob old:s.mobs())if(old.index()==mob.index()){original=old;break;}
                if(mob.occupies(next)&&(original==null||!original.occupies(s.player())
                    ||overlapDepth(mob,next)>=overlapDepth(original,s.player())))
                    return invalid(s,goal,"NPC can occupy the next step: "+reason);
                if(!s.meleeMode()&&mob.kind()==Kind.JAD&&jadDanger(mob,next)>0&&(original==null
                    ||jadDanger(mob,next)>=jadDanger(original,s.player())))
                    return invalid(s,goal,"Jad buffer does not improve: "+reason);
            }
        }
        Protection protection=protection(s,tiles);
        int risk=risk(s,tiles,protection);
        return new Plan(goal==null?next:goal,next,protection,-1,risk==0,0,0,risk,reason);
    }

    /** Conservative movement protection: do not turn it off between sampled clocks. */
    private static Protection protection(Snapshot s,List<Tile> tiles) {
        if(s.meleeMode())return MeleeProtection.choose(s,tiles.get(tiles.size()-1),Protection.NONE);
        boolean jad=false;
        for (Mob mob:s.mobs()) if (mob.kind()==Kind.JAD) {
            for (Tile tile:tiles) if (CombatPlanner.threats(s.grid(),mob,tile,s.jadStyle())!=0) jad=true;
            if (mob.lastAttackTick()>=0 && s.tick()-mob.lastAttackTick()<=5) jad=true;
        }
        if (jad) return s.jadStyle()==Protection.NONE?Protection.MAGIC:s.jadStyle();
        Protection best=CombatPlanner.protectionForNextTick(s,tiles.get(tiles.size()-1));
        int bestRisk=risk(s,tiles,best);
        for (Protection candidate:Protection.values()) {
            int risk=risk(s,tiles,candidate);
            if (risk<bestRisk) { bestRisk=risk;best=candidate; }
        }
        return best;
    }
    private static int risk(Snapshot s,List<Tile> tiles,Protection protection) {
        int risk=0;
        for (Tile tile:tiles) {
            int geometric=0;
            for (Mob mob:s.mobs()) {
                int mask=CombatPlanner.threats(s.grid(),mob,tile,s.jadStyle());
                if ((mask&~CombatPlanner.bit(protection))!=0)
                    geometric+=mob.kind()==Kind.JAD?970:mob.kind().maxHit;
            }
            risk=Math.max(risk,Math.max(geometric,CombatPlanner.immediateExposure(s,tile,protection)));
        }
        return risk;
    }
    /** Re-score with a live prayer override (e.g. a mage projectile already in flight). */
    public static Plan withProtection(Snapshot s,Plan plan,Protection protection) {
        Tile next=plan.nextStep();
        ArrayList<Tile> tiles=new ArrayList<>(); tiles.add(s.player());
        if (next!=null) {
            int length=s.player().distance(next);
            for (int i=1;i<=length;i++) tiles.add(s.player().add(
                Integer.signum(next.x()-s.player().x())*i,Integer.signum(next.y()-s.player().y())*i));
        }
        int risk=risk(s,tiles,protection);
        return new Plan(plan.destination(),plan.nextStep(),protection,plan.targetIndex(),risk==0,
            plan.blockedMobs(),plan.exposedStyles(),risk,plan.reason());
    }
}
