/* Copyright (c) 2026, DRO (droplugins). SPDX-License-Identifier: BSD-2-Clause */
package net.runelite.client.plugins.microbot.drofirecape;

import java.util.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;

/** Ties each hit on the player to the NPC attack that caused it, from local evidence only: the
 * attack animation tick, the projectile that just landed on the player, and the protection
 * observed on each game tick. Prayer is judged on the attack tick: the 2026-10-20 recordings
 * show Fight Caves damage is decided there, not on the landing tick. Diagnostics only; nothing
 * here changes combat, prayer or input timing. */
final class FcHitAttribution {
    static final int KEEP_TICKS=12,LANDED_CYCLES=20,MELEE_TICKS=1,SHOT_MIN=2,SHOT_MAX=6;

    static final class Attack {
        final int tick,index;final Kind kind;final Protection style;boolean consumed;
        Attack(int tick,int index,Kind kind,Protection style){this.tick=tick;this.index=index;this.kind=kind;this.style=style;}
    }
    private static final class Shot {
        final int source,endCycle;
        Shot(int source,int endCycle){this.source=source;this.endCycle=endCycle;}
    }
    static final class Hit {
        final int tick,amount,type,attacker,attackTick,candidates,simultaneous;final boolean otherStyleProtected;
        final Kind kind;final Protection style,prayerAtAttack,prayerAtHit;final String method;
        Hit(int tick,int amount,int type,Attack attack,String method,int candidates,int simultaneous,boolean otherStyleProtected,
            Protection prayerAtAttack,Protection prayerAtHit) {
            this.otherStyleProtected=otherStyleProtected;this.tick=tick;this.amount=amount;this.type=type;this.method=method;this.candidates=candidates;
            this.simultaneous=simultaneous;this.prayerAtAttack=prayerAtAttack;this.prayerAtHit=prayerAtHit;
            attacker=attack==null?-1:attack.index;attackTick=attack==null?-1:attack.tick;
            kind=attack==null?null:attack.kind;style=attack==null?null:attack.style;
        }
        boolean attributed(){return attacker>=0;}
        int delay(){return attributed()?tick-attackTick:-1;}
        boolean protectedAtAttack(){return style!=null&&style==prayerAtAttack;}
        /** Cause in one word for per-wave totals. */
        String cause() {
            if(!attributed())return "unattributed";
            if(protectedAtAttack())return amount>0?"protected-but-hit":"blocked";
            if(otherStyleProtected)return "simultaneous";
            return prayerAtAttack==null?"prayer-unknown":prayerAtAttack==Protection.NONE?"prayer-off":"wrong-prayer";
        }
        Map<String,Object> values(int wave) {
            Map<String,Object> v=new LinkedHashMap<>();
            v.put("record","firecape_hit");v.put("tick",tick);v.put("wave",wave);v.put("amount",amount);v.put("hitsplatType",type);
            v.put("attacker",attacker);v.put("attackerKind",kind==null?"UNKNOWN":kind.name());
            v.put("style",style==null?"UNKNOWN":style.name());v.put("attackTick",attackTick);v.put("delay",delay());
            v.put("prayerAtAttack",name(prayerAtAttack));v.put("prayerAtHit",name(prayerAtHit));
            v.put("cause",cause());v.put("method",method);v.put("candidates",candidates);v.put("simultaneous",simultaneous);
            return v;
        }
        @Override public String toString() {
            return "player-hit tick="+tick+" amount="+amount+" type="+type+" attacker="+attacker+" kind="+(kind==null?"UNKNOWN":kind)
                +" style="+(style==null?"UNKNOWN":style)+" attackTick="+attackTick+" delay="+delay()+" prayerAtAttack="+name(prayerAtAttack)
                +" prayerAtHit="+name(prayerAtHit)+" cause="+cause()+" method="+method+" candidates="+candidates+" simultaneous="+simultaneous;
        }
        private static String name(Protection p){return p==null?"UNKNOWN":p.name();}
    }

    private final ArrayDeque<Attack> attacks=new ArrayDeque<>();
    private final ArrayDeque<Shot> shots=new ArrayDeque<>();
    private final Map<Integer,Protection> prayer=new HashMap<>();
    private int lastTick=Integer.MIN_VALUE;

    synchronized void reset(){attacks.clear();shots.clear();prayer.clear();lastTick=Integer.MIN_VALUE;}
    /** Protection observed on this game tick (the state the server's NPC attacks on this tick see). */
    synchronized void tick(int tick,Protection observed) {
        prayer.put(tick,observed);lastTick=Math.max(lastTick,tick);
        prayer.keySet().removeIf(t->t<tick-KEEP_TICKS);
        attacks.removeIf(a->a.tick<tick-KEEP_TICKS);
    }
    synchronized Attack attack(int tick,int index,Kind kind,Protection style) {
        Attack a=new Attack(tick,index,kind,style);attacks.add(a);return a;
    }
    /** A projectile targeting the player; repeated reports of the same shot are merged. */
    synchronized void shot(int source,int endCycle,int gameCycle) {
        shots.removeIf(s->s.endCycle<gameCycle-4*LANDED_CYCLES||s.source==source&&s.endCycle==endCycle);
        shots.add(new Shot(source,endCycle));
    }
    synchronized Hit hit(int tick,int gameCycle,int amount,int type) {
        Protection atHit=prayer.containsKey(tick)?prayer.get(tick):prayer.get(lastTick);
        // 1. The shot that just landed names its source exactly.
        Shot landed=null;
        for(Shot s:shots)if(Math.abs(s.endCycle-gameCycle)<=LANDED_CYCLES
            &&(landed==null||Math.abs(s.endCycle-gameCycle)<Math.abs(landed.endCycle-gameCycle)))landed=s;
        if(landed!=null) {
            Attack best=null;
            for(Attack a:attacks)if(a.index==landed.source&&a.style!=Protection.MELEE&&a.tick<=tick&&!a.consumed
                &&(best==null||a.tick<best.tick))best=a;
            if(best!=null){shots.remove(landed);return resolve(tick,amount,type,best,"projectile",1,atHit);}
        }
        // 2. Melee lands on its attack tick.
        List<Attack> melee=new ArrayList<>();
        for(Attack a:attacks)if(a.style==Protection.MELEE&&!a.consumed&&tick-a.tick>=0&&tick-a.tick<=MELEE_TICKS)melee.add(a);
        List<Attack> shotsInFlight=new ArrayList<>();
        for(Attack a:attacks)if(a.style!=Protection.MELEE&&!a.consumed&&tick-a.tick>=SHOT_MIN&&tick-a.tick<=SHOT_MAX)shotsInFlight.add(a);
        List<Attack> pool=!melee.isEmpty()?melee:shotsInFlight;
        if(pool.isEmpty())return new Hit(tick,amount,type,null,"none",0,0,false,null,atHit);
        // Fight Caves protection blocks the whole hit: damage points at an unprotected attack,
        // a zero at a protected one. Oldest first among equals, since shots land in order.
        Attack best=null;
        for(Attack a:pool) {
            boolean protectedA=a.style==prayer.get(a.tick);
            boolean fits=amount>0?!protectedA:protectedA;
            if(best==null)best=a;
            else {
                boolean bestFits=amount>0?best.style!=prayer.get(best.tick):best.style==prayer.get(best.tick);
                if(fits&&!bestFits||fits==bestFits&&a.tick<best.tick)best=a;
            }
        }
        return resolve(tick,amount,type,best,melee.isEmpty()?"window":"melee",pool.size(),atHit);
    }
    private Hit resolve(int tick,int amount,int type,Attack attack,String method,int candidates,Protection atHit) {
        attack.consumed=true;
        int simultaneous=0;boolean otherProtected=false;Protection then=prayer.get(attack.tick);
        for(Attack other:attacks)if(other!=attack&&other.tick==attack.tick&&other.style!=attack.style) {
            simultaneous++;otherProtected|=other.style==then;
        }
        return new Hit(tick,amount,type,attack,method,candidates,simultaneous,otherProtected,then,atHit);
    }
}
