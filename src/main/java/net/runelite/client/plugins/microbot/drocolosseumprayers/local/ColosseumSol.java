package net.runelite.client.plugins.microbot.drocolosseumprayers.local;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.client.plugins.microbot.drocolosseumprayers.local.ColosseumCollision.Tile;
import static net.runelite.client.plugins.microbot.drocolosseumprayers.local.ColosseumSnapshot.*;
import static net.runelite.client.plugins.microbot.drocolosseumprayers.local.ColosseumDecisionEngine.command;

final class ColosseumSol
{
    static final int SPEAR=10883,SHIELD=10885,SHORT_TRIPLE=10887,LONG_TRIPLE=10886,GRAPPLE=10884;
    private enum Phase { APPROACH, BASIC, TRIPLE, GRAPPLE }
    private Phase phase=Phase.APPROACH;
    private String key;
    private long previous=-1,onset=-1,lastMove=-1000,lastAttack=-1000;
    private int animation=-1,kind=-1,consecutive;
    private int anchorX,anchorY,sector;
    private Tile destination,returnTile;
    private boolean defended,continued;
    private final Set<Tile> hot=new HashSet<>();
    private final Map<Tile,Long> lasers=new HashMap<>();
    private String status="Sol: waiting for an observed attack";

    void reset()
    {
        phase=Phase.APPROACH;key=null;previous=onset=-1;animation=kind=-1;consecutive=0;
        lastMove=lastAttack=-1000;destination=returnTile=null;defended=false;hot.clear();lasers.clear();
    }

    List<JsonObject> decide(ColosseumSnapshot f,Npc boss,ColosseumCollision collision)
    {
        List<JsonObject> plan=new ArrayList<>();
        if(!boss.key.equals(key)) { reset();key=boss.key; }
        boolean contiguous=previous<0||f.tick==previous+1;
        if(!contiguous){phase=Phase.APPROACH;onset=-1;animation=kind=-1;consecutive=0;destination=null;}
        boolean observed=false;
        for(JsonElement e:f.events)
        {
            JsonObject event=e.getAsJsonObject();
            if("anim".equals(string(event,"e",""))&&actor(event,boss)&&integer(event,"anim",-1)>0)
            {
                int seen=integer(event,"anim",-1);
                if(seen==SPEAR||seen==SHIELD||seen==SHORT_TRIPLE||seen==LONG_TRIPLE||seen==GRAPPLE)
                {begin(f,boss,seen);observed=true;}
            }
        }

        if(!observed&&boss.animation!=animation&&boss.animationFrame==0
            &&(boss.animation==SPEAR||boss.animation==SHIELD||boss.animation==SHORT_TRIPLE||boss.animation==LONG_TRIPLE||boss.animation==GRAPPLE))
            begin(f,boss,boss.animation);
        animation=boss.animation;previous=f.tick;
        observeGround(f);
        if(!collision.available()){status="Sol: waiting for current arena collision";return plan;}
        int age=onset<0?-1:(int)(f.tick-onset);
        boolean protect=phase==Phase.TRIPLE&&(age==2||age==5||age==(kind==LONG_TRIPLE?9:8));
        if(protect&&!f.prayers.contains("PROTECT_FROM_MELEE"))plan.add(command("prayer.set","prayer","PROTECT_FROM_MELEE","on",true));
        if(!protect&&f.prayers.contains("PROTECT_FROM_MELEE"))plan.add(command("prayer.set","prayer","PROTECT_FROM_MELEE","on",false));
        if(phase==Phase.GRAPPLE)
        {
            int called=grappleSlot(boss.say);
            if(!defended&&called>=0)
            {
                for(Item item:f.equipment)if(item.slot==called)
                {plan.add(command("equipment.slot","slot",called,"option","Defend"));defended=true;break;}
            }
            if(boss.animation==GRAPPLE)
            {
                Tile here=new Tile(f.x,f.y);
                if(f.interacting!=boss.index&&boss.distance(f.x,f.y)>0&&collision.melee(boss,here)&&!unsafe(f,here,0)&&f.tick-lastAttack>=2)
                {plan.add(command("npc.attack","index",boss.index));lastAttack=f.tick;}
                status="Sol: responding to the observed body grapple";return plan;
            }
            phase=Phase.APPROACH;
        }
        Tile current=new Tile(f.x,f.y),chosen=current;
        boolean dodge=phase==Phase.BASIC&&age>=0&&age<2;
        boolean travelling=f.moving&&destination!=null&&!current.equals(destination)&&f.tick-lastMove<=2;
        boolean finishingRoute=travelling&&phase!=Phase.APPROACH&&!dodge&&!(phase==Phase.BASIC&&age==2)
            &&eligible(f,collision,destination,1)
            &&safeRoute(f,collision.movementPaths(current,12).to(destination));

        boolean holding=phase==Phase.BASIC&&age>=3&&age<=6&&!unsafe(f,current,0)&&boss.distance(f.x,f.y)>0;
        if(dodge)chosen=dodge(f,boss,collision,current);
        else if(holding)chosen=current;
        else if(phase==Phase.BASIC&&age==2)
        {
            boolean back=returnTile!=null&&!returnTile.equals(current)&&boss.distance(returnTile.x,returnTile.y)==1
                &&collision.melee(boss,returnTile)&&eligible(f,collision,returnTile,1)
                &&safeRoute(f,collision.movementPaths(current,12).to(returnTile));
            chosen=back?returnTile:adjacent(f,boss,collision,current,true);
        }
        else if(finishingRoute)chosen=current;
        else if(unsafe(f,current,0)||boss.distance(f.x,f.y)==0||!collision.melee(boss,current))chosen=adjacent(f,boss,collision,current,false);

        else if(phase!=Phase.TRIPLE&&kind!=GRAPPLE&&f.interacting==boss.index)
        {
            Tile home=home(boss);

            boolean sameAnchor=phase==Phase.APPROACH||boss.x==anchorX&&boss.y==anchorY;
            if(sameAnchor&&!current.equals(home)&&eligible(f,collision,home,1)
                &&safeRoute(f,collision.movementPaths(current,10).to(home)))chosen=home;
        }
        boolean moved=chosen!=null&&!chosen.equals(current)&&move(plan,f,chosen);
        String offense=meleeOffensive(f);
        if(!offense.isEmpty()&&!f.prayers.contains(offense))plan.add(command("prayer.set","prayer",offense,"on",true));

        int reach=boss.distance(f.x,f.y);
        boolean reattack=phase==Phase.BASIC&&f.interacting!=boss.index&&!unsafe(f,current,0)&&reach==1
            &&(age==3||age==4&&lastAttack<onset+3);
        if(reattack||!moved&&!travelling&&!dodge&&!unsafe(f,current,0)&&boss.distance(f.x,f.y)>0&&collision.melee(boss,current)&&f.interacting!=boss.index&&f.tick-lastAttack>=2)
        {plan.add(command("npc.attack","index",boss.index));lastAttack=f.tick;}
        String name=phase==Phase.BASIC?(kind==SPEAR?"spear ":"shield ")+(consecutive%2==1?1:2):phase.name().toLowerCase(java.util.Locale.ROOT);
        status="Sol: "+name+(age>=0?" +"+age:"");
        return plan;
    }

    static String meleeOffensive(ColosseumSnapshot f)
    {
        String best=string(object(f.player,"bestOffensive"),"melee","");
        if(!best.isEmpty())return best;
        return f.maxPrayer>=31?"ULTIMATE_STRENGTH":f.maxPrayer>=13?"SUPERHUMAN_STRENGTH":f.maxPrayer>=4?"BURST_OF_STRENGTH":"";
    }

    private void begin(ColosseumSnapshot f,Npc boss,int seen)
    {
        consecutive=seen==kind?consecutive+1:1;kind=seen;onset=f.tick;defended=continued=false;
        anchorX=boss.x;anchorY=boss.y;returnTile=new Tile(f.x,f.y);

        int cx=boss.x+boss.size/2,cy=boss.y+boss.size/2,dx=f.x-cx,dy=f.y-cy;
        sector=Math.abs(dx)>Math.abs(dy)?(dx>0?1:3):(dy>0?2:0);
        phase=seen==SHORT_TRIPLE||seen==LONG_TRIPLE?Phase.TRIPLE:seen==GRAPPLE?Phase.GRAPPLE:Phase.BASIC;
    }

    private static boolean actor(JsonObject event,Npc boss)
    {
        JsonElement value=event.get("actor");
        if(value==null||!value.isJsonPrimitive())return false;
        return value.getAsString().equals(Integer.toString(boss.index))||value.getAsString().equals(boss.key);
    }

    private void observeGround(ColosseumSnapshot f)
    {
        lasers.entrySet().removeIf(e->e.getValue()<f.tick);
        for(JsonElement e:f.events)
        {
            JsonObject g=e.getAsJsonObject();if(!"gfxobj".equals(string(g,"e","")))continue;
            int id=integer(g,"id",-1),x=integer(g,"tx",-1),y=integer(g,"ty",-1);
            if(x<1792||x>1855||y<3072||y>3135)continue;
            Tile tile=new Tile(x,y);
            if(id==2698)hot.add(tile);
            if(id==2689||id==2690)lasers.put(tile,f.tick+4);
        }
    }

    private boolean unsafe(ColosseumSnapshot f,Tile tile,int arrival)
    {
        if(hot.contains(tile)||f.sand.contains(tile))return true;
        Long impact=lasers.get(tile);

        if(impact!=null&&impact>=f.tick+arrival&&impact<=f.tick+arrival+4)return true;
        for(Npc n:f.npcs)if(n.alive()&&n.role==Role.FLARE&&n.distance(tile.x,tile.y)==0)return true;
        return false;
    }

    private boolean laserOrFlare(ColosseumSnapshot f,Tile tile,int arrival)
    {
        Long impact=lasers.get(tile);
        if(impact!=null&&impact>=f.tick+arrival&&impact<=f.tick+arrival+4)return true;
        for(Npc n:f.npcs)if(n.alive()&&n.role==Role.FLARE&&n.distance(tile.x,tile.y)==0)return true;
        return false;
    }

    private boolean eligible(ColosseumSnapshot f,ColosseumCollision collision,Tile tile,int arrival)
    {return collision.free(tile.x,tile.y)&&!unsafe(f,tile,arrival);}

    private Tile home(Npc boss)
    {
        if(sector==1)return new Tile(boss.x+boss.size,boss.y+1);
        if(sector==2)return new Tile(boss.x+boss.size-2,boss.y+boss.size);
        if(sector==3)return new Tile(boss.x-1,boss.y+boss.size-2);
        return new Tile(boss.x+1,boss.y-1);
    }

    private Tile dodge(ColosseumSnapshot f,Npc boss,ColosseumCollision collision,Tile current)
    {
        boolean first=consecutive%2==1;
        int own=returnTile==null?-1:lateral(returnTile,boss);
        int depth=kind==SHIELD?(first?2:3):2;
        int lateral=kind==SPEAR?(first?2:1):1;
        if(own>=0&&own<boss.size)
        {
            if(kind==SHIELD)lateral=own;
            else
            {
                int bestLane=lateral,bestDepth=depth,bestKey=Integer.MAX_VALUE;
                for(int lane=-1;lane<=boss.size;lane++)
                {
                    boolean corner=lane<0||lane>=boss.size;
                    if(!corner&&Math.floorMod(lane,2)==(consecutive%2))continue;

                    int d=corner&&first?1:2,steps=Math.max(Math.abs(lane-own),d-1);
                    int key=steps*100+(corner?0:10)+(boss.size/2-Math.abs(lane-boss.size/2));
                    if(key<bestKey){bestKey=key;bestLane=lane;bestDepth=d;}
                }
                lateral=bestLane;depth=bestDepth;
            }
        }
        Tile preferred=relative(boss,lateral,-depth);

        if(f.tick==onset+1&&own>=0&&own<boss.size&&depth(current,boss)==-2
            &&!(sector==0&&own==2))
        {
            Tile along=relative(boss,own<=1?own+3:own-3,-1);
            if(!along.equals(current)&&eligible(f,collision,along,1)&&collision.movementPaths(current,6).distance(along)>=0
                &&safeRoute(f,collision.movementPaths(current,6).to(along)))
            {continued=true;return along;}
        }

        if(f.tick==onset+1&&kind==SHIELD&&!first&&own>=0&&own<boss.size&&depth(current,boss)==-3)
        {
            Tile across=relative(boss,own==2||own==4?own-4:own+4,-2);
            if(!across.equals(current)&&eligible(f,collision,across,1)&&collision.movementPaths(current,8).distance(across)>=0
                &&safeRoute(f,collision.movementPaths(current,8).to(across)))
                return across;
        }
        if(f.tick>onset&&current.equals(destination)&&!basicHit(current,boss)&&!unsafe(f,current,0))return current;
        ColosseumCollision.Paths paths=collision.paths(current,6);
        ColosseumCollision.Paths movement=collision.movementPaths(current,6);
        if(eligible(f,collision,preferred,1)&&paths.distance(preferred)>=0&&paths.distance(preferred)<=4
            &&safeRoute(f,movement.to(preferred)))
            return preferred;
        Tile best=current;double score=Double.POSITIVE_INFINITY;
        for(Tile tile:paths.reachable)
        {
            int steps=paths.distance(tile),arrival=Math.max(1,(steps+(f.runOn?1:0))/(f.runOn?2:1));
            if(steps>4||!eligible(f,collision,tile,arrival)||basicHit(tile,boss)||!safeRoute(f,movement.to(tile)))continue;
            double value=tile.distance(preferred)*8+steps;
            if(value<score){score=value;best=tile;}
        }

        if(best.equals(current)&&basicHit(current,boss))
        {
            for(Tile tile:paths.reachable)
            {
                int steps=paths.distance(tile),arrival=Math.max(1,(steps+(f.runOn?1:0))/(f.runOn?2:1));
                if(steps>4||!collision.free(tile.x,tile.y)||basicHit(tile,boss)||laserOrFlare(f,tile,arrival))continue;
                double value=tile.distance(preferred)*8+steps+(hot.contains(tile)||f.sand.contains(tile)?20:0);
                if(value<score){score=value;best=tile;}
            }
        }
        return best;
    }

    private int lateral(Tile tile,Npc boss)
    {
        int x=tile.x-anchorX,y=tile.y-anchorY;
        return sector==1?y:sector==2?boss.size-1-x:sector==3?boss.size-1-y:x;
    }

    private int depth(Tile tile,Npc boss)
    {
        int x=tile.x-anchorX,y=tile.y-anchorY;
        return sector==1?boss.size-1-x:sector==2?boss.size-1-y:sector==3?x:y;
    }

    private Tile relative(Npc boss,int lateral,int depth)
    {
        if(sector==1)return new Tile(boss.x+boss.size-1-depth,boss.y+lateral);
        if(sector==2)return new Tile(boss.x+boss.size-1-lateral,boss.y+boss.size-1-depth);
        if(sector==3)return new Tile(boss.x+depth,boss.y+boss.size-1-lateral);
        return new Tile(boss.x+lateral,boss.y+depth);
    }

    boolean basicHit(Tile tile,Npc boss)
    {
        int distance=boss.distance(tile.x,tile.y);
        if(kind==SHIELD)return distance!=(consecutive%2==0?3:2);
        int x=tile.x-anchorX,y=tile.y-anchorY;
        int lateral=sector==1?y:sector==2?boss.size-1-x:sector==3?boss.size-1-y:x;
        int depth=sector==1?boss.size-1-x:sector==2?boss.size-1-y:sector==3?x:y;
        if(distance==0)return true;
        return lateral>=0&&lateral<boss.size&&depth<0&&(depth==-1||Math.floorMod(lateral,2)==(consecutive%2));
    }

    private Tile adjacent(ColosseumSnapshot f,Npc boss,ColosseumCollision collision,Tile current,boolean returning)
    {
        ColosseumCollision.Paths paths=collision.paths(current,12);
        ColosseumCollision.Paths movement=collision.movementPaths(current,12);
        Tile best=current;double score=Double.POSITIVE_INFINITY;Tile home=home(boss);
        for(Tile tile:paths.reachable)
        {
            int steps=paths.distance(tile);if(steps<0||!collision.melee(boss,tile)||boss.distance(tile.x,tile.y)!=1)continue;
            int arrival=Math.max(1,(steps+(f.runOn?1:0))/(f.runOn?2:1));
            if(!eligible(f,collision,tile,arrival))continue;
            if(!safeRoute(f,movement.to(tile)))continue;

            double value=(steps<=2?0:steps*10)+(returning||steps<=2?0:tile.distance(home)*3)+tile.x*.0001+tile.y*.000001;

            if(!returning&&unsafe(f,current,0))value=steps*10
                +Math.abs(tile.x-(boss.x+(boss.size-1)/2.0))
                +Math.abs(tile.y-(boss.y+(boss.size-1)/2.0));
            if(value<score){score=value;best=tile;}
        }

        if(best.equals(current)&&unsafe(f,current,0))
        {
            for(Tile tile:paths.reachable)
            {
                int steps=paths.distance(tile);if(steps<1||steps>3)continue;
                int arrival=Math.max(1,(steps+(f.runOn?1:0))/(f.runOn?2:1));
                if(!eligible(f,collision,tile,arrival)||!safeRoute(f,movement.to(tile)))continue;
                if(phase==Phase.BASIC&&onset>=0&&f.tick-onset<=2&&basicHit(tile,boss))continue;
                double value=steps*10+boss.distance(tile.x,tile.y);
                if(value<score){score=value;best=tile;}
            }
        }
        return best;
    }

    private boolean safeRoute(ColosseumSnapshot f,List<Tile> route)
    {
        if(route.isEmpty())return false;
        for(int i=1;i<route.size();i++)
            if(unsafe(f,route.get(i),Math.max(1,(i+(f.runOn?1:0))/(f.runOn?2:1))))return false;
        return true;
    }

    private boolean move(List<JsonObject> plan,ColosseumSnapshot f,Tile tile)
    {
        if(tile.equals(destination)&&f.moving&&f.tick-lastMove<4)return false;
        plan.add(command("movement.tile","x",tile.x,"y",tile.y,"plane",f.plane));
        destination=tile;lastMove=f.tick;return true;
    }
    String status(){return status;}


    static int grappleSlot(String say)
    {
        String s=say==null?"":say.toUpperCase(java.util.Locale.ROOT);
        if(s.contains("CRUSH YOUR BODY"))return 4;
        if(s.contains("BREAK YOUR BACK"))return 1;
        if(s.contains("TWIST YOUR HANDS"))return 9;
        if(s.contains("BREAK YOUR LEGS"))return 7;
        if(s.contains("CUT YOUR FEET"))return 10;
        return -1;
    }
}
