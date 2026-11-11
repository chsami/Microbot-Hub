package net.runelite.client.plugins.microbot.drocolosseumprayers.local;

import com.google.gson.*;
import java.util.*;
import static net.runelite.client.plugins.microbot.drocolosseumprayers.local.ColosseumSnapshot.*;
import net.runelite.client.plugins.microbot.drocolosseumprayers.local.ColosseumCollision.Tile;

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

        int throwCount=-1;
    }
    static final int JAVELIN_SPECIAL=10893;
    static final class Hit
    {
        final String prayer;final int weight,certainty;final Npc npc;
        Hit(String prayer,int weight,Npc npc){this(prayer,weight,npc,2);}
        Hit(String prayer,int weight,Npc npc,int certainty){this.prayer=prayer;this.weight=weight;this.npc=npc;this.certainty=certainty;}
    }

    static final String MANTICORE_FIRST=System.getProperty("colo.manticoreFirst","mayhem");
    static final boolean HELD_CHARGE=!"false".equals(System.getProperty("colo.heldCharge")),JAGUAR_REACH=!"false".equals(System.getProperty("colo.jaguarReach"));

    static final int MANTICORE_PRIORITY=1000;

    static boolean manticoreFirst(ColosseumSnapshot f){return "always".equals(MANTICORE_FIRST)||!"false".equals(MANTICORE_FIRST)&&f.mantimayhem>0;}

    static int priority(Hit h,boolean manticoreFirst){return manticoreFirst&&h.npc!=null&&h.npc.role==Role.MANTICORE?MANTICORE_PRIORITY:h.weight;}
    private final Map<Tile,ColosseumMarch.Result> marchOne=new HashMap<>();
    private long marchTick=-1;

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
            if(!n.enemy()&&n.role!=Role.MANTICORE)continue;
            Clock c=clocks.computeIfAbsent(n.index,k->new Clock());
            if(c.id!=n.id||!n.key.equals(c.key)||spawned.contains(n.index)||f.tick-c.seen>1){c=new Clock();c.id=n.id;c.key=n.key;clocks.put(n.index,c);}
            if(spawned.contains(n.index)||n.animation==10861||n.animation==10862)if(c.spawn<0)c.spawn=f.tick;
            if(spawned.contains(n.index)&&n.role==Role.JAVELIN&&c.throwCount<0)c.throwCount=0;
            int animation=events.getOrDefault(n.index,n.animation);

            boolean changed=events.containsKey(n.index)||c.seen==f.tick-1&&animation!=c.animation;
            if(changed&&n.role==Role.MANTICORE&&animation==10868)c.charge=f.tick;

            if(changed&&attackAnimation(n.id,animation))
            {
                c.attack=f.tick;

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
                addHits(f,n,c,ahead,false,false,false,hits);continue;
            }
            if(!n.enemy())continue;
            int distance=n.distance(tile.x,tile.y);
            boolean adjacent=collision.melee(n,tile)||collision.melee(n,future);
            boolean changingSight=c!=null&&n.targetsPlayer()&&(c.dx!=0||c.dy!=0||playerDx!=0||playerDy!=0);
            boolean approaching=n.meleeOnly()&&(changingSight||ahead>0&&(n.targetsPlayer()||n.warband()))
                &&collision.npcMeleeAfterApproach(n,tile,ahead+1);

            if(n.meleeOnly()&&!adjacent&&!approaching)continue;

            boolean inSight=collision.firingSight(n,tile);

            boolean speculative=c!=null&&n.targetsPlayer()&&(tile.x!=f.x||tile.y!=f.y);

            boolean firingNow=inSight&&distance<=15;
            boolean stepping=!firingNow&&(speculative?collision.npcFiringAfterApproach(n,tile,ahead+1)
                :(changingSight||n.targetsPlayer())&&(collision.npcFiringAfterApproach(n,tile)||collision.npcFiringAfterApproachPastDying(n,tile)));
            if(!stepping&&!firingNow&&MARCH_STEP&&ahead==0&&!n.meleeOnly()&&n.targetsPlayer()&&collision.available()&&n.role!=Role.MANTICORE)
            {Tile next=march(f,collision,tile).rest.get(n.index);stepping=next!=null&&(next.x!=n.x||next.y!=n.y)&&collision.firingFrom(n,next,tile,ColosseumMarch.range(n));}

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

    private void addHits(ColosseumSnapshot f,Npc n,Clock c,int ahead,boolean adjacent,boolean stepping,boolean ray,List<Hit> hits)
    {
            if(n.role==Role.MANTICORE)
            {
                if(c==null)return;
                long delta=f.tick+ahead-c.attack;

                if(c.released.size()==3&&delta>=0&&delta<=1)
                {hits.add(new Hit(c.released.get((int)delta+1),60,n));return;}
                if(c.charge>c.attack&&c.charge>=0&&c.queue.size()==3)
                {
                    long elapsed=f.tick-c.charge;

                    long readyIn=Math.max(0,5-elapsed),projected=ahead-readyIn;
                    if(projected>=0&&projected<=2){hits.add(new Hit(c.queue.get((int)projected),60,n,elapsed<=5||ray?2:1));return;}
                }
                if(c.charge<=c.attack&&spotQueue(n.spots).size()==3&&(c.attack<0||f.tick-c.attack>=9)&&ahead<=2)
                    hits.add(new Hit(c.queue.get(ahead),60,n,1));
                return;
            }
            String prayer=n.meleeOnly()?MELEE:n.role==Role.ARCHER||n.role==Role.JAVELIN?RANGE:n.role==Role.SEER||n.role==Role.SHAMAN||n.role==Role.SHOCKWAVE?MAGIC:null;
            if(prayer==null)return;
            boolean timed=c!=null&&c.attack>=0;
            int period=n.warband()?6:5;

            long readyIn=timed?Math.max(0,period-1-(f.tick-c.attack)):Long.MAX_VALUE;
            boolean due=timed&&ahead>=readyIn&&(ahead-readyIn)%period==0;
            if(!timed&&n.warband()&&c!=null&&c.spawn>=0)
            {
                int first=n.role==Role.BERSERKER?8:n.role==Role.SEER?9:10;
                long elapsed=f.tick+ahead-c.spawn-first;
                due=elapsed>=0&&elapsed%6==0;
            }

            int weight=n.warband()?25:n.role==Role.JAVELIN||n.role==Role.SHOCKWAVE||n.role==Role.MINOTAUR?80:40;

            if(due&&n.role==Role.JAVELIN&&c.throwCount>=0&&(c.throwCount+1+(ahead-readyIn)/period)%5==0)weight=1;

            boolean overdue=timed&&f.tick-c.attack>period-1&&!(stepping&&ahead==0);
            if(due)hits.add(new Hit(prayer,weight,n,overdue?1:2));
            else if(!timed&&!n.warband())
            {
                if(f.prayers.contains(prayer)||n.targetsPlayer()||adjacent&&n.meleeOnly())hits.add(new Hit(prayer,UNCLOCKED_BY_ROLE?weight:20,n,0));
            }
    }
    static final boolean PEEK_TIER=!"false".equals(System.getProperty("colo.peekTier")),
        UNCLOCKED_BY_ROLE=!"false".equals(System.getProperty("colo.peekTier"));
    static final boolean MARCH_STEP=!"false".equals(System.getProperty("colo.marchStep"));
    private static int tier(Hit h,ColosseumSnapshot f){return PEEK_TIER&&f.moving&&h.certainty==1&&h.npc!=null&&!h.npc.meleeOnly()&&h.npc.role!=Role.MANTICORE?2:h.certainty;}
    String protection(ColosseumSnapshot f,ColosseumCollision collision,Tile tile)
    {
        Map<String,Integer> scores=new LinkedHashMap<>();
        if(jaguarIncoming(f,collision,tile))return MELEE;
        List<Hit> hits=hits(f,collision,tile,0);int certainty=-1;
        for(Hit h:hits)certainty=Math.max(certainty,tier(h,f));

        boolean first=manticoreFirst(f);
        for(Hit h:hits)if(tier(h,f)==certainty)scores.merge(h.prayer,priority(h,first),Integer::sum);
        String best=null;int score=0;
        for(Map.Entry<String,Integer> e:scores.entrySet())if(e.getValue()>score){best=e.getKey();score=e.getValue();}
        return best;
    }

    private boolean jaguarIncoming(ColosseumSnapshot f,ColosseumCollision collision,Tile tile)
    {
        boolean own=tile.x==f.x&&tile.y==f.y;
        JsonObject destination=object(f.player,"destination");
        boolean walking=own&&destination.has("tx")
            &&(integer(destination,"tx",f.x)!=f.x||integer(destination,"ty",f.y)!=f.y);
        for(Npc n:f.npcs)
        {
            if(!n.alive()||n.role!=Role.JAGUAR||!n.targetsPlayer())continue;
            Clock c=clocks.get(n.index);
            if(c==null||c.attack>=0&&f.tick-c.attack<4)continue;

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
