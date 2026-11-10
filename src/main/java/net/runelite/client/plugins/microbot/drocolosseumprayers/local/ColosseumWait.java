package net.runelite.client.plugins.microbot.drocolosseumprayers.local;

import java.util.*;
import java.util.function.Predicate;
import static net.runelite.client.plugins.microbot.drocolosseumprayers.local.ColosseumSnapshot.*;
import net.runelite.client.plugins.microbot.drocolosseumprayers.local.ColosseumCollision.Tile;

/**
 * 1.0.96, the safe spot (user, live 2026-11-09 20:22 wave 2).
 * <p>
 * With the warband dead the player stood on (1816,3116), north of the north-west pillar. The Javelin was diagonal to it
 * round the pillar's corner and the Shaman straight south of the pillar: neither could see the tile or walk to it, and
 * nothing hit the player for 28 ticks. The engine then clicked the Javelin, the game walked the player out to the
 * corner where both threw on the same tick, the engine walked back and clicked again (41 and 30 damage). The user:
 * "it should have waited there for the jaguar while nothing was hitting it, killed the jaguar, run around the north side
 * to the east, killed the mage on that side, leaving the javelin alone."
 * <p>
 * Three steps, each one prayer style at a time:
 * <ol>
 * <li><b>Hold.</b> On a pillar tile no shooter can fire at now or after its step, with two or more shooter styles alive
 * and the warband dead, the player stays while the reinforcements are still to come (they arrive 65-66 ticks after the
 * wave's first enemies in every wave of the 38 server runs) or a Jaguar or Minotaur is on its way.</li>
 * <li><b>Fight the comer there.</b> It is attacked from the tile as soon as the game fires at it in place; only its
 * melee can land.</li>
 * <li><b>Lure.</b> With nothing left to wait for and no shot from the tile, the player walks to a pillar face that is
 * just as quiet and that exactly one style can walk into sight of, and waits there for that one.</li>
 * </ol>
 * A shooter beside the tile without sight of it does not step (server runs: a Shaman diagonal to a still player, 561
 * ticks, never stepped), so it is not counted as coming. -Dcolo.wait=false turns the rule off for simulator comparisons.
 */
final class ColosseumWait
{
    static final boolean ON=!"false".equals(System.getProperty("colo.wait"));
    /** Ticks from a wave's first enemies to its reinforcements (server runs: 65-66 in every wave). */
    static final int REINFORCEMENTS=66;
    /** An enemy that has not moved for this long is stuck, not on its way. */
    static final int STUCK=6;
    /** Approach steps looked ahead for a shooter walking into sight of a tile. */
    static final int LURE_STEPS=4;
    /** All the waiting one wave may hold: a bound, so no misjudged tile can stall a wave. */
    static final int WAVE_CAP=120;
    private int wave=-1,held;
    private long waveStart=-1,lastHeld=-1;
    private final Map<Integer,long[]> seen=new HashMap<>();

    void reset(){wave=-1;held=0;waveStart=lastHeld=-1;seen.clear();}

    /** Once per snapshot, before any other call. */
    void observe(ColosseumSnapshot f,int wave)
    {
        if(wave!=this.wave){this.wave=wave;waveStart=-1;held=0;lastHeld=-1;seen.clear();}
        boolean any=false;
        for(Npc n:f.npcs)
        {
            if(!n.alive()||!n.enemy()||n.role==Role.FLARE)continue;
            any=true;
            long[] s=seen.get(n.index);
            if(s==null||s[0]!=n.x||s[1]!=n.y)seen.put(n.index,new long[]{n.x,n.y,f.tick});
        }
        if(!any){waveStart=-1;seen.clear();}
        else if(waveStart<0)waveStart=f.tick;
    }
    /** The wave's reinforcements have not arrived yet. */
    boolean pending(ColosseumSnapshot f){return waveStart>=0&&f.tick-waveStart<REINFORCEMENTS;}
    private boolean moving(ColosseumSnapshot f,Npc n){long[] s=seen.get(n.index);return s==null||f.tick-s[2]<STUCK;}

    /** A Jaguar or Minotaur that walls do not keep off {@code tile} and that is still walking, or already in reach. */
    Npc comer(ColosseumSnapshot f,ColosseumCollision collision,Tile tile)
    {
        Npc best=null;int bestGap=Integer.MAX_VALUE;
        for(Npc n:f.npcs)
        {
            if(!n.alive()||!n.enemy()||n.role!=Role.JAGUAR&&n.role!=Role.MINOTAUR)continue;
            boolean reach=collision.melee(n,tile);
            if(!reach&&(ColosseumTrap.trapped(collision,n,tile)||!moving(f,n)))continue;
            int gap=n.distance(tile.x,tile.y);
            if(gap<bestGap){best=n;bestGap=gap;}
        }
        return best;
    }
    /** No shooter can fire at the pillar tile now or after its step, and no warband is alive. */
    static boolean quiet(ColosseumSnapshot f,ColosseumCollision collision,Tile tile)
    {
        if(!collision.available()||!ColosseumSolo.hugs(tile,null))return false;
        for(Npc n:f.npcs)if(n.alive()&&n.enemy()&&n.warband())return false;
        return ColosseumSolo.styles(f,collision,tile)==0;
    }
    /**
     * There is something to wait for from {@code tile}: leaving a tile two styles fire at, a quiet tile then counts as
     * well as a tile one style can fire at (ColosseumSolo).
     */
    boolean waitable(ColosseumSnapshot f,ColosseumCollision collision,Tile tile)
    {return ON&&held<WAVE_CAP&&ColosseumSolo.aliveStyles(f)>=2&&(pending(f)||comer(f,collision,tile)!=null);}
    /** Step 1: stay on {@code current}. The caller has ruled out hazards, flares and its own special states. */
    boolean hold(ColosseumSnapshot f,ColosseumCollision collision,Tile current)
    {
        if(!ON||held>=WAVE_CAP||ColosseumSolo.aliveStyles(f)<2||!quiet(f,collision,current))return false;
        if(!pending(f)&&comer(f,collision,current)==null)return false;
        count(f);return true;
    }
    /** Step 3, waiting: exactly one style is walking into sight of the quiet tile the player stands on. */
    boolean luring(ColosseumSnapshot f,ColosseumCollision collision,Tile current)
    {
        if(!ON||held>=WAVE_CAP||ColosseumSolo.aliveStyles(f)<2||!quiet(f,collision,current))return false;
        if(arriving(f,collision,current).size()!=1)return false;
        count(f);return true;
    }
    /**
     * Step 3, walking: the nearest face of the player's pillar that is quiet on arrival, that exactly one style can walk
     * into sight of, and that {@code safe} accepts. Null when the player is not on a quiet tile, when a style is already
     * on its way to it, or when no such face exists.
     */
    Tile lure(ColosseumSnapshot f,ColosseumCollision collision,Tile current,Predicate<Tile> safe)
    {
        if(!ON||held>=WAVE_CAP||ColosseumSolo.aliveStyles(f)<2||!quiet(f,collision,current))return null;
        if(!arriving(f,collision,current).isEmpty())return null;
        ColosseumCollision.Paths paths=collision.movementPaths(current,14);
        Tile pillar=ColosseumDecisionEngine.nearPillar(current),best=null;int bestCost=Integer.MAX_VALUE;
        for(Tile t:paths.reachable)
        {
            int d=paths.distance(t);
            if(d<=0||!collision.free(t.x,t.y)||!ColosseumSolo.hugs(t,pillar)||!ColosseumSolo.face(t))continue;
            // The middle of the face: its end tiles lie beside the corners, in sight along the neighbouring faces.
            int cost=d+(pillar!=null&&(t.x==pillar.x+1||t.y==pillar.y+1)?0:2);
            if(cost>=bestCost||ColosseumSolo.styles(f,collision,t)!=0||arriving(f,collision,t).size()!=1||!safe.test(t))continue;
            best=t;bestCost=cost;
        }
        return best;
    }
    /** Styles of the shooters that are still walking and reach firing sight of {@code tile} within {@link #LURE_STEPS} steps. */
    private Set<String> arriving(ColosseumSnapshot f,ColosseumCollision collision,Tile tile)
    {
        Set<String> styles=new HashSet<>();
        for(Npc n:f.npcs)
        {
            if(!n.alive()||!n.enemy()||!ColosseumSolo.SHOOTERS.contains(n.role)||ColosseumSolo.cornered(collision,n,tile))continue;
            if(collision.npcFiringAfterApproach(n,tile,LURE_STEPS))styles.add(ColosseumSolo.style(n));
        }
        return styles;
    }
    private void count(ColosseumSnapshot f){if(lastHeld!=f.tick){lastHeld=f.tick;held++;}}
}
