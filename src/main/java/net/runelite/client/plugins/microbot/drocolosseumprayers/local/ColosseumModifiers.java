package net.runelite.client.plugins.microbot.drocolosseumprayers.local;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;
import static net.runelite.client.plugins.microbot.drocolosseumprayers.local.ColosseumSnapshot.*;

final class ColosseumModifiers
{
    private final Map<String,Integer> active=new HashMap<>();
    private Map<String,Integer> selected=Map.of();
    private boolean visible,continued;

    private volatile Map<String,Integer> published=Map.of();

    Map<String,Integer> active(){return published;}
    void reset(){active.clear();selected=Map.of();visible=continued=false;published=Map.of();}
    void selected(JsonObject widget){selected=ColosseumTables.modifierLevels(widget);continued=false;}
    void continued(){continued=true;}
    void rejectedSelection(){selected=Map.of();continued=false;}
    Map<String,Integer> observe(JsonObject raw)
    {
        boolean nowVisible=false;
        for(JsonElement e:array(raw,"widgets"))
        {
            if(!e.isJsonObject())continue;
            JsonObject widget=e.getAsJsonObject();int id=integer(widget,"id",-1);
            if(id<56688655||id>56688657)continue;
            nowVisible=true;

            ColosseumTables.modifierLevels(widget).forEach((name,level)->active.merge(name,level-1,Math::max));
        }
        if(visible&&!nowVisible&&continued)
            selected.forEach((name,level)->active.merge(name,level,Math::max));
        if(nowVisible!=visible){selected=Map.of();continued=false;}
        visible=nowVisible;
        JsonObject nativeLevels=object(object(raw,"player"),"modifiers");
        for(String name:new String[]{"frailty","myopia"})
        {
            int level=integer(nativeLevels,name,-1);
            if(level>=0&&level<=3)active.put(name,level);
        }
        published=Map.copyOf(active);
        return published;
    }
}
