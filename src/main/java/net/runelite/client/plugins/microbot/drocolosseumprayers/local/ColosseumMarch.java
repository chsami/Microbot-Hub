package net.runelite.client.plugins.microbot.drocolosseumprayers.local;

import java.util.*;
import static net.runelite.client.plugins.microbot.drocolosseumprayers.local.ColosseumSnapshot.*;
import net.runelite.client.plugins.microbot.drocolosseumprayers.local.ColosseumCollision.Tile;

/**
 * 1.0.97: where the monsters end up if the player stands or walks somewhere.
 * <p>
 * The reinforcement monsters and the shooters outside the warband have no route finding: each tick one that cannot attack
 * steps straight at the player (diagonal, else along x, else along y) and stays put when walls or another monster are in
 * the way. That is what stacks them behind a pillar and what holds a Jaguar behind a Javelin. This plays those steps
 * forward, every monster in index order and each seeing the others where the steps before it left them, with the player
 * on the tile it had before its own move of the tick.
 * <p>
 * Checked against the recordings before anything was built on it (MarchProbe, 14 live recordings of 2026-11-09, 22,059
 * monster-ticks): one tick ahead it has the monster on the recorded tile 99.9% of the time (a step, 1,504 of 1,530; a
 * stay, 20,527 of 20,529), and it reproduced the user's 23-tick run to the north-east pillar tile for tile. What the
 * recordings added to the engine's older one-step model:
 * <ul>
 * <li>a monster steps by its south-west tile, not by the edge of its footprint (steps right: 83% to 94%);</li>
 * <li>a dead monster stays in the way for about three ticks (stays wrongly called steps: 110 to 2);</li>
 * <li>a shooter beside the player, diagonals included, does not step;</li>
 * <li>a shooter's diagonal step needs both of its straight steps clear of other monsters; a Jaguar's does not;</li>
 * <li>the Fremennik warband is in nobody's way (steps right: 1,482 to 1,504; -Dcolo.marchWarband=true has it block).</li>
 * </ul>
 */
final class ColosseumMarch
{
    /** Ticks without a step after which the monsters count as settled. */
    static final int SETTLE=3;
    private ColosseumMarch(){}

    static final class Result
    {
        /** Each monster's south-west tile after the last tick played. */
        final Map<Integer,Tile> rest=new HashMap<>();
        /** Styles ({@link ColosseumSolo#style}) with a firing ray at the player's last tile at the end. */
        final Set<String> atRest=new HashSet<>();
        /** Styles that had a firing ray at the player on some tick before it arrived. */
        final Set<String> onTheWay=new HashSet<>();
        /** Ticks on the way on which two styles had a firing ray together. */
        int doubles;
        /** First tick (1-based) a Jaguar or Minotaur stood in claw reach, or -1. */
        int clawed=-1;
        /** Jaguars and Minotaurs in claw reach of the player's last tile at the end. */
        final Set<Integer> reach=new HashSet<>();
        /** The last tick each monster stepped on (absent: it never did). */
        final Map<Integer,Integer> stepped=new HashMap<>();
        /** No monster stepped over the last {@link #SETTLE} ticks, the player standing on its last tile. */
        boolean settled;
        int ticks;
    }
    /** The monsters this plays: alive, hostile, with a footprint in the arena. */
    static boolean marches(Npc n)
    {
        return n.alive()&&n.enemy()&&(ColosseumSolo.SHOOTERS.contains(n.role)||n.role==Role.JAGUAR||n.role==Role.MINOTAUR);
    }
    static int range(Npc n){return n.role==Role.SHAMAN?10:15;}
    /** A monster that stands in the way without being played forward. The warband finds its own routes and blocks nobody. */
    private static boolean stands(Npc n){return n.alive()&&n.enemy()&&n.role!=Role.FLARE&&n.role!=Role.BEES&&!marches(n)&&(WARBAND_BLOCKS||!n.warband());}
    static final boolean WARBAND_BLOCKS="true".equals(System.getProperty("colo.marchWarband"));
    /** Dead but still on its tile: it is in the way until it despawns, about three ticks later. */
    private static boolean lingers(Npc n){return CORPSES&&n.index>=0&&n.dead&&n.role!=Role.FLARE&&n.role!=Role.PILLAR&&n.role!=Role.MINIMUS&&n.role!=Role.SPECTATOR&&(WARBAND_BLOCKS||!n.warband());}
    static final boolean CORPSES=!"false".equals(System.getProperty("colo.marchCorpses"));
    static final int CORPSE_TICKS=3;

    /**
     * @param perTick the player's tile after each tick's own move; the tile before the first is the snapshot's. After
     *                the list ends the player stands on its last tile.
     * @param limit   ticks played at most
     */
    static Result run(ColosseumSnapshot f,ColosseumCollision collision,List<Tile> perTick,int limit)
    {return run(f,collision,null,new Tile(f.x,f.y),perTick,limit);}
    /**
     * @param from   where the monsters stand at the start (index to south-west tile; a result's {@link Result#rest}), or
     *               null for the snapshot's tiles
     * @param start  the player's tile at the start
     */
    static Result run(ColosseumSnapshot f,ColosseumCollision collision,Map<Integer,Tile> from,Tile start,List<Tile> perTick,int limit)
    {
        Result r=new Result();
        List<Npc> order=new ArrayList<>();Map<Integer,Tile> at=new HashMap<>();List<Integer> corpses=new ArrayList<>();
        for(Npc n:f.npcs)
        {
            if(marches(n)){order.add(n);at.put(n.index,from!=null&&from.containsKey(n.index)?from.get(n.index):new Tile(n.x,n.y));}
            else if(stands(n))at.put(n.index,new Tile(n.x,n.y));
            else if(from==null&&lingers(n)){at.put(n.index,new Tile(n.x,n.y));corpses.add(n.index);}
        }
        order.sort(Comparator.comparingInt(n->n.index));
        Tile player=start;int still=0;
        for(int tick=1;tick<=limit;tick++)
        {
            boolean moved=false,arrived=tick>perTick.size();
            if(tick==CORPSE_TICKS+1)for(int index:corpses)at.remove(index);
            Set<String> sight=new HashSet<>();
            for(Npc n:order)
            {
                Tile here=at.get(n.index);
                if(n.meleeOnly())
                {
                    if(collision.meleeFrom(n,here,player)){if(r.clawed<0)r.clawed=tick;continue;}
                }
                else if(collision.firingFrom(n,here,player,range(n))){sight.add(ColosseumSolo.style(n));continue;}
                Tile next=collision.marchStep(n,here,player,at,range(n));
                if(!next.equals(here)){at.put(n.index,next);moved=true;r.stepped.put(n.index,tick);}
            }
            if(!arrived){r.onTheWay.addAll(sight);if(sight.size()>=2)r.doubles++;}
            if(tick<=perTick.size())player=perTick.get(tick-1);
            r.ticks=tick;
            still=moved||!arrived?0:still+1;
            if(still>=SETTLE){r.settled=true;break;}
        }
        for(Npc n:order)
        {
            Tile here=at.get(n.index);r.rest.put(n.index,here);
            if(n.meleeOnly()){if(collision.meleeFrom(n,here,player))r.reach.add(n.index);}
            else if(collision.firingFrom(n,here,player,range(n)))r.atRest.add(ColosseumSolo.style(n));
        }
        return r;
    }
    /** The player's tile after each tick of a run along {@code route} (two tiles a tick; the route starts on its tile). */
    static List<Tile> running(List<Tile> route)
    {
        List<Tile> perTick=new ArrayList<>();
        for(int i=2;i<route.size();i+=2)perTick.add(route.get(i));
        if(route.size()>1&&(route.size()-1)%2!=0)perTick.add(route.get(route.size()-1));
        return perTick;
    }
}
