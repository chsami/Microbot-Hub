package net.runelite.client.plugins.microbot.drocolosseumprayers.local;

import com.google.gson.*;
import java.util.*;
import static net.runelite.client.plugins.microbot.drocolosseumprayers.local.ColosseumSnapshot.*;

/** Offensive prayer is requested for the next attack, then disabled on its native receipt. */
final class ColosseumOffense
{
    private long attack=-1000,tick=-1;
    private int speed=5,animation=-1,weaponSpeed;
    void reset(){attack=-1000;tick=-1;speed=5;animation=-1;weaponSpeed=0;}
    void observe(ColosseumSnapshot f)
    {
        weaponSpeed=ColosseumGear.speed(f,f.weapon());
        boolean contiguous=tick==f.tick-1;
        if(tick>=0&&f.tick!=tick+1){attack=-1000;animation=-1;}
        boolean observed=false;
        for(JsonElement e:f.events)
        {
            JsonObject event=e.getAsJsonObject();
            if("anim".equals(string(event,"e",""))&&"player".equals(string(event,"actor","")))
            {int next=attackSpeed(integer(event,"anim",-1));if(next>0){speed=next;observed=true;}}
        }
        int next=attackSpeed(f.animation);
        // A positive animation in a resumed snapshot can already be underway.
        // Only a native event or a transition across consecutive snapshots establishes onset.
        if(contiguous&&f.animation!=animation&&next>0){speed=next;observed=true;}
        // Continuous blowpipe animation retains its two-tick phase. Projectile collections
        // can gain a late/overlapping projectile between those ticks (run 1, 4422-4423);
        // treating collection arrival as a new shot inverted every subsequent Rigour toggle.
        // Only the source's animation receipts establish or reset the offensive clock.
        if(observed)attack=f.tick;
        animation=f.animation;tick=f.tick;
    }
    boolean needed(ColosseumSnapshot f,boolean requestingAttack)
    {
        if(attack<0)return requestingAttack;
        long elapsed=f.tick-attack;
        // The blowpipe's animation repeats every two ticks without a new receipt: while it still plays, a re-attack
        // request keeps that phase and drops the prayer on the shot tick (server corpus: off on 2,045 of 2,045 such
        // requests, on for all 1,590 one tick before).
        if(requestingAttack&&f.animation==5061&&elapsed%speed!=speed-1)return false;
        if(requestingAttack&&elapsed>=speed-1)return true;
        return f.interacting>=0&&elapsed>=0&&elapsed%speed==speed-1;
    }
    /**
     * Player attack animations (rsprox seq table, cache 2740): bows incl. twisted bow, bofa and magic shortbow
     * (human_bow), crossbows, zaryte crossbow, blowpipe, chinchompas, venator; staff, wand and sceptre casts;
     * whip/tentacle, scythe, fang, rapier, swords, maces and the inquisitor's mace. Blocks, eating and drinking
     * (415, 424, 430, 4177, 829) are not attacks.
     */
    private static final Set<Integer> ATTACKS=Set.of(
        426,427,4230,7552,11465,9166,9168,5061,10656,2779,7618,9858,11057,
        711,727,1162,1167,7855,11431,811,1978,1979,7753,10092,9492,9493,
        1658,8056,9471,8145,386,390,391,392,393,395,396,401,7045,7046,7054,4503,422,423);
    /** Ticks between attacks for an attack animation: the worn weapon's speed, else the recorded constant. */
    private int attackSpeed(int animation)
    {
        // The replayed server corpus fixes these three.
        if(animation==1658)return 4;
        if(animation==5061)return 2;
        if(animation==10092||animation==1978||animation==1979)return 5;
        if(!ATTACKS.contains(animation))return 0;
        return weaponSpeed>0?weaponSpeed:5;
    }
}
