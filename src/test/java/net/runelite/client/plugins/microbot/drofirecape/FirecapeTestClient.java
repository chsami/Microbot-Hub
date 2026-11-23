/* Copyright (c) 2026, DRO (droplugins). SPDX-License-Identifier: BSD-2-Clause */
package net.runelite.client.plugins.microbot.drofirecape;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.BiFunction;
import net.runelite.api.*;
import net.runelite.api.coords.*;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.drofirecape.core.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.Tile;

/** JDK interface proxies expose observed game state without instrumentation or a live client. */
final class FirecapeTestClient {
    int tick=100,prayer=99; boolean instance; Tile position=new Tile(28,28);
    final Map<Integer,Integer> varbits=new HashMap<>(); final List<List<Object>> menus=new ArrayList<>();
    final Widget widget=proxy(Widget.class,(m,a)->null);
    final Player player=proxy(Player.class,(m,a)-> {
        if(m.equals("getLocalLocation"))return new LocalPoint(position.x()*128+64,position.y()*128+64,WorldView.TOPLEVEL);
        if(m.equals("getWorldLocation"))return new WorldPoint(10332,5276,0);
        return null;
    });
    final WorldView view=proxy(WorldView.class,(m,a)-> {
        if(m.equals("getBaseX"))return instance?10304:2368;
        if(m.equals("getBaseY"))return instance?5248:5120;
        if(m.equals("isInstance"))return instance;
        if(m.equals("getInstanceTemplateChunks")) {
            int[][][] chunks=new int[4][13][13];
            for(int x=0;x<13;x++)for(int y=0;y<13;y++)chunks[0][x][y]=((2368/8+x)<<14)|((5056/8+y)<<3);
            return chunks;
        }
        return null;
    });
    final Client client=proxy(Client.class,(m,a)-> {
        switch(m) {
            case "getTickCount":return tick;
            case "getWorld":return 630;
            case "getWorldView":case "getTopLevelWorldView":return view;
            case "getLocalPlayer":return player;
            case "getGameState":return GameState.LOGGED_IN;
            case "isClientThread":return true;
            case "getBoostedSkillLevel":return a[0]==Skill.PRAYER?prayer:99;
            case "getRealSkillLevel":return 99;
            case "getEnergy":return 10000;
            case "getVarbitValue":return varbits.getOrDefault((Integer)a[0],0);
            case "getWidget":return widget;
            case "menuAction":menus.add(Arrays.asList(a.clone()));return null;
            default:return null;
        }
    });
    static <T>T proxy(Class<T> type,BiFunction<String,Object[],Object> answer) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(p,m,a)-> {
            Object result=answer.apply(m.getName(),a==null?new Object[0]:a);
            if(result!=null)return result;
            Class<?> r=m.getReturnType();if(!r.isPrimitive())return null;
            if(r==boolean.class)return false;if(r==void.class)return null;
            if(r==long.class)return 0L;if(r==double.class)return 0D;if(r==float.class)return 0F;
            if(r==byte.class)return (byte)0;if(r==short.class)return (short)0;if(r==char.class)return (char)0;return 0;
        }));
    }
    FcFrame frame(int tick,List<Mob> mobs,Protection jad)throws Exception {
        this.tick=tick;
        Constructor<FcFrame> ctor=FcFrame.class.getDeclaredConstructor(Client.class,WorldView.class,Player.class,
            List.class,CollisionGrid.class,Protection.class,Map.class,int.class,Set.class,Map.class,boolean.class);
        ctor.setAccessible(true);
        return ctor.newInstance(client,view,player,mobs,new CollisionGrid(new int[104][104]),jad,Map.of(),7,Set.of(),Map.of(),false);
    }
    static FcFrame frame(int tick,int itemId)throws Exception {
        FirecapeTestClient c=new FirecapeTestClient();FcFrame f=c.frame(tick,List.of(),Protection.NONE);
        set(f,"inventory",List.of(new FcFrame.ItemSlot(0,itemId,1,itemId==3024?"Super restore(4)":"Super restore(3)",List.of("Drink"))));
        set(f,"containersReady",true);return f;
    }
    static void set(Object target,String name,Object value)throws Exception {
        Class<?> type=target.getClass();while(type!=null)try {Field f=type.getDeclaredField(name);f.setAccessible(true);f.set(target,value);return;}catch(NoSuchFieldException e){type=type.getSuperclass();}
        throw new NoSuchFieldException(name);
    }
}
