package net.runelite.client.plugins.microbot.drocolosseumprayers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Colosseum modifiers as the game draws them: sprite per tier, the modifier-select script's id and the tier varbit.
 * Table from the Plugin Hub's Fortis Colosseum plugin (LlemonDuck, BSD 2-Clause), {@code util/Modifier.java}.
 */
final class ColosseumModifierIcons
{
    /** The modifier-select screen's init script: args[2..4] are the offers. */
    static final int SCRIPT_MODIFIER_SELECT_INIT=4931;

    static final class Info
    {
        final String key,title;final int[] sprites;final int id,levelVarbit;
        Info(String key,String title,int[] sprites,int id,int levelVarbit){this.key=key;this.title=title;this.sprites=sprites;this.id=id;this.levelVarbit=levelVarbit;}
        int sprite(int level){return sprites[Math.max(0,Math.min(sprites.length,level)-1)];}
        String name(int level){return levelVarbit<0||level<=1?title:title+(level==2?" (II)":" (III)");}
    }

    /** Keyed like the engine's modifier names, in the plugin's order. */
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

    /** Varbit holding the offer taken (1-3) when Minimus leaves; 0 for none. */
    static final int VARBIT_MODIFIER_SELECTED=9788;
    static final int MINIMUS=12808;
    /** The offers' keys in screen order (args[2..4]); null for an id the table lacks. */
    static List<String> offers(Object[] args)
    {
        List<String> out=new ArrayList<>(3);
        for(int i=2;i<=4;i++){String key=null;if(args.length>i&&args[i] instanceof Integer)for(Info info:ALL.values())if(info.id==(Integer)args[i])key=info.key;out.add(key);}
        return out;
    }
    /** The offer taken: the selected slot (1-3) among the offers, or null. */
    static String picked(List<String> offers,int selected)
    {return selected>=1&&selected<=offers.size()?offers.get(selected-1):null;}
    private ColosseumModifierIcons(){}
}
