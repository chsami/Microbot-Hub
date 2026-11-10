package net.runelite.client.plugins.microbot.drocolosseumprayers;

import java.util.regex.Pattern;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.VarPlayer;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

final class ArenaHudTracker {
    private static final Pattern WAVE=Pattern.compile("(?i)^Wave\\s*:?\\s*(\\d{1,3})[.!]?$");

    private static final Pattern COLOUR_MARKER=Pattern.compile("@[a-z_]+@");
    private final ArenaProduct product;
    private final long startedAt;
    private int wave=-1;
    private long waveAt;
    ArenaHudTracker(ArenaProduct product,long now){this.product=product;startedAt=now;}
    void message(String text,long now){
        var m=WAVE.matcher(text==null?"":COLOUR_MARKER.matcher(text).replaceAll("").trim());
        if(m.matches()){int next=Integer.parseInt(m.group(1));if(next>0&&next!=wave){wave=next;waveAt=now;}}
    }

    static java.util.List<String> modifierTokens(java.util.Map<String,Integer> levels){
        java.util.List<String> out=new java.util.ArrayList<>();
        for(var info:ColosseumModifierIcons.ALL.values()){
            int level=levels.getOrDefault(info.key,0);
            if(level>0)out.add(info.key+":"+Math.min(info.sprites.length,level));
        }
        return out;
    }
    ArenaHud snapshot(Client client,String status){return snapshot(client,status,null,java.util.Map.of());}

    ArenaHud snapshot(Client client,String status,String state,java.util.Map<String,Integer> picked){
        java.util.Map<String,Integer> levels=new java.util.HashMap<>(picked);
        for(var info:ColosseumModifierIcons.ALL.values()){
            if(info.levelVarbit<0||!levels.containsKey(info.key))continue;
            int v=client.getVarbitValue(info.levelVarbit);if(v>0)levels.merge(info.key,Math.min(3,v),Math::max);
        }
        int brews=0,restores=0;
        for(var item:Rs2Inventory.all()){
            String name=item.getName();if(name==null)continue;
            int doses=doses(name,item.getQuantity());
            if(name.startsWith("Saradomin brew("))brews+=doses;
            if(name.startsWith("Super restore(")||name.startsWith("Blighted super restore("))restores+=doses;
        }
        return new ArenaHud(product,status,wave,client.getBoostedSkillLevel(Skill.HITPOINTS),
            client.getBoostedSkillLevel(Skill.PRAYER),Math.max(0,Math.min(100,client.getVarpValue(VarPlayer.SPECIAL_ATTACK_PERCENT)/10)),brews,restores,startedAt,waveAt,state,modifierTokens(levels));
    }
    static int doses(String name,int quantity){
        int open=name.lastIndexOf('('),close=name.lastIndexOf(')');
        if(open<0||close!=name.length()-1||close-open!=2||quantity<1)return 0;
        char n=name.charAt(open+1);return n>='1'&&n<='4'?(int)Math.min(Integer.MAX_VALUE,(long)(n-'0')*quantity):0;
    }
}
