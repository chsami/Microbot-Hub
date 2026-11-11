/* Copyright (c) 2026, DRO (droplugins). SPDX-License-Identifier: BSD-2-Clause */
package net.runelite.client.plugins.microbot.drofirecape.core;

import java.util.Map;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;

/**
 * Ancient magic from a carried switch, decided from the current tile only (it never moves the player):
 * <ol>
 * <li>Magers and Jad are never spell targets, and no spell is cast during Jad.</li>
 * <li>Ice stops a monster coming at the player in the open (untrapped, unfrozen, not yet in contact): a bat
 * always first (its hits drain prayer even through Protect from Melee; user, 2026-11-06), then a big melee, then
 * a big blob, then a split blob with another split blob beside it (the user's manual play, live
 * 2026-11-06).</li>
 * <li>Blood only heals: it starts below {@link #BLOOD_START_PERCENT}% HP and stops at {@link #BLOOD_STOP_PERCENT}%. A clump (another
 * monster touching the target, inside the 3x3 splash) is preferred for the larger heal; otherwise a ranger, then
 * the nearest blob, melee or bat in reach, with no safespot needed. It is cast only from safety: at most one attack
 * style can reach the player's tile ({@link #reachingStyles}).</li>
 * </ol>
 */
public final class MagicTactics {
    private MagicTactics(){}
    public enum Element { ICE, BLOOD }
    public static final class Cast {
        public final Element element;
        public final int target;
        public final String reason;
        Cast(Element element,int target,String reason){this.element=element;this.target=target;this.reason=reason;}
        @Override public String toString(){return element+" on "+target+": "+reason;}
    }
    /** Every combat spell reaches ten tiles. */
    public static final int RANGE=10;
    /** Blood healing starts below this HP percentage and stops at {@link #BLOOD_STOP_PERCENT}% (user's manual
     * play, live 2026-11-06: Blood at 68/77, back to range at 74/77). */
    public static final int BLOOD_START_PERCENT=90,BLOOD_STOP_PERCENT=95;
    /** Blood healing state: starts below {@link #BLOOD_START_PERCENT}%, continues below {@link #BLOOD_STOP_PERCENT}%. */
    public static boolean healing(boolean active,int hp,int maxHp) {
        if(maxHp<=0||hp*100>=maxHp*BLOOD_STOP_PERCENT)return false;
        return active||hp*100<maxHp*BLOOD_START_PERCENT;
    }

    /**
     * @param healing Blood healing is wanted ({@link #healing})
     * @param frozenUntil last tick each NPC index is expected to stay frozen
     * @return the spell to cast now, or null to keep fighting with the main weapon
     */
    public static Cast decide(Snapshot s,boolean ice,boolean blood,boolean healing,Map<Integer,Integer> frozenUntil) {
        if(s==null||!ice&&!blood||s.mobs().isEmpty())return null;
        for(Mob m:s.mobs())if(m.kind()==Kind.JAD)return null;
        Snapshot spell=new Snapshot(s.tick(),s.player(),s.grid(),s.mobs(),s.runEnergy(),s.running(),RANGE,s.jadStyle()).atWave(s.wave());
        // Freezes only pay when a big monster is in the wave (user, live 2026-11-06 Firecape test: freezing the bats and blobs of the
        // early waves slowed them down and wasted runes); small monsters alone are simply shot.
        if(ice&&bigMonster(s)) {
            for(Kind kind:new Kind[]{Kind.BAT,Kind.MELEER,Kind.BLOB,Kind.BABY}) {
                Mob best=null;
                for(Mob m:s.mobs())if(m.kind()==kind&&freezable(s,spell,m,frozenUntil)&&(kind!=Kind.BABY||splitPair(s,m))
                    &&(best==null||m.distance(s.player())<best.distance(s.player())))best=m;
                if(best!=null)return new Cast(Element.ICE,best.index(),"Freeze the "+kind.name+(kind==Kind.BABY?" pair":"")+" in the open before contact");
            }
        }
        if(!blood||!healing)return null;
        // Blood heals only from safety: one prayer must cover everything that can reach the player (user, 2026-11-07).
        // Live 1.1.42 wave 28: a Tz-Kek and the Yt-MejKot in contact and a Tok-Xil four tiles off; Blood held the
        // loop for 136 ticks and the separation move to cover never ran (246 damage). On the recorded runs
        // 1.1.36-1.1.42, Blood cast with two or more styles in reach was followed by 6.6 damage per cast in five
        // ticks, against 0.3 with at most one.
        if(Integer.bitCount(reachingStyles(s))>1)return null;
        Mob clump=null;int clumpSize=0;
        for(Mob m:s.mobs()) {
            if(!spellTarget(m)||!CombatPlanner.playerCanAttack(spell,s.player(),m))continue;
            int size=1;for(Mob o:s.mobs())if(o.index()!=m.index()&&gap(m,o)==0)size++;
            if(size>=2&&size>clumpSize){clump=m;clumpSize=size;}
        }
        if(clump!=null)return new Cast(Element.BLOOD,clump.index(),"Heal on a clump of "+clumpSize);
        // Otherwise a monster that cannot attack the player's tile first (a trapped melee: the heal is free while the
        // overhead flicks for the others; user, 2026-11-06), then a ranger, then the nearest other target in reach.
        // "Trapped" alone is not enough: a Tok-Xil stuck behind a rock still shoots (live 1.1.39 wave 45).
        Mob best=null;int bestRank=-1;
        for(Mob m:s.mobs()) {
            if(!spellTarget(m)||!CombatPlanner.playerCanAttack(spell,s.player(),m))continue;
            int rank=CombatPlanner.threats(s.grid(),m,s.player(),s.jadStyle())==0?2:m.kind()==Kind.RANGER?1:0;
            if(best==null||rank>bestRank||rank==bestRank&&m.distance(s.player())<best.distance(s.player())){best=m;bestRank=rank;}
        }
        return best==null?null:new Cast(Element.BLOOD,best.index(),"Heal on the "+(bestRank==2?"harmless ":"")+best.kind().name);
    }
    /** The attack styles that can reach the player's tile now or after every monster's next step. */
    static int reachingStyles(Snapshot s) {
        int mask=0;
        for(Mob m:s.mobs())mask|=CombatPlanner.threats(s.grid(),m,s.player(),s.jadStyle());
        for(Mob m:CombatPlanner.advance(s.grid(),s.mobs(),s.player(),s.jadStyle()))
            mask|=CombatPlanner.threats(s.grid(),m,s.player(),s.jadStyle());
        return mask;
    }
    /** A Tok-Xil, Yt-MejKot or Ket-Zek is alive in the wave. */
    static boolean bigMonster(Snapshot s) {
        for(Mob m:s.mobs())if(m.kind()==Kind.RANGER||m.kind()==Kind.MELEER||m.kind()==Kind.MAGER)return true;
        return false;
    }
    /** A mager that can hit the player's tile now. */
    static boolean activeMager(Snapshot s) {
        for(Mob m:s.mobs())if(m.kind()==Kind.MAGER
            &&(CombatPlanner.threats(s.grid(),m,s.player(),s.jadStyle())&CombatPlanner.bit(Protection.MAGIC))!=0)return true;
        return false;
    }
    /** Magers (Magic level 240) and Jad are never spell targets; rangers, blobs, melees and bats are. */
    static boolean spellTarget(Mob m) {
        return m.kind()!=Kind.MAGER&&m.kind()!=Kind.JAD;
    }
    static boolean freezable(Snapshot s,Snapshot spell,Mob m,Map<Integer,Integer> frozenUntil) {
        return !frozen(s,m,frozenUntil)&&m.distance(s.player())>1&&!CaveSafety.trapped(s,m)
            &&CombatPlanner.playerCanAttack(spell,s.player(),m);
    }
    static boolean frozen(Snapshot s,Mob m,Map<Integer,Integer> frozenUntil) {
        Integer until=frozenUntil==null?null:frozenUntil.get(m.index());
        return until!=null&&until>=s.tick();
    }
    /** A split blob with another split blob at most one empty tile away (both inside a Burst splash). */
    static boolean splitPair(Snapshot s,Mob m) {
        for(Mob o:s.mobs())if(o.index()!=m.index()&&o.kind()==Kind.BABY&&gap(m,o)<=1)return true;
        return false;
    }
    /** No monster can hit the current tile. */
    public static boolean safe(Snapshot s) {
        for(Mob m:s.mobs())if(CombatPlanner.threats(s.grid(),m,s.player(),s.jadStyle())!=0)return false;
        return true;
    }
    /** Empty tiles between two footprints (0 when touching or overlapping). */
    static int gap(Mob a,Mob b) {
        int dx=Math.max(0,Math.max(a.tile().x()-(b.tile().x()+b.size()-1),b.tile().x()-(a.tile().x()+a.size()-1))-1);
        int dy=Math.max(0,Math.max(a.tile().y()-(b.tile().y()+b.size()-1),b.tile().y()-(a.tile().y()+a.size()-1))-1);
        return Math.max(dx,dy);
    }
    /** Freeze length in ticks: Burst 16, Blitz 24, Barrage 32. */
    public static int freezeTicks(int tier){return tier>=3?32:tier==2?24:16;}
}
