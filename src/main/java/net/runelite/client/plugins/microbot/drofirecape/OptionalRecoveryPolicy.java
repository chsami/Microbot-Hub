/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape;

import java.util.Locale;

/** Paused-wave supplies use actual levels and acknowledged doses, never elapsed recovery guesses. */
final class OptionalRecoveryPolicy {
    static final class Decision {
        final FcFrame.ItemSlot item;
        final String action,status;
        final boolean ready;
        Decision(FcFrame.ItemSlot item,String action,String status,boolean ready){this.item=item;this.action=action;this.status=status;this.ready=ready;}
    }
    private static Decision waitFor(String reason){return new Decision(null,"",reason,false);}
    private static Decision dose(FcFrame.ItemSlot item,String action,String reason){return item==null?waitFor(reason+"; required supply unavailable"):
        new Decision(item,action,reason,false);}
    private static FcFrame.ItemSlot potion(FcFrame f,String prefix){return f.inventory.stream()
        .filter(i->i.name().toLowerCase(Locale.ROOT).startsWith(prefix)&&i.hasAction("Drink")).findFirst().orElse(null);}
    static int hpTarget(int base,boolean overbrew){return base+(overbrew?base*15/100+2:0);}
    static boolean statsDrained(FcFrame f,boolean melee,boolean magic){return f.defence<f.baseDefence
        ||(melee?f.attack<f.baseAttack||f.strength<f.baseStrength:f.ranged<f.baseRanged)||magic&&f.magic<f.baseMagic;}
    static Decision choose(FcFrame f,boolean melee,boolean sweets,boolean overbrew,int energyTarget,int prayerPercent,
                           boolean boost,boolean magic,int wave,int brewDebt,boolean pending) {
        return choose(f,melee,sweets,overbrew,energyTarget,prayerPercent,boost,magic,wave,brewDebt,pending,false);
    }
    /** Pure runs keep fighting when supplies are exhausted. Missing items are
     * observed inventory state; an outstanding click still requires its ACK. */
    static Decision choose(FcFrame f,boolean melee,boolean sweets,boolean overbrew,int energyTarget,int prayerPercent,
                           boolean boost,boolean magic,int wave,int brewDebt,boolean pending,boolean continueDepleted) {
        if(pending)return waitFor("Confirming recovery supply consumption");
        if(!f.containersReady||f.stale())return waitFor("Waiting for fresh recovery inventory");
        FcFrame.ItemSlot sweet=sweets?f.inventory.stream().filter(i->FcSupplyPolicy.sweet(i.name())&&i.hasAction("Eat")).findFirst().orElse(null):null;
        boolean energy=f.energyPercent()<energyTarget;
        // Complete run recovery before spending the final temporary HP boost.
        if(sweet!=null&&(f.hp<f.maxHp||energy))return dose(sweet,"Eat","Recovering HP / run with purple sweets");
        FcFrame.ItemSlot restore=potion(f,"super restore("),brew=potion(f,"saradomin brew(");
        FcFrame.ItemSlot prayerDose=restore!=null?restore:potion(f,"prayer potion(");
        // Natural healing and sweets cannot produce boosted HP. Do not spend a
        // brew just for its optional boost when no restore can repair its drain.
        int target=hpTarget(f.maxHp,overbrew&&(!continueDepleted||brew!=null&&restore!=null));
        boolean heal=f.hp<target,drained=statsDrained(f,melee,magic);
        boolean depleted=continueDepleted&&(f.hp<hpTarget(f.maxHp,overbrew)||drained
            ||f.prayer*100<f.maxPrayer*prayerPercent);
        // An acknowledged brew at <=50% prayer is followed by a restore. With
        // higher prayer, finish at most three brews before repairing real levels.
        if((drained&&(brewDebt>=3||!heal)||brewDebt>0&&f.prayer*2<=f.maxPrayer)
            &&(restore!=null||!continueDepleted))
            return dose(restore,"Drink","Restoring levels after recovery brews");
        if(f.prayer<=3&&(prayerDose!=null||!continueDepleted))return dose(prayerDose,"Drink","Restoring exhausted prayer");
        if(heal) {
            if(energy&&f.hp>=f.maxHp)return waitFor("Recovering run energy "+f.energyPercent()+"% / "+energyTarget+"% before overbrew");
            FcFrame.ItemSlot food=f.hp<f.maxHp?f.inventory.stream().filter(i->i.hasAction("Eat")&&!FcSupplyPolicy.sweet(i.name())).findFirst().orElse(null):null;
            if(food!=null)return dose(food,"Eat","Recovering HP to "+target);
            if(brew!=null||!continueDepleted)return dose(brew,"Drink","Recovering HP to "+target);
        }
        if(energy)return waitFor("Recovering run energy "+f.energyPercent()+"% / "+energyTarget+"%");
        if(drained&&(restore!=null||!continueDepleted))return dose(restore,"Drink","Restoring actual combat / Magic levels");
        if(f.prayer*100<f.maxPrayer*prayerPercent&&(prayerDose!=null||!continueDepleted))return dose(prayerDose,"Drink","Restoring prayer to "+prayerPercent+"%");
        if(boost) {
            if(!melee&&f.ranged<=f.baseRanged) {
                if(!FcSupplyPolicy.rangedDoseAllowed(wave,f.ranged,f.baseRanged,FcSupplyPolicy.rangedDoses(f.inventory)))
                    return ready(depleted,"; optional ranged boost skipped: reserve / depleted potions");
                return dose(f.inventory.stream().filter(i->FcSupplyPolicy.rangedPotion(i.name())&&i.hasAction("Drink")).findFirst().orElse(null),"Drink","Final ranged boost after recovery");
            }
            if(melee&&(f.attack<=f.baseAttack||f.strength<=f.baseStrength)) {
                FcFrame.ItemSlot combat=potion(f,"super combat potion(");if(combat==null)combat=potion(f,"combat potion(");
                if(combat==null&&f.attack<=f.baseAttack){combat=potion(f,"super attack(");if(combat==null)combat=potion(f,"attack potion(");}
                if(combat==null&&f.strength<=f.baseStrength){combat=potion(f,"super strength(");if(combat==null)combat=potion(f,"strength potion(");}
                if(combat!=null)return dose(combat,"Drink","Final melee boost after recovery");
            }
        }
        return ready(depleted,"");
    }
    private static Decision ready(boolean depleted,String detail){return new Decision(null,"",
        (depleted?"Recovery supplies depleted; continuing with available HP / prayer":"Recovery targets observed")+detail,true);}
}
