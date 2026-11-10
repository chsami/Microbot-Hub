package net.runelite.client.plugins.microbot.drocolosseumprayers.local;

import java.util.*;
import static net.runelite.client.plugins.microbot.drocolosseumprayers.local.ColosseumSnapshot.*;
import net.runelite.client.plugins.microbot.drocolosseumprayers.local.ColosseumCollision.Tile;

final class ColosseumMarch
{
    static final Set<Role> SHOOTERS=EnumSet.of(Role.SHAMAN,Role.JAVELIN,Role.MANTICORE,Role.SHOCKWAVE);
    static String style(Npc n){return n.role==Role.MANTICORE?"volley":n.role==Role.JAVELIN?"ranged":"magic";}
    static final int SETTLE=3;
    private ColosseumMarch(){}

    static final class Result
    {
        final Map<Integer,Tile> rest=new HashMap<>();

        final Set<String> atRest=new HashSet<>();

        final Set<String> onTheWay=new HashSet<>();

        int doubles;

        int clawed=-1;

        final Set<Integer> reach=new HashSet<>();

        final Map<Integer,Integer> stepped=new HashMap<>();

        boolean settled;
        int ticks;
    }

    static boolean marches(Npc n)
    {
        return n.alive()&&n.enemy()&&(SHOOTERS.contains(n.role)||n.role==Role.JAGUAR||n.role==Role.MINOTAUR);
    }
    static int range(Npc n){return n.role==Role.SHAMAN?10:15;}

    private static boolean stands(Npc n){return n.alive()&&n.enemy()&&n.role!=Role.FLARE&&n.role!=Role.BEES&&!marches(n)&&(WARBAND_BLOCKS||!n.warband());}
    static final boolean WARBAND_BLOCKS="true".equals(System.getProperty("colo.marchWarband"));

    private static boolean lingers(Npc n){return CORPSES&&n.index>=0&&n.dead&&n.role!=Role.FLARE&&n.role!=Role.PILLAR&&n.role!=Role.MINIMUS&&n.role!=Role.SPECTATOR&&(WARBAND_BLOCKS||!n.warband());}
    static final boolean CORPSES=!"false".equals(System.getProperty("colo.marchCorpses"));
    static final int CORPSE_TICKS=3;

    static Result run(ColosseumSnapshot f,ColosseumCollision collision,List<Tile> perTick,int limit)
    {return run(f,collision,null,new Tile(f.x,f.y),perTick,limit);}

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
                else if(collision.firingFrom(n,here,player,range(n))){sight.add(style(n));continue;}
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
            else if(collision.firingFrom(n,here,player,range(n)))r.atRest.add(style(n));
        }
        return r;
    }

    static List<Tile> running(List<Tile> route)
    {
        List<Tile> perTick=new ArrayList<>();
        for(int i=2;i<route.size();i+=2)perTick.add(route.get(i));
        if(route.size()>1&&(route.size()-1)%2!=0)perTick.add(route.get(route.size()-1));
        return perTick;
    }
}
