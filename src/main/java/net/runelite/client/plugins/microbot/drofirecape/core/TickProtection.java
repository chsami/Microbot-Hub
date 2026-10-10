/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape.core;

import java.util.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;

/**
 * Prayer-only, next-server-tick forecast. It never changes a route, target or lure.
 * Ordinary attack animations are evidence of a COMPLETED launch, not permission
 * to react to that launch. Two matching intervals are required before using a
 * cooldown gap. A missing expected attack is READY, never extrapolated modulo a
 * period through a line-of-sight break.
 */
public final class TickProtection {
    private final boolean continuousCadence;
    public TickProtection(){this(false);}
    public TickProtection(boolean continuousCadence){this.continuousCadence=continuousCadence;}
    public static final class Decision {
        public final int tick, dueMask, uncertainMask;
        public final Protection protection;
        /** An observed/current Jad demand; movement must never override it. */
        public final Protection jadProtection;
        /** A calibrated one-tick healer gap must return to this guard next tick. */
        public final Protection jadReturnProtection;
        public final String reason;
        public Decision(int tick,Protection protection,int dueMask,int uncertainMask,String reason) {
            this(tick,protection,dueMask,uncertainMask,reason,Protection.NONE,Protection.NONE);
        }
        private Decision(int tick,Protection protection,int dueMask,int uncertainMask,String reason,
            Protection jadProtection,Protection jadReturnProtection) {
            this.tick=tick;this.protection=protection;this.dueMask=dueMask;
            this.uncertainMask=uncertainMask;this.reason=reason;this.jadProtection=jadProtection;
            this.jadReturnProtection=jadReturnProtection;
        }
        public boolean conflict(){return Integer.bitCount(dueMask)>1;}
    }
    private static final class Sample {
        Kind kind;Protection style;int last=-1,matches;boolean continuous;
    }
    private static final class Seen {
        final Kind kind;final Protection style;
        Seen(Kind kind,Protection style){this.kind=kind;this.style=style;}
    }
    // Written and read only on the client thread by the adapter.
    private final Map<Integer,Sample> samples=new HashMap<>();
    private final Map<Integer,Seen> pending=new HashMap<>();
    private int lastFrameTick=-1;
    public void reset(){samples.clear();pending.clear();lastFrameTick=-1;}
    public void removed(int index){samples.remove(index);pending.remove(index);}
    public void invalidateCadence(){for(Sample s:samples.values()){s.matches=0;s.continuous=false;}}

    /** Events are labelled when the following GameTick is captured, not by a
     * getTickCount() value read later during a 20ms client tick. */
    public void animation(int index,Kind kind,int animation) {
        Protection style=attackStyle(kind,animation);
        if(style!=null)pending.put(index,new Seen(kind,style));
    }
    public void beginTick(int tick,List<Mob> live) {
        if(tick==lastFrameTick)return;
        if(lastFrameTick>=0&&tick!=lastFrameTick+1)invalidateCadence();
        Set<Integer> present=new HashSet<>();for(Mob m:live)present.add(m.index());
        samples.keySet().retainAll(present);
        for(Map.Entry<Integer,Seen> e:pending.entrySet())if(present.contains(e.getKey())) {
            Seen seen=e.getValue();Sample s=samples.computeIfAbsent(e.getKey(),k->new Sample());
            if(s.last!=tick) {
                s.matches=(!continuousCadence||s.continuous)&&s.kind==seen.kind&&(s.style==seen.style||seen.kind==Kind.JAD)&&s.last>=0
                    &&tick-s.last==seen.kind.speed?s.matches+1:0;
                s.kind=seen.kind;s.style=seen.style;s.last=tick;s.continuous=true;
            }
        }
        pending.clear();lastFrameTick=tick;
    }
    /** Verified attack animations only. Healing/defence animations never seed a clock.
     * Source: Microbot 2.6.24 gameval/AnimationID.java, FIREBAT/LAVABEAST and
     * MAGMAQURIS/LIZARD_CLERIC/IGNIFERUM/LORDMAGMUS attack constants.
     * Unverified healer animations intentionally use conservative contact protection.
     */
    public static Protection attackStyle(Kind kind,int animation) {
        return AttackClock.animationWithTiny(kind,animation);
    }

    /** Called once for the real captured scene, never for a look-ahead snapshot.
     * A forecast must not invalidate the clock it is consulting. Reacquiring LOS
     * requires fresh attack evidence, not a modulo-four guess across the gap. */
    public void spatialEvidence(Snapshot scene) {
        for(Mob mob:scene.mobs()) {
            Sample clock=samples.get(mob.index());
            if(clock!=null&&CombatPlanner.threats(scene.grid(),mob,scene.player(),scene.jadStyle())==0) {
                clock.matches=0;clock.continuous=false;
            }
        }
    }

    public Decision choose(Snapshot s,boolean moving,Protection held,Protection spawnGuard) {
        return choose(s,moving,held,spawnGuard,2);
    }
    /** Direct widget input needs no cursor-return tick. Forecast the next launch, never its projectile impact. */
    public Decision chooseNative(Snapshot s,boolean moving,Protection held,Protection spawnGuard) {
        return choose(s,moving,held,spawnGuard,1);
    }
    /** Pure transport uses the same two-interval evidence rule for every NPC.
     * A baby spawning or swinging once is not a calibrated attack cycle. */
    public Decision chooseNativePure(Snapshot s,boolean moving,Protection held,Protection spawnGuard) {
        return choose(s,moving,held,spawnGuard,1,true);
    }
    private Decision choose(Snapshot s,boolean moving,Protection held,Protection spawnGuard,int magicLead) {
        return choose(s,moving,held,spawnGuard,magicLead,false);
    }
    private Decision choose(Snapshot s,boolean moving,Protection held,Protection spawnGuard,int magicLead,boolean earlyTiny) {
        if(s.mobs().isEmpty())return new Decision(s.tick(),spawnGuard,
            CombatPlanner.bit(spawnGuard),0,spawnGuard==Protection.NONE?"No incoming attack":"Pre-arm next wave");
        List<Tile> positions=positions(s,moving);
        Map<Integer,Integer> masks=new HashMap<>();
        for(Tile p:positions) {
            List<Mob> after=CombatPlanner.advance(s.grid(),s.mobs(),p,s.jadStyle());
            List<Mob> approaching=CombatPlanner.advance(s.grid(),after,p,s.jadStyle());
            for(int i=0;i<s.mobs().size();i++) {
                Mob m=s.mobs().get(i);
                int mask=CombatPlanner.threats(s.grid(),m,p,s.jadStyle())
                    |CombatPlanner.threats(s.grid(),after.get(i),p,s.jadStyle());
                Sample clock=samples.get(m.index());
                // First contact has no cadence to react to. Leave a full input
                // tick before an approaching ranged NPC can launch, including
                // a delayed/batched GameTick at the edge of its attack range.
                if((m.kind()==Kind.MAGER||m.kind()==Kind.RANGER)
                    &&(clock==null||clock.matches<2||clock.last+m.kind().speed<=s.tick()))
                    mask|=CombatPlanner.threats(s.grid(),approaching.get(i),p,s.jadStyle());
                masks.merge(m.index(),mask,(a,b)->a|b);
            }
        }
        int[] weights=new int[4];int due=0,unknown=0;Protection jadLock=Protection.NONE,jadReturn=Protection.NONE;
        boolean healerContact=s.mobs().stream().anyMatch(m->m.kind()==Kind.HEALER&&m.attackingPlayer()
            &&(masks.getOrDefault(m.index(),0)&CombatPlanner.bit(Protection.MELEE))!=0);
        boolean exposedMage=false,exposedRanger=false,meleeDue=false,bigMeleeDue=false,rangerDue=false;
        int[] exposedWeights=new int[4];
        for(Mob m:s.mobs()) {
            int mask=masks.getOrDefault(m.index(),0);Sample clock=samples.get(m.index());
            if(m.kind()==Kind.JAD) {
                // Retain an observed Jad wind-up even after moving out of LOS.
                // There is no attempt to predict his next random attack style.
                int age=clock==null?-1:s.tick()-clock.last;
                if(!continuousCadence&&clock!=null&&mask==0)clock.matches=0;
                boolean contact=(mask&CombatPlanner.bit(Protection.MELEE))!=0;
                boolean healerGap=clock!=null&&clock.kind==Kind.JAD&&age==6&&clock.matches>=2&&m.attackingPlayer()&&!moving&&!contact
                    &&mask!=0&&healerContact&&(clock.style==Protection.MAGIC||clock.style==Protection.RANGE);
                if(age>=0&&age<=5) {
                    jadLock=clock.style;mask=CombatPlanner.bit(jadLock);
                } else if(healerGap) {
                    // Preserve the complete observed wind-up guard (ages 0-5).
                    // Borrow only age 6 of two verified eight-tick intervals;
                    // age 7 rearms before the next random wind-up. Never repeat
                    // this gap modulo eight through an unobserved/late attack.
                    jadReturn=clock.style;mask=0;
                } else if(mask!=0) {
                    Protection arm=s.meleeMode()&&contact?Protection.MELEE:
                        s.jadStyle()==Protection.NONE?Protection.MAGIC:s.jadStyle();
                    jadLock=arm;mask=CombatPlanner.bit(arm);
                }
                if(mask!=0){due|=mask;add(weights,mask,10000);}
                continue;
            }
            // Include a legal next-step attack lane. Current LOS alone cannot
            // prove safety when either the player or an attacker is approaching.
            if(mask==0){if(!continuousCadence&&clock!=null)clock.matches=0;continue;}
            boolean mageMelee=!moving&&CaveSafety.observedMageMelee(s,m);
            if(mageMelee)mask=CombatPlanner.bit(Protection.MELEE);
            add(exposedWeights,mask,m.kind().maxHit);
            // Every verified melee phase can use a clear ranged/magic gap.
            // An unknown small blob must not guess away active magic protection.
            if(m.kind()==Kind.MAGER&&(mask&CombatPlanner.bit(Protection.MAGIC))!=0)exposedMage=true;
            if(m.kind()==Kind.RANGER&&(mask&CombatPlanner.bit(Protection.RANGE))!=0)exposedRanger=true;
            boolean known=clock!=null&&clock.kind==m.kind()
                &&clock.matches>=2&&(!continuousCadence||clock.continuous)
                &&m.attackingPlayer()
                &&clock.last>=0&&clock.last<=s.tick()&&s.tick()<clock.last+m.kind().speed;
            // Walking alone does not change a continuous four-tick attack phase.
            // A possible contact-style change or an LOS break does invalidate it.
            if(known&&moving&&(mask!=CombatPlanner.bit(clock.style)
                ||CombatPlanner.threats(s.grid(),m,s.player(),s.jadStyle())==0))known=false;
            // A ranged monster's melee animation does not establish a safe gap
            // for its ranged style after it steps away from contact.
            if(known&&(m.kind()==Kind.MAGER||m.kind()==Kind.RANGER)&&clock.style!=m.kind().protection)known=false;
            // The 0.3.22 recording returned to Magic 461–521 ms into the last
            // input tick and still lost protection at the launch. Reserve an
            // extra tick for that return; verified earlier gaps still flick.
            int lead=m.kind()==Kind.MAGER&&clock!=null&&clock.style==Protection.MAGIC?magicLead:1;
            if(known&&s.tick()+lead<clock.last+m.kind().speed)continue;
            if(!known)unknown|=mask;
            due|=mask;add(weights,mask,m.kind().maxHit);
            if((known||m.kind()==Kind.MELEER||mageMelee)&&(mask&CombatPlanner.bit(Protection.MELEE))!=0)meleeDue=true;
            if(m.kind()==Kind.MELEER&&(mask&CombatPlanner.bit(Protection.MELEE))!=0)bigMeleeDue=true;
            if(m.kind()==Kind.RANGER&&(mask&CombatPlanner.bit(Protection.RANGE))!=0)rangerDue=true;
        }
        Protection best=Protection.NONE;int score=0;
        // Fixed tie order favors the higher-consequence ranged styles. Hold on
        // exact ordinary ties only after a positive-risk style has been found.
        for(Protection p:new Protection[]{Protection.MAGIC,Protection.RANGE,Protection.MELEE})
            if(weights[p.ordinal()]>score){score=weights[p.ordinal()];best=p;}
        if(held!=null&&held!=Protection.NONE&&score>0&&weights[held.ordinal()]==score)best=held;
        // Keep an exposed protection selected through an otherwise clear gap;
        // an already-active overhead needs no extra OFF/ON cursor work.
        if(best==Protection.NONE)for(Protection p:new Protection[]{Protection.MAGIC,Protection.RANGE,Protection.MELEE})
            if(exposedWeights[p.ordinal()]>score){score=exposedWeights[p.ordinal()];best=p;}
        boolean retainMage=exposedMage&&((due&CombatPlanner.bit(Protection.MAGIC))!=0||!rangerDue&&!meleeDue);
        boolean retainRange=exposedRanger&&((due&CombatPlanner.bit(Protection.RANGE))!=0||!meleeDue);
        if(retainMage)best=Protection.MAGIC;
        else if(bigMeleeDue)best=Protection.MELEE;
        else if(retainRange)best=Protection.RANGE;
        else if(meleeDue)best=Protection.MELEE;
        if(jadLock!=Protection.NONE)best=jadLock;
        String reason=best==Protection.NONE?"Clear cooldown / blocked attackers":
            jadLock!=Protection.NONE?"Observed Jad wind-up":
            jadReturn!=Protection.NONE?"Healer melee in verified Jad gap; re-arm next tick":
            retainMage?"Magic due / uncertain, or no other major attack due":
            bigMeleeDue?"Threatening big melee wins Range conflict":
            retainRange?"Range due / uncertain, or no verified melee attack due":
            meleeDue?"Melee due inside verified ranged/magic cooldown gap":
            unknown!=0?"Conservative first / uncertain attack":"Prepared before next attack";
        if(Integer.bitCount(due)>1)reason+="; conflicting styles";
        return new Decision(s.tick(),best,due,unknown,reason,jadLock,jadReturn);
    }
    private static void add(int[] weights,int mask,int amount) {
        for(Protection p:Protection.values())if((mask&CombatPlanner.bit(p))!=0)weights[p.ordinal()]+=amount;
    }
    /** Cursor work may use only a verified interval before the next major-style switch.
     * Inventory/spellbook work must not reset or guess these independent attack clocks.
     */
    public int inputWindowTicks(Snapshot s,boolean moving,Protection held) {
        int styles=0;
        for(Mob m:s.mobs())
            styles|=CombatPlanner.threats(s.grid(),m,s.player(),s.jadStyle());
        if(Integer.bitCount(styles)<2)return 4;
        if(moving||held==Protection.NONE)return 0;
        int other=styles&~CombatPlanner.bit(held);
        for(int ahead=0;ahead<4;ahead++) {
            // Do not forecast cursor availability beyond an expected attack
            // without observing it. The live clock may instead stall or change.
            if(ahead>0)for(Mob m:s.mobs()) {
                Sample clock=samples.get(m.index());
                if(clock!=null&&clock.matches>=2&&clock.last+m.kind().speed<=s.tick()+ahead)return ahead;
            }
            Snapshot future=new Snapshot(s.tick()+ahead,s.player(),s.grid(),s.mobs(),s.runEnergy(),s.running(),
                s.weaponRange(),s.jadStyle(),s.meleeMode());
            Decision next=choose(future,false,held,Protection.NONE);
            if(next.protection!=held||(next.uncertainMask&other)!=0)return ahead;
        }
        return 4;
    }
    /** All legal next-tick player positions while a movement command is active.
     * This is a conservative PRAYER envelope, not a path command or new lure.
     */
    private static List<Tile> positions(Snapshot s,boolean moving) {
        LinkedHashSet<Tile> all=new LinkedHashSet<>();all.add(s.player());
        if(!moving)return new ArrayList<>(all);
        Set<Tile> edge=new LinkedHashSet<>(all);
        int distance=s.running()&&s.runEnergy()>0?2:1;
        for(int n=0;n<distance;n++) {
            Set<Tile> next=new LinkedHashSet<>();
            for(Tile from:edge)for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++) {
                if(dx==0&&dy==0)continue;Tile to=from.add(dx,dy);
                if(!s.grid().step(from,to))continue;
                boolean occupied=false;for(Mob m:s.mobs())if(m.occupies(to)){occupied=true;break;}
                if(!occupied&&all.add(to))next.add(to);
            }
            edge=next;
        }
        return new ArrayList<>(all);
    }
}
