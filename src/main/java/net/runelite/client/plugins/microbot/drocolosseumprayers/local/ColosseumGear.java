package net.runelite.client.plugins.microbot.drocolosseumprayers.local;

import com.google.gson.*;
import java.util.*;
import static net.runelite.client.plugins.microbot.drocolosseumprayers.local.ColosseumSnapshot.*;

/** The combat style of a weapon, for the offensive prayer. */
final class ColosseumGear
{
    enum Style { MAGE, RANGED, MELEE }

    private static final int WEAPON=3;
    private static final Map<Integer,Style> WEAPONS=new HashMap<>();
    static
    {
        weapons(Style.MELEE,90,22325,25736,25739);
        weapons(Style.MELEE,80,26219);
        weapons(Style.MELEE,75,28997);
        weapons(Style.MELEE,70,4151,12773,12774,12006);
        weapons(Style.MELEE,60,4587,20000);
        weapons(Style.MELEE,50,21742);
        weapons(Style.MELEE,45,11037);
        weapons(Style.MELEE,40,1333);
        weapons(Style.RANGED,90,20997);
        weapons(Style.RANGED,85,25865,25867,25884,25886,25888,25890,25892,25894,25896);
        weapons(Style.RANGED,80,11785);
        weapons(Style.RANGED,75,29000);
        weapons(Style.RANGED,70,21902,21012,12926);
        weapons(Style.RANGED,40,12788,861);
        weapons(Style.MAGE,80,21006);
        weapons(Style.MAGE,70,27624,28262,28260);
        weapons(Style.MAGE,60,6914);
        weapons(Style.MAGE,50,28988);
        weapons(Style.MAGE,20,4675);
    }
    private static void weapons(Style style,int rank,int... ids){for(int id:ids)WEAPONS.put(id,style);}

    static Style type(Item item)
    {
        if(item==null)return null;Style known=WEAPONS.get(item.id);if(known!=null)return known;String n=item.lower;
        if(n.contains("blue moon spear"))return Style.MAGE;
        if(n.contains("atlatl"))return Style.RANGED;
        if(n.contains("macuahuitl"))return Style.MELEE;
        if(n.contains("wand")||n.contains("staff")||n.contains("sceptre")||n.contains("trident")||n.contains("tumeken"))return Style.MAGE;
        if(n.contains("bow")||n.contains("blowpipe")||n.contains("chinchompa")||n.contains("ballista"))return Style.RANGED;
        if(n.contains("whip")||n.contains("abyssal tentacle")||n.contains("scimitar")||n.contains("sabre")||n.contains("granite hammer")||n.contains("sword")||n.contains("scythe")||n.contains("fang")||n.contains("rapier")||n.contains("halberd")||n.contains("dagger")||n.contains("spear")||n.contains("lance")||n.contains("mace")||n.contains("bludgeon")||n.contains("claws")||n.contains("axe"))return Style.MELEE;

        JsonObject g=item.gear;
        if(g==null||integer(g,"eq",-1)!=WEAPON)return null;
        if(integer(g,"range",0)>0)return Style.RANGED;
        if(integer(g,"magic",0)>=10)return Style.MAGE;
        return Math.max(integer(g,"stab",0),Math.max(integer(g,"slash",0),integer(g,"crush",0)))>0?Style.MELEE:null;
    }
}
