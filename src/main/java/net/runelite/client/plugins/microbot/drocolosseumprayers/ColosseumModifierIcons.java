package net.runelite.client.plugins.microbot.drocolosseumprayers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ColosseumModifierIcons
{
    static final class Info
    {
        final String key,title;final int[] sprites;final int id,levelVarbit;
        Info(String key,String title,int[] sprites,int id,int levelVarbit){this.key=key;this.title=title;this.sprites=sprites;this.id=id;this.levelVarbit=levelVarbit;}
        int sprite(int level){return sprites[Math.max(0,Math.min(sprites.length,level)-1)];}
        String name(int level){return levelVarbit<0||level<=1?title:title+(level==2?" (II)":" (III)");}
    }

    static final Map<String,Info> ALL=table();
    private static Map<String,Info> table()
    {
        Map<String,Info> m=new LinkedHashMap<>();
        add(m,"bees","Bees!",new int[]{5544,5559,5574},2,9791);
        add(m,"blasphemy","Blasphemy",new int[]{5538,5553,5568},4,9790);
        add(m,"doom","Doom",new int[]{5543,5558,5573},8,10681);
        add(m,"dynamic duo","Dynamic Duo",new int[]{5545},9,-1);
        add(m,"frailty","Frailty",new int[]{5541,5556,5571},12,9796);
        add(m,"mantimayhem","Mantimayhem",new int[]{5539,5554,5569},0,4588);
        add(m,"myopia","Myopia",new int[]{5547,5562,5577},11,9795);
        add(m,"reentry","Reentry",new int[]{5536,5551,5566},1,9792);
        add(m,"red flag","Red Flag",new int[]{5540},13,-1);
        add(m,"relentless","Relentless",new int[]{5535,5550,5565},5,9798);
        add(m,"solarflare","Solarflare",new int[]{5537,5552,5567},10,9797);
        add(m,"quartet","Quartet",new int[]{5546},6,-1);
        add(m,"totemic","Totemic",new int[]{5542},7,-1);
        add(m,"volatility","Volatility",new int[]{5534,5549,5564},3,9799);
        return java.util.Collections.unmodifiableMap(m);
    }
    private static void add(Map<String,Info> m,String key,String title,int[] sprites,int id,int levelVarbit)
    {m.put(key,new Info(key,title,sprites,id,levelVarbit));}

    private ColosseumModifierIcons(){}
}
