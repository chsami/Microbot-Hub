package net.runelite.client.plugins.microbot.drocolosseumprayers.local;

import com.google.gson.*;
import java.util.*;
import static net.runelite.client.plugins.microbot.drocolosseumprayers.local.ColosseumSnapshot.*;
import net.runelite.client.plugins.microbot.drocolosseumprayers.local.ColosseumCollision.Tile;

/** Animation clocks inferred from every completed recording; current sight/footprints remain authoritative. */
final class ColosseumThreats
{
    static final String MAGIC="PROTECT_FROM_MAGIC", RANGE="PROTECT_FROM_MISSILES", MELEE="PROTECT_FROM_MELEE";
    static final Set<String> PROTECTIONS=Set.of(MAGIC,RANGE,MELEE);
    private final Map<Integer,Clock> clocks=new HashMap<>();
    private long playerTick=-1;
    private int playerX,playerY,playerDx,playerDy;
    private ColosseumCollision forecastCollision;
    private Tile playerForecast;
    private static final class Clock
    {
        int id,animation=-1,x,y,dx,dy;String key;long attack=-1000,charge=-1000,spawn=-1000,seen=-1;List<String> queue=new ArrayList<>(),released=new ArrayList<>();
        /** Javelin throws since spawn (-1 unknown); every fifth is the typeless sky-javelin special. */
        int throwCount=-1;
    }
    static final int JAVELIN_SPECIAL=10893;
    static final class Hit
    {
        final String prayer;final int weight,certainty;final Npc npc;
        Hit(String prayer,int weight,Npc npc){this(prayer,weight,npc,2);}
        Hit(String prayer,int weight,Npc npc,int certainty){this.prayer=prayer;this.weight=weight;this.npc=npc;this.certainty=certainty;}
    }
    /**
     * 1.0.97, the user's two prayer corrections after the live run of 2026-11-09 22:20 (both measured on that day's 14
     * recordings with PrayProbe before they went in):
     * <ul>
     * <li><b>Manticore first.</b> "Manticores ought to be prayed against over all monsters except jaguar." Wave 5 t1393
     * and t1528: the Manticore came into sight with the Javelin, the Javelin's throw outweighed the first orb and the orb
     * landed (45 and 31 with Mantimayhem's doubled orbs, and venom). An orb's style now outweighs every other attack due
     * on its tick; only the Jaguar's claw stays above it. By default this holds while a Mantimayhem tier is active (each
     * orb lands twice, up to 62/72, and from tier II venoms): without one an orb is 31/36 against the Javelin's 45, the
     * servers pray the Javelin, and putting the orb first cost 0.2 points of their prayer parity (98.4 to 98.2 over 46
     * runs). -Dcolo.manticoreFirst=always applies it in every wave, =false never.</li>
     * <li><b>A held charge fires the tick it gets a line of fire.</b> Three orbs up and no release since: released on
     * the next tick 100 of 100 times with a firing ray now or after its step ({@link ColosseumMarch}), 0 of 870 without.
     * That release is as certain as a timed attack; before, it ranked below one and lost to it.</li>
     * <li><b>A Jaguar that cannot reach is not prayed against.</b> "Overly protective when a jaguar arrives even when it
     * is blocked." Wave 4 t1051: a Jaguar stepped to within two tiles on the far side of the pillar's corner, melee went
     * up and two Shamans cast into it (39). A Jaguar with its claw ready and out of reach clawed on the next tick 28 of
     * 28 times when one step put it in claw reach and 0 of 2,884 when it did not.</li>
     * </ul>
     * -Dcolo.manticoreFirst=false, -Dcolo.heldCharge=false and -Dcolo.jaguarReach=false restore 1.0.96 one by one. The
     * held charge and the Jaguar's reach each moved the servers' prayer parity up or left it (98.3 to 98.4 together).
     */
    static final String MANTICORE_FIRST=System.getProperty("colo.manticoreFirst","mayhem");
    static final boolean HELD_CHARGE=!"false".equals(System.getProperty("colo.heldCharge")),JAGUAR_REACH=!"false".equals(System.getProperty("colo.jaguarReach"));
    /** Above any sum of the other weights on one tick. */
    static final int MANTICORE_PRIORITY=1000;
    /** Manticore orbs come before every other attack but the Jaguar's claw in this snapshot. */
    static boolean manticoreFirst(ColosseumSnapshot f){return "always".equals(MANTICORE_FIRST)||!"false".equals(MANTICORE_FIRST)&&f.mantimayhem>0;}
    /** The weight a hit carries in the choice of the protection prayer. */
    static int priority(Hit h,boolean manticoreFirst){return manticoreFirst&&h.npc!=null&&h.npc.role==Role.MANTICORE?MANTICORE_PRIORITY:h.weight;}
    private final Map<Tile,ColosseumMarch.Result> marchOne=new HashMap<>();
    private long marchTick=-1;
    /** Every monster's next step with the player on {@code tile} (one result per tile and tick). */
    private ColosseumMarch.Result march(ColosseumSnapshot f,ColosseumCollision collision,Tile tile)
    {
        if(marchTick!=f.tick){marchOne.clear();marchTick=f.tick;}
        return marchOne.computeIfAbsent(tile,t->ColosseumMarch.run(f,collision,null,t,List.of(),1));
    }
    void reset(){clocks.clear();playerTick=-1;playerDx=playerDy=0;forecastCollision=null;playerForecast=null;marchOne.clear();marchTick=-1;}
    void observe(ColosseumSnapshot f)
    {
        playerDx=playerTick==f.tick-1?Math.max(-2,Math.min(2,f.x-playerX)):0;
        playerDy=playerTick==f.tick-1?Math.max(-2,Math.min(2,f.y-playerY)):0;
        playerX=f.x;playerY=f.y;playerTick=f.tick;
        forecastCollision=null;playerForecast=null;
        Map<Integer,Integer> events=new HashMap<>();
        Set<Integer> spawned=new HashSet<>();
        for(JsonElement e:f.events)
        {
            JsonObject event=e.getAsJsonObject();
            String kind=string(event,"e","");
            if("spawn".equals(kind)||"despawn".equals(kind)){int index=integer(event,"index",integer(event,"actor",-1));spawned.add(index);events.remove(index);continue;}
            if(!"anim".equals(kind))continue;
            int actor=integer(event,"actor",-1),animation=integer(event,"anim",-1);
            Npc npc=f.npc(actor);if(npc!=null&&(attackAnimation(npc.id,animation)||npc.role==Role.MANTICORE&&animation==10868))events.put(actor,animation);
        }
        for(Npc n:f.npcs)
        {
            // A Manticore can die on its release tick. The emitted volley still completes.
            if(!n.enemy()&&n.role!=Role.MANTICORE)continue;
            Clock c=clocks.computeIfAbsent(n.index,k->new Clock());
            if(c.id!=n.id||!n.key.equals(c.key)||spawned.contains(n.index)||f.tick-c.seen>1){c=new Clock();c.id=n.id;c.key=n.key;clocks.put(n.index,c);}
            if(spawned.contains(n.index)||n.animation==10861||n.animation==10862)if(c.spawn<0)c.spawn=f.tick;
            if(spawned.contains(n.index)&&n.role==Role.JAVELIN&&c.throwCount<0)c.throwCount=0;
            int animation=events.getOrDefault(n.index,n.animation);
            // Joining/resuming on a positive animation does not reveal its start tick.
            boolean changed=events.containsKey(n.index)||c.seen==f.tick-1&&animation!=c.animation;
            if(changed&&n.role==Role.MANTICORE&&animation==10868)c.charge=f.tick;
            // Capture the previous full charge BEFORE consuming the release snapshot's shorter queue.
            if(changed&&attackAnimation(n.id,animation))
            {
                c.attack=f.tick;
                // User 2026-11-08: the special (10893) is every fifth throw; it resyncs an unknown or drifted count.
                if(n.role==Role.JAVELIN)c.throwCount=animation==JAVELIN_SPECIAL?(c.throwCount<0?5:(c.throwCount+5)/5*5):c.throwCount<0?-1:c.throwCount+1;
                if(n.role==Role.MANTICORE)
                {
                    c.released=new ArrayList<>(c.queue);
                    if(c.released.size()!=3)
                    {
                        c.released=spotQueue(n.spots);
                        if(c.released.size()==2)
                        {for(String p:List.of(MAGIC,RANGE,MELEE))if(!c.released.contains(p)){c.released.add(0,p);break;}}
                    }
                }
            }
            List<String> queue=spotQueue(n.spots);
            if(queue.size()==3)c.queue=queue;
            c.dx=c.seen==f.tick-1?Integer.signum(n.x-c.x):0;c.dy=c.seen==f.tick-1?Integer.signum(n.y-c.y):0;
            c.x=n.x;c.y=n.y;
            c.animation=animation;c.seen=f.tick;
        }
        clocks.entrySet().removeIf(e->e.getValue().seen!=f.tick);
    }
    static List<String> spotQueue(JsonArray spots)
    {
        List<String> queue=new ArrayList<>();
        for(JsonElement e:spots){int id=e.getAsInt();String p=id==2681?MAGIC:id==2683?RANGE:id==2685?MELEE:null;if(p!=null&&!queue.contains(p))queue.add(p);}
        return queue;
    }
    static boolean attackAnimation(int id,int animation)
    {
        return id==12817&&(animation==10892||animation==10893)||id==12811&&animation==10859
            ||id==12819&&animation==10903||id==12818&&animation==10869
            ||id==12814&&animation==10850||id==12815&&animation==10853||id==12816&&animation==10856
            ||(id==12812||id==12813)&&animation==10843||id==12810&&animation==10847;
    }
    List<Hit> hits(ColosseumSnapshot f,ColosseumCollision collision,Tile tile,int ahead)
    {
        List<Hit> hits=new ArrayList<>();
        Tile future=f.moving&&tile.x==f.x&&tile.y==f.y?playerFuture(f,collision,tile):tile;
        for(Npc n:f.npcs)
        {
            Clock c=clocks.get(n.index);
            if(n.role==Role.MANTICORE&&c!=null&&c.released.size()==3
                &&f.tick+ahead-c.attack>=0&&f.tick+ahead-c.attack<=1)
            {
                // Already released shots no longer depend on the living shooter's sight.
                // Run 0's dying Manticore releases at 643 and still requires its third style at 644.
                addHits(f,n,c,ahead,false,false,false,hits);continue;
            }
            if(!n.enemy())continue;
            int distance=n.distance(tile.x,tile.y);
            boolean adjacent=collision.melee(n,tile)||collision.melee(n,future);
            boolean changingSight=c!=null&&n.targetsPlayer()&&(c.dx!=0||c.dy!=0||playerDx!=0||playerDy!=0);
            boolean approaching=n.meleeOnly()&&(changingSight||ahead>0&&(n.targetsPlayer()||n.warband()))
                &&collision.npcMeleeAfterApproach(n,tile,ahead+1);
            // A nearby footprint cannot cross a pillar or another actor just because a
            // future tick falls inside its distance. This also keeps blocked Minotaurs
            // from manufacturing repeated retreats while the player holds cover.
            if(n.meleeOnly()&&!adjacent&&!approaching)continue;
            // The recorded controller tests the ray to the closest footprint tile.
            // Native WorldArea edge visibility admits covered shooters at pillar corners.
            boolean inSight=collision.firingSight(n,tile);
            // On a tile the player would walk to, a covered shooter after the player walks one step a tick until it
            // has sight, as melee does. Counting only today's sight sent the player out of a double onto tiles where
            // the Javelin and Shaman walked into sight and threw together again, 11 times (1.0.11 wave 2 t204-246;
            // wave 5 t1224 north of the pillar). The servers let them throw together 4 times in 4,923 attacks.
            boolean speculative=c!=null&&n.targetsPlayer()&&(tile.x!=f.x||tile.y!=f.y);
            // 1.0.64: a shooter targeting the player that will have range and sight after its next step throws on that step,
            // moving or not (live 2026-11-05: 15:35 w2 t300 a Javelin parked behind the NW pillar stepped into sight, 44;
            // 16:09 t322 a Javelin at (1832,3111) walked into range and threw, 40, both unprotected).
            boolean firingNow=inSight&&distance<=15;
            boolean stepping=!firingNow&&(speculative?collision.npcFiringAfterApproach(n,tile,ahead+1)
                :(changingSight||n.targetsPlayer())&&(collision.npcFiringAfterApproach(n,tile)||collision.npcFiringAfterApproachPastDying(n,tile)));
            // NPCs take their approach/shot before the player's next path step. A speculative
            // player destination must not expose a stationary, covered ranged attack early.
            // 1.0.97: a Manticore's line of fire now or after its next step, by the stepping model the recordings bear out.
            boolean ray=false;
            if(HELD_CHARGE&&n.role==Role.MANTICORE)
            {
                Tile next=firingNow?null:march(f,collision,tile).rest.get(n.index);
                ray=firingNow||next!=null&&collision.firingFrom(n,next,tile,15);
            }
            if(!n.meleeOnly()&&!firingNow&&!stepping&&!ray)continue;
            addHits(f,n,c,ahead,adjacent,stepping,ray,hits);
        }
        return hits;
    }
    private Tile playerFuture(ColosseumSnapshot f,ColosseumCollision collision,Tile tile)
    {
        if(forecastCollision==collision&&playerForecast!=null)return playerForecast;
        forecastCollision=collision;playerForecast=tile;
        JsonObject nativeDestination=object(f.player,"destination");
        if(nativeDestination.has("tx")&&nativeDestination.has("ty"))
        {
            Tile target=new Tile(integer(nativeDestination,"tx",-1),integer(nativeDestination,"ty",-1));
            if(integer(nativeDestination,"plane",f.plane)!=f.plane||!collision.free(target.x,target.y))return tile;
            List<Tile> route=collision.paths(tile,32).to(target);
            if(route.size()>1)playerForecast=route.get(Math.min(route.size()-1,f.runOn&&f.runEnergy>0?2:1));
            return playerForecast;
        }
        Tile step=new Tile(tile.x+Integer.signum(playerDx),tile.y+Integer.signum(playerDy));
        if(collision.step(tile,step))
        {
            playerForecast=step;
            if(f.runOn&&(Math.abs(playerDx)>1||Math.abs(playerDy)>1))
            {
                Tile second=new Tile(step.x+Integer.signum(playerDx),step.y+Integer.signum(playerDy));
                if(collision.step(step,second))playerForecast=second;
            }
        }
        return playerForecast;
    }
    /**
     * @param stepping a covered shooter whose next approach step gives it a shot at the tile
     * @param ray      a Manticore with a firing ray at the tile now or after its next step: a held charge goes off
     */
    private void addHits(ColosseumSnapshot f,Npc n,Clock c,int ahead,boolean adjacent,boolean stepping,boolean ray,List<Hit> hits)
    {
            if(n.role==Role.MANTICORE)
            {
                if(c==null)return;
                long delta=f.tick+ahead-c.attack;
                // Actual release supersedes a charge forecast, including the observed five-tick
                // charge exception. On the release snapshot the first hit was already protected.
                if(c.released.size()==3&&delta>=0&&delta<=1)
                {hits.add(new Hit(c.released.get((int)delta+1),60,n));return;}
                if(c.charge>c.attack&&c.charge>=0&&c.queue.size()==3)
                {
                    long elapsed=f.tick-c.charge;
                    // A charged attack waits when cover blocks it. Its timer does not keep
                    // cycling while it cannot fire; retain the first style until release.
                    long readyIn=Math.max(0,5-elapsed),projected=ahead-readyIn;
                    if(projected>=0&&projected<=2){hits.add(new Hit(c.queue.get((int)projected),60,n,elapsed<=5||ray?2:1));return;}
                    // All three recorded five-tick releases have protection OFF at release-1.
                    // Their source policy still waits for elapsed five; do not invent an early
                    // protection step to compensate for this observed source behavior.
                }
                if(c.charge<=c.attack&&spotQueue(n.spots).size()==3&&(c.attack<0||f.tick-c.attack>=9)&&ahead<=2)
                    hits.add(new Hit(c.queue.get(ahead),60,n,1));
                return;
            }
            String prayer=n.meleeOnly()?MELEE:n.role==Role.ARCHER||n.role==Role.JAVELIN?RANGE:n.role==Role.SEER||n.role==Role.SHAMAN||n.role==Role.SHOCKWAVE?MAGIC:null;
            if(prayer==null)return;
            boolean timed=c!=null&&c.attack>=0;
            int period=n.warband()?6:5;
            // Current protection stays ready until a native attack. A hypothetical route,
            // however, must not count that same ready attack on every future tick.
            long readyIn=timed?Math.max(0,period-1-(f.tick-c.attack)):Long.MAX_VALUE;
            boolean due=timed&&ahead>=readyIn&&(ahead-readyIn)%period==0;
            if(!timed&&n.warband()&&c!=null&&c.spawn>=0)
            {
                int first=n.role==Role.BERSERKER?8:n.role==Role.SEER?9:10;
                long elapsed=f.tick+ahead-c.spawn-first;
                due=elapsed>=0&&elapsed%6==0;
            }
            // Simultaneous recorded windows rank Shaman above warband and Javelin above
            // one Manticore style; timing certainty is still resolved before these weights.
            int weight=n.warband()?25:n.role==Role.JAVELIN||n.role==Role.SHOCKWAVE||n.role==Role.MINOTAUR?80:40;
            // User 2026-11-08 (and 1.0.43): the Javelin's fifth throw is the sky-javelin special, which Protect from Missiles
            // does not block. It weighs 1, so a Shaman, orb or melee hit due on that tick gets the prayer instead.
            if(due&&n.role==Role.JAVELIN&&c.throwCount>=0&&(c.throwCount+1+(ahead-readyIn)/period)%5==0)weight=1;
            // An overdue shooter that walks into sight throws on that tick: the servers prayed against 317 of 326
            // such Javelin throws, 27 of 29 alongside another attack (1.0.10 t217: 43 unprotected beside a Shaman).
            boolean overdue=timed&&f.tick-c.attack>period-1&&!(stepping&&ahead==0);
            if(due)hits.add(new Hit(prayer,weight,n,overdue?1:2));
            else if(!timed&&!n.warband())
            {
                // No invented first-shot phase: retain an already-correct prayer until an attack
                // receipt establishes its clock. Uncertain visible threats still influence cover.
                if(f.prayers.contains(prayer)||n.targetsPlayer()||adjacent&&n.meleeOnly())hits.add(new Hit(prayer,20,n,0));
            }
    }
    String protection(ColosseumSnapshot f,ColosseumCollision collision,Tile tile)
    {
        Map<String,Integer> scores=new LinkedHashMap<>();
        if(jaguarIncoming(f,collision,tile))return MELEE;
        List<Hit> hits=hits(f,collision,tile,0);int certainty=-1;
        for(Hit h:hits)certainty=Math.max(certainty,h.certainty);
        // A blocked, overdue clock means "ready", not "will fire now". It must not
        // override an attack whose next protection tick is known from a native receipt.
        boolean first=manticoreFirst(f);
        for(Hit h:hits)if(h.certainty==certainty)scores.merge(h.prayer,priority(h,first),Integer::sum);
        String best=null;int score=0;
        for(Map.Entry<String,Integer> e:scores.entrySet())if(e.getValue()>score){best=e.getKey();score=e.getValue();}
        return best;
    }
    /**
     * Copy of the servers: Protect from Melee goes up on the tick before a Jaguar's claw, over any other prayer
     * (292 of 293 claws in 24 server runs). A claw is incoming when its five-tick clock is ready (no claw yet, or
     * four ticks since the last) and the Jaguar is in claw range or closing within two tiles (first claws: melee went
     * up at distance 1 in 8, distance 2 in 19). A Jaguar parked at a pillar corner is left alone. 1.0.97: closing means
     * its next step puts it in claw reach, walls and other monsters in the way ({@link #JAGUAR_REACH}).
     * <p>
     * A diagonal Jaguar that has stopped counts in two more cases (only for the player's own tile):
     * <ul>
     * <li>The player is walking and only walls could stop its step (1.0.36: 1.0.35 local 2026-11-04 09:07 wave 1
     * walked past a Jaguar standing diagonal behind the Shaman; it stepped onto the Shaman's tile and clawed 50 under
     * Rigour alone). A standing player keeps the servers' rule here: ignoring a live enemy in the way cost 68 over
     * the 49 server runs, because a Jaguar parked behind one keeps a ready clock.</li>
     * <li>The only thing in its way is an enemy playing its death animation (1.0.44 local 2026-11-04 22:14 wave 2
     * t234-237: the Shaman died on the Jaguar's step tile; it stepped in and clawed 18+10 with no protection up).</li>
     * </ul>
     */
    private boolean jaguarIncoming(ColosseumSnapshot f,ColosseumCollision collision,Tile tile)
    {
        // Walking means a native destination off the player's tile. The snapshot's "moving" is any non-idle pose: a
        // player turning to shoot from (1815,3114) looked like a step into the Jaguar's north slot (server 20261025-122437).
        boolean own=tile.x==f.x&&tile.y==f.y;
        JsonObject destination=object(f.player,"destination");
        boolean walking=own&&destination.has("tx")
            &&(integer(destination,"tx",f.x)!=f.x||integer(destination,"ty",f.y)!=f.y);
        for(Npc n:f.npcs)
        {
            if(!n.alive()||n.role!=Role.JAGUAR||!n.targetsPlayer())continue;
            Clock c=clocks.get(n.index);
            if(c==null||c.attack>=0&&f.tick-c.attack<4)continue;
            // 1.0.97: out of claw reach, the claw comes exactly when its next step reaches (ColosseumMarch): 28 of 28
            // with, 0 of 2,884 without, whether or not it moved on this tick. Two of the 28 had stood still diagonal to
            // the player (and the simulator's seed 54 died of that one: a sidestep left the Jaguar diagonal, it took its
            // x step and clawed 72 with no prayer up).
            boolean closing=JAGUAR_REACH&&collision.available()?march(f,collision,tile).reach.contains(n.index)
                :(c.dx!=0||c.dy!=0)&&n.distance(tile.x,tile.y)<=2;
            boolean passing=walking&&collision.diagonalStepOpenByWalls(n,tile);
            boolean freed=own&&collision.diagonalStepHeldByDying(n,tile);
            if(closing||passing||freed||collision.melee(n,tile))return true;
        }
        return false;
    }
    double danger(ColosseumSnapshot f,ColosseumCollision collision,Tile tile)
    {
        double danger=0;
        for(int ahead=0;ahead<4;ahead++)
        {
            Map<String,Integer> weights=new HashMap<>();
            for(Hit h:hits(f,collision,tile,ahead))weights.merge(h.prayer,h.weight,Integer::sum);
            int total=0,best=0;for(int weight:weights.values()){total+=weight;best=Math.max(best,weight);}
            danger+=(total-best)*4.0/(ahead+1)+total*.04;
        }
        return danger;
    }
}
