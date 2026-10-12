/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape.core;

import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;

public final class PureLureController extends LureController {
    private boolean purePullUsed;
    private final RecordedSideLure sideLure=new RecordedSideLure();

    /** Spacing supersedes the old route; it must not send us back into the ranger. */
    public void rangerSpacingAccepted(){sideLure.finish();meleeTrap.reset();finishMeleeTrap();purePullUsed=true;}
    /** Keep the normal acquisition path after the single conservative pull.
     * A contact bat must never wait behind an owed return. */
    public boolean allowsImmediateShot(Snapshot s,Mob target) {
        if(target==null)return false;
        if(target.kind()==Kind.BAT||target.kind()==Kind.BABY&&s.grid().melee(target,s.player()))
            return !hasPendingReturn()||returnTile==null
            ||s.player().equals(returnTile)||s.grid().melee(target,s.player());
        return !hasPendingReturn()&&(purePullUsed||!PureCombatPolicy.crowded(s));
    }
    /** A planned stationary shot uses the existing prayer-safe acquisition wait. */
    public boolean allowsImmediateShot(Snapshot s,Mob target,Plan selected) {
        if(allowsImmediateShot(s,target))return true;
        return target!=null&&!hasPendingReturn()&&selected!=null&&selected.targetIndex()==target.index()
            &&s.player().equals(selected.destination())&&s.player().equals(selected.nextStep())
            &&CombatPlanner.actionable(selected,false)&&canShoot(s,target);
    }

    @Override void resetVariant(){purePullUsed=false;sideLure.reset();}
    @Override boolean excursionActive(){return sideLure.active();}
    @Override Plan excursionPlan(Snapshot s){return sideLure.plan(s);}
    @Override void excursionFinish(){sideLure.finish();}
    @Override void rebaseVariant(int dx,int dy){sideLure.rebase(dx,dy);}
    @Override Plan retainEstablishedTrap(Snapshot s,Tile main) {
        // A committed route is an intention, not evidence that another leg is
        // needed. Keep a rock trap even when its final settling step precedes LOS.
        Mob wallShot=PureCombatPolicy.settlingWallShot(s);
        if(wallShot!=null) {
            meleeTrap.reset();finishMeleeTrap();
            return canShoot(s,wallShot)?attack(s,wallShot,"Pure: preserve established melee trap and shoot"):
                hold(s,"Pure: let melee settle into existing wall shot");
        }
        if(phase==Phase.PULL&&goal!=null&&s.player().equals(goal)&&sideLure.startFromNorth(s,main)) {
            finishMeleeTrap();return sideLure.plan(s);
        }
        return retainedPosition(s);
    }
    @Override Plan retainedPosition(Snapshot s) {
        Mob established=PureCombatPolicy.establishedMeleeTrap(s);
        if(established==null||s.mobs().stream().anyMatch(m->m.kind()==Kind.BAT)
            ||!PureCombatPolicy.shelteredRangedAtWall(s))return null;
        meleeTrap.reset();finishMeleeTrap();
        Mob shot=preferredShot(s,protection(s,s.player()));
        if(shot!=null)return attack(s,shot,"Pure: preserve established rock trap");
        return firingApproach(s,established,home,"Pure: obtain wall shot without repeating lure");
    }
    @Override Plan contactShot(Snapshot s) {
        Mob contactTiny=preferredShot(s,protection(s,s.player()));
        if(contactTiny!=null&&(contactTiny.kind()==Kind.BABY||contactTiny.kind()==Kind.BAT)
            &&s.grid().melee(contactTiny,s.player())&&!hasPendingReturn())
            return attack(s,contactTiny,"Pure: clear contacting tiny monster without leaving cover");
        return null;
    }
    @Override Plan beforeRangerFirst(Snapshot s,Tile main,Tile pull) {
        if(!hasPendingReturn()&&sideLure.startCorner(s,main)) {
            finishMeleeTrap();return sideLure.plan(s);
        }
        if(!hasPendingReturn()) {
            Mob shot=preferredShot(s,protection(s,s.player()));
            if(shot!=null&&PureCombatPolicy.coveredMelee(s)
                &&s.mobs().stream().noneMatch(m->s.grid().melee(m,s.player())))
                return attack(s,shot,"Pure: keep rock cover and attack reachable monster");
            Mob ranged=rangedTarget(s);
            if(!purePullUsed&&ranged!=null&&PureCombatPolicy.crowded(s)
                &&main!=null&&pull!=null&&CaveSafety.preservesMageCover(s,pull)) {
                Plan preview=move(s,pull,"Pure: recorded northern pull before approaching crowded centre");
                if(CombatPlanner.actionable(preview,false)) {
                    purePullUsed=true;attempted=true;returnTile=main;phase=Phase.PULL;
                    return begin(s,pull,preview.reason());
                }
            }
        }
        return null;
    }
    @Override boolean canShoot(Snapshot s,Mob m){return m!=null&&CombatPlanner.playerCanAttack(s,s.player(),m)
        &&PureSafety.releaseSafe(s,m,protection(s,s.player()));}
    @Override Mob rangedTarget(Snapshot s){return PureCombatPolicy.rangedTarget(s);}
    @Override Mob preferredShot(Snapshot s,Protection prayer){return PureCombatPolicy.preferredShot(s,prayer);}
    @Override int targetPriority(Snapshot s,Mob m,Protection prayer){return PureCombatPolicy.targetPriority(s,m,prayer);}
    @Override boolean rangerBeforeBat(Snapshot s,Mob m){return PureCombatPolicy.rangerBeforeBat(s,m);}
    @Override Plan approach(Snapshot s,Mob target,Tile anchor,String reason){return PureCombatPolicy.approach(s,target,anchor,reason);}
}
