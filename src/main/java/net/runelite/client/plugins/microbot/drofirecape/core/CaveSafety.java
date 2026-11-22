/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape.core;

import java.util.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;

/** Contact recovery and firing access. No mouse or prayer input timing lives here. */
public final class CaveSafety {
    private CaveSafety() { }
    /** Ordinary followers are attack threats, not player movement collision walls. */
    public static boolean meleeFollower(Mob m) {
        return m.kind()==Kind.MELEER||m.kind()==Kind.BLOB||m.kind()==Kind.BABY||m.kind()==Kind.BAT;
    }

    public static boolean mageContact(Snapshot s) {
        return !s.meleeMode() && s.mobs().stream().anyMatch(m -> m.kind()==Kind.MAGER
            && s.grid().melee(m,s.player()));
    }
    public static boolean clearOfMagers(Snapshot s,List<Mob> mobs,Tile tile) {
        if(s.meleeMode())return true;
        for(Mob m:mobs)if(m.kind()==Kind.MAGER && m.distance(tile)<2)return false;
        return true;
    }
    /** Existing contact may be escaped, but a route must not acquire new contact. */
    public static boolean mageStep(Snapshot s,List<Mob> mobs,Tile from,Tile to) {
        if(s.meleeMode())return true;
        for(Mob m:mobs)if(m.kind()==Kind.MAGER && m.distance(to)<2) {
            if(m.distance(s.player())>=2 || m.distance(to)<m.distance(from))return false;
        }
        return true;
    }
    public static boolean observedMageMelee(Snapshot s,Mob m) {
        return m.kind()==Kind.MAGER && m.attackingPlayer() && s.grid().melee(m,s.player())
            && m.lastStyle()==Protection.MELEE && m.lastAttackTick()>=0
            && s.tick()>=m.lastAttackTick() && s.tick()-m.lastAttackTick()<=m.kind().speed;
    }
    /** Recent contact evidence is a temporary guard, never a prediction of the next random style. */
    public static Protection contactProtection(Snapshot s,Protection fallback) {
        boolean melee=false;
        for(Mob m:s.mobs()) {
            if(m.kind()==Kind.JAD)return fallback;
            if(m.kind()!=Kind.MAGER)continue;
            if(observedMageMelee(s,m))melee=true;
            else if(CombatPlanner.threats(s.grid(),m,s.player(),s.jadStyle())!=0)return Protection.MAGIC;
        }
        return melee?Protection.MELEE:fallback;
    }
    /** Leave contact before another stationary attack or lure return can be selected. */
    public static Plan escapeMage(Snapshot s) {
        if(!mageContact(s) || s.mobs().stream().anyMatch(m->m.kind()==Kind.JAD))return null;
        Plan best=null;long score=Long.MAX_VALUE;
        int stride=s.running()&&s.runEnergy()>0?2:1;
        for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++) {
            if(dx==0&&dy==0)continue;
            for(int n=1;n<=stride;n++) {
                Tile tile=s.player().add(dx*n,dy*n);
                if(!clearOfMagers(s,s.mobs(),tile))continue;
                Plan candidate=TacticalMovement.checked(s,tile,tile,"Step out of Ket-Zek melee contact");
                if(!CombatPlanner.actionable(candidate,false))continue;
                List<Mob> future=CombatPlanner.advance(s.grid(),s.mobs(),tile,s.jadStyle());
                if(!clearOfMagers(s,future,tile))continue;
                long cost=candidate.risk()*1000L+n;
                if(cost<score){score=cost;best=candidate;}
            }
        }
        return best;
    }
    public static boolean active(Snapshot s,Mob m) {
        return CombatPlanner.threats(s.grid(),m,s.player(),s.jadStyle())!=0;
    }
    public static int targetPriority(Snapshot s,Mob m,Protection prayer) {
        if(lateAttackingRanger(s,m))return 1100;
        if(m.kind()==Kind.BAT)return 1000;
        // A blocked ranger must still be rushed before centre melee cleanup.
        if(m.kind()==Kind.RANGER)return 900;
        if(active(s,m)) {
            if(m.kind()==Kind.MELEER)return 850;
            if(m.kind()==Kind.BLOB)return 800;
            if(m.kind()==Kind.BABY)return 750;
            if(m.kind()==Kind.MAGER)return 700;
        }
        return CombatPlanner.priority(m,prayer);
    }
    public static boolean lateAttackingRanger(Snapshot s,Mob m) {
        return s.wave()>=56&&s.wave()<=60&&m.kind()==Kind.RANGER&&m.attackingPlayer()
            &&(CombatPlanner.threats(s.grid(),m,s.player(),s.jadStyle())&CombatPlanner.bit(Protection.RANGE))!=0;
    }
    /** Keep a productive stationary rock shot, including before interaction ACK.
     * Re-evaluate every frame: death, contact, reach/trap loss, exposure or an
     * actionable higher-priority target immediately releases this choice. */
    public static Mob retainedSafeShot(Snapshot s,int currentTarget) {
        if(s.meleeMode()||s.mobs().stream().anyMatch(m->m.kind()==Kind.JAD||m.kind()==Kind.HEALER))return null;
        Protection prayer=CombatPlanner.protectionForNextTick(s,s.player());
        if(CombatPlanner.immediateExposure(s,s.player(),prayer)!=0)return null;
        Mob candidate=s.mobs().stream().filter(m->m.kind()==Kind.MELEER&&m.index()==currentTarget&&CombatPlanner.playerCanAttack(s,s.player(),m)
            &&trapped(s,m)).findFirst().orElse(null);
        if(candidate==null)candidate=s.mobs().stream().filter(m->m.kind()==Kind.MELEER&&trapped(s,m)
            &&CombatPlanner.playerCanAttack(s,s.player(),m)).findFirst().orElse(null);
        if(candidate==null)return null;
        Mob preferred=preferredShot(s,prayer);
        return preferred!=null&&targetPriority(s,preferred,prayer)>targetPriority(s,candidate,prayer)?null:candidate;
    }
    public static Mob rangedTarget(Snapshot s) {
        return s.mobs().stream().filter(m->m.kind()==Kind.RANGER||m.kind()==Kind.MAGER)
            .max(Comparator.comparingInt((Mob m)->targetPriority(s,m,Protection.MAGIC))
                .thenComparingInt(m->CombatPlanner.playerCanAttack(s,s.player(),m)?1:0)
                .thenComparingInt(m->-m.distance(s.player()))).orElse(null);
    }
    public static Mob preferredShot(Snapshot s,Protection prayer) {
        return s.mobs().stream().filter(m->CombatPlanner.playerCanAttack(s,s.player(),m))
            .max(Comparator.comparingInt((Mob m)->targetPriority(s,m,prayer)).thenComparingInt(m->-m.distance(s.player())))
            .orElse(null);
    }
    public static boolean trapped(Snapshot s,Mob m) {
        if(s.grid().melee(m,s.player()))return false;
        for(Mob next:CombatPlanner.advance(s.grid(),s.mobs(),s.player(),s.jadStyle()))
            if(next.index()==m.index())return next.tile().equals(m.tile());
        return false;
    }
    /** Predict permanent rock cover while staying put; transient NPC body blocks do not count. */
    public static List<Mob> coveredMages(Snapshot s) {
        List<Mob> covered=new ArrayList<>();
        if(s.meleeMode()||s.mobs().stream().noneMatch(m->m.kind()!=Kind.MAGER))return covered;
        for(Mob mage:s.mobs())if(mage.kind()==Kind.MAGER&&!active(s,mage)
            &&!CombatPlanner.playerCanAttack(s,s.player(),mage)) {
            Mob settled=mage;
            for(int tick=0;tick<64;tick++) {
                Mob next=CombatPlanner.advance(s.grid(),List.of(settled),s.player(),s.jadStyle()).get(0);
                if(next.tile().equals(settled.tile()))break;
                settled=next;
            }
            if(!active(s,settled))covered.add(settled);
        }
        return covered;
    }
    /** A melee lure must not release a mage that staying at this rock would contain. */
    public static boolean preservesMageCover(Snapshot s,Tile destination) {
        if(destination==null||destination.equals(s.player()))return true;
        List<Mob> covered=coveredMages(s);
        if(covered.isEmpty())return true;
        List<Tile> path=MinimapMovement.path(s,destination);
        if(path.isEmpty())return false;
        for(Mob mage:covered) {
            Mob future=mage;
            // Check the excursion AND its return. The Dragon-rock failure stayed
            // blocked at the north tile, then followed around the rock on return.
            List<Tile> excursion=new ArrayList<>(path);
            for(int tick=0;tick<6;tick++)excursion.add(destination);
            List<Tile> back=new ArrayList<>(path);Collections.reverse(back);excursion.addAll(back);
            for(int tick=0;tick<64;tick++)excursion.add(s.player());
            for(Tile tile:excursion) {
                future=CombatPlanner.advance(s.grid(),List.of(future),tile,s.jadStyle()).get(0);
                if(CombatPlanner.threats(s.grid(),future,tile,s.jadStyle())!=0)return false;
            }
        }
        return true;
    }
    public static boolean southTrappedMage(Snapshot s,Mob m,Tile main) {
        return main!=null&&m.kind()==Kind.MAGER&&m.tile().y()<main.y()
            &&!active(s,m)&&!CombatPlanner.playerCanAttack(s,s.player(),m)&&trapped(s,m);
    }
    public static boolean dragonMage(Snapshot s,Tile main,Mob mage) {
        // Recorded Dragon pocket: stay south of template y=5104 while clearing
        // other monsters. This bound also applies before the NW mage settles.
        return main!=null&&mage!=null&&mage.kind()==Kind.MAGER&&!active(s,mage)
            &&mage.tile().x()+mage.size()<main.x()-10&&mage.tile().y()>=main.y()+4
            &&s.mobs().stream().anyMatch(m->m.kind()!=Kind.MAGER);
    }
    /** Keep the NW stack behind Dragon rock while fighting inside the Italy pocket. */
    public static boolean pocketAllowed(Snapshot s,Tile main,Tile tile) {
        return main==null||s.mobs().stream().noneMatch(m->dragonMage(s,main,m))||tile.y()<=main.y()+4;
    }
    /** A single verified wall correction for a following melee, selected from recorded anchors. */
    public static Tile nearbyWallTrap(Snapshot s,Tile home) {
        if(home==null||s.meleeMode()||s.mobs().stream().anyMatch(m->m.kind()==Kind.BAT||m.kind()==Kind.RANGER||m.kind()==Kind.JAD))return null;
        List<Mob> followers=new ArrayList<>();
        for(Mob m:s.mobs())if(m.kind()==Kind.MELEER&&m.distance(s.player())<=2&&!trapped(s,m))followers.add(m);
        if(followers.isEmpty())return null;
        List<Tile> walls=new ArrayList<>(List.of(home,home.add(-1,-5),home.add(-1,-6),home.add(8,4),home.add(-1,4)));
        walls.sort(Comparator.comparingInt(t->t.distance(s.player())));
        for(Tile tile:walls) {
            if(tile.equals(s.player())||tile.distance(s.player())>8||!clearOfMagers(s,s.mobs(),tile)
                ||!pocketAllowed(s,home,tile)||!preservesMageCover(s,tile))continue;
            List<Tile> path=MinimapMovement.path(s,tile);if(path.isEmpty())continue;
            List<Mob> future=s.mobs();int stride=s.running()&&s.runEnergy()>0?2:1;
            for(int n=1;n<path.size();n+=stride)future=CombatPlanner.advance(s.grid(),future,path.get(Math.min(n+stride-1,path.size()-1)),s.jadStyle());
            for(int n=0;n<24;n++)future=CombatPlanner.advance(s.grid(),future,tile,s.jadStyle());
            Snapshot end=new Snapshot(s.tick()+24,tile,s.grid(),future,s.runEnergy(),s.running(),s.weaponRange(),s.jadStyle());
            boolean trappedAll=true;
            for(Mob original:s.mobs())if(original.kind()==Kind.MELEER&&(followers.contains(original)||trapped(s,original))) {
                Mob settled=future.stream().filter(m->m.index()==original.index()).findFirst().orElse(null);
                if(settled==null||!trapped(end,settled)||!CombatPlanner.playerCanAttack(end,tile,settled)){trappedAll=false;break;}
            }
            if(trappedAll&&CombatPlanner.actionable(MinimapMovement.route(s,tile,null,Protection.MELEE,false,"Recorded wall correction"),false))return tile;
        }
        return null;
    }
    /** Approach a blocked ranged attacker, or a last monster after bounded lure attempts. */
    public static Plan approach(Snapshot s,Mob target,Tile main,String reason) {
        if(target==null)return null;
        if(target.kind()==Kind.MAGER&&main!=null&&!s.meleeMode()) {
            Tile sole=main.add(-1,-5);
            if(!sole.equals(s.player())&&CombatPlanner.playerCanAttack(s,sole,target)
                &&clearOfMagers(s,s.mobs(),sole))
                return MinimapMovement.route(s,sole,null,Protection.MAGIC,false,"Range mage from usable Italy sole");
        }
        CollisionGrid.PathTree paths=s.grid().pathsFrom(s.player(),s.mobs(),t->
            TacticalMovement.clearOfJad(s,s.mobs(),t)&&clearOfMagers(s,s.mobs(),t)&&pocketAllowed(s,main,t));
        List<Tile> bestPath=null;Tile best=null;long score=Long.MAX_VALUE;
        int range=s.weaponRange();
        for(int x=target.tile().x()-range;x<target.tile().x()+target.size()+range;x++)
            for(int y=target.tile().y()-range;y<target.tile().y()+target.size()+range;y++) {
                Tile tile=new Tile(x,y);
                if(!s.grid().open(tile)||!CombatPlanner.playerCanAttack(s,tile,target))continue;
                List<Tile> path=paths.to(tile);if(path.isEmpty())continue;
                // Last-melee cleanup must fire from a wall, not deliberately stand in contact.
                if(!s.meleeMode()&&s.grid().melee(target,tile))continue;
                Protection p=CombatPlanner.bestProtection(s.grid(),s.mobs(),tile,s.jadStyle());
                long cost=CombatPlanner.immediateExposure(s,tile,p)*1000L+path.size()*10L;
                if(cost<score){score=cost;best=tile;bestPath=path;}
            }
        if(best==null)return null;
        if(best.equals(s.player())) {
            Protection p=contactProtection(s,CombatPlanner.protectionForNextTick(s,s.player()));
            int risk=CombatPlanner.immediateExposure(s,s.player(),p);
            return new Plan(best,best,p,target.index(),risk==0,0,0,risk,reason);
        }
        // Keep the checked pocket path, rather than routing a long click across its boundary.
        return (target.kind()==Kind.MAGER||preservesMageCover(s,best))?MinimapMovement.route(s,best,null,
            CombatPlanner.bestProtection(s.grid(),s.mobs(),s.player(),s.jadStyle()),false,reason):null;
    }
}
