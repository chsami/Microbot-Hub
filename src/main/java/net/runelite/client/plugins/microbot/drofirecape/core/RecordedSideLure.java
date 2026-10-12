/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape.core;

import java.util.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;

/** Demonstrated side routes. One committed excursion per follower, never adjacent retry destinations. */
public final class RecordedSideLure {
    private enum Phase { IDLE, EAST_CORNER, EAST_HOLD, EAST_WAIT, ITALY_WALL, WEST_PASSAGE, DRAGON_WALL }
    private Phase phase=Phase.IDLE;
    private final Set<Integer> attempted=new HashSet<>();
    private final LureProgress progress=new LureProgress();
    private Tile home,goal;
    private int follower=-1,started=-1,arrived=-1;
    public boolean active(){return phase!=Phase.IDLE;}
    public void reset(){finish();attempted.clear();}
    public void finish(){phase=Phase.IDLE;home=goal=null;follower=started=arrived=-1;progress.reset();}
    public void rebase(int dx,int dy){if(home!=null)home=home.add(dx,dy);if(goal!=null)goal=goal.add(dx,dy);progress.reset();}
    private Mob eligible(Snapshot s) {
        if(s.meleeMode()||s.mobs().stream().anyMatch(m->m.kind()==Kind.JAD||m.kind()==Kind.HEALER))return null;
        return s.mobs().stream().filter(m->m.kind()==Kind.MELEER&&!attempted.contains(m.index()))
            .min(Comparator.comparingInt(m->m.distance(s.player()))).orElse(null);
    }
    public boolean startCorner(Snapshot s,Tile italy) {
        Mob big=eligible(s);
        if(active()||italy==null||big==null||s.player().distance(italy)>3
            ||big.tile().y()<italy.y()-3||big.tile().x()<italy.x()-4||big.tile().x()>italy.x()+6
            ||s.mobs().stream().noneMatch(m->m.kind()==Kind.RANGER&&m.tile().y()<italy.y()-5)
            ||s.mobs().stream().anyMatch(m->m.kind()==Kind.BAT&&CombatPlanner.playerCanAttack(s,s.player(),m)))return false;
        if(CaveSafety.trapped(s,big)&&CombatPlanner.playerCanAttack(s,s.player(),big))return false;
        Tile elbow=italy.add(6,0),wall=italy.add(-1,-7);
        if(!usable(s,elbow)||!usable(s,wall)||!CaveSafety.preservesMageCover(s,wall))return false;
        return start(s,big,italy,Phase.EAST_CORNER,elbow);
    }
    public boolean startFromNorth(Snapshot s,Tile italy) {
        Mob big=eligible(s);
        if(active()||italy==null||big==null||s.player().distance(italy.add(8,17))>1
            ||s.mobs().stream().anyMatch(m->m.kind()==Kind.RANGER||m.kind()==Kind.BAT&&m.distance(s.player())<=s.weaponRange()))return false;
        if(CaveSafety.trapped(s,big)&&CombatPlanner.playerCanAttack(s,s.player(),big))return false;
        boolean westMage=s.mobs().stream().anyMatch(m->m.kind()==Kind.MAGER&&m.tile().x()<italy.x()-15&&m.tile().y()>italy.y());
        boolean southMage=s.mobs().stream().anyMatch(m->m.kind()==Kind.MAGER&&m.tile().x()>=italy.x()-1&&m.tile().y()<italy.y()-8);
        Tile endpoint=westMage?italy.add(-6,16):italy.add(7,0);
        if((!westMage&&!southMage)||!usable(s,endpoint))return false;
        return start(s,big,italy,westMage?Phase.WEST_PASSAGE:Phase.EAST_HOLD,endpoint);
    }
    private boolean start(Snapshot s,Mob big,Tile italy,Phase next,Tile endpoint) {
        Plan preview=route(s,endpoint,"Recorded side route");
        if(!CombatPlanner.actionable(preview,false))return false;
        home=italy;goal=endpoint;phase=next;follower=big.index();attempted.add(follower);started=s.tick();arrived=-1;progress.reset();return true;
    }
    public Plan plan(Snapshot s) {
        if(!active())return null;
        Mob big=s.mobs().stream().filter(m->m.index()==follower).findFirst().orElse(null);
        if(big==null||s.tick()-started>80||progress.stalled(s,goal)){finish();return null;}
        // A usable trap already achieved (including manual input) ends the
        // excursion. Never leave it just to finish our preferred route.
        if(PureCombatPolicy.establishedMeleeTrap(s)!=null
            &&PureCombatPolicy.shelteredRangedAtWall(s)
            &&PureCombatPolicy.preferredShot(s,CombatPlanner.bestProtection(s.grid(),s.mobs(),s.player(),s.jadStyle()))!=null){finish();return null;}
        if(!s.player().equals(goal)) {
            Plan p=route(s,goal,"Complete recorded side lure");
            if(!CombatPlanner.actionable(p,false)){finish();return null;}
            return p;
        }
        if(arrived<0)arrived=s.tick();
        switch(phase) {
            case EAST_CORNER: return next(s,Phase.ITALY_WALL,home.add(-1,-7));
            case WEST_PASSAGE: return next(s,Phase.DRAGON_WALL,home.add(-18,4));
            case EAST_HOLD: phase=Phase.EAST_WAIT;break;
            case ITALY_WALL: case DRAGON_WALL:
                if(s.tick()>arrived){finish();return null;}
                break;
            default: break;
        }
        if(phase==Phase.EAST_WAIT) {
            // 19:39 recording: wait on the east shelf until the western follower
            // has crossed Italy, then return along the wall. A fixed three-tick
            // pause sends the player back while that distant follower is still west.
            if(big.tile().x()>=home.x()-1&&big.tile().y()+big.size()>home.y())
                return next(s,Phase.ITALY_WALL,home.add(-1,-6));
            if(s.tick()-arrived>=32){finish();return null;}
        }
        Protection p=CombatPlanner.bestProtection(s.grid(),s.mobs(),s.player(),s.jadStyle());
        int risk=CombatPlanner.immediateExposure(s,s.player(),p);
        return new Plan(s.player(),s.player(),p,-1,risk==0,0,0,risk,"Hold recorded side tile; observe follower arrival");
    }
    private Plan next(Snapshot s,Phase next,Tile endpoint) {
        if(!usable(s,endpoint)){finish();return null;}
        phase=next;goal=endpoint;arrived=-1;progress.reset();return plan(s);
    }
    private static boolean usable(Snapshot s,Tile p) {
        return s.grid().open(p)&&CaveSafety.clearOfMagers(s,s.mobs(),p)
            &&s.mobs().stream().noneMatch(m->m.occupies(p))&&!MinimapMovement.path(s,p).isEmpty();
    }
    private static Plan route(Snapshot s,Tile p,String reason) {
        return MinimapMovement.route(s,p,null,CombatPlanner.bestProtection(s.grid(),s.mobs(),s.player(),s.jadStyle()),false,reason);
    }
}
