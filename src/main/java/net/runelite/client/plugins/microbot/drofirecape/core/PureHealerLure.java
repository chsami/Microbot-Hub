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
 * One bounded, observed north/south Italy Rock pull for an exposed Jad.
 * No prayer timing or input code. A waypoint arrival is NOT proof of a healer
 * trap: every living tag must follow north and remain terrain-separated at the
 * final firing position. Failed pulls yield to the existing healer combat path.
 */
public final class PureHealerLure {
    public enum Phase { IDLE, NORTH, COLLECT, SOUTH, SETTLE, ACQUIRE, HOLD, ABANDONED }
    private static final String PREFIX="Pure Jad healers: ";
    private Phase phase=Phase.IDLE;
    private Tile home,goal,lastPlayer;
    private int began=-1,phaseAt=-1,progressAt=-1,lastTick=-1,blockedAt=-1,verifiedAt=-1;
    private int jadIndex=-1,lowestJadRatio=-1,lowestJadScale=-1;
    private Set<Integer> group=Set.of();
    private String reason="Not requested";

    public void reset() {
        phase=Phase.IDLE;home=goal=lastPlayer=null;began=phaseAt=progressAt=lastTick=blockedAt=verifiedAt=-1;
        jadIndex=lowestJadRatio=lowestJadScale=-1;group=Set.of();reason="Not requested";
    }
    public Phase phase(){return phase;}
    public boolean attempted(){return began>=0;}
    public String reason(){return reason;}
    public boolean active(){return phase!=Phase.IDLE&&phase!=Phase.ABANDONED;}
    public boolean owns(Plan p){return active()&&p!=null&&p.reason().startsWith(PREFIX);}
    public void abandon(String why){phase=Phase.ABANDONED;goal=null;reason=why;}

    /** An already terrain-trapped Jad must not be dragged out of its setup.
     * Probe the same footprint as a melee pursuer, ignoring temporary NPC body
     * blocks. A projectile-capable Jad otherwise stops early in advance(). */
    public static boolean terrainTrappedJad(Snapshot s,Mob jad) {
        if(jad==null||jad.kind()!=Kind.JAD)return false;
        Mob probe=new Mob(jad.index(),Kind.MELEER,jad.tile(),jad.size(),1,1,-1,Protection.MELEE,true);
        for(int t=0;t<80;t++) {
            if(s.grid().melee(probe,s.player())||probe.occupies(s.player()))return false;
            Mob next=CombatPlanner.advance(s.grid(),List.of(probe),s.player(),Protection.NONE).get(0);
            if(next.tile().equals(probe.tile()))return true;
            probe=next;
        }
        return true; // No proven open approach: preserve the existing setup.
    }
    private static Mob jad(Snapshot s){return s.mobs().stream().filter(m->m.kind()==Kind.JAD).findFirst().orElse(null);}
    private static Set<Integer> indices(Snapshot s){
        Set<Integer> result=new HashSet<>();for(Mob m:s.mobs())if(m.kind()==Kind.HEALER)result.add(m.index());return result;
    }
    private static Snapshot at(Snapshot s,Tile p,List<Mob> mobs,int tick){
        return new Snapshot(tick,p,s.grid(),mobs,s.runEnergy(),s.running(),s.weaponRange(),s.jadStyle(),s.meleeMode()).atWave(s.wave());
    }
    private void enter(Phase next,Snapshot s,Tile nextGoal,String why){
        phase=next;phaseAt=progressAt=s.tick();blockedAt=verifiedAt=-1;goal=nextGoal;lastPlayer=s.player();reason=why;
    }
    private Plan hold(Snapshot s,int target,String why){
        Protection p=CombatPlanner.protectionForNextTick(s,s.player());
        int risk=CombatPlanner.immediateExposure(s,s.player(),p);
        return new Plan(s.player(),s.player(),p,target,risk==0,0,0,risk,PREFIX+why);
    }
    private Plan route(Snapshot s){
        Plan p=MinimapMovement.healerRoute(s,goal,CombatPlanner.protectionForNextTick(s,s.player()),PREFIX+reason);
        if(!CombatPlanner.actionable(p,false)) {
            if(blockedAt<0)blockedAt=s.tick();
            if(s.tick()-blockedAt>=3){abandon("Route blocked on fresh frames; yield instead of retaining an invalid goal");return null;}
            return hold(s,-1,"Rechecking blocked "+phase+" route");
        }
        blockedAt=-1;return p;
    }
    /** All healers must have actually followed into the north/east pocket. */
    private boolean collected(Snapshot s){
        for(Mob m:s.mobs())if(m.kind()==Kind.HEALER) {
            if(m.tile().x()<home.x()+3||m.tile().y()<home.y()+4||m.distance(s.player())>9)return false;
        }
        return !group.isEmpty();
    }
    /** Durable separation, without relying on Jad or another healer as a blocker.
     * The three-tile exclusion is a conservative policy reserve, NOT an asserted
     * server healing radius. Observed targeting of Jad is checked independently
     * at the live boundary. All healers stay in the observed threat model. */
    public static boolean separated(Snapshot s,Tile player) {
        Mob boss=jad(s);if(boss==null||!TacticalMovement.clearOfJad(s.mobs(),player))return false;
        int count=0;
        for(Mob healer:s.mobs())if(healer.kind()==Kind.HEALER) {
            count++;if(!healer.attackingPlayer()||boss.distance(healer.tile())<=3)return false;
            Mob future=healer;boolean settled=false;
            for(int tick=0;tick<80;tick++) {
                if(future.occupies(player)||s.grid().melee(future,player))return false;
                Mob next=CombatPlanner.advance(s.grid(),List.of(future),player,s.jadStyle()).get(0);
                if(boss.distance(next.tile())<=3)return false;
                if(next.tile().equals(future.tile())){settled=true;break;}
                future=next;
            }
            if(!settled)return false;
        }
        return count>0;
    }
    private Snapshot travel(Snapshot s,Tile destination) {
        List<Tile> path=MinimapMovement.path(s,destination);if(path.isEmpty())return null;
        List<Mob> future=s.mobs();int tick=s.tick();int stride=s.running()&&s.runEnergy()>0?2:1;
        for(int i=1;i<path.size();) {
            Tile to=path.get(Math.min(i+stride-1,path.size()-1));
            future=CombatPlanner.advance(s.grid(),future,to,s.jadStyle());tick++;i+=stride;
            Snapshot next=at(s,to,future,tick);
            if(!separated(next,to))return null;
        }
        for(int t=0;t<8;t++) {
            future=CombatPlanner.advance(s.grid(),future,destination,s.jadStyle());tick++;
            if(!separated(at(s,destination,future,tick),destination))return null;
        }
        return at(s,destination,future,tick);
    }
    private Tile firingTile(Snapshot s) {
        Tile best=null;int cost=Integer.MAX_VALUE;
        // Remain wholly SOUTH of Italy. Never walk back around its north corner
        // merely to acquire Jad: that would release the healer trap just created.
        for(int x=home.x();x<=home.x()+8;x++)for(int y=home.y()-18;y<=home.y()-11;y++) {
            Tile tile=new Tile(x,y);if(!s.grid().open(tile))continue;
            if(!CombatPlanner.actionable(MinimapMovement.healerChecked(s,tile,tile,s.jadStyle(),PREFIX+"Acquire south-side Jad shot"),false))continue;
            List<Tile> path=MinimapMovement.path(s,tile);
            if(path.stream().anyMatch(p->p.y()>home.y()-11)||path.size()>=cost)continue;
            Snapshot end=travel(s,tile);Mob boss=end==null?null:jad(end);
            if(end==null||!CombatPlanner.playerCanAttack(end,tile,boss))continue;
            best=tile;cost=path.size();
        }
        return best;
    }
    /** Call only with a fresh fully-tagged group, from the pure ranged owner.
     * Null means do not own this decision; never interpret it as a successful trap. */
    public Plan decide(Snapshot s,Tile italy,Set<Integer> confirmed,Set<Integer> targetingJad,boolean moving) {
        Mob boss=jad(s);Set<Integer> living=indices(s);
        if(s.meleeMode()||boss==null||living.isEmpty()||italy==null)return null;
        if(active()&&(boss.index()!=jadIndex||!living.equals(group)||!italy.equals(home))) {
            abandon("Healer generation / scene changed");return null;
        }
        if(!confirmed.containsAll(living)||!Collections.disjoint(living,targetingJad)) {
            if(active())abandon("Healer tag lost; return to tagging");return null;
        }
        if(phase==Phase.ABANDONED)return null;
        if(phase==Phase.IDLE) {
            if(terrainTrappedJad(s,boss)){abandon("Jad already terrain-trapped; preserve existing setup");return null;}
            if(!s.running()||s.runEnergy()<20){abandon("Insufficient running reserve for a two-leg pull");return null;}
            home=italy;group=Set.copyOf(living);jadIndex=boss.index();began=s.tick();
            Tile north=home.add(8,17),south=home.add(8,-14);
            if(!s.grid().open(north)||!s.grid().open(south)) {abandon("Italy anchors not present in this collision map");return null;}
            enter(Phase.NORTH,s,north,"Run fully north before collecting the tagged healers");
        }
        if(lastTick>s.tick()){abandon("Non-monotonic scene tick");return null;}
        if(lastTick!=s.tick()) {
            if(lastPlayer==null||!lastPlayer.equals(s.player())){progressAt=s.tick();lastPlayer=s.player();}
            lastTick=s.tick();
        }
        if(phase!=Phase.HOLD&&s.tick()-began>120) {abandon("Pull deadline exhausted");return null;}
        if((phase==Phase.NORTH||phase==Phase.SOUTH||phase==Phase.ACQUIRE)&&s.tick()-progressAt>=8) {
            abandon("No observed route progress for eight ticks");return null;
        }
        if(phase==Phase.NORTH) {
            if(!s.player().equals(goal))return route(s);
            if(moving)return hold(s,-1,"Waiting for north arrival to settle");
            enter(Phase.COLLECT,s,goal,"Wait for every tagged healer to follow north");
        }
        if(phase==Phase.COLLECT) {
            if(!s.player().equals(goal)){abandon("North collection position changed");return null;}
            if(!collected(s)) {
                if(s.tick()-phaseAt>=20){abandon("Healer group did not follow north within its deadline");return null;}
                return hold(s,-1,reason);
            }
            if(!s.running()||s.runEnergy()<10){abandon("Run reserve lost before south leg");return null;}
            enter(Phase.SOUTH,s,home.add(8,-14),"Run fully south around Italy; do not stop on the west face");
        }
        if(phase==Phase.SOUTH) {
            if(!s.player().equals(goal))return route(s);
            if(moving)return hold(s,-1,"Waiting for south arrival to settle");
            enter(Phase.SETTLE,s,goal,"Verify the north-side healer trap on fresh frames");
        }
        if(phase==Phase.SETTLE) {
            if(!separated(s,s.player())) {
                verifiedAt=-1;
                if(s.tick()-phaseAt>=4){abandon("South arrival did not establish durable healer separation");return null;}
                return hold(s,-1,reason);
            }
            if(verifiedAt<0){verifiedAt=s.tick();return hold(s,-1,reason);}
            if(s.tick()<=verifiedAt||moving)return hold(s,-1,reason);
            Tile fire=firingTile(s);
            if(fire==null){abandon("No checked south-side Jad firing position; do not release the trap blindly");return null;}
            enter(Phase.ACQUIRE,s,fire,"Acquire Jad while retaining the healer trap south of Italy");
        }
        if(phase==Phase.ACQUIRE) {
            if(!separated(s,s.player())) {abandon("Healer separation changed during Jad acquisition");return null;}
            if(!s.player().equals(goal)) {
                if(travel(s,goal)==null){abandon("Acquisition route would release a healer");return null;}
                return route(s);
            }
            if(moving)return hold(s,-1,"Waiting for the checked firing position");
            enter(Phase.HOLD,s,goal,"Ignore terrain-separated healers and finish Jad");
        }
        if(phase==Phase.HOLD) {
            // Range is an approximate model, not proof that healing has ceased.
            // A rising observed Jad bar revokes permission to ignore healers.
            if(boss.healthRatio()>=0&&boss.healthScale()>0) {
                if(lowestJadRatio>=0&&(long)boss.healthRatio()*lowestJadScale>
                    (long)lowestJadRatio*boss.healthScale()) {
                    abandon("Jad health increased while healers were ignored; resume healer combat");return null;
                }
                lowestJadRatio=boss.healthRatio();lowestJadScale=boss.healthScale();
            }
            if(moving||!s.player().equals(goal)||!separated(s,s.player())||!CombatPlanner.playerCanAttack(s,s.player(),boss)) {
                abandon("Verified firing/trap state changed; return control to healer combat");return null;
            }
            return hold(s,boss.index(),reason);
        }
        return null;
    }
}
