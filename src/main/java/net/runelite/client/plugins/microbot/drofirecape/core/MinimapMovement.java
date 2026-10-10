package net.runelite.client.plugins.microbot.drofirecape.core;

import java.util.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;

/**
 * Converts an existing combat/lure destination into a minimap command. Unlike the
 * tactical solver's first-step preview, a command can span many tiles. The next
 * command is not issued until observed arrival, loss of progress, or changed danger.
 */
public final class MinimapMovement {
    private MinimapMovement() { }
    private static final int MAX_RADIUS_SQUARED=15*15,MAX_PATH_TILES=40;

    private static final int[][] DIRECTIONS={{-1,0},{1,0},{0,-1},{0,1},{-1,-1},{1,-1},{-1,1},{1,1}};

    /**
     * A cardinal-first terrain route. The tactical grid's diagonal-first BFS can
     * return a zigzag even for an unobstructed straight run; that preview must not
     * be treated as the player's exact movement acknowledgement path.
     * This is a local prediction, not a claim that every server breaks ties alike.
     */
    public static List<Tile> path(Snapshot s,Tile destination) {
        return findPath(s,destination,false);
    }
    private static List<Tile> findPath(Snapshot s,Tile destination,boolean avoidMobs) {
        CollisionGrid g=s.grid();Tile start=s.player();
        if(!g.open(start)||!g.open(destination))return List.of();
        int[][] previous=new int[g.width][g.height];
        for(int[] row:previous)Arrays.fill(row,-1);
        int[] queue=new int[g.width*g.height];int head=0,tail=0;
        int origin=start.x()*g.height+start.y();
        queue[tail++]=origin;previous[start.x()][start.y()]=origin;
        while(head<tail) {
            int code=queue[head++];Tile at=new Tile(code/g.height,code%g.height);
            if(at.equals(destination)) {
                List<Tile> result=new ArrayList<>();
                for(int guard=0;guard<=g.width*g.height;guard++) {
                    result.add(at);
                    if(at.equals(start)){Collections.reverse(result);return result;}
                    int parent=previous[at.x()][at.y()];
                    at=new Tile(parent/g.height,parent%g.height);
                }
                return List.of();
            }
            for(int[] direction:DIRECTIONS) {
                Tile next=at.add(direction[0],direction[1]);
                if(!g.inside(next)||previous[next.x()][next.y()]>=0||!g.step(at,next))continue;
                if(avoidMobs&&!clear(s,s.mobs(),at,next))continue;
                previous[next.x()][next.y()]=code;
                queue[tail++]=next.x()*g.height+next.y();
            }
        }
        return List.of();
    }
    public static Plan route(Snapshot s,Tile goal,Tile fallback,Protection protection,boolean strict,String reason) {
        if(goal==null)return TacticalMovement.invalid(s,goal,"No minimap destination");
        List<Tile> safe=findPath(s,goal,true);
        // Try the full destination first; only use a waypoint when the minimap,
        // terrain, or an NPC footprint makes that command unsuitable.
        for(int i=Math.min(safe.size()-1,MAX_PATH_TILES);i>0;i--) {
            Tile candidate=safe.get(i);
            int dx=candidate.x()-s.player().x(),dy=candidate.y()-s.player().y();
            if(dx*dx+dy*dy>MAX_RADIUS_SQUARED)continue;
            if(!candidate.equals(goal)&&candidate.distance(s.player())<4
                &&s.mobs().stream().anyMatch(CaveSafety::meleeFollower))continue;
            Plan checked=checked(s,goal,candidate,protection,reason);
            if(CombatPlanner.actionable(checked,strict))return checked;
        }
        // Existing emergency overlap escapes may need a genuinely short step.
        // It is still sent on the minimap, not as an on-screen walk command.
        return fallback==null||s.mobs().stream().anyMatch(CaveSafety::meleeFollower)?
            TacticalMovement.invalid(s,goal,"No complete melee route; do not substitute tiny steps"):
            checked(s,goal,fallback,protection,reason);
    }
    private static boolean occupied(List<Mob> mobs,Tile tile) {
        for(Mob mob:mobs)if(!CaveSafety.meleeFollower(mob)&&mob.occupies(tile))return true;
        return false;
    }
    private static boolean clear(Snapshot s,List<Mob> mobs,Tile previous,Tile tile) {
        if(occupied(mobs,tile)||!TacticalMovement.clearOfJad(s,mobs,tile)||!CaveSafety.mageStep(s,mobs,previous,tile))return false;
        if(previous.x()!=tile.x()&&previous.y()!=tile.y()) {
            if(occupied(mobs,new Tile(previous.x(),tile.y()))
                ||occupied(mobs,new Tile(tile.x(),previous.y())))return false;
        }
        return true;
    }
    private static int exposure(Snapshot s,List<Mob> mobs,Tile tile,Protection protection) {
        int risk=0;
        for(Mob mob:mobs)if((CombatPlanner.threats(s.grid(),mob,tile,s.jadStyle())
            &~CombatPlanner.bit(protection))!=0)risk+=mob.kind()==Kind.JAD?970:mob.kind().maxHit;
        return risk;
    }
    /** Arm before the click can carry the player into a new ranged attack lane. */
    public static Protection routeProtection(Snapshot s,Tile destination,Protection fallback) {
        if(s.meleeMode()||s.mobs().stream().anyMatch(m->m.kind()==Kind.JAD))return fallback;
        List<Tile> route=path(s,destination);List<Mob> future=s.mobs();int mask=0;boolean bigMelee=false;
        int stride=s.running()&&s.runEnergy()>0?2:1;
        for(int i=0;i<route.size();i++) {
            Tile tile=route.get(i);
            for(Mob m:future){int threats=CombatPlanner.threats(s.grid(),m,tile,s.jadStyle());mask|=threats;
                bigMelee|=m.kind()==Kind.MELEER&&(threats&CombatPlanner.bit(Protection.MELEE))!=0;}
            if(i>0&&(i%stride==0||i==route.size()-1)) {
                future=CombatPlanner.advance(s.grid(),future,tile,s.jadStyle());
                for(Mob m:future){int threats=CombatPlanner.threats(s.grid(),m,tile,s.jadStyle());mask|=threats;
                bigMelee|=m.kind()==Kind.MELEER&&(threats&CombatPlanner.bit(Protection.MELEE))!=0;}
            }
        }
        if((mask&CombatPlanner.bit(Protection.MAGIC))!=0)return Protection.MAGIC;
        if(bigMelee)return Protection.MELEE;
        if((mask&CombatPlanner.bit(Protection.RANGE))!=0)return Protection.RANGE;
        return fallback;
    }
    /** Validate the entire remaining click, not just its endpoint or the first two tiles. */
    public static Plan checked(Snapshot s,Tile goal,Tile destination,Protection protection,String reason) {
        if(destination==null)return TacticalMovement.invalid(s,goal,"Missing minimap tile");
        if(occupied(s.mobs(),s.player())&&s.player().distance(destination)<=2) {
            Plan escape=TacticalMovement.checked(s,goal,destination,reason);
            return CombatPlanner.actionable(escape,false)?TacticalMovement.withProtection(s,escape,protection):escape;
        }
        List<Tile> route=path(s,destination);
        if(route.isEmpty()||route.size()>MAX_PATH_TILES+1)
            return TacticalMovement.invalid(s,goal,"No bounded terrain route to minimap tile");
        int risk=exposure(s,s.mobs(),s.player(),protection);
        List<Mob> future=s.mobs();int stride=s.running()&&s.runEnergy()>0?2:1;
        for(int i=1;i<route.size();i++) {
            Tile previous=route.get(i-1),tile=route.get(i);
            if(!s.grid().step(previous,tile)||!clear(s,s.mobs(),previous,tile)||!clear(s,future,previous,tile))
                return TacticalMovement.invalid(s,goal,"Minimap route crosses terrain/NPC/Jad buffer");
            risk=Math.max(risk,Math.max(exposure(s,s.mobs(),tile,protection),exposure(s,future,tile,protection)));
            if(i%stride==0||i==route.size()-1) {
                future=CombatPlanner.advance(s.grid(),future,tile,s.jadStyle());
                // The player has already crossed this stride's edges. A following
                // NPC may occupy a passed diagonal corner without blocking the
                // completed run. Future endpoints and the next edges remain checked.
                if(!clear(s,future,tile,tile))return TacticalMovement.invalid(s,goal,"NPC can occupy minimap route");
                risk=Math.max(risk,exposure(s,future,tile,protection));
            }
        }
        return new Plan(goal==null?destination:goal,destination,protection,-1,risk==0,0,0,risk,reason);
    }
}
