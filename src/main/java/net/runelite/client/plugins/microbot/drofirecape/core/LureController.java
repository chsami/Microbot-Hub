/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape.core;

import java.util.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;

/** Recorded intentions with live local route checks and finite fallback to the combat planner. */
public class LureController {
    enum Phase { MAIN, PULL, RETURN, NORTHWEST, PEEK, PEEK_RETURN, NW_EDGE, FIGHT }
    Phase phase=Phase.MAIN;
    Tile goal,returnTile,home,wall,north;
    private final LureProgress routeProgress=new LureProgress();
    final RecordedMeleeTrap meleeTrap=new RecordedMeleeTrap();
    private boolean northernRecoveryUsed;
    private int recoveryAttempts,rushTarget=-1,nwTarget=-1;
    private final Set<Integer> peekMelee=new HashSet<>();
    private final Set<Integer> southMages=new HashSet<>();
    private boolean rangerEngaged,centreReturnUsed;
    private final Set<Integer> previousBlobs=new HashSet<>(),wallAdjustments=new HashSet<>();
    private int splitSettleUntil=-1;
    private int phaseAt=-1,arrivedAt=-1;
    boolean attempted,dynamicOnly;
    private int recordedIndex,failedMoves;
    private final Map<Integer,Tile> lureStarts=new HashMap<>();
    private final Map<Integer,Tile> peekAtArrival=new HashMap<>();
    private final Set<Integer> blockedAtStart=new HashSet<>();
    public void reset(){resetVariant();previousBlobs.clear();wallAdjustments.clear();splitSettleUntil=-1;meleeTrap.reset();phase=Phase.MAIN;goal=returnTile=home=wall=north=null;routeProgress.reset();northernRecoveryUsed=false;recoveryAttempts=0;rushTarget=nwTarget=-1;peekMelee.clear();southMages.clear();peekAtArrival.clear();rangerEngaged=centreReturnUsed=false;phaseAt=arrivedAt=-1;attempted=false;dynamicOnly=false;recordedIndex=0;failedMoves=0;lureStarts.clear();blockedAtStart.clear();}
    /** Observe before the fast attack path so ranger death cannot become a melee shot in the centre. */
    public void observeFight(Snapshot s,Tile main,int currentTarget) {
        if(s.meleeMode()||main==null||meleeTrap.active()||excursionActive())return;
        home=main;
        if(wall==null)wall=main.add(-1,-5);
        if(previousBlobs.stream().anyMatch(id->find(s,id)==null))splitSettleUntil=s.tick()+4;
        previousBlobs.clear();for(Mob m:s.mobs())if(m.kind()==Kind.BLOB)previousBlobs.add(m.index());
        rememberSouthMages(s);
        Mob target=find(s,currentTarget);
        if(target!=null&&target.kind()==Kind.RANGER)rangerEngaged=true;
        if(s.mobs().stream().anyMatch(m->m.kind()==Kind.RANGER))return;
        if(!hasPendingReturn()&&s.mobs().stream().anyMatch(m->m.kind()==Kind.MELEER&&!wallAdjustments.contains(m.index()))) {
            Tile correction=CaveSafety.nearbyWallTrap(s,main);
            if(correction!=null) {
                for(Mob m:s.mobs())if(m.kind()==Kind.MELEER)wallAdjustments.add(m.index());
                returnTile=goal=correction;phase=Phase.PEEK_RETURN;phaseAt=s.tick();arrivedAt=-1;return;
            }
        }
        boolean afterRanger=rangerEngaged;rangerEngaged=false;
        boolean large=s.mobs().stream().anyMatch(m->(m.kind()==Kind.MELEER||m.kind()==Kind.BLOB)&&!trapped(s,m));
        if(!large||s.player().equals(main)||hasPendingReturn()
            ||!afterRanger&&(centreReturnUsed||rushTarget>=0||s.player().distance(main)<=3))return;
        centreReturnUsed=true;
        rushTarget=-1;dynamicOnly=false;attempted=false;recordedIndex=1;recoveryAttempts=0;
        returnTile=goal=main;phase=Phase.RETURN;phaseAt=s.tick();arrivedAt=-1;
    }
    /** Includes the settling interval needed to catch an already queued outward click. */
    public boolean hasPendingReturn(){return returnTile!=null||meleeTrap.active()||excursionActive();}
    /** A temporary empty scene must not replace an unfinished lure with next-wave positioning. */
    public Plan finishReturn(Snapshot s) {
        if(excursionActive())return excursionPlan(s);
        if(meleeTrap.active())return recordedMelee(s);
        if(!hasPendingReturn())return null;
        if(phase!=Phase.PEEK_RETURN&&phase!=Phase.RETURN) {
            phase=Phase.PEEK_RETURN;goal=returnTile;phaseAt=s.tick();arrivedAt=-1;
        }
        Plan returning=continueReturn(s);
        return returning==null?hold(s,"Return to cover confirmed"):returning;
    }
    public Plan decide(Snapshot s,CombatPlanner planner,int currentTarget,List<Tile> candidates) {
        return decide(s,planner,currentTarget,candidates,0);
    }
    public Plan decide(Snapshot s,CombatPlanner planner,int currentTarget,List<Tile> candidates,int shotDelay) {
        return decide(s,planner,currentTarget,candidates,shotDelay,
            candidates.isEmpty()?null:candidates.get(0),null,null,null);
    }
    public Plan decide(Snapshot s,CombatPlanner planner,int currentTarget,List<Tile> candidates,int shotDelay,
                       Tile main,Tile pull,Tile northwest,Tile recordedPeek) {
        return decide(s,planner,currentTarget,candidates,shotDelay,main,pull,northwest,recordedPeek,
            main==null?null:main.add(-1,-5));
    }
    public Plan decide(Snapshot s,CombatPlanner planner,int currentTarget,List<Tile> candidates,int shotDelay,
                       Tile main,Tile pull,Tile northwest,Tile recordedPeek,Tile recordedWall) {
        home=main;
        wall=recordedWall;
        north=pull;
        if(excursionActive()) {
            Plan side=excursionPlan(s);if(side!=null)return side;
        }
        observeFight(s,main,currentTarget);
        Plan escape=CaveSafety.escapeMage(s);
        if(escape!=null)return escape;
        Plan established=retainEstablishedTrap(s,main);
        if(established!=null)return established;
        if(goal!=null&&routeProgress.stalled(s,goal))return recoverRoute(s);
        if(main!=null&&!s.mobs().isEmpty()&&s.mobs().stream().allMatch(m->m.kind()==Kind.MAGER)) {
            Mob mage=rangedTarget(s);
            if(canShoot(s,mage)){finishMeleeTrap();return attack(s,mage,"Range remaining mage from current firing tile");}
            if(mage.tile().y()<main.y()&&(CaveSafety.active(s,mage)||trapped(s,mage))) {
                finishMeleeTrap();return trappedMageCleanup(s,mage);
            }
        }
        if(dynamicOnly)return null;
        if(s.mobs().isEmpty())return finishReturn(s);
        if(s.mobs().stream().anyMatch(m->m.kind()==Kind.JAD||m.kind()==Kind.HEALER))return null;
        Plan tiny=contactShot(s);
        if(tiny!=null)return tiny;
        // Prayer does not stop Tz-Kih prayer drain, even when the damage roll is zero.
        // Acquire every legal bat shot before considering any positioning action.
        Mob bat=s.mobs().stream().filter(m->m.kind()==Kind.BAT&&canShoot(s,m))
            .min(Comparator.comparingInt(m->m.distance(s.player()))).orElse(null);
        if(bat!=null&&s.mobs().stream().noneMatch(m->rangerBeforeBat(s,m))
            &&(returnTile==null||s.player().equals(returnTile)||s.grid().melee(bat,s.player())))
            return attack(s,bat,"Kill bat immediately; never lure or kite it");
        if(!hasPendingReturn()&&!meleeTrap.active()) {
            Mob retained=CaveSafety.retainedSafeShot(s,currentTarget);
            if(retained!=null)return attack(s,retained,"Keep productive trapped shot from current wall");
        }
        Plan cover=beforeRangerFirst(s,main,pull);
        if(cover!=null)return cover;
        if(!hasPendingReturn()) {
            Plan ranger=rangerFirst(s);if(ranger!=null)return ranger;
            if(s.tick()<=splitSettleUntil) {
                Mob shot=preferredShot(s,protection(s,s.player()));
                return shot==null?hold(s,"Hold wall while blob splits and stack settles"):attack(s,shot,"Keep wall shot while blob splits");
            }
        }
        if(meleeTrap.active())return recordedMelee(s);
        if((returnTile==null||phase==Phase.RETURN)&&meleeTrap.start(s,main,currentTarget,!northernRecoveryUsed)) {
            finishMeleeTrap();return recordedMelee(s);
        }
        if(returnTile!=null&&(phase==Phase.PEEK_RETURN||phase==Phase.RETURN)) {
            // After the ranger, draw both medium blobs and big melee north while
            // the southern mage stays behind Italy. Do not stop midway at home.
            if(phase==Phase.RETURN&&!northernRecoveryUsed&&needsNorthernPull(s)) {
                northernRecoveryUsed=true;phase=Phase.PULL;
                return begin(s,north,"Draw remaining melee north; keep southern mage behind Italy");
            }
            Plan returning=continueReturn(s);
            if(returning!=null)return returning;
        }
        if(returnTile!=null&&phase==Phase.PEEK&&peekMelee.isEmpty()&&!s.player().equals(returnTile)&&releasedForReturn(s)) {
            phase=Phase.PEEK_RETURN;return begin(s,returnTile,"Return to cover as soon as the trapped monster moves");
        }
        boolean contact=s.mobs().stream().anyMatch(m->s.grid().melee(m,s.player()));
        boolean contactNext=returnTile!=null&&phase==Phase.PEEK&&CombatPlanner.advance(s.grid(),s.mobs(),s.player(),s.jadStyle())
            .stream().anyMatch(m->m.kind()==Kind.MELEER&&peekMelee.contains(m.index())&&s.grid().melee(m,s.player()));
        if((contact||contactNext)&&returnTile!=null&&!s.player().equals(returnTile)
            &&phase==Phase.PEEK) {
            phase=Phase.PEEK_RETURN;return begin(s,returnTile,"Return to cover immediately; lure reached player");
        }
        if(returnTile==null&&goal==null) {
            Mob ranged=rangedTarget(s);
            if(ranged!=null&&heldSouthMage(s,ranged))ranged=null;
            if(ranged!=null) {
                Mob exposed=preferredShot(s,protection(s,s.player()));
                if(exposed!=null&&targetPriority(s,exposed,protection(s,s.player()))
                    >targetPriority(s,ranged,protection(s,s.player())))
                    return attack(s,exposed,"Dispatch exposed attacker before mage / trapped melee");
                if(canShoot(s,ranged))return attack(s,ranged,"Dispatch ranged threat before trapped melee");
                // A ranger does not follow a one/two-tile melee peek. Rush to firing range.
                boolean centerMelee=s.mobs().stream().anyMatch(m->m.kind()==Kind.MELEER&&!trapped(s,m));
                if(attempted||!centerMelee||pull==null)
                    return firingApproach(s,ranged,main,"Approach blocked ranged attacker under protection");
            }
        }
        if(returnTile==null&&rushTarget>=0) {
            Mob target=find(s,rushTarget);
            if(target!=null)return finishRush(s,target,main);
            rushTarget=-1;
        }
        if(returnTile==null&&goal==null) {
            Mob mage=s.mobs().stream().filter(m->m.kind()==Kind.MAGER&&heldSouthMage(s,m))
                .findFirst().orElse(null);
            boolean exposed=s.mobs().stream().anyMatch(m->m.kind()!=Kind.MAGER&&!trapped(s,m));
            if(mage!=null&&!exposed&&!CaveSafety.dragonMage(s,home,mage))return trappedMageCleanup(s,mage);
        }
        if(returnTile==null&&goal==null&&!attempted&&pull!=null
            &&s.mobs().stream().noneMatch(m->m.kind()==Kind.RANGER)
            &&(s.mobs().stream().anyMatch(m->heldSouthMage(s,m))
                &&s.mobs().stream().anyMatch(m->m.kind()!=Kind.MAGER&&!canShoot(s,m))
                ||s.mobs().stream().anyMatch(m->m.kind()==Kind.MAGER)
                &&s.mobs().stream().anyMatch(m->m.kind()==Kind.MELEER&&!trapped(s,m)))) {
            attempted=true;returnTile=main;phase=Phase.PULL;
            return begin(s,pull,"Keep south mage trapped; use recorded northern pull for remaining monsters");
        }
        if(phase==Phase.FIGHT)return fallback(s);
        if(contact&&goal==null)return fallback(s);
        if(goal!=null) {
            if(s.tick()-phaseAt>32){
                if(returnTile!=null&&!s.player().equals(returnTile)&&phase!=Phase.PEEK_RETURN&&phase!=Phase.RETURN) {
                    phase=Phase.PEEK_RETURN;return begin(s,returnTile,"Lure timeout: return to cover");
                }
                // A return remains owed even if the walker has timed out repeatedly.
                // Do not discard it and start shooting from the exposed peek tile.
                if(returnTile!=null){phase=Phase.PEEK_RETURN;return begin(s,returnTile,"Retry committed return to cover");}
                phase=Phase.FIGHT;goal=null;return fallback(s);
            }
            if(!s.player().equals(goal))return move(s,goal,"Continue to recorded lure destination");
            if(arrivedAt<0){arrivedAt=s.tick();if(phase==Phase.PEEK)rememberPeekArrival(s);if(phase==Phase.MAIN)phaseAt=s.tick();}
            if(phase==Phase.PULL) {
                // Rare demonstrated pull: center melee + NW ranger. Complete the south
                // pull before running to the user's NW firing tile, never bounce back.
                boolean nwRanger=northwest!=null&&s.mobs().stream().anyMatch(m->m.kind()==Kind.RANGER&&m.distance(northwest)<=12);
                boolean melee=s.mobs().stream().anyMatch(m->m.kind()==Kind.MELEER);
                if(s.tick()-arrivedAt>=3) {
                    phase=nwRanger&&melee?Phase.NORTHWEST:Phase.RETURN;
                    return begin(s,phase==Phase.NORTHWEST?northwest:returnTile==null?main:returnTile,"Finish recorded pull; move to firing corner");
                }
                return hold(s,"Hold recorded pull tile with protection");
            }
            if(phase==Phase.PEEK) {
                if(!peekMelee.isEmpty())return observedPeek(s);
                // Step out only long enough to clear the blocked face. One initial
                // sideways NPC step can still re-block when we return (recorded
                // wave-three blob); a real release or the short bound ends the peek.
                // Shooting from its tip must never cancel the committed return.
                boolean firingAtCover=returnTile!=null&&s.mobs().stream().anyMatch(m->canShootFrom(s,returnTile,m));
                boolean oneStepBat=returnTile!=null&&goal.distance(returnTile)==1&&s.mobs().stream().anyMatch(m->m.kind()==Kind.BAT);
                if((!oneStepBat&&firingAtCover)||releasedForReturn(s)||s.tick()-arrivedAt>=2) {
                    phase=Phase.PEEK_RETURN;
                    return begin(s,returnTile==null?main:returnTile,"Return promptly from recorded peek");
                }
                return hold(s,"Hold recorded peek briefly while the blocked face clears");
            }
            goal=null;
            if(phase==Phase.RETURN||phase==Phase.PEEK_RETURN||phase==Phase.NORTHWEST) {
                phase=Phase.MAIN;returnTile=null;phaseAt=s.tick();
            }
        }
        boolean rarePull=northwest!=null&&s.mobs().stream().anyMatch(m->m.kind()==Kind.RANGER&&m.distance(northwest)<=12)
            &&s.mobs().stream().anyMatch(m->m.kind()==Kind.MELEER);
        if(!attempted&&rarePull&&pull!=null){attempted=true;returnTile=main;phase=Phase.PULL;return begin(s,pull,"Rare recorded pull before NW ranger attack");}
        Tile opening=candidates.isEmpty()?main:candidates.get(0);
        Plan hold=planner.hold(s);
        if(hold!=null&&hold.targetIndex()>=0) {
            Mob target=find(s,currentTarget);
            Mob selected=preferredShot(s,hold.protection());
            if(target==null||!canShoot(s,target)||selected!=null&&targetPriority(s,selected,hold.protection())>targetPriority(s,target,hold.protection())+15)target=selected;
            if(target!=null)return attack(s,target,"Hold recorded firing tile; attack trapped monster");
        }
        if(opening!=null&&!s.player().equals(opening)&&phaseAt<0) {
            recordedIndex=1;return begin(s,opening,"Move to recorded wave opening");
        }
        if(phaseAt<0)phaseAt=s.tick();
        // Incoming bats are waited for and shot at maximum weapon range. Movement
        // cannot replace firing when they approach or are already draining prayer.
        if(s.mobs().stream().anyMatch(m->m.kind()==Kind.BAT)&&s.tick()-phaseAt<6)
            return hold(s,"Hold firing tile; acquire approaching bat");
        if(!attempted&&main!=null&&recordedPeek!=null&&s.player().equals(main)
            &&s.mobs().stream().anyMatch(m->m.kind()==Kind.BAT&&!canShoot(s,m)&&trapped(s,m))) {
            // The user's west peek is two tiles. A trapped bat needs only its first
            // tile; a visible or adjacent bat was already selected above.
            Tile batPeek=main.add(Integer.signum(recordedPeek.x()-main.x()),Integer.signum(recordedPeek.y()-main.y()));
            if(s.grid().step(main,batPeek)&&s.mobs().stream().noneMatch(m->m.occupies(batPeek))) {
                attempted=true;returnTile=main;phase=Phase.PEEK;
                return begin(s,batPeek,"One recorded step out for trapped bat; return immediately");
            }
        }
        if(!attempted&&s.tick()-phaseAt>=3) {
            // Advance through the supplied wave's observed stopping/firing tiles in order.
            // Never generate a new kite destination or replay a marked bad wave.
            while(recordedIndex<candidates.size()&&candidates.get(recordedIndex).equals(s.player()))recordedIndex++;
            if(recordedIndex<candidates.size()) {
                Tile next=candidates.get(recordedIndex++);
                returnTile=candidates.contains(s.player())?s.player():opening;
                phase=next.equals(pull)?Phase.PULL:Phase.PEEK;
                // Consume the recorded return in this excursion, not as another outward trip.
                if(recordedIndex<candidates.size()&&candidates.get(recordedIndex).equals(returnTile))recordedIndex++;
                return begin(s,next,"Follow recorded excursion with committed return");
            }
            boolean centerRanged=s.mobs().stream().anyMatch(m->(m.kind()==Kind.RANGER||m.kind()==Kind.MAGER)
                &&main!=null&&m.tile().x()<=main.x()+8&&m.tile().x()>=main.x()-10);
            boolean rare=northwest!=null&&s.mobs().stream().anyMatch(m->m.kind()==Kind.RANGER&&m.distance(northwest)<=12)
                &&s.mobs().stream().anyMatch(m->m.kind()==Kind.MELEER);
            if(pull!=null&&(centerRanged||rare)&&s.mobs().stream().anyMatch(m->m.kind()==Kind.MELEER)) {attempted=true;returnTile=main;phase=Phase.PULL;return begin(s,pull,"Use recorded center-spawn pull tile");}
            if(recordedPeek!=null&&s.mobs().stream().allMatch(m->m.kind()==Kind.BLOB||m.kind()==Kind.BABY||m.kind()==Kind.MELEER||heldSouthMage(s,m))) {
                // First let the approaching monster settle against Italy rock.
                // A peek before it is blocked just walks the player into it.
                boolean blocked=s.mobs().stream().anyMatch(m->m.kind()!=Kind.MAGER&&trapped(s,m));
                if(!blocked&&s.tick()-phaseAt<18)return hold(s,"Wait at Italy wall for melee to reach the rock");
                if(blocked) {
                    attempted=true;returnTile=main;phase=Phase.PEEK;
                    Tile destination=meleePeek(s,main,recordedPeek);
                    return begin(s,destination,"Recorded "+(destination.distance(main)==3?"three-step big melee":"two-step blob")+" peek after monster reaches Italy rock");
                }
            }
            attempted=true;phase=Phase.FIGHT;return fallback(s);
        }
        if(attempted&&goal==null&&returnTile==null)return fallback(s);
        return hold(s,"Hold recorded corner; observe approach");
    }
    /** A stalled lure yields to live planning after any owed return is completed. */
    public void recover(){
        excursionFinish();
        attempted=true;
        if(returnTile!=null){goal=returnTile;phase=Phase.PEEK_RETURN;arrivedAt=-1;phaseAt=-1;dynamicOnly=false;}
        else {goal=null;phase=Phase.FIGHT;dynamicOnly=true;}
    }
    /** Retry the recorded peek after a stall, without erasing a return already owed. */
    public Plan recoverRecorded(Snapshot s,Tile main,Tile peek) {
        if(excursionActive())return excursionPlan(s);
        home=main;
        Plan established=retainedPosition(s);
        if(established!=null)return established;
        if(meleeTrap.active())return recordedMelee(s);
        rememberSouthMages(s);
        if(wall==null&&main!=null)wall=main.add(-1,-5);
        if(hasPendingReturn()) {
            if(goal!=null&&routeProgress.stalled(s,goal))return recoverRoute(s);
            // A recovery timer cannot turn a committed northern pull into an
            // immediate return, recreating the same short out/back loop.
            if(phase==Phase.PULL&&s.tick()-phaseAt<=32) {
                if(!s.player().equals(goal))return move(s,goal,"Complete northern pull before returning to cover");
                if(arrivedAt<0)arrivedAt=s.tick();
                if(s.tick()-arrivedAt<3)return hold(s,"Hold northern pull while melee follows");
            }
            // The watchdog must not shorten the committed excursion while its
            // tracked monster is still moving around the rock.
            if(phase==Phase.PEEK&&!peekMelee.isEmpty()&&s.tick()-phaseAt<=32) {
                if(!s.player().equals(goal))return move(s,goal,"Complete committed melee peek before recovery");
                if(arrivedAt<0){arrivedAt=s.tick();rememberPeekArrival(s);}
                if(CombatPlanner.advance(s.grid(),s.mobs(),s.player(),s.jadStyle()).stream()
                    .noneMatch(m->m.kind()==Kind.MELEER&&peekMelee.contains(m.index())&&s.grid().melee(m,s.player())))return observedPeek(s);
            }
            return finishReturn(s);
        }
        if(s.mobs().isEmpty()||s.mobs().stream().anyMatch(m->m.kind()==Kind.JAD||m.kind()==Kind.HEALER))return null;
        Plan ranger=rangerFirst(s);if(ranger!=null)return ranger;
        if(s.tick()<=splitSettleUntil)return hold(s,"Hold wall while blob splits and stack settles");
        if(meleeTrap.start(s,main,-1,!northernRecoveryUsed)){finishMeleeTrap();return recordedMelee(s);}
        if(s.mobs().stream().anyMatch(m->m.kind()==Kind.MELEER)) {
            Mob mage=s.mobs().stream().filter(m->m.kind()==Kind.MAGER&&main!=null&&m.tile().y()<main.y()).findFirst().orElse(null);
            boolean exposed=s.mobs().stream().anyMatch(m->m.kind()!=Kind.MAGER&&!trapped(s,m));
            if(mage!=null&&!exposed)return trappedMageCleanup(s,mage);
            Plan shot=fallback(s);if(shot!=null)return shot;
            Mob big=s.mobs().stream().filter(m->m.kind()==Kind.MELEER).findFirst().orElse(null);
            return firingApproach(s,big,main,"Recorded trap exhausted: approach firing range once");
        }

        if(main!=null&&++recoveryAttempts>2) {
            boolean other=s.mobs().stream().anyMatch(m->!heldSouthMage(s,m));
            Mob target=s.mobs().stream().filter(m->!other||!heldSouthMage(s,m))
                .max(Comparator.comparingInt(m->targetPriority(s,m,Protection.MAGIC))).orElse(null);
            if(heldSouthMage(s,target))return trappedMageCleanup(s,target);
            rushTarget=target.index();dynamicOnly=false;goal=null;phase=Phase.FIGHT;
            return finishRush(s,target,main);
        }
        if(main==null||peek==null||!s.player().equals(main)||s.mobs().isEmpty()
            ||s.mobs().stream().anyMatch(m->m.kind()==Kind.JAD||m.kind()==Kind.HEALER||canShoot(s,m)))return null;
        boolean bat=s.mobs().stream().anyMatch(m->m.kind()==Kind.BAT&&trapped(s,m));
        boolean melee=s.mobs().stream().allMatch(m->m.kind()==Kind.BLOB||m.kind()==Kind.BABY||m.kind()==Kind.MELEER||heldSouthMage(s,m))
            &&s.mobs().stream().anyMatch(m->m.kind()!=Kind.MAGER&&trapped(s,m));
        if(!bat&&!melee)return null;
        Tile destination=bat?main.add(Integer.signum(peek.x()-main.x()),Integer.signum(peek.y()-main.y())):meleePeek(s,main,peek);
        if(s.mobs().stream().anyMatch(m->m.occupies(destination)))return null;
        Plan checked=TacticalMovement.route(s,destination,"Stalled monster: retry recorded "+(bat?"one-step bat":destination.distance(main)==3?"three-step big melee":"two-step blob")+" peek");
        if(!CombatPlanner.actionable(checked,false)||checked.nextStep().equals(s.player()))return null;
        attempted=true;dynamicOnly=false;failedMoves=0;phase=Phase.PEEK;returnTile=main;
        return begin(s,destination,checked.reason());
    }
    private static Tile meleePeek(Snapshot s,Tile main,Tile peek) {
        boolean big=s.mobs().stream().anyMatch(m->m.kind()==Kind.MELEER&&!canShootFrom(s,main,m)&&trapped(s,m));
        return big?main.add(3*Integer.signum(peek.x()-main.x()),3*Integer.signum(peek.y()-main.y())):peek;
    }
    private Plan observedPeek(Snapshot s) {
        boolean remaining=s.mobs().stream().anyMatch(m->peekMelee.contains(m.index()));
        boolean bigMoved=s.tick()>arrivedAt&&wall!=null&&s.mobs().stream().anyMatch(m->m.kind()==Kind.MELEER
            &&peekMelee.contains(m.index())&&peekAtArrival.containsKey(m.index())
            &&!peekAtArrival.get(m.index()).equals(m.tile())&&canShootFrom(s,wall,m)&&m.distance(wall)>=2);
        if(bigMoved&&CaveSafety.clearOfMagers(s,s.mobs(),wall)) {
            Plan checked=move(s,wall,"Big melee released; return to recorded Italy wall firing tile");
            if(CombatPlanner.actionable(checked,false)) {
                returnTile=wall;phase=Phase.PEEK_RETURN;return begin(s,wall,checked.reason());
            }
        }
        if(!remaining||s.tick()>arrivedAt&&releasedForReturn(s)||s.tick()-arrivedAt>=6) {
            phase=Phase.PEEK_RETURN;
            return begin(s,returnTile,s.tick()-arrivedAt>=6?"Melee lure stalled: return to cover":"Tracked melee released; return to cover");
        }
        return hold(s,goal.distance(returnTile)==3?"Hold three-tile lure; wait for observed big melee movement":
            "Hold two-tile lure; wait for observed medium blob movement");
    }
    private void rememberPeekArrival(Snapshot s) {
        for(Mob m:s.mobs())if(peekMelee.contains(m.index()))peekAtArrival.put(m.index(),m.tile());
    }
    private Plan finishRush(Snapshot s,Mob target,Tile main) {
        // Once a shot is available, protect and shoot. Searching for another
        // non-contact tile on every tick walks across the entire cave as it follows.
        return canShoot(s,target)?attack(s,target,"Protected fallback attack; hold firing tile"):
            CaveSafety.approach(s,target,main,"Stuck monster: approach a checked firing tile");
    }
    public void movementFailed(){
        if(excursionActive()){excursionFinish();finishMeleeTrap();return;}
        if(meleeTrap.active()){meleeTrap.failed();if(!meleeTrap.active())finishMeleeTrap();return;}
        // A blocked return is an intent, not permission to repeat an impossible click forever.
        // Recheck a failed route while retaining any owed return to cover.
        if(++failedMoves>=3){recover();return;}
        if(returnTile!=null) {
            goal=returnTile;phase=Phase.PEEK_RETURN;arrivedAt=-1;phaseAt=-1;
        }else {goal=null;phase=Phase.FIGHT;}
        attempted=true;
    }
    public void rebase(int dx,int dy) {
        meleeTrap.rebase(dx,dy);rebaseVariant(dx,dy);
        if(home!=null)home=home.add(dx,dy);
        if(wall!=null)wall=wall.add(dx,dy);
        if(goal!=null)goal=goal.add(dx,dy);
        if(returnTile!=null)returnTile=returnTile.add(dx,dy);
        if(north!=null)north=north.add(dx,dy);
        routeProgress.reset();
        lureStarts.replaceAll((index,tile)->tile.add(dx,dy));
        peekAtArrival.replaceAll((index,tile)->tile.add(dx,dy));
    }
    Plan begin(Snapshot s,Tile tile,String reason) {
        if(tile==null){phase=Phase.FIGHT;return fallback(s);}
        if(!CaveSafety.pocketAllowed(s,home,tile)||!CaveSafety.preservesMageCover(s,tile)) {
            finishMeleeTrap();
            Mob shot=preferredShot(s,protection(s,s.player()));
            return shot==null?hold(s,"Preserve trapped mage; wait for remaining melee at this rock"):
                attack(s,shot,"Preserve trapped mage; dispatch reachable attacker");
        }
        if(phase==Phase.PULL&&tile.equals(north))northernRecoveryUsed=true;
        if(phase==Phase.PEEK||phase==Phase.PULL) {
            lureStarts.clear();blockedAtStart.clear();peekMelee.clear();peekAtArrival.clear();
            for(Mob mob:s.mobs()) {
                lureStarts.put(mob.index(),mob.tile());
                if(phase==Phase.PEEK&&returnTile!=null
                    &&(tile.distance(returnTile)==2&&mob.kind()==Kind.BLOB||tile.distance(returnTile)==3&&mob.kind()==Kind.MELEER)
                    &&!canShootFrom(s,returnTile,mob))peekMelee.add(mob.index());
                if(!canShootFrom(s,returnTile,mob)&&trapped(s,mob))blockedAtStart.add(mob.index());
            }
        }
        goal=tile;phaseAt=s.tick();arrivedAt=-1;return move(s,tile,reason);
    }
    private boolean needsNorthernPull(Snapshot s) {
        return north!=null&&home!=null&&s.mobs().stream().noneMatch(m->m.kind()==Kind.RANGER)
            &&s.mobs().stream().anyMatch(m->m.kind()==Kind.MAGER&&m.tile().y()<home.y())
            &&s.mobs().stream().anyMatch(m->m.kind()==Kind.MELEER||m.kind()==Kind.BLOB)
            &&CaveSafety.pocketAllowed(s,home,north)&&CaveSafety.preservesMageCover(s,north);
    }
    /** An owed return is bounded: blocked routes must not starve every legal shot. */
    public Plan recoverBlockedRoute(Snapshot s,Tile main) {
        home=main;
        return recoverRoute(s);
    }
    private Plan recoverRoute(Snapshot s) {
        if(!northernRecoveryUsed&&needsNorthernPull(s)) {
            Plan preview=move(s,north,"Stalled return: draw melee to recorded northern pull");
            Plan command=MinimapMovement.route(s,north,preview.nextStep(),protection(s,north),false,preview.reason());
            northernRecoveryUsed=true;
            if(CombatPlanner.actionable(command,false)&&!routeProgress.visited(command.nextStep())) {
                routeProgress.reset();returnTile=home;phase=Phase.PULL;
                return begin(s,north,preview.reason());
            }
        }
        // If NPC footprints block even that route, dispatch the reachable blocker
        // under the existing prayer controller instead of recycling visited tiles.
        goal=returnTile=null;routeProgress.reset();phase=Phase.FIGHT;attempted=true;
        centreReturnUsed=true;dynamicOnly=false;arrivedAt=-1;
        peekMelee.clear();peekAtArrival.clear();lureStarts.clear();blockedAtStart.clear();
        Plan ranger=rangerFirst(s);
        if(ranger!=null&&CombatPlanner.actionable(ranger,false))return ranger;
        Mob target=preferredShot(s,protection(s,s.player()));
        if(target!=null){rushTarget=target.index();return attack(s,target,"Movement loop ended: attack reachable threat under protection");}
        rushTarget=-1;
        return fallback(s);
    }
    private Plan continueReturn(Snapshot s) {
        if(!CaveSafety.clearOfMagers(s,s.mobs(),returnTile)) {
            Tile anchor=home==null?returnTile:home;
            Tile replacement=null;
            for(Tile tile:List.of(anchor.add(8,4),anchor.add(-1,4),s.player())) {
                if(!CaveSafety.clearOfMagers(s,s.mobs(),tile))continue;
                Plan checked=move(s,tile,"Return to pocket boundary outside Ket-Zek melee range");
                if(CombatPlanner.actionable(checked,false)){replacement=tile;break;}
            }
            if(replacement==null)return CaveSafety.escapeMage(s);
            returnTile=replacement;arrivedAt=-1;
        }
        goal=returnTile;
        if(!s.player().equals(returnTile)) {
            arrivedAt=-1;
            return move(s,returnTile,"Return to the same safe tile before attacking");
        }
        // A failed EW request can still have a queued game click: the latest log
        // sees cover at tick168, then the outward step finally lands at tick169.
        // Retain the destination across three stable ticks at cover, rather than
        // treating that first cover snapshot as acknowledgement of a return.
        if(arrivedAt<0)arrivedAt=s.tick();
        if(s.tick()-arrivedAt<3) {
            Mob visible=preferredShot(s,protection(s,s.player()));
            return visible==null?hold(s,"Hold safe tile; confirm committed return"):
                attack(s,visible,"Attack from safe tile while confirming return");
        }
        goal=returnTile=null;phase=Phase.MAIN;phaseAt=s.tick();arrivedAt=-1;failedMoves=0;
        lureStarts.clear();blockedAtStart.clear();peekMelee.clear();peekAtArrival.clear();
        return null;
    }
    private boolean releasedForReturn(Snapshot s) {
        for(Mob mob:s.mobs()) {
            if(!peekMelee.isEmpty()&&!peekMelee.contains(mob.index()))continue;
            Tile start=lureStarts.get(mob.index());
            if(start!=null&&!start.equals(mob.tile())
                &&(canShootFrom(s,returnTile,mob)||blockedAtStart.contains(mob.index())&&approachesCover(s,mob.index())))return true;
        }
        return false;
    }
    private boolean approachesCover(Snapshot s,int target) {
        List<Mob> future=s.mobs();
        for(int tick=0;tick<18;tick++) {
            for(Mob mob:future)if(mob.index()==target&&canShootFrom(s,returnTile,mob))return true;
            List<Mob> moved=CombatPlanner.advance(s.grid(),future,returnTile,s.jadStyle());
            boolean changed=false;
            for(int index=0;index<future.size();index++)
                if(!future.get(index).tile().equals(moved.get(index).tile())){changed=true;break;}
            if(!changed)return false;
            future=moved;
        }
        return false;
    }
    private static boolean trapped(Snapshot s,Mob mob) {
        if(s.grid().melee(mob,s.player()))return false;
        for(Mob predicted:CombatPlanner.advance(s.grid(),s.mobs(),s.player(),s.jadStyle()))
            if(predicted.index()==mob.index())return predicted.tile().equals(mob.tile());
        return false;
    }
    private static Mob find(Snapshot s,int id){return s.mobs().stream().filter(m->m.index()==id).findFirst().orElse(null);}
    boolean canShoot(Snapshot s,Mob m){return CombatPlanner.playerCanAttack(s,s.player(),m);}
    void resetVariant(){}
    boolean excursionActive(){return false;}
    Plan excursionPlan(Snapshot s){return null;}
    void excursionFinish(){}
    void rebaseVariant(int dx,int dy){}
    Plan retainEstablishedTrap(Snapshot s,Tile main){return null;}
    Plan retainedPosition(Snapshot s){return null;}
    Plan contactShot(Snapshot s){return null;}
    Plan beforeRangerFirst(Snapshot s,Tile main,Tile pull){return null;}
    Mob rangedTarget(Snapshot s){return CaveSafety.rangedTarget(s);}
    Mob preferredShot(Snapshot s,Protection prayer){return CaveSafety.preferredShot(s,prayer);}
    int targetPriority(Snapshot s,Mob m,Protection prayer){return CaveSafety.targetPriority(s,m,prayer);}
    boolean rangerBeforeBat(Snapshot s,Mob m){return CaveSafety.lateAttackingRanger(s,m);}
    Plan approach(Snapshot s,Mob target,Tile anchor,String reason){return CaveSafety.approach(s,target,anchor,reason);}
    private static boolean canShootFrom(Snapshot s,Tile player,Mob mob){return player!=null&&!mob.occupies(player)&&mob.distance(player)<=s.weaponRange()&&s.grid().playerSight(player,mob);}
    static Protection protection(Snapshot s,Tile destination) {
        Protection p=CombatPlanner.bestProtection(s.grid(),s.mobs(),s.player(),s.jadStyle());
        if(p==Protection.NONE)p=CombatPlanner.protectionForNextTick(s,destination);
        Protection next=CombatPlanner.bestProtection(s.grid(),s.mobs(),destination,s.jadStyle());
        // Geometric protection stays on throughout a pull; do not flick based on
        // modeled NPC cooldowns while the walker is moving the player.
        boolean mage=s.mobs().stream().anyMatch(m->m.kind()==Kind.MAGER
            &&(m.distance(s.player())<=m.kind().range&&s.grid().sight(m,s.player())
                ||m.distance(destination)<=m.kind().range&&s.grid().sight(m,destination)));
        boolean ranger=s.mobs().stream().anyMatch(m->m.kind()==Kind.RANGER
            &&(m.distance(s.player())<=m.kind().range&&s.grid().sight(m,s.player())
                ||m.distance(destination)<=m.kind().range&&s.grid().sight(m,destination)));
        Protection selected=mage||p==Protection.MAGIC||next==Protection.MAGIC?Protection.MAGIC:ranger?Protection.RANGE:
            p==Protection.RANGE||next==Protection.RANGE?Protection.RANGE:p!=Protection.NONE?p:next;
        return destination.equals(s.player())?CaveSafety.contactProtection(s,selected):selected;
    }
    static Plan attack(Snapshot s,Mob target,String reason){Protection p=protection(s,s.player());int risk=CombatPlanner.immediateExposure(s,s.player(),p);return new Plan(s.player(),s.player(),p,target.index(),risk==0,0,0,risk,reason);}
    static Plan hold(Snapshot s,String reason){Protection p=protection(s,s.player());int risk=CombatPlanner.immediateExposure(s,s.player(),p);return new Plan(s.player(),s.player(),p,-1,risk==0,0,0,risk,reason);}
    static Plan move(Snapshot s,Tile goal,String reason){return MinimapMovement.route(s,goal,null,protection(s,goal),false,reason);}
    private Plan recordedMelee(Snapshot s) {
        Plan result=meleeTrap.plan(s);
        if(!meleeTrap.active())finishMeleeTrap();
        return result;
    }
    /** A completed/failed recorded big-melee route must not restart the old return loop. */
    public void finishMeleeTrap(){goal=returnTile=null;routeProgress.reset();phase=Phase.FIGHT;
        attempted=centreReturnUsed=true;dynamicOnly=false;arrivedAt=-1;rushTarget=-1;}
    private Plan fallback(Snapshot s) {
        Plan ranger=rangerFirst(s);if(ranger!=null)return ranger;
        Mob target=s.mobs().stream().filter(m->canShoot(s,m)).min(Comparator
            .comparingInt((Mob m)->-targetPriority(s,m,protection(s,s.player())))
            .thenComparingInt(m->m.distance(s.player()))).orElse(null);
        if(target!=null)return attack(s,target,"Failed lure: kill immediately with protection");
        Mob blockedMage=s.mobs().stream().filter(m->heldSouthMage(s,m)).findFirst().orElse(null);
        if(blockedMage!=null) {
            return trappedMageCleanup(s,blockedMage);
        }
        Mob remaining=s.mobs().stream().filter(m->m.kind()!=Kind.MAGER)
            .max(Comparator.comparingInt(m->targetPriority(s,m,protection(s,s.player())))).orElse(null);
        return remaining==null?null:firingApproach(s,remaining,home,"Approach remaining monster without restarting old lure");
    }
    private Plan rangerFirst(Snapshot s) {
        Mob ranger=rangedTarget(s);
        if(ranger==null||ranger.kind()!=Kind.RANGER)return null;
        Mob bat=s.mobs().stream().filter(m->m.kind()==Kind.BAT&&canShoot(s,m)).findFirst().orElse(null);
        if(bat!=null&&!rangerBeforeBat(s,ranger))return attack(s,bat,"Bat before ranger");
        return canShoot(s,ranger)?attack(s,ranger,"Dispatch ranger before melee"):
            firingApproach(s,ranger,home,"Approach blocked ranged attacker before melee cleanup");
    }
    Plan firingApproach(Snapshot s,Mob target,Tile anchor,String reason) {
        Plan p=approach(s,target,anchor,reason);
        // A deliberate mage firing approach is already a cleanup decision.
        // Do not interpret arrival there as an accidental escape from camp.
        if(p!=null&&target.kind()==Kind.MAGER&&CombatPlanner.actionable(p,false))centreReturnUsed=true;
        return p;
    }
    private Plan trappedMageCleanup(Snapshot s,Mob mage) {
        Mob shot=preferredShot(s,protection(s,s.player()));
        if(shot!=null)return attack(s,shot,"Use reachable shot before changing the trapped stack");
        Mob melee=s.mobs().stream().filter(m->m.kind()!=Kind.MAGER)
            .max(Comparator.comparingInt(m->targetPriority(s,m,Protection.MAGIC))).orElse(null);
        // A marker that cannot shoot is not a safe camp. Clear the trapped melee,
        // then approach the remaining mage with actual weapon range and LOS.
        if(melee!=null) {
            Plan approach=firingApproach(s,melee,home,"Clear trapped melee before approaching blocked mage");
            if(approach!=null)return approach;
            if(wall!=null&&!wall.equals(s.player())&&CaveSafety.clearOfMagers(s,s.mobs(),wall)
                &&CaveSafety.preservesMageCover(s,wall)) {
                Plan route=move(s,wall,"Use Italy wall to obtain a shot on trapped stack");
                if(CombatPlanner.actionable(route,false))return route;
            }
            // If the mage itself physically blocks every melee shot, take the
            // reachable ranged shot under protection instead of an infinite hold.
        }
        return firingApproach(s,mage,home,"Approach remaining mage within weapon range");
    }
    private boolean heldSouthMage(Snapshot s,Mob mage) {
        // Moving to the wall may expose its ranged attack before our shot is in
        // range. Keep the recorded trap intent instead of turning that into a rush.
        return mage!=null&&mage.kind()==Kind.MAGER
            &&(southMages.contains(mage.index())||CaveSafety.southTrappedMage(s,mage,home)
                ||CaveSafety.dragonMage(s,home,mage)
                ||CaveSafety.coveredMages(s).stream().anyMatch(m->m.index()==mage.index()));
    }
    private void rememberSouthMages(Snapshot s) {
        southMages.removeIf(id->find(s,id)==null);
        for(Mob mob:s.mobs())if(CaveSafety.southTrappedMage(s,mob,home))southMages.add(mob.index());
    }
}
