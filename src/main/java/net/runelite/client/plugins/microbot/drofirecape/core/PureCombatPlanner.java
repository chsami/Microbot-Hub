/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
/*
 * NPC travelling-pattern adaptation:
 * Copyright (c) 2018, Woox <https://github.com/wooxsolo>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.microbot.drofirecape.core;

import java.util.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;

/**
 * Full-run receding-horizon controller. Terrain and full NPC footprints determine
 * lures; observed attack clocks determine protection BEFORE ordinary attacks.
 *
 * "safe" means zero modeled exposure, NOT permission to dispatch. A legal positive-
 * risk plan remains available in full-run mode instead of the v0.1 permanent hold.
 * The live layer still checks scene freshness, overhead acknowledgement, and range.
 * The model is intentionally independent of RuneLite for reproducible replay tests.
 */
public final class PureCombatPlanner {
    private static final int HORIZON=48, MAX_CORNERS=64;
    private Tile retainedDestination,recoveryTile;
    private int recoveryUntil=-1;
    private int observedTick=-1, staleTicks;
    private long lastFingerprint=Long.MIN_VALUE;
    private final Map<Tile,Integer> visits=new HashMap<>();
    public void reset(){retainedDestination=recoveryTile=null;recoveryUntil=-1;visits.clear();observedTick=-1;staleTicks=0;lastFingerprint=Long.MIN_VALUE;}
    public void clearRoute(){retainedDestination=null;}
    public void widenSearch(){retainedDestination=null;staleTicks=Math.max(staleTicks,24);}
    /** Excludes an unproductive firing tile briefly; fresh NPC evidence still controls safety. */
    public void recover(Snapshot s){widenSearch();staleTicks=40;recoveryTile=s.player();recoveryUntil=s.tick()+10;}
    public static int bit(Protection p){return p==Protection.NONE?0:1<<(p.ordinal()-1);}

    /** Geometrical threat envelope, regardless of current attack cooldown. */
    public static int threats(CollisionGrid grid,Mob m,Tile player,Protection jad) {
        if(m.kind()==Kind.HEALER&&!m.attackingPlayer())return 0;
        boolean melee=grid.melee(m,player);
        if(m.kind().range==1)return melee?bit(Protection.MELEE):0;
        int mask=melee?bit(Protection.MELEE):0;
        if(m.distance(player)<=m.kind().range&&grid.sight(m,player))
            mask|=bit(m.kind()==Kind.JAD?(jad==Protection.NONE?Protection.MAGIC:jad):m.kind().protection);
        return mask;
    }
    public static Protection bestProtection(CollisionGrid grid,List<Mob> mobs,Tile player,Protection jad) {
        int[] weights=new int[4];
        for(Mob m:mobs)addWeights(weights,threats(grid,m,player,jad),m.kind()==Kind.JAD?10000:m.kind().maxHit);
        return maximum(weights,Protection.NONE);
    }
    private static void addWeights(int[] w,int mask,int damage){for(Protection p:Protection.values())if((mask&bit(p))!=0)w[p.ordinal()]+=damage;}
    private static Protection maximum(int[] w,Protection fallback){int max=0;Protection p=fallback;for(Protection v:Protection.values())if(w[v.ordinal()]>max){max=w[v.ordinal()];p=v;}return p;}
    private static int dueMask(CollisionGrid g,Mob m,Tile p,int tick,Protection jad) {
        // Jad is always a spatial threat: do not infer his NEXT random style from his last one.
        // Native animation priority owns his actual prayer; lures must also avoid melee range.
        return m.kind()==Kind.JAD||AttackClock.due(m,tick)?threats(g,m,p,jad):0;
    }
    /** Mirrors the live three-second Jad priority window even after breaking his LOS. */
    private static Protection recentJadProtection(List<Mob> mobs,int tick,Protection jad) {
        for(Mob m:mobs)if(m.kind()==Kind.JAD&&m.lastAttackTick()>=0&&tick>=m.lastAttackTick()
            &&tick-m.lastAttackTick()<=5)return jad==Protection.NONE?Protection.MAGIC:jad;
        return Protection.NONE;
    }
    private static Protection protection(CollisionGrid g,List<Mob> before,List<Mob> after,
                                         Tile from,Tile to,int tick,Protection jad,boolean meleeMode) {
        if(meleeMode)return MeleeProtection.choose(g,before,after,from,to,tick-1,jad,Protection.NONE);
        int[] weights=new int[4];
        for(int i=0;i<before.size();i++) {
            Mob a=before.get(i), b=after.get(i);
            int mask=dueMask(g,a,from,tick,jad)|dueMask(g,b,to,tick,jad);
            addWeights(weights,mask,a.kind()==Kind.JAD?10000:a.kind().maxHit);
        }
        Protection locked=recentJadProtection(before,tick,jad);
        if(locked!=Protection.NONE)return locked;
        Protection fallback=bestProtection(g,after,to,jad);
        return maximum(weights,fallback);
    }
    /** Desired prayer for the server tick that follows this captured snapshot. */
    public static Protection protectionForNextTick(Snapshot s,Tile destination) {
        Tile to=destination==null?s.player():destination;
        return protection(s.grid(),s.mobs(),advance(s.grid(),s.mobs(),to,s.jadStyle()),s.player(),to,s.tick()+1,s.jadStyle(),s.meleeMode());
    }
    private static int exposure(CollisionGrid grid,List<Mob> before,List<Mob> after,Tile from,Tile to,
                                Protection prayer,Protection jad,int tick) {
        int risk=0;
        for(int i=0;i<before.size();i++) {
            Mob a=before.get(i),b=after.get(i);
            int mask=dueMask(grid,a,from,tick,jad)|dueMask(grid,b,to,tick,jad);
            if((mask&~bit(prayer))!=0)risk+=a.kind()==Kind.JAD?970:a.kind().maxHit;
        }
        return risk;
    }
    public static int immediateExposure(Snapshot s,Tile to,Protection prayer) {
        return exposure(s.grid(),s.mobs(),advance(s.grid(),s.mobs(),to,s.jadStyle()),s.player(),to,prayer,s.jadStyle(),s.tick()+1);
    }
    public static boolean playerCanAttack(Snapshot s,Tile p,Mob m) {
        if(m==null||m.occupies(p))return false;
        return s.meleeMode()?s.grid().melee(m,p):m.distance(p)<=s.weaponRange()&&s.grid().playerSight(p,m);
    }
    /** Death of a large blocker can expose a smaller follower before the next decision. */
    public static List<Mob> afterDefeat(List<Mob> mobs,Mob target) {
        ArrayList<Mob> result=new ArrayList<>();
        for(Mob m:mobs)if(m.index()!=target.index())result.add(m);
        if(target.kind()==Kind.BLOB) {
            int index=mobs.stream().mapToInt(Mob::index).max().orElse(0)+1;
            result.add(new Mob(index,Kind.BABY,target.tile(),1,-1,-1,-1,Protection.MELEE,true));
            result.add(new Mob(index+1,Kind.BABY,target.tile().add(1,0),1,-1,-1,-1,Protection.MELEE,true));
        }
        return List.copyOf(result);
    }
    /** Java 11 value type; preserves the former record API and value semantics. */
    private static final class KillWindow {
        private final Protection prayer;
        private final int risk;

        private KillWindow(Protection prayer, int risk) {
            this.prayer = prayer;
            this.risk = risk;
        }

        public Protection prayer() { return prayer; }
        public int risk() { return risk; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof KillWindow)) return false;
            KillWindow that = (KillWindow) other;
            return java.util.Objects.equals(prayer, that.prayer)
                && risk == that.risk;
        }

        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + java.util.Objects.hashCode(prayer);
            result = 31 * result + Integer.hashCode(risk);
            return result;
        }

        @Override
        public String toString() {
            return "KillWindow[prayer=" + prayer + ", risk=" + risk + "]";
        }
    }
    private static KillWindow killWindow(Snapshot s,Tile p,List<Mob> mobs,Mob target,int tick) {
        List<Mob> alive=advance(s.grid(),mobs,p,s.jadStyle());
        List<Mob> released=advance(s.grid(),afterDefeat(mobs,target),p,s.jadStyle());
        int[] weights=new int[4];
        for(Mob m:alive)addWeights(weights,dueMask(s.grid(),m,p,tick,s.jadStyle()),m.kind()==Kind.JAD?10000:m.kind().maxHit);
        for(Mob m:released)addWeights(weights,dueMask(s.grid(),m,p,tick,s.jadStyle()),m.kind()==Kind.JAD?10000:m.kind().maxHit);
        Protection chosen=recentJadProtection(mobs,tick,s.jadStyle());
        if(chosen==Protection.NONE)chosen=maximum(weights,bestProtection(s.grid(),mobs,p,s.jadStyle()));
        int a=0,b=0;
        for(Mob m:alive)if((dueMask(s.grid(),m,p,tick,s.jadStyle())&~bit(chosen))!=0)a+=m.kind().maxHit;
        for(Mob m:released)if((dueMask(s.grid(),m,p,tick,s.jadStyle())&~bit(chosen))!=0)b+=m.kind().maxHit;
        return new KillWindow(chosen,Math.max(a,b));
    }
    public boolean attackSafe(Snapshot s,Mob target,Protection prayer) {
        return attackAllowed(s,target,prayer,true);
    }
    public boolean attackAllowed(Snapshot s,Mob target,Protection prayer,boolean strict) {
        if(!playerCanAttack(s,s.player(),target))return false;
        if(!s.meleeMode()&&target.kind()==Kind.HEALER&&!target.attackingPlayer()&&target.distance(s.player())<3)return false;
        if(!s.meleeMode())for(Mob m:s.mobs())if(m.kind()==Kind.JAD&&s.grid().melee(m,s.player()))return false;
        if(!PureSafety.releaseSafe(s,target,prayer))return false;
        if(!strict)return true;
        KillWindow release=killWindow(s,s.player(),s.mobs(),target,s.tick()+1);
        return immediateExposure(s,s.player(),prayer)==0&&release.risk()==0
            &&(release.prayer()==Protection.NONE||release.prayer()==prayer);
    }
    public static boolean actionable(Plan plan,boolean strict) {
        return plan!=null&&plan.nextStep()!=null&&plan.risk()!=Integer.MAX_VALUE&&(!strict||plan.safe());
    }
    private void observe(Snapshot s) {
        if(observedTick==s.tick())return;
        observedTick=s.tick();long fp=17;
        for(Mob m:s.mobs())fp=fp*31+m.index()*73L+m.healthRatio();
        if(fp==lastFingerprint)staleTicks++;else{staleTicks=0;visits.clear();}
        lastFingerprint=fp;visits.merge(s.player(),1,Integer::sum);
    }
    public Plan plan(Snapshot s,Tile preferred) {
        if(s.player()==null||s.grid()==null||!s.grid().open(s.player()))return invalid(s,s.player(),"Scene unavailable");
        observe(s);
        Plan mageEscape=CaveSafety.escapeMage(s);
        if(mageEscape!=null)return mageEscape;
        Plan overlap=s.mobs().stream().anyMatch(m->!CaveSafety.meleeFollower(m)&&m.occupies(s.player()))?
            TacticalMovement.escapeOverlap(s):null;
        if(overlap!=null&&actionable(overlap,false))return overlap;
        boolean recover=recoveryTile!=null&&s.tick()<=recoveryUntil&&s.player().equals(recoveryTile);
        Evaluation hold=evaluate(s,List.of(s.player()),preferred);
        // Most combat ticks are stable protected shooting. Avoid a full scene search then.
        if(!recover&&hold!=null&&hold.risk==0&&hold.target>=0&&staleTicks<40)
            return toPlan(s,hold,"Protected attack; maintaining the stack");
        CollisionGrid.PathTree paths=s.grid().pathsFrom(s.player(),s.mobs(),tile->TacticalMovement.clearOfJad(s,s.mobs(),tile));
        // Keep a still-valid zero-exposure lure across tick-phase changes. Re-scoring every
        // destination each tick caused a two-tile east/west oscillation in wave 38.
        // This is NOT a blind movement lease: the entire remaining path, NPC motion,
        // firing access and prayers are revalidated from the new snapshot.
        if(retainedDestination!=null&&!retainedDestination.equals(s.player())) {
            List<Tile> committedPath=paths.to(retainedDestination);
            if(!committedPath.isEmpty()) {
                Evaluation committed=evaluate(s,committedPath,preferred);
                if(committed!=null&&committed.risk==0&&committed.terminalTarget>=0)
                    return toPlan(s,committed,"Continuing revalidated zero-exposure lure");
            }
        }
        Evaluation best=null,wait=null;
        for(Tile t:candidates(s,preferred)) {
            if(recover&&t.equals(recoveryTile))continue;
            List<Tile> path=paths.to(t);if(path.isEmpty())continue;
            Evaluation e=evaluate(s,path,preferred);
            if(e==null)continue;
            if(t.equals(s.player()))wait=e;
            // A forever-hidden stationary camp with no target is not a solution.
            if(e.terminalTarget<0)continue;
            if(best==null||e.score<best.score)best=e;
        }
        if(best==null) {
            // Widen to reachable firing tiles. This also handles odd-size rocks and short-range weapons.
            ArrayList<Tile> firing=new ArrayList<>();
            for(int x=1;x<s.grid().width-1;x++)for(int y=1;y<s.grid().height-1;y++) {
                Tile t=new Tile(x,y);if(!s.grid().open(t)||t.distance(s.player())>48)continue;
                for(Mob m:s.mobs())if(playerCanAttack(s,t,m)){firing.add(t);break;}
            }
            firing.sort(Comparator.comparingInt(t->t.distance(s.player())));
            for(int i=0;i<Math.min(100,firing.size());i++) {
                if(recover&&firing.get(i).equals(recoveryTile))continue;
                List<Tile> path=paths.to(firing.get(i));if(path.isEmpty())continue;
                Evaluation e=evaluate(s,path,preferred);
                if(e!=null&&e.terminalTarget>=0&&(best==null||e.score<best.score))best=e;
            }
        }
        if(best==null) {
            // A blocked stack can require changing the pull direction before any firing
            // tile becomes available. After eight no-progress ticks, explore a legal
            // short step instead of returning the same non-productive hold forever.
            if(staleTicks>=8&&s.mobs().stream().noneMatch(CaveSafety::meleeFollower)) {
                Evaluation escape=null;
                for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++) {
                    if(dx==0&&dy==0)continue;
                    Tile tile=s.player().add(dx,dy);
                    if(!s.grid().step(s.player(),tile)||occupied(tile,s.mobs()))continue;
                    if(!s.meleeMode()&&s.mobs().stream().anyMatch(m->m.kind()==Kind.JAD&&s.grid().melee(m,tile)))continue;
                    Evaluation candidate=evaluate(s,List.of(s.player(),tile),null);
                    if(candidate==null)continue;
                    long score=candidate.score+visits.getOrDefault(tile,0)*500L;
                    candidate=new Evaluation(candidate.destination,candidate.next,candidate.prayer,
                        candidate.target,candidate.terminalTarget,candidate.risk,candidate.blocked,score);
                    if(escape==null||candidate.score<escape.score)escape=candidate;
                }
                if(escape!=null)return toPlan(s,escape,"Changing pull direction to recover blocked firing access");
            }
            if(wait!=null)return toPlan(s,wait,"Re-evaluating blocked target access");
            return invalid(s,s.player(),"No legal route in loaded collision map");
        }
        retainedDestination=best.destination;
        String reason=best.next.equals(s.player())?(best.target>=0?"Protected target selection":"Letting the stack settle"):
            (best.risk==0?"Luring through checked cover":"Lowest modeled exposure route");
        return toPlan(s,best,reason);
    }
    private static Plan invalid(Snapshot s,Tile dest,String reason) {
        Protection p=s.grid()==null||s.player()==null?Protection.MAGIC:bestProtection(s.grid(),s.mobs(),s.player(),s.jadStyle());
        return new Plan(dest,s.player(),p,-1,false,0,0,Integer.MAX_VALUE,reason);
    }
    private Plan toPlan(Snapshot s,Evaluation e,String reason) {
        int mask=0;for(Mob m:s.mobs())mask|=threats(s.grid(),m,s.player(),s.jadStyle());
        return new Plan(e.destination,e.next,e.prayer,e.target,e.risk==0,e.blocked,Integer.bitCount(mask),e.risk,reason);
    }
    /** Expose the stationary decision without global movement re-scoring. */
    public Plan hold(Snapshot s) {
        if(s.player()==null||s.grid()==null||!s.grid().open(s.player()))return null;
        Evaluation e=evaluate(s,List.of(s.player()),null);
        return e==null?null:toPlan(s,e,"Hold current position");
    }
    /** A recorded cover candidate is usable only if its live rollout has firing access. */
    public Plan firingRoute(Snapshot s,Tile exact) {
        if(exact==null)return null;
        List<Tile> path=s.grid().pathsFrom(s.player(),s.mobs(),tile->TacticalMovement.clearOfJad(s,s.mobs(),tile)).to(exact);if(path.isEmpty())return null;
        Evaluation e=evaluate(s,path,exact);
        return e==null||e.terminalTarget<0?null:toPlan(s,e,"Checked firing cover");
    }
    public Plan route(Snapshot s,Tile exact) {
        if(exact==null)return invalid(s,s.player(),"No destination");
        List<Tile> path=s.grid().pathsFrom(s.player(),s.mobs(),tile->TacticalMovement.clearOfJad(s,s.mobs(),tile)).to(exact);
        if(path.isEmpty())return invalid(s,exact,"Destination currently blocked");
        Evaluation e=evaluate(s,path,exact);
        return e==null?invalid(s,exact,"Route intersects moving NPC footprint"):toPlan(s,e,"Positioning at selected cover");
    }
    /** Search global terrain corners with the full upcoming wave, not just the next NPC. */
    public Tile chooseCamp(Snapshot upcoming) {
        Tile best=upcoming.player();long score=Long.MAX_VALUE;
        for(Tile t:candidates(upcoming,null)) {
            Snapshot stationary=new Snapshot(upcoming.tick(),t,upcoming.grid(),upcoming.mobs(),upcoming.runEnergy(),upcoming.running(),upcoming.weaponRange(),upcoming.jadStyle(),upcoming.meleeMode());
            Evaluation e=evaluate(stationary,List.of(t),null);
            if(e==null||e.terminalTarget<0)continue;
            long value=e.score+t.distance(upcoming.player())*20L;
            if(value<score){score=value;best=t;}
        }
        return best;
    }
    /** A finish tile must also be a real firing tile for the survivor of THIS wave. */
    public Tile chooseFinishTile(Snapshot current,Snapshot upcoming) {
        if(current.mobs().size()!=1)return chooseCamp(upcoming);
        Mob last=current.mobs().get(0);
        Tile best=current.player();long score=Long.MAX_VALUE;
        LinkedHashSet<Tile> tiles=new LinkedHashSet<>(candidates(upcoming,null));
        tiles.add(current.player());
        int r=current.weaponRange();
        for(int x=last.tile().x()-r;x<=last.tile().x()+last.size()-1+r;x++)
            for(int y=last.tile().y()-r;y<=last.tile().y()+last.size()-1+r;y++)
                if((x+y)%2==0)tiles.add(new Tile(x,y));
        CollisionGrid.PathTree paths=current.grid().pathsFrom(current.player(),current.mobs(),tile->TacticalMovement.clearOfJad(current,current.mobs(),tile));
        for(Tile t:tiles) {
            if(!current.grid().open(t)||!playerCanAttack(current,t,last))continue;
            if(upcoming.mobs().stream().anyMatch(m->m.occupies(t)))continue;
            List<Tile> route=paths.to(t);if(route.isEmpty())continue;
            Evaluation travel=evaluate(current,route,t);if(travel==null||travel.risk>0)continue;
            Snapshot placed=new Snapshot(upcoming.tick(),t,upcoming.grid(),upcoming.mobs(),upcoming.runEnergy(),upcoming.running(),upcoming.weaponRange(),upcoming.jadStyle(),upcoming.meleeMode());
            Evaluation future=evaluate(placed,List.of(t),null);
            if(future==null||future.terminalTarget<0)continue;
            long cost=future.score+route.size()*30L;
            if(cost<score){score=cost;best=t;}
        }
        return best;
    }
    private ArrayList<Tile> candidates(Snapshot s,Tile preferred) {
        LinkedHashSet<Tile> result=new LinkedHashSet<>();result.add(s.player());
        if(preferred!=null)result.add(preferred);if(retainedDestination!=null)result.add(retainedDestination);
        // Every possible one-tick move, including two-step straight running.
        for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++){result.add(s.player().add(dx,dy));if(s.running()&&s.runEnergy()>0)result.add(s.player().add(dx*2,dy*2));}
        ArrayList<Tile> corners=new ArrayList<>(s.grid().corners());
        corners.sort(Comparator.comparingInt(t->t.distance(preferred==null?s.player():preferred)));
        for(int i=0;i<Math.min(staleTicks>20?128:MAX_CORNERS,corners.size());i++) {
            Tile c=corners.get(i);result.add(c);
            // One tile away from a rock corner is often the actual shooting/safespot tile.
            for(int[] d:new int[][]{{0,1},{1,0},{0,-1},{-1,0}})result.add(c.add(d[0],d[1]));
        }
        for(Mob m:s.mobs()) {
            if(s.meleeMode())for(int i=0;i<m.size();i++) {
                result.add(m.tile().add(-1,i));result.add(m.tile().add(m.size(),i));
                result.add(m.tile().add(i,-1));result.add(m.tile().add(i,m.size()));
            }
            for(int r:new int[]{3,Math.max(3,s.weaponRange())})for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++) {
                if(dx==0&&dy==0)continue;
                result.add(new Tile(m.tile().x()+(dx<0?-r:dx>0?m.size()-1+r:m.size()/2),m.tile().y()+(dy<0?-r:dy>0?m.size()-1+r:m.size()/2)));
            }
        }
        result.removeIf(t->!s.grid().open(t)||s.mobs().stream().anyMatch(m->m.occupies(t)));
        return new ArrayList<>(result);
    }
    /** Java 11 value type; preserves the former record API and value semantics. */
    private static final class Evaluation {
        private final Tile destination;
        private final Tile next;
        private final Protection prayer;
        private final int target;
        private final int terminalTarget;
        private final int risk;
        private final int blocked;
        private final long score;

        private Evaluation(Tile destination, Tile next, Protection prayer, int target, int terminalTarget, int risk, int blocked, long score) {
            this.destination = destination;
            this.next = next;
            this.prayer = prayer;
            this.target = target;
            this.terminalTarget = terminalTarget;
            this.risk = risk;
            this.blocked = blocked;
            this.score = score;
        }

        public Tile destination() { return destination; }
        public Tile next() { return next; }
        public Protection prayer() { return prayer; }
        public int target() { return target; }
        public int terminalTarget() { return terminalTarget; }
        public int risk() { return risk; }
        public int blocked() { return blocked; }
        public long score() { return score; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Evaluation)) return false;
            Evaluation that = (Evaluation) other;
            return java.util.Objects.equals(destination, that.destination)
                && java.util.Objects.equals(next, that.next)
                && java.util.Objects.equals(prayer, that.prayer)
                && target == that.target
                && terminalTarget == that.terminalTarget
                && risk == that.risk
                && blocked == that.blocked
                && score == that.score;
        }

        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + java.util.Objects.hashCode(destination);
            result = 31 * result + java.util.Objects.hashCode(next);
            result = 31 * result + java.util.Objects.hashCode(prayer);
            result = 31 * result + Integer.hashCode(target);
            result = 31 * result + Integer.hashCode(terminalTarget);
            result = 31 * result + Integer.hashCode(risk);
            result = 31 * result + Integer.hashCode(blocked);
            result = 31 * result + Long.hashCode(score);
            return result;
        }

        @Override
        public String toString() {
            return "Evaluation[destination=" + destination + ", next=" + next + ", prayer=" + prayer + ", target=" + target + ", terminalTarget=" + terminalTarget + ", risk=" + risk + ", blocked=" + blocked + ", score=" + score + "]";
        }
    }
    private Evaluation evaluate(Snapshot s,List<Tile> path,Tile preferred) {
        List<Mob> mobs=s.mobs();Tile p=s.player();int pi=0,total=0,peak=0,blocked=0;
        Tile immediate=path.get(Math.min(commandStride(s,path),path.size()-1));
        if(!immediate.equals(s.player())&&(!PureSafety.routeAllowed(s,immediate)
            ||!actionable(TacticalMovement.checked(s,path.get(path.size()-1),immediate,"Planner step validation"),false)))return null;
        Protection first=protectionForNextTick(s,immediate);
        int terminal=-1, simulatedTicks=0;
        for(int t=1;t<=HORIZON;t++) {
            simulatedTicks=t;
            Tile before=p;List<Mob> old=mobs;
            int steps=1;
            if(s.running()&&s.runEnergy()>t/2&&pi+2<path.size()) {
                Tile a=path.get(pi),b=path.get(pi+1),c=path.get(pi+2);
                if(b.x()-a.x()==c.x()-b.x()&&b.y()-a.y()==c.y()-b.y())steps=2;
            }
            for(int j=0;j<steps&&pi+1<path.size();j++) {
                Tile next=path.get(pi+1);
                if(!s.grid().step(p,next)||occupied(next,mobs)||!TacticalMovement.clearOfJad(s,mobs,next))return null;
                p=next;pi++;
            }
            mobs=advance(s.grid(),mobs,p,s.jadStyle());
            if(!p.equals(s.player())&&!TacticalMovement.clearOfJad(s,mobs,p))return null;
            Protection pray=protection(s.grid(),old,mobs,before,p,s.tick()+t,s.jadStyle(),s.meleeMode());
            int hit=exposure(s.grid(),old,mobs,before,p,pray,s.jadStyle(),s.tick()+t);
            total+=hit;peak=Math.max(peak,hit);
            ArrayList<Mob> clocks=new ArrayList<>(mobs.size());
            for(Mob m:mobs) {
                int mask=dueMask(s.grid(),m,p,s.tick()+t,s.jadStyle());
                clocks.add(mask!=0&&AttackClock.due(m,s.tick()+t)?AttackClock.launched(m,s.tick()+t,m.kind().protection):m);
            }
            mobs=clocks;
            if(pi==path.size()-1) {
                terminal=pickTarget(s,p,mobs,pray);
                if(t>=Math.max(8,(path.size()-1)/steps+8))break;
            }
        }
        if(pi<path.size()-1)return null;
        Protection endPrayer=bestProtection(s.grid(),mobs,p,s.jadStyle());
        if(terminal<0)terminal=pickTarget(s,p,mobs,endPrayer);
        for(Mob m:mobs)if(threats(s.grid(),m,p,s.jadStyle())==0)blocked++;
        int target=immediate.equals(s.player())?pickTarget(s,s.player(),s.mobs(),first):-1;
        if(terminal>=0) {
            Mob dying=null;for(Mob m:mobs)if(m.index()==terminal)dying=m;
            if(dying!=null){KillWindow kill=killWindow(s,p,mobs,dying,s.tick()+simulatedTicks+1);total+=kill.risk();peak=Math.max(peak,kill.risk());}
        }
        if(target>=0) {
            Mob dying=null;for(Mob m:s.mobs())if(m.index()==target)dying=m;
            if(dying!=null) {
                KillWindow kill=killWindow(s,s.player(),s.mobs(),dying,s.tick()+1);
                if(kill.prayer()!=Protection.NONE&&!s.meleeMode())first=kill.prayer();
                total+=kill.risk();peak=Math.max(peak,kill.risk());
            }
        }
        // Model damage dominates speed, but permanently non-actionable holds are excluded by plan().
        long score=peak*1_000_000L+total*10_000L+path.size()*35L-blocked*90L;
        score+=PureCombatPolicy.penalty(s,p,mobs);
        if(terminal>=0)for(Mob m:mobs)if(m.index()==terminal)score-=(priority(m,endPrayer)+(PureCombatPolicy.rangerBeforeBat(s,m)?100:0))*8L;
        if(preferred!=null)score+=p.distance(preferred)*45L;
        if(p.equals(retainedDestination))score-=100;
        if(target>=0)score-=500;
        if(staleTicks>16)score+=visits.getOrDefault(p,0)*90L;
        return new Evaluation(p,immediate,first,target,terminal,total,blocked,score);
    }
    private static int commandStride(Snapshot s,List<Tile> path) {
        if(s.running()&&s.runEnergy()>0&&path.size()>2) {
            Tile a=path.get(0),b=path.get(1),c=path.get(2);
            if(b.x()-a.x()==c.x()-b.x()&&b.y()-a.y()==c.y()-b.y())return 2;
        }
        return 1;
    }
    private static boolean occupied(Tile t,List<Mob> mobs){for(Mob m:mobs)if(m.occupies(t))return true;return false;}
    public static int priority(Mob m,Protection p) {
        int value;
        switch(m.kind()) {
            case HEALER: value=250; break;
            case BAT: value=280; break;
            // Ranger-first remains stable while the overhead flicks to a big
            // melee. Health/target-stickiness bonuses must not put babies first.
            case RANGER: value=230; break;
            case MAGER: value=p==Protection.RANGE?230:100; break;
            case MELEER: value=70; break;
            case BLOB: value=60; break;
            case BABY: value=80; break;
            case JAD: value=20; break;
            default: throw new IllegalStateException("Unhandled monster kind: "+m.kind());
        }
        if(m.healthRatio()>0&&m.healthScale()>0)value+=(m.healthScale()-m.healthRatio())*25/m.healthScale();
        return value;
    }
    private static int pickTarget(Snapshot s,Tile p,List<Mob> mobs,Protection prayer) {
        boolean healers=mobs.stream().anyMatch(m->m.kind()==Kind.HEALER);int best=-1,max=Integer.MIN_VALUE;
        for(Mob m:mobs) {
            if(m.kind()==Kind.JAD&&healers)continue;
            if(!playerCanAttack(s,p,m))continue;
            if(!s.meleeMode()&&m.kind()==Kind.HEALER&&!m.attackingPlayer()&&m.distance(p)<3)continue;
            Snapshot positioned=new Snapshot(s.tick(),p,s.grid(),mobs,s.runEnergy(),s.running(),s.weaponRange(),s.jadStyle(),s.meleeMode()).atWave(s.wave());
            if(!PureSafety.releaseSafe(positioned,m,prayer))continue;
            int score=PureCombatPolicy.targetPriority(positioned,m,prayer)-m.distance(p);
            if(score>max){max=score;best=m.index();}
        }
        return best;
    }
    /** NPC greedy pursuit in index order. NPCs are NOT routed using player A*. */
    public static List<Mob> advance(CollisionGrid grid,List<Mob> original,Tile player,Protection jad) {
        ArrayList<Mob> ordered=new ArrayList<>(original);ordered.sort(Comparator.comparingInt(Mob::index));
        Map<Integer,Mob> moved=new HashMap<>();
        Mob jadNpc=original.stream().filter(m->m.kind()==Kind.JAD).findFirst().orElse(null);
        for(int i=0;i<ordered.size();i++) {
            Mob m=ordered.get(i);Tile pursue=player;
            if(m.kind()==Kind.HEALER&&!m.attackingPlayer()) {
                if(jadNpc==null||m.distance(jadNpc.tile())<=3){moved.put(m.index(),m);continue;}
                pursue=jadNpc.tile();
            } else if(threats(grid,m,player,jad)!=0){moved.put(m.index(),m);continue;}
            // The normal NPC pursuit rule is NOT player pathfinding. At diagonal
            // melee adjacency it only tries the X-axis step. Allowing the Y
            // fallback here incorrectly "untrapped" the recorded wave-3 blob
            // and its babies and made the controller abandon a working corner.
            // Adapted from Woox's WorldArea.calculateNextTravellingPoint
            // (BSD-2-Clause; see THIRD-PARTY-NOTICES.txt).
            int nearestX=Math.max(m.tile().x(),Math.min(pursue.x(),m.tile().x()+m.size()-1));
            int nearestY=Math.max(m.tile().y(),Math.min(pursue.y(),m.tile().y()+m.size()-1));
            int gapX=Math.abs(pursue.x()-nearestX),gapY=Math.abs(pursue.y()-nearestY);
            int rawDx=pursue.x()-m.tile().x(),rawDy=pursue.y()-m.tile().y();
            int dx=Integer.signum(rawDx),dy=Integer.signum(rawDy);
            LinkedHashSet<Tile> attempts=new LinkedHashSet<>();
            if(gapX+gapY==1) {
                // Adjacent across a blocking edge: stay trapped, not walk around.
            } else if(gapX==1&&gapY==1) {
                if(dx!=0)attempts.add(m.tile().add(dx,0));
            } else {
                if(dx!=0||dy!=0)attempts.add(m.tile().add(dx,dy));
                if(dx!=0)attempts.add(m.tile().add(dx,0));
                if(dy!=0&&Math.max(Math.abs(rawDx),Math.abs(rawDy))>1)attempts.add(m.tile().add(0,dy));
            }
            for(Tile next:attempts)if(grid.areaStep(m,next,ordered,player)){m=m.at(next);break;}
            ordered.set(i,m);moved.put(m.index(),m);
        }
        ArrayList<Mob> out=new ArrayList<>();for(Mob m:original)out.add(moved.get(m.index()));return List.copyOf(out);
    }
}
