package net.runelite.client.plugins.microbot.drocolosseumprayers.local;

import java.util.*;
import java.util.function.Predicate;
import static net.runelite.client.plugins.microbot.drocolosseumprayers.local.ColosseumSnapshot.*;
import net.runelite.client.plugins.microbot.drocolosseumprayers.local.ColosseumCollision.Tile;

/**
 * 1.0.99, the stack move. The user, 2026-11-10, after the 09:12 run of 1.0.98 (waves 1-4 for 80 damage, then 120 in
 * wave 5 against a Manticore and a Javelin standing in a row east of the north-west pillar): "I'd change nothing other
 * than when a manticore and javelin are stacked on top of each other like that, after clearing the rest out then the move
 * takes place. South stack = move east, east stack = move south." "It posts up south of the south pillar when monsters
 * are stacked east and east of the east pillar when monsters are stacked south." "It should pause and wait for monsters
 * to settle after a run."
 * <p>
 * The scene. The Manticore stands against one face of the player's pillar and the Javelin right behind it, level with
 * the pillar. Every tile of this pillar shows both or neither, so stepping out means a volley and a throw on one tick
 * (09:12 t1796: 42). A run to the next pillar across the stack turns the row into two monsters side by side, and that
 * pillar shows them one at a time.
 * <p>
 * The user's own run, 2026-11-09 22:36 (stack south of the north-west pillar): t108-115 east along the north side,
 * t116 onto (1834,3114), the middle of the north-east pillar's east face; stood there eleven ticks while both monsters
 * walked up and stopped west of that pillar, out of sight; t127-128 round the corner to its north face (1833,3116),
 * where the Manticore alone had a ray, and fought it there. The servers' own pillar changes start in wave 5 and are
 * almost all north-west to south-west.
 * <p>
 * The rule is this narrow on purpose (user: "the changing sides code is really for very specific situations you cannot
 * solve otherwise"; 1.0.97's general pillar run fired 73 times in waves 2-4 over the recorded states and was withdrawn):
 * <ol>
 * <li><b>When.</b> The only enemies left are one Manticore and one Javelin and the reinforcements are in; both have
 * stood still for {@link #STILL} ticks, stacked on one side of the player's pillar ({@link #side}); the player's tile
 * is not one that exactly one of them can fire at (that is the solve already).</li>
 * <li><b>Where.</b> The pillar across the stack ({@link #destination}), and on it the middle of the face that looks
 * away from the pillar left ({@link #post}): south of the south pillar, east of the east pillar.</li>
 * <li><b>Pause.</b> That tile is held, nothing attacked, until both monsters have stopped walking.</li>
 * <li><b>Then.</b> One of them with a ray at the tile is fought from it by the engine as usual. With neither in sight,
 * one walk along that pillar to the tile {@link ColosseumMarch} says shows exactly one is taken (the user's step to
 * the north face), and that tile is held the same way.</li>
 * </ol>
 * -Dcolo.stackRun=false turns the move off for simulator comparisons.
 */
final class ColosseumStackRun
{
    static final boolean ON=!"false".equals(System.getProperty("colo.stackRun"));
    /** Steps of the longest run considered and ticks of march played. */
    static final int REACH=40,LIMIT=70;
    /** Ticks a run may take, and runs one wave may make (a bound, so a misread scene cannot ping-pong). */
    static final int RUN_TICKS=30,RUNS=3;
    /** Run energy (hundredths of a percent) the run needs. */
    static final int ENERGY=2000;
    /** Ticks without a step after which a monster has stopped. */
    static final int STILL=3;
    /** Tiles between the two footprints along the stack, and the offset across it, that still make a stack. */
    static final int GAP=2,OFFSET=2;
    /** Showing the Javelin costs this many steps more than showing the Manticore (220 hitpoints: the user leaves it for last). */
    static final int JAVELIN_LAST=6;
    /** Steps of the walk along the far pillar. */
    static final int LURE_REACH=14;
    /** Ticks a tile is held at most, and ticks after reaching it in which the monsters are given time to start walking. */
    static final int HOLD_TICKS=45,REACT=3;
    /** A monster the march has walking that has not moved for this long is not on its way. */
    static final int STUCK=6;

    private int wave=-1,runs;
    private Tile goal;
    private long started=-1;
    private String status="";
    /** After the run: the tile held, its pillar, the tick the hold began and whether the walk along the pillar is spent. */
    private Tile post,postPillar;
    private long arrived=-1;
    private boolean stepped;
    private String where="";
    /** Per monster: tile and the tick it was last seen to move. */
    private final Map<Integer,long[]> seen=new HashMap<>();

    void reset(){wave=-1;runs=0;clear();status="";seen.clear();}
    private void clear(){goal=post=postPillar=null;started=arrived=-1;stepped=false;}
    /** Once per snapshot, before any other call. */
    void observe(ColosseumSnapshot f,int wave)
    {
        if(wave!=this.wave){this.wave=wave;runs=0;clear();seen.clear();}
        for(Npc n:f.npcs)
        {
            if(!n.alive()||!n.enemy())continue;
            long[] s=seen.get(n.index);
            if(s==null)seen.put(n.index,new long[]{n.x,n.y,f.tick});
            else if(s[0]!=n.x||s[1]!=n.y){s[0]=n.x;s[1]=n.y;s[2]=f.tick;}
        }
    }
    private long sinceStep(ColosseumSnapshot f,Npc n){long[] s=seen.get(n.index);return s==null?0:f.tick-s[2];}
    String status(){return status;}

    /** The Manticore and the Javelin when they are all that is left to fight, else null. */
    static Npc[] pair(List<Npc> enemies)
    {
        if(enemies.size()!=2)return null;
        Npc a=enemies.get(0),b=enemies.get(1);
        if(a.role==Role.MANTICORE&&b.role==Role.JAVELIN)return new Npc[]{a,b};
        if(a.role==Role.JAVELIN&&b.role==Role.MANTICORE)return new Npc[]{b,a};
        return null;
    }
    /**
     * The side of {@code pillar} the two stand stacked on, "east", "west", "north" or "south": both level with the
     * pillar, one behind the other with at most {@link #GAP} tiles between them and at most {@link #OFFSET} out of line.
     * Null when they are not stacked.
     */
    static String side(Tile pillar,Npc a,Npc b)
    {
        int ax=a.x+a.size-1,ay=a.y+a.size-1,bx=b.x+b.size-1,by=b.y+b.size-1,px=pillar.x+2,py=pillar.y+2;
        boolean row=a.y<=py&&ay>=pillar.y&&b.y<=py&&by>=pillar.y&&Math.abs(a.y-b.y)<=OFFSET;
        if(row)
        {
            int gap=Math.max(a.x,b.x)-Math.min(ax,bx)-1;
            if(gap>=0&&gap<=GAP)
            {
                if(Math.min(a.x,b.x)>px)return "east";
                if(Math.max(ax,bx)<pillar.x)return "west";
            }
        }
        boolean column=a.x<=px&&ax>=pillar.x&&b.x<=px&&bx>=pillar.x&&Math.abs(a.x-b.x)<=OFFSET;
        if(column)
        {
            int gap=Math.max(a.y,b.y)-Math.min(ay,by)-1;
            if(gap>=0&&gap<=GAP)
            {
                if(Math.max(ay,by)<pillar.y)return "south";
                if(Math.min(a.y,b.y)>py)return "north";
            }
        }
        return null;
    }
    /** The pillar the user's rule sends the player to: across the stack, not along it. */
    static Tile destination(Tile pillar,String side)
    {
        boolean row="east".equals(side)||"west".equals(side);
        for(Tile q:ColosseumDecisionEngine.PILLARS)
        {
            if(q.equals(pillar))continue;
            if(row?q.x==pillar.x:q.y==pillar.y)return q;
        }
        return null;
    }
    private static String name(Tile from,Tile to){return to.y<from.y?"south":to.y>from.y?"north":to.x>from.x?"east":"west";}
    /**
     * Where the player posts up at {@code pillar}, coming from {@code home}: the face that looks away from {@code home}
     * (south of the south pillar, east of the east pillar), its middle tile first, as the user stood on (1834,3114).
     */
    static List<Tile> post(Tile home,Tile pillar)
    {
        List<Tile> face=new ArrayList<>();
        for(int i:new int[]{1,0,2})
            face.add(pillar.y<home.y?new Tile(pillar.x+i,pillar.y-1):pillar.y>home.y?new Tile(pillar.x+i,pillar.y+3)
                :pillar.x>home.x?new Tile(pillar.x+3,pillar.y+i):new Tile(pillar.x-1,pillar.y+i));
        return face;
    }

    /**
     * The goal of a run under way, from the tick after it was chosen until the player stands on it; from then on
     * {@link #holding} speaks. Dropped when it has taken longer than {@link #RUN_TICKS} or {@code safe} no longer
     * accepts the goal.
     */
    Tile running(ColosseumSnapshot f,Tile current,Predicate<Tile> safe)
    {
        if(goal==null)return null;
        long age=f.tick-started;
        if(current.equals(goal))
        {
            post=goal;postPillar=pillarOf(goal);arrived=f.tick;stepped=false;goal=null;
            return null;
        }
        if(age>RUN_TICKS||age<0||!safe.test(goal)){goal=null;return null;}
        return goal;
    }
    /**
     * After the run: the tile to stand on while both monsters are still walking, then the walk along the pillar when
     * they have stopped with neither in sight. Null once the engine is to act again: they have stopped and one (or both)
     * has a ray at the tile, something else is alive, or {@link #HOLD_TICKS} have passed.
     */
    Tile holding(ColosseumSnapshot f,ColosseumCollision collision,Tile current,List<Npc> enemies,Predicate<Tile> safe)
    {
        if(post==null)return null;
        Npc[] pair=pair(enemies);
        if(pair==null||postPillar==null||f.tick-arrived>HOLD_TICKS||!collision.available()||!safe.test(post)){post=null;return null;}
        if(!current.equals(post)){status="At the "+where+" pillar: stepping to the face one of them can fire at";return post;}
        // Still walking: one of them stepped within the last ticks, or the march has one still to step and it is being
        // acted out (just arrived, or that monster moved a moment ago).
        ColosseumMarch.Result stand=ColosseumMarch.run(f,collision,null,current,List.of(),LIMIT);
        boolean walking=false,due=f.tick-arrived<=REACT&&!stand.stepped.isEmpty();
        for(Npc n:pair)
        {
            if(sinceStep(f,n)<STILL)walking=true;
            if(stand.stepped.containsKey(n.index)&&sinceStep(f,n)<STUCK)due=true;
        }
        if(walking||due)
        {status="Posted "+where+" of the "+where+" pillar: waiting for the Manticore and Javelin to settle";return post;}
        // Both have stopped. One in sight is the fight; both in sight is the engine's to answer.
        if(ColosseumSolo.styles(f,collision,current)>=1){post=null;return null;}
        if(!stepped)
        {
            stepped=true;
            Tile next=show(f,collision,current,postPillar,safe);
            if(next!=null){post=next;arrived=f.tick;status="At the "+where+" pillar: stepping to the face one of them can fire at";return post;}
        }
        post=null;return null;
    }
    /**
     * Starts the move when the scene is the one described on the class: returns the tile to post up on and remembers
     * it, or null. {@code reinforcementsPending} and {@code hurt} (below the eat threshold: the run shows the player to
     * both once, so it starts healed) hold it back.
     */
    Tile start(ColosseumSnapshot f,ColosseumCollision collision,Tile current,List<Npc> enemies,boolean reinforcementsPending,boolean hurt,Predicate<Tile> safe)
    {
        if(!ON||goal!=null||post!=null||runs>=RUNS||reinforcementsPending||hurt||!collision.available())return null;
        if(!f.runOn||f.runEnergy<ENERGY)return null;
        Npc[] pair=pair(enemies);
        if(pair==null||sinceStep(f,pair[0])<STILL||sinceStep(f,pair[1])<STILL)return null;
        Tile home=ColosseumDecisionEngine.nearPillar(current);
        if(home==null)return null;
        String side=side(home,pair[0],pair[1]);
        if(side==null)return null;
        // Exactly one of them with a ray at the player's tile is the solve already.
        if(ColosseumSolo.styles(f,collision,current)==1)return null;
        Tile to=destination(home,side);
        if(to==null)return null;
        ColosseumCollision.Paths paths=collision.movementPaths(current,REACH);
        Tile chosen=null;
        for(Tile t:post(home,to))
            if(collision.free(t.x,t.y)&&safe.test(t)&&paths.distance(t)>0){chosen=t;break;}
        if(chosen==null)return null;
        goal=chosen;started=f.tick;runs++;post=null;where=name(home,to);
        status="Running to the "+where+" pillar: Manticore and Javelin stacked "+side;
        return goal;
    }

    /** The pillar {@code t} is a face tile of, or null. */
    static Tile pillarOf(Tile t)
    {
        for(Tile q:ColosseumDecisionEngine.PILLARS)
        {
            int dx=t.x<q.x?q.x-t.x:t.x>q.x+2?t.x-q.x-2:0,dy=t.y<q.y?q.y-t.y:t.y>q.y+2?t.y-q.y-2:0;
            if(dx+dy==1)return q;
        }
        return null;
    }
    private static boolean under(ColosseumSnapshot f,ColosseumMarch.Result r,Tile tile)
    {
        for(Npc n:f.npcs)
        {
            Tile at=r.rest.get(n.index);
            if(at!=null&&tile.x>=at.x&&tile.x<at.x+n.size&&tile.y>=at.y&&tile.y<at.y+n.size)return true;
        }
        return false;
    }
    /**
     * The walk along {@code pillar} from {@code from}, the monsters where they stand: the nearest face tile where, once
     * they have stopped again, exactly one has a ray and the walk never showed the player to both. Null when there is none.
     */
    static Tile show(ColosseumSnapshot f,ColosseumCollision collision,Tile from,Tile pillar,Predicate<Tile> safe)
    {
        ColosseumCollision.Paths paths=collision.movementPaths(from,LURE_REACH);Tile best=null;int bestCost=Integer.MAX_VALUE;
        for(Tile t:paths.reachable)
        {
            int d=paths.distance(t);
            if(d<=0||!collision.free(t.x,t.y)||!pillar.equals(pillarOf(t))||!safe.test(t))continue;
            ColosseumMarch.Result r=ColosseumMarch.run(f,collision,ColosseumMarch.running(paths.to(t)),LIMIT);
            if(!r.settled||under(f,r,t)||r.atRest.size()!=1||r.doubles>0)continue;
            int cost=d+(r.atRest.contains("ranged")?JAVELIN_LAST:0);
            if(cost<bestCost){best=t;bestCost=cost;}
        }
        return best;
    }
}
