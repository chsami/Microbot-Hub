package net.runelite.client.plugins.microbot.drocolosseumprayers.local;

import java.util.*;
import java.util.function.Predicate;
import static net.runelite.client.plugins.microbot.drocolosseumprayers.local.ColosseumSnapshot.*;
import net.runelite.client.plugins.microbot.drocolosseumprayers.local.ColosseumCollision.Tile;

/**
 * 1.0.88, the solved tile: the nearest tile that at most one prayer style can fire at.
 * <p>
 * Live 1.0.87, 2026-11-09 12:56 (pure). Wave 4: the bot hid from a Manticore and a Shaman on (1814,3113) for 50 ticks,
 * the failsafe stepped it out to (1815,3112) where both threw on the same tick, it hid again and did the same 60 ticks
 * later; the user walked it to (1817,3116), north of the pillar, where only the Manticore could fire: 0 damage for the
 * rest of the wave. Wave 5: on (1815,3115) with the warband, a Javelin and a Shaman south of the pillar threw together
 * five times in 20 ticks (83 damage, death); every tile on the pillar's north face was out of sight of both.
 * <p>
 * This is the user's side switch: with two styles throwing together at one side of the pillar, walk to another side.
 * The shooter that can follow comes round and is fought alone; the other walks into the pillar and stays out of sight
 * (wave 4: the Manticore came north to (1819,3114), the Shaman went west along the south side to (1819,3113) and stuck).
 * <p>
 * Styles are counted over the shooters outside the warband (the warband follows the player to any tile): a shooter
 * counts when it can fire at the tile now or after its next approach step, and a Manticore's volley is a style of its
 * own. -Dcolo.solo=false turns the rule off for simulator comparisons.
 */
final class ColosseumSolo
{
    static final boolean ON=!"false".equals(System.getProperty("colo.solo"));
    static final Set<Role> SHOOTERS=EnumSet.of(Role.SHAMAN,Role.JAVELIN,Role.MANTICORE,Role.SHOCKWAVE);
    /** Extra steps a corner tile must save to be taken over a face tile. */
    static final int CORNER_COST=3;
    private ColosseumSolo(){}

    /** Prayer styles among the non-warband shooters that can fire at {@code tile} now or after one approach step. */
    static int styles(ColosseumSnapshot f,ColosseumCollision collision,Tile tile)
    {
        Set<String> styles=new HashSet<>();
        for(Npc n:f.npcs)
        {
            if(!n.alive()||!n.enemy()||!SHOOTERS.contains(n.role))continue;
            boolean now=n.distance(tile.x,tile.y)<=(n.role==Role.SHAMAN?10:15)&&collision.firingSight(n,tile);
            if(now||!cornered(collision,n,tile)&&collision.npcFiringAfterApproach(n,tile,1))styles.add(style(n));
        }
        return styles.size();
    }
    static String style(Npc n){return n.role==Role.MANTICORE?"volley":n.role==Role.JAVELIN?"ranged":"magic";}
    /**
     * 1.0.96: a shooter beside the tile (its footprint within one tile, diagonals included) without sight of it stays
     * where it is. Server runs, a still player and a Shaman diagonal to it round a corner: 561 ticks, no step. Live
     * 2026-11-09 20:22 wave 2: the Javelin on (1813,3113) never stepped north while the player stood on (1816,3116).
     */
    static boolean cornered(ColosseumCollision collision,Npc n,Tile tile)
    {return n.distance(tile.x,tile.y)<=1&&!collision.firingSight(n,tile);}
    /** Prayer styles among the shooters alive. */
    static int aliveStyles(ColosseumSnapshot f)
    {
        Set<String> styles=new HashSet<>();
        for(Npc n:f.npcs)if(n.alive()&&n.enemy()&&SHOOTERS.contains(n.role))styles.add(style(n));
        return styles.size();
    }
    /** Beside (or diagonal to) one of the four 3x3 pillars. */
    static boolean hugs(Tile t,Tile only)
    {
        for(Tile q:ColosseumDecisionEngine.PILLARS)
        {
            if(only!=null&&!only.equals(q))continue;
            int dx=t.x<q.x?q.x-t.x:t.x>q.x+2?t.x-q.x-2:0,dy=t.y<q.y?q.y-t.y:t.y>q.y+2?t.y-q.y-2:0;
            if(Math.max(dx,dy)==1)return true;
        }
        return false;
    }
    /** On a pillar's face (straight out from it), not diagonal to one of its corners. */
    static boolean face(Tile t)
    {
        for(Tile q:ColosseumDecisionEngine.PILLARS)
        {
            int dx=t.x<q.x?q.x-t.x:t.x>q.x+2?t.x-q.x-2:0,dy=t.y<q.y?q.y-t.y:t.y>q.y+2?t.y-q.y-2:0;
            if(dx+dy==1)return true;
        }
        return false;
    }
    /**
     * The nearest tile against the player's pillar (the user's rule: hug the pillar, so a hidden shooter walks into it and stays
     * hidden) within 12 steps that {@code safe} accepts and that exactly one style can fire at, or none when
     * {@code coverCounts} (the warband is the fight there). Null when the current tile is already such a tile or none
     * exists.
     */
    static Tile find(ColosseumSnapshot f,ColosseumCollision collision,Tile current,boolean coverCounts,Predicate<Tile> safe)
    {
        if(!ON||!collision.available())return null;
        int here=styles(f,collision,current);
        if(here==1||here==0&&coverCounts)return null;
        // The player walks through NPC footprints (boxed in by the warband on (1815,3115), every route crosses one).
        ColosseumCollision.Paths paths=collision.movementPaths(current,12);
        Tile best=null;int bestCost=Integer.MAX_VALUE;
        // The pillar the player is at; from the open yard, any pillar.
        Tile pillar=ColosseumDecisionEngine.nearPillar(current);
        for(Tile t:paths.reachable)
        {
            int d=paths.distance(t);if(d<=0||!collision.free(t.x,t.y)||!hugs(t,pillar))continue;
            // Switching sides (user, 2026-11-09): a face tile puts the whole pillar between the player and the shooters
            // left behind, which walk into it and stay there; a corner tile is seen along two faces.
            int cost=d+(face(t)?0:CORNER_COST);
            if(cost>=bestCost)continue;
            int s=styles(f,collision,t);
            if(s>1||s==0&&!coverCounts||!safe.test(t))continue;
            best=t;bestCost=cost;
        }
        return best;
    }
}
