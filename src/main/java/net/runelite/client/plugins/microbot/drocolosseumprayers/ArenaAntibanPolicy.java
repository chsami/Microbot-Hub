package net.runelite.client.plugins.microbot.drocolosseumprayers;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;

public final class ArenaAntibanPolicy implements AutoCloseable {
    private final Map<Field,Object> previous=new LinkedHashMap<>();
    private ActivityIntensity intensity;
    public synchronized void start(){
        if(!previous.isEmpty())return;
        intensity=Rs2Antiban.getActivityIntensity();
        for(Field f:Rs2AntibanSettings.class.getFields())try{
            if(Modifier.isStatic(f.getModifiers())&&!Modifier.isFinal(f.getModifiers())
                &&(f.getType()==boolean.class||f.getName().endsWith("Chance")))previous.put(f,f.get(null));
        }catch(IllegalAccessException e){throw new IllegalStateException(e);}
        apply();
    }
    public synchronized void apply(){
        if(previous.isEmpty())return;
        for(Field f:previous.keySet())try{if(f.getType()==boolean.class)f.setBoolean(null,false);else if(f.getType()==double.class)f.setDouble(null,0);}
        catch(IllegalAccessException e){throw new IllegalStateException(e);}
        Rs2AntibanSettings.antibanEnabled=true;Rs2AntibanSettings.naturalMouse=true;

        boolean preparing=false;
        Rs2AntibanSettings.simulateMistakes=preparing;
        Rs2Antiban.setActivityIntensity(preparing?ActivityIntensity.LOW:ActivityIntensity.EXTREME);
    }
    @Override public synchronized void close(){
        if(previous.isEmpty())return;
        Rs2Antiban.setActivityIntensity(intensity);
        for(var e:previous.entrySet())try{e.getKey().set(null,e.getValue());}catch(IllegalAccessException x){throw new IllegalStateException(x);}
        previous.clear();intensity=null;
    }
}
