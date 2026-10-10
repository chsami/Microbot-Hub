/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape;

import javax.inject.Singleton;
import java.awt.Rectangle;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.util.magic.thralls.Rs2Thrall;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import java.util.*;
import java.util.concurrent.*;
import net.runelite.api.*;
import net.runelite.api.coords.*;
import net.runelite.api.events.*;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import net.runelite.client.plugins.microbot.drofirecape.core.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;

/** Optional combat policy; shared lifecycle helpers and state stay on this controller instance. */
@Singleton
public final class OptionalFirecapeScript extends DroFirecapeScript {
    public OptionalFirecapeScript(){super(true);}

    private final BrewHealing brewHealing=new BrewHealing();
    private final OpeningBatPolicy openingBats=new OpeningBatPolicy();
    private final PureHealerLure pureHealerLure=new PureHealerLure();
    private final HealerPullLease healerPullLease=new HealerPullLease();
    private String lastHealerLureState="";
    private final PureCombatPlanner purePlanner=new PureCombatPlanner();
    private final PureLureController pureLures=new PureLureController();
    private int pureWave=-1,pureFailedMoveTick=-1;
    private boolean pureResetAfterReturn;
    @Override protected boolean observeOnly(){return false;}
    @Override protected boolean strictZeroExposure(){return false;}
    @Override protected int weaponRangeSetting(){return 0;}
    @Override protected boolean rangingPotion(){return true;}
    @Override protected boolean demonstrationLures(){return true;}
    @Override protected boolean blowpipeSpecial(){return true;}
    @Override protected boolean useThralls(){return true;}
    @Override protected boolean exitOnDamage(){return false;}
    @Override protected boolean traceEnabled(){return config.recordRun();}
    @Override protected String startupDetail(){return "; Record run="+config.recordRun()+" (collision maps saved after cave decisions)";}
    @Override protected FcTickPrayers createTickPrayers(){return new OptionalTickPrayers(client,meleeMode,events::add);}
    @Override protected FcFrame captureFrame(){return FcFrame.captureOptional(client,0,meleeMode,jadStyle,attackTicks,attackStyles,dead);}
    @Override protected void resetVariantRun() {
        lastObservationTick=-1;lastObservedControls=lastObservedSettings=lastHealerLureState="";pureHealerLure.reset();healerPullLease.reset();openingBats.reset();
        pureRunAttempt=-1000;pureWave=pureFailedMoveTick=-1;pureResetAfterReturn=false;brewHealing.reset();pureSpacing.reset();purePlanner.reset();pureLures.reset();
    }
    @Override protected void resetVariantHealers(){pureHealerLure.reset();healerPullLease.reset();}
    @Override protected void resetVariantTransition(){brewHealing.reset();pureHealerLure.reset();healerPullLease.reset();}
    @Override protected void resetVariantPlanning(boolean resetLure){openingBats.reset();pureHealerLure.reset();healerPullLease.reset();pureTraceTick=-1;pureCombatRecovery.reset();pureRepositionWatchdog.reset();pureSpacing.reset();purePlanner.reset();if(resetLure){pureLures.reset();pureWave=pureFailedMoveTick=-1;pureResetAfterReturn=false;}}
    @Override protected void rebaseVariant(int dx,int dy){pureLures.rebase(dx,dy);}
    @Override protected void userTookControl(){if(pureHealerLure.active()){pureHealerLure.abandon("User/pause took control during the pull");movement.reset();}}
    @Override protected boolean cameraNeedsOffscreenTarget(){return !config.pureMode();}
    @Override protected boolean entryProtectionReady(){return actions.entryProtectionReady(config.prayerConservation());}
    @Override protected String entryProtectionStatus(){return "confirming the configured entry prayer state";}
    @Override protected boolean enterPredictedRotation(int rotation){return actions.enterPredictedRotation(entryGate,rotation,config.prayerConservation());}
    @Override protected Protection heldCaveProtection(Snapshot model,Protection protection) {
        // Opt-in remote/empty idle gaps may release a merely held prayer.
        // Explicit route guards and every mage/Jad safeguard remain intact.
        if(!(config.prayerConservation()&&protection==Protection.NONE&&PrayerConservation.remote(model)))
            return config.pureMode()?PureMageGuard.held(model,protection,requestedCaveProtection):
                HeldProtection.choose(model,protection,requestedCaveProtection);
        return protection;
    }
    @Override protected boolean beforeHealerCancellation(FcFrame f,Protection urgent) {
        if((!config.pureMode()||FcSupplyPolicy.criticalHealth(f.model,f.hp,f.maxHp))
            &&healingNeeded(f)&&supplies(f,urgent))return true;
        // Preserve the final trapped monster before immediate Pure shots can finish the wave.
        return !exitRequested&&config.pureMode()&&healWithSweets(f,urgent);
    }
    @Override protected boolean variantCombat(FcFrame f,Protection urgent) {
        return config.pureMode()&&!meleeMode
            &&f.model.mobs().stream().noneMatch(m->m.kind()==Kind.JAD)&&handlePureCombat(f,urgent);
    }
    @Override protected Boolean variantHealerPull(FcFrame f,Snapshot model,Protection urgent) {
        // Only the pure ranged owner may select the exposed-Jad Italy pull.
        // A trapped Jad or an untagged group never enters this controller.
        if(config.pureMode()&&!meleeMode) {
            Plan healerPlan=pureHealerLure.decide(model,f.recordedAnchor(RecordedLureBook.ITALY),
                healers.confirmedIndices(),f.healersTargetingJad,f.moving);
            String lureState=pureHealerLure.phase()+": "+pureHealerLure.reason();
            if(!lureState.equals(lastHealerLureState)) {
                lastHealerLureState=lureState;
                trace.event("pure-healer-lure","tick="+f.tick+" player="+model.player()+" "+lureState);
            }
            if(healerPlan!=null) {
                combatPhase=lureState;plan=healerPlan;prayerPlanTick=f.tick;
                healerRetreat=healerPlan.destination();
                Protection protection=urgent!=Protection.NONE?urgent:healerPlan.protection();
                if(!protectInCave(protection))return true;
                if(movement.pending()) {
                    // Validate the actual outstanding click, not a newly chosen
                    // alternative path while the old command is still running.
                    Plan remaining=MinimapMovement.healerChecked(model,healerRetreat,movement.destination(),
                        protection,healerPlan.reason());
                    if(!CombatPlanner.actionable(remaining,false)) {
                        pureHealerLure.abandon("Outstanding healer route became unsafe: "+remaining.reason());
                        healerPlan=null;
                    } else {
                        plan=remaining;movePlan(f,remaining);status=remaining.reason();return true;
                    }
                }
                if(healerPlan!=null) {
                    if(!healerPlan.nextStep().equals(model.player()))movePlan(f,healerPlan);
                    else if(healerPlan.targetIndex()>=0) {
                        int targetIndex=healerPlan.targetIndex();
                        Mob boss=model.mobs().stream().filter(m->m.index()==targetIndex).findFirst().orElse(null);
                        if(boss!=null&&(f.interactingIndex!=boss.index()||forceReengage))
                            dispatchAttack(f,boss,true,"verified Italy healer separation");
                    }
                    status=healerPlan.reason();return true;
                }
            }
            if(pureHealerLure.attempted()) {
                // Bounded failure, not success. Keep the old command ledger
                // until the existing Jad-protected cancellation observes a stop.
                healers.lureComplete();healerPullLease.reset();lastPlanTick=-1;forceReengage=true;
                trace.event("pure-healer-fallback",pureHealerLure.reason());
                if(cancelHealerRetreat(f))return true;
                healerRetreat=null;return false;
            }
        }
        return null;
    }
    @Override protected boolean healerPullExpired(FcFrame f,Snapshot model,Plan retreat) {
        if(config.pureMode()&&!healerPullLease.allow(f.tick,model.player(),healerRetreat,
            CombatPlanner.actionable(retreat,false))) {
            trace.event("healer-pull-expired","tick="+f.tick+" goal="+healerRetreat+" reason="+retreat.reason());
            movement.reset();healerRetreat=null;healers.lureComplete();healerPullLease.reset();
            lastPlanTick=-1;forceReengage=true;return true;
        }
        return false;
    }
    @Override protected boolean batFirstAllowed(){return !config.pureMode();}
    @Override protected CombatPlanner attackPlanner(FcFrame current){return config.pureMode()&&!meleeMode&&!hasJad(current)?purePlanner:planner;}
    @Override protected boolean primesWithoutOffence(){return config.prayerConservation();}
    @Override protected MoveOwner moveOwner(FcFrame current,Plan p) {
        boolean spacingPure=config.pureMode()&&!meleeMode&&!hasJad(current)&&pureSpacing.owns(p);
        boolean recoveringPure=config.pureMode()&&!meleeMode&&!hasJad(current)&&!spacingPure&&pureCombatRecovery.owns(p);
        boolean exposedHealerPull=config.pureMode()&&!meleeMode&&hasJad(current)&&pureHealerLure.owns(p);
        return new MoveOwner(spacingPure,recoveringPure,exposedHealerPull);
    }
    @Override protected void moveRejected(FcFrame current,Snapshot live,Plan p,MoveOwner owner) {
        if(owner.spacing)pureSpacing.yieldToRecovery(current.tick);
        if(config.pureMode()&&!meleeMode&&!hasJad(current)&&!exitRequested) {
            pureCombatRecovery.rejected(live,p);lastFailedMoveTick=current.tick;
        }
    }
    @Override protected boolean variantRouteRejected(FcFrame current,Snapshot live,Plan checked,MoveOwner owner) {
        if(config.pureMode()&&!meleeMode&&!hasJad(current)&&!exitRequested
            &&!(owner.recovering?pureCombatRecovery.routeAllowed(live,checked.nextStep()):PureSafety.routeAllowed(live,checked.nextStep()))) {
            pureLures.movementFailed();purePlanner.widenSearch();lastPlanTick=-1;
            pureCombatRecovery.rejected(live,checked);lastFailedMoveTick=current.tick;
            if(owner.spacing)pureSpacing.yieldToRecovery(current.tick);
            status="Pure: rejecting newly exposed lane / contact endpoint";
            trace.event("pure-route-rejected",checked.reason());return true;
        }
        return false;
    }
    @Override protected boolean keepsFightingWhenDepleted(){return config.pureMode();}
    @Override protected boolean supplyCritical(FcFrame f,boolean healing) {
        return healing||brewDebt>0||f.prayer<=8||FcSupplyPolicy.criticalHealth(f.model,f.hp,f.maxHp);
    }
    @Override protected SupplyChoice prioritySupply(FcFrame f,boolean sweets,boolean healing) {
        FcFrame.ItemSlot item=null;String action="Drink";
        // Keep the chosen dose through required prayer/tab preemption. A critical
        // prayer shortage may replace a pending heal; optional boosts may not.
        if(item==null&&f.prayer>8&&supplyPreparation.pending()) {
            item=f.inventory.stream().filter(i->i.id()==supplyPreparation.itemId()).findFirst().orElse(null);
            if(item!=null&&item.name().toLowerCase(Locale.ROOT).startsWith("saradomin brew(")
                &&(!healing||!brewAllowed(f)))item=null;
            if(PureSupplyBudget.restoreFirst(f.prayer,brewDebt)&&item!=null
                &&!isPrayerPotion(item.name()))item=null;
            if(healing&&item!=null&&!item.name().toLowerCase(Locale.ROOT).startsWith("saradomin brew(")
                &&!isPrayerPotion(item.name())&&!item.hasAction("Eat"))item=null;
            if(item!=null&&item.hasAction("Eat"))action="Eat";
        }
        boolean healingFinished=!healing;
        boolean restoreNeeded=f.prayer<=config.restorePrayer()
            ||brewDebt>=3||healingFinished&&brewDebt>0;
        // A healing batch wins over nonurgent supplies. Restore between batches of
        // three confirmed brews, or immediately when prayer is nearly depleted.
        if(healing&&brewAllowed(f)) {
            item=f.inventory.stream().filter(i->i.name().toLowerCase(Locale.ROOT).startsWith("saradomin brew(")).findFirst().orElse(null);
            action="Drink";
        }
        if(item==null&&(restoreNeeded||(combatStatsDrained(f)&&(brewDebt>=3||healingFinished||sweets&&!FcSupplyPolicy.criticalHealth(f.model,f.hp,f.maxHp))&&f.inventory.stream().anyMatch(i->i.name().toLowerCase(Locale.ROOT).startsWith("super restore("))))) {
            // Prefer a super restore after brews, but never ignore remaining prayer
            // potions when prayer is low simply because ranged stats are also drained.
            if(combatStatsDrained(f)||brewDebt>0&&restoreNeeded)item=f.inventory.stream()
                .filter(i->i.name().toLowerCase(Locale.ROOT).startsWith("super restore("))
                .findFirst().orElse(null);
            if(item==null&&f.prayer<=config.restorePrayer())item=f.inventory.stream()
                .filter(i->isPrayerPotion(i.name())).findFirst().orElse(null);
            if(item==null&&f.prayer<=3&&!config.pureMode()){fail("Prayer supplies exhausted");return null;}
        }
        if(item==null&&healing) {
            if(brewAllowed(f))item=f.inventory.stream()
                .filter(i->i.name().toLowerCase(Locale.ROOT).startsWith("saradomin brew(")).findFirst().orElse(null);
            if(item==null) {
                item=f.inventory.stream().filter(i->i.hasAction("Eat")&&!FcSupplyPolicy.sweet(i.name())).findFirst().orElse(null);
                if(item!=null)action="Eat";
            }
        }
        return new SupplyChoice(item,action);
    }
    @Override protected boolean sweetsWaitForLureReturn(){return !config.pureMode();}
    @Override protected boolean recoveryPauseUnneeded(FcFrame f){return config.pureMode()&&!PrayerConservation.recoveryNeeded(f.hp,f.maxHp);}
    @Override protected String pauseRequestDetail(FcFrame f) {
        return " HP="+f.hp+"/"+f.maxHp+" prayer="+f.prayer+"/"+f.maxPrayer+" pureNeedsRecovery="+config.pureMode();
    }
    @Override protected FcRecoveryPolicy.Decision recoveryDecision(FcFrame f) {
        OptionalRecoveryPolicy.Decision recovery=OptionalRecoveryPolicy.choose(f,meleeMode,config.usePurpleSweets(),
            config.recoveryOverbrew()&&energyPause.nextWave()>=53,40,config.recoveryPrayerPercent(),true,
            true,energyPause.nextWave(),brewDebt,supplyAck.pending(),config.pureMode());
        return new FcRecoveryPolicy.Decision(recovery.item,recovery.action,recovery.status,recovery.ready);
    }
    @Override protected void recoveryReady(FcFrame f,String status) {
        trace.event("recovery-ready",status+"; HP="+f.hp+"/"+f.maxHp+" prayer="+f.prayer+"/"+f.maxPrayer);
    }
    @Override protected String resumeStatus() {
        return config.pureMode()&&frame!=null&&frame.prayer==0?
            "Fresh cave observed; continuing Pure run with exhausted prayer":super.resumeStatus();
    }
    @Override protected void prepareTickDriver(FcTickPrayers driver) {
        driver.nativeWidgets(config!=null&&config.nativeTickPrayers());
        driver.pureTinyPrayers(config!=null&&config.pureMode());
        driver.conservation(config!=null&&config.prayerConservation());
        driver.pureWave(waves.wave());
    }
    @Override protected void afterTickDriver(FcTickPrayers driver) {
        if(config!=null&&config.nativeTickPrayers())
            driver.clientTick(prayerInputAllowed(),config!=null&&config.offensivePrayer(),movement.pending());
    }
    @Override protected void configureClientTickDriver(FcTickPrayers driver) {
        driver.nativeWidgets(config!=null&&config.nativeTickPrayers());
        driver.pureTinyPrayers(config!=null&&config.pureMode());
        driver.conservation(config!=null&&config.prayerConservation());
    }
    @Override protected Protection attackAnimation(Kind kind,int animation){return AttackClock.animationWithTiny(kind,animation);}
    private int lastObservationTick=-1;
    private String lastObservedControls="",lastObservedSettings="";
    @Override
    protected void captureObservation(FcFrame f,String controls) {
        if(f==null||!f.cave)return;
        String settings="pure="+config.pureMode()+" native="+config.nativeTickPrayers()+" melee="+meleeMode
            +" offence="+config.offensivePrayer()+" effectiveOffence="+offenceEnabled(f)+" conservation="+config.prayerConservation()+" recovery="+config.energyPause()+" rotationPreference="+config.entryRotation();
        if(!settings.equals(lastObservedSettings)){lastObservedSettings=settings;trace.event("settings-observed","tick="+f.tick+" "+settings);}
        if(!controls.equals(lastObservedControls)){lastObservedControls=controls;trace.event("control-ownership","tick="+f.tick+" "+controls+"; detector state, not attribution of every input");}
        if(lastObservationTick==f.tick)return;
        lastObservationTick=f.tick;
        trace.observation(f,waves.wave(),plan,controls,combatPhase,healers.phase()+" "+healers.progress(),
            pureHealerLure.phase().name(),tickPrayers==null?Protection.NONE:tickPrayers.requested());
    }
    /** The established acquisition/window logic, with Pure-only lure ownership. */
    protected boolean tryPureImmediateAttack(FcFrame f,Snapshot model,Protection urgent) {
        if(f.moving||movement.pending()||model.mobs().isEmpty()||model.mobs().stream().anyMatch(m->m.kind()==Kind.JAD||m.kind()==Kind.HEALER))return false;
        Mob target=null;int best=Integer.MIN_VALUE;
        Protection protect=actions.attackProtection(desiredCaveProtection(urgent!=Protection.NONE?urgent:CombatPlanner.protectionForNextTick(model,model.player())));
        for(Mob m:model.mobs())if(CombatPlanner.playerCanAttack(model,model.player(),m)) {
            int score=PureCombatPolicy.targetPriority(model,m,protect)+(m.index()==f.interactingIndex?15:0)-m.distance(model.player());
            if(score>best){target=m;best=score;}
        }
        if(target==null||f.interactingIndex==target.index()&&!forceReengage)return false;
        if(!pureLures.allowsImmediateShot(model,target,plan))return false;
        Mob ranged=PureCombatPolicy.rangedTarget(model);
        Mob retained=CaveSafety.retainedSafeShot(model,f.interactingIndex);
        if(ranged!=null&&(target.kind()!=Kind.BAT||PureCombatPolicy.rangerBeforeBat(model,ranged))&&PureCombatPolicy.targetPriority(model,ranged,protect)
            >PureCombatPolicy.targetPriority(model,target,protect)&&(retained==null||retained.index()!=target.index()))return false;
        // Apply the configured exposure policy under the tick controller's overhead.
        if(!purePlanner.attackAllowed(model,target,protect,false))return false;
        int risk=CombatPlanner.immediateExposure(model,model.player(),protect);
        plan=new Plan(model.player(),model.player(),protect,target.index(),risk==0,0,0,risk,"Immediate protected attack");
        prayerPlanTick=f.tick;
        // The ordinary loop can repeatedly land during a protection switch. Wait
        // briefly on this background worker for the existing driver's safe input
        // window instead of making target acquisition depend on that poll phase.
        // No input lock is held; the prayer worker keeps its original schedule.
        FcTickPrayers driver=tickPrayers;
        if(driver!=null&&driver.ownsInput()&&!hasJad(f)) {
            net.runelite.client.plugins.microbot.util.Global.sleepUntil(()->{
                FcFrame live=frame;
                return !enabled||Microbot.pauseAllScripts.get()||InputArbiter.isHuman()
                    ||live==null||!live.sameScene(f)||live.moving||movement.pending()
                    ||driver.attackInputWindow(live.tick,ATTACK_INPUT_BUDGET_MS);
            },()->{},260,8);
        }
        FcFrame ready=dispatchFrame(f);
        if(ready==null)return true;
        protect=actions.attackProtection(protect);
        if(!protectInCave(protect)&&ready.prayer>0){status="Preparing "+protect+" for "+target.kind().name;return true;}
        boolean sent=dispatchAttack(ready,target,true,"Pure / regular acquisition window");
        status=sent?"Attack requested: "+target.kind().name:"Checking attack acknowledgement / current NPC";
        combatPhase="Protected attack";
        return true;
    }
    @Override
    protected void observeSupply(FcFrame f) {
        if(!f.containersReady)return;
        for(SupplyAck.Consumption consumed:supplyAck.observeDoses(
            supplyCount(f,SupplyAck.Kind.BREW,-1),supplyCount(f,SupplyAck.Kind.RESTORE,-1),f.capturedAt)) {
            if(consumed.kind()==SupplyAck.Kind.BREW){brewDebt++;lastBrewTick=f.tick;brewHealing.confirmed();}
            else brewDebt=0;
            recoverySupplyMisses=0;forceReengage=true;
            trace.event("supply-observed","tick="+f.tick+" item="+consumed.itemId()+" brewDebt="+brewDebt);
        }
        if(!supplyAck.pending())return;
        SupplyAck.Result result=supplyAck.observe(supplyCount(f,supplyAck.kind(),supplyAck.itemId()),f.tick,f.capturedAt,
            !hasJad(f)&&supplyAck.kind()==SupplyAck.Kind.BREW?2:5);
        if(result==SupplyAck.Result.CONSUMED) {
            recoverySupplyMisses=0;
            if(supplyAck.kind()==SupplyAck.Kind.OTHER)
                trace.event("supply-observed","tick="+f.tick+" item="+supplyAck.itemId()+" brewDebt="+brewDebt);
            supplyAck.clearPending();forceReengage=true;
        } else if(result==SupplyAck.Result.TIMED_OUT) {
            trace.event("supply-unconfirmed","tick="+f.tick+" item="+supplyAck.itemId()+"; retain late dose evidence, retry allowed");
            if(energyPause.recovering()){recoverySupplyMisses++;recoveryRetryAt=System.currentTimeMillis()+3000;}
            supplyAck.clearPending();
        }
    }
    protected static int supplyCount(FcFrame f,SupplyAck.Kind kind,int id) {
        if(kind==SupplyAck.Kind.OTHER)return f.count(id);
        String prefix=kind==SupplyAck.Kind.BREW?"saradomin brew(":"super restore(";
        int doses=0;
        for(FcFrame.ItemSlot item:f.inventory) {
            String name=item.name().toLowerCase(Locale.ROOT);
            if(!name.startsWith(prefix)||name.length()!=prefix.length()+2||!name.endsWith(")"))continue;
            int count=name.charAt(prefix.length())-'0';
            if(count>=1&&count<=4)doses+=count*item.quantity();
        }
        return doses;
    }
    @Override
    protected void supplyRequested(FcFrame f,FcFrame.ItemSlot item) {
        String name=item.name().toLowerCase(Locale.ROOT);
        SupplyAck.Kind kind=name.startsWith("saradomin brew(")?SupplyAck.Kind.BREW:
            name.startsWith("super restore(")?SupplyAck.Kind.RESTORE:SupplyAck.Kind.OTHER;
        FcFrame dispatched=frame;
        int tick=dispatched!=null&&dispatched.sameScene(f)?Math.max(f.tick,dispatched.tick):f.tick;
        supplyAck.sent(item.id(),supplyCount(f,kind,item.id()),tick,kind,System.currentTimeMillis());
    }
    protected boolean pureSweetsAvailable(FcFrame f) {
        return f.cave&&config.pureMode()&&config.usePurpleSweets()
            &&f.inventory.stream().anyMatch(i->FcSupplyPolicy.sweet(i.name())&&i.quantity()>0&&i.hasAction("Eat"));
    }
    @Override
    protected boolean healingNeeded(FcFrame f) {
        if(pureSweetsAvailable(f)) {
            boolean safe=f.tick>=sweetRetryTick&&!f.moving&&!movement.pending()
                &&!hasJad(f)&&FcSupplyPolicy.sweetPauseSafe(f.model);
            boolean nonurgent=f.hp*100>f.maxHp*config.eatPercent()
                &&!FcSupplyPolicy.criticalHealth(f.model,f.hp,f.maxHp);
            if(safe||nonurgent){brewHealing.cancel();return false;}
        }
        boolean hasBrew=f.inventory.stream().anyMatch(i->i.name().toLowerCase(Locale.ROOT).startsWith("saradomin brew("));
        boolean hasHealing=hasBrew||f.inventory.stream().anyMatch(i->i.hasAction("Eat")&&!FcSupplyPolicy.sweet(i.name()));
        boolean topUp=f.prayer<=config.restorePrayer()&&brewDebt==0&&!f.moving&&!movement.pending()
            &&FcSupplyPolicy.sweetPauseSafe(f.model);
        return brewHealing.needed(f.hp,f.maxHp,config.eatPercent(),topUp,hasHealing,
            config.recoveryOverbrew()&&hasBrew,waves.wave());
    }
    protected boolean brewAllowed(FcFrame f) {
        boolean restoreAvailable=f.inventory.stream().anyMatch(i->isPrayerPotion(i.name()));
        return PureSupplyBudget.brewAllowed(f.prayer,brewDebt,config.pureMode(),restoreAvailable);
    }
    @Override
    protected boolean recoveryProtectionReady(FcFrame f,Protection guard) {
        if(config.pureMode()&&f.prayer==0&&tickPrayers!=null&&tickPrayers.exhaustedCombatReady())return true;
        return actions.overheadActive(guard)
            &&(tickPrayers==null||!tickPrayers.ownsInput()||tickPrayers.protectionReady());
    }
    /** Pure-only copy of the original combat dispatch, using isolated conservative planner/lure state. */
    private final PureSpacing pureSpacing=new PureSpacing();
    private int pureRunAttempt=-1000,pureTraceTick=-1;
    private final PureRepositionWatchdog pureRepositionWatchdog=new PureRepositionWatchdog();
    private final PureCombatRecovery pureCombatRecovery=new PureCombatRecovery();
    private final PureRangerSpacing pureRangerSpacing=new PureRangerSpacing();
    private FcModel.Tile pureRangerSpacingDestination;
    protected boolean handlePureCombat(FcFrame f,Protection urgent) {
        boolean handled=handlePureCombatOwned(f,urgent);
        if(handled) {
            // The borrowed dispatch helpers observe the regular lure ledger.
            // Pure owns these shots: discard those side effects before yielding
            // so disabling Pure cannot inherit a return Pure never committed.
            lures.reset();planner.reset();
        }
        return handled;
    }
    /** Scoped opt-in only; supply service precedes this branch. */
    protected boolean handleOpeningBats(FcFrame f,Snapshot spatial) {
        Plan opening=openingBats.decide(spatial,f.recordedAnchor(RecordedLureBook.ITALY),
            movement.pending()?movement.destination():null);
        if(opening==null)return false;
        plan=opening;prayerPlanTick=f.tick;combatPhase="Pure / opening bat conservation";status=opening.reason();
        if(f.tick!=pureTraceTick){trace.decision(f,waves.wave(),plan);pureTraceTick=f.tick;}
        if(!protectInCave(opening.protection()))return true;
        if(!opening.nextStep().equals(spatial.player())){movePlan(f,opening);return true;}
        if(movement.pending()||f.moving) {
            // An attack click must not extend an old run into contact. Stop once
            // on the current tile, then acquire on a fresh stationary frame.
            if(lastMoveTick!=f.tick&&actions.move(f,spatial.player(),false)) {
                movement.reset();lastMoveTick=f.tick;forceReengage=true;
            }
            return true;
        }
        Mob target=spatial.mobs().stream().filter(m->m.index()==opening.targetIndex()).findFirst().orElse(null);
        if(target!=null&&(f.interactingIndex!=target.index()||forceReengage)) {
            if(dispatchAttack(f,target,forceReengage,"opening bat acquisition"))forceReengage=false;
        }
        return true;
    }
    protected boolean handlePureCombatOwned(FcFrame f,Protection urgent) {
        if(pureWave!=waves.wave()) {
            pureWave=waves.wave();pureTraceTick=-1;pureCombatRecovery.reset();purePlanner.reset();pureRepositionWatchdog.reset();pureRangerSpacing.reset();pureRangerSpacingDestination=null;pureSpacing.reset();
            if(pureLures.hasPendingReturn())pureResetAfterReturn=true;else pureLures.reset();
        }
        Snapshot spatial=withTaggedHealer(f.model);
        boolean recovering=pureCombatRecovery.active();
        // Retain combat debt across spacing intentions. Only observed player
        // progress on a bounded committed leg earns the existing movement grace.
        FcModel.Tile committedRoute=pureSpacing.destination()!=null?pureSpacing.destination():
            pureLures.hasPendingReturn()&&plan!=null?plan.destination():null;
        pureCombatRecovery.observe(spatial,committedRoute);
        if(!recovering&&pureCombatRecovery.active()) {
            movement.reset();pureSpacing.yieldToRecovery(f.tick);pureRangerSpacing.reset();pureRangerSpacingDestination=null;
            pureLures.rangerSpacingAccepted();pureResetAfterReturn=false;forceReengage=true;
            trace.event("pure-recovery","No damage or kill progress; committed lure stalled or expired; acquiring a checked firing position");
        }
        // Critical supplies cannot be starved by repeated positioning/target
        // acquisition returns. Protection remains owned by the tick writer.
        if((f.prayer<=config.restorePrayer()||f.hp*100<=f.maxHp*config.eatPercent()||brewDebt>=3)
            &&protectInCave(urgent)&&supplies(f,urgent))return true;
        if(config.prayerConservation()&&handleOpeningBats(f,spatial))return true;
        if(movement.pending()&&!spatial.mobs().isEmpty()) {
            Protection overhead=desiredCaveProtection(urgent);
            boolean recoveryOwnsRoute=pureCombatRecovery.owns(plan)&&!pureSpacing.owns(plan);
            FcModel.Tile intent=recoveryOwnsRoute?pureCombatRecovery.destination():
                pureSpacing.owns(plan)?pureSpacing.destination():movement.destination();
            Plan pending=MinimapMovement.checked(spatial,intent,movement.destination(),overhead,
                "Pure: continue acknowledged firing route");
            if(!CombatPlanner.actionable(pending,false)||!(recoveryOwnsRoute?pureCombatRecovery.routeAllowed(spatial,movement.destination()):
                    PureSafety.routeAllowed(spatial,movement.destination()))) {
                if(protectInCave(overhead)&&actions.move(f,spatial.player())) {
                    pureCombatRecovery.rejected(spatial,pending);
                    movement.reset();pureSpacing.yieldToRecovery(f.tick);pureLures.movementFailed();purePlanner.widenSearch();
                    lastMoveTick=f.tick;forceReengage=true;plan=null;lastPlanTick=-1;
                    trace.event("pure-route-cancelled","Live NPC movement invalidated the remaining route");
                }
                return true;
            }
            plan=pending;prayerPlanTick=f.tick;
            if(f.tick!=pureTraceTick){trace.decision(f,waves.wave(),plan);pureTraceTick=f.tick;}
            if(protectInCave(overhead)) {
                if(supplies(f,urgent))return true;
                if(movement.observe(spatial.player(),f.tick)==MovementAck.Result.RETRY_MINIMAP)movePlan(f,pending);
            }
            status=pending.reason();return true;
        }
        // A firing-access watchdog must not bypass separation from a contacting
        // melee. Its successful shots are not evidence that this hold is safe.
        Plan separation=pureSpacing.decide(spatial,bowPrayer.ticksUntilShot(f.tick));
        if(separation!=null) {
            // No reset here: choosing or attempting a move does not prove either
            // arrival or combat progress. A failed dispatch must still recover.
            pureLures.rangerSpacingAccepted();pureResetAfterReturn=false;
            plan=separation;prayerPlanTick=f.tick;status=separation.reason();combatPhase="Pure / separate contact";
            if(f.tick!=pureTraceTick){trace.decision(f,waves.wave(),plan);pureTraceTick=f.tick;}
            if(protectInCave(desiredCaveProtection(separation.protection())))movePlan(f,separation);
            return true;
        }
        if(pureCombatRecovery.active()) {
            Protection overhead=desiredCaveProtection(urgent);
            if(protectInCave(overhead)) {
                if(supplies(f,urgent)||healWithSweets(f,urgent))return true;
                requestRecoveryPause(f,urgent);
                Plan acquisition=pureCombatRecovery.plan(spatial);
                if(acquisition!=null) {
                    plan=acquisition;prayerPlanTick=f.tick;status=acquisition.reason();combatPhase="Pure / recover firing access";
                    if(f.tick!=lastPlanTick){trace.decision(f,waves.wave(),plan);lastPlanTick=f.tick;}
                    if(!spatial.player().equals(acquisition.nextStep()))movePlan(f,acquisition);
                    else {
                        Mob target=spatial.mobs().stream().filter(m->m.index()==acquisition.targetIndex()).findFirst().orElse(null);
                        if(target!=null&&!f.moving&&(f.interactingIndex!=target.index()||forceReengage)) {
                            if(dispatchAttack(f,target,forceReengage,"Pure recovery acquisition"))forceReengage=false;
                        }
                        if(target!=null&&f.interactingIndex==target.index()) {
                            if(!tryPureBlowpipeSpecial(f,target))handleThrall(f,target,plan,overhead,urgent);
                        }
                    }
                    return true;
                }
                // Recovery owns the stalled encounter. Falling through here can
                // choose the ordinary goal that final dispatch just rejected.
                status="Pure recovery: no checked firing route yet; retaining protection";return true;
            } else {status="Pure recovery: confirming protection";return true;}
        }
        // Pure owns this branch before the regular run toggle is reached.
        // Preserve the client's raw-energy threshold and avoid retry spam.
        if(!f.running&&f.rawEnergy>Math.max(0,Microbot.runEnergyThreshold)
            &&f.tick-pureRunAttempt>=3&&optionalInputWindow(f,450)) {
            pureRunAttempt=f.tick;actions.enableRun();status="Pure: enabling run for checked spacing";return true;
        }
        Plan spacing=pureRangerSpacing.decide(spatial);
        if(spacing!=null) {
            if(!spacing.destination().equals(pureRangerSpacingDestination)) {
                movement.reset();pureRangerSpacingDestination=spacing.destination();
            }
            pureLures.rangerSpacingAccepted();pureResetAfterReturn=false;
            if((f.hp*100<=f.maxHp*config.eatPercent()||f.prayer<=config.restorePrayer())&&supplies(f,urgent))return true;
            plan=spacing;prayerPlanTick=f.tick;status=spacing.reason();
            combatPhase="Pure / ranger spacing";
            if(protectInCave(desiredCaveProtection(spacing.protection())))movePlan(f,spacing);
            return true;
        }
        pureRangerSpacingDestination=null;
        if(pureResetAfterReturn&&!pureLures.hasPendingReturn()) {
            pureLures.reset();pureResetAfterReturn=false;
        }
        if(lastFailedMoveTick>=0&&pureFailedMoveTick!=lastFailedMoveTick) {
            pureFailedMoveTick=lastFailedMoveTick;pureLures.movementFailed();purePlanner.widenSearch();
        }
        if(f.model.mobs().isEmpty()) {
            Plan returning=pureLures.finishReturn(f.model);
            if(returning==null)return false;
            plan=returning;prayerPlanTick=f.tick;
            if(protectInCave(returning.protection())&&!returning.nextStep().equals(f.model.player()))movePlan(f,returning);
            status=returning.reason();return true;
        }
        if(pureRepositionWatchdog.stalled(f.model,plan,f.moving||movement.pending(),f.interactingIndex)) {
            // A stall changes the pure search, never the selected combat mode.
            pureRepositionWatchdog.reset();pureSpacing.yieldToRecovery(f.tick);pureLures.recover();purePlanner.recover(spatial);
            lastPlanTick=-1;lastPlanAt=0;plan=null;prayerPlanTick=-1;forceReengage=true;
            trace.event("pure-recovery","No attack progress: widening pure-only cover search");
        }
        // This safety check must run before an overhead/movement handoff can return.
        if(!movement.pending()&&!f.moving&&recoverStalledPureCombat(f,System.currentTimeMillis()))return true;
        if(tryMageEscape(f))return true;
        Snapshot model=withTaggedHealer(f.model);
        pureLures.observeFight(model,f.recordedAnchor(RecordedLureBook.ITALY),f.interactingIndex);
        if(tryPureImmediateAttack(f,model,urgent))return true;
        Protection guard=desiredCaveProtection(urgent!=Protection.NONE?urgent:
            CombatPlanner.protectionForNextTick(model,model.player()));
        if(!protectInCave(guard)){status="Pure: confirming forecast protection";return true;}
        if(supplies(f,urgent))return true;
        requestRecoveryPause(f,urgent);
        if(healWithSweets(f,urgent))return true;
        Plan next=pureLures.decide(model,purePlanner,f.interactingIndex,italyCandidates(f,waves.wave()),
            bowPrayer.ticksUntilShot(f.tick),f.recordedAnchor(RecordedLureBook.ITALY),
            f.recordedAnchor(RecordedLureBook.PULL),f.recordedAnchor(RecordedLureBook.NORTHWEST),
            f.recordedAnchor(RecordedLureBook.WEST_PEEK),f.recordedAnchor(RecordedLureBook.MELEE_WALL));
        if(next!=null&&!CombatPlanner.actionable(next,false)) {
            pureLures.movementFailed();
            next=pureLures.recoverBlockedRoute(model,f.recordedAnchor(RecordedLureBook.ITALY));
        }
        if(next==null||!CombatPlanner.actionable(next,false))
            next=purePlanner.plan(model,f.recordedAnchor(RecordedLureBook.ITALY));
        if(next==null) {
            status="Pure: no firing route yet; retain protection and re-evaluate";return true;
        }
        if(next.nextStep()!=null&&!next.nextStep().equals(model.player())&&!PureSafety.routeAllowed(model,next.nextStep()))
            next=purePlanner.plan(model,f.recordedAnchor(RecordedLureBook.ITALY));
        plan=next;prayerPlanTick=f.tick;combatPhase="Pure / protected combat";status=next.reason();
        if(f.tick!=lastPlanTick){trace.decision(f,waves.wave(),next);lastPlanTick=f.tick;}
        if(!CombatPlanner.actionable(next,false)){status="Pure: rechecking firing access / watchdog";return true;}
        guard=desiredCaveProtection(urgent!=Protection.NONE?urgent:next.protection());
        if(!protectInCave(guard))return true;
        if(!next.nextStep().equals(model.player())){movePlan(f,next);return true;}
        Mob target=model.mobs().stream().filter(m->m.index()==plan.targetIndex()).findFirst().orElse(null);
        if(target!=null&&!f.moving&&(f.interactingIndex!=target.index()||forceReengage)) {
            if(dispatchAttack(f,target,forceReengage,"Pure / original protected attack"))forceReengage=false;
        }
        if(target!=null&&tryPureBlowpipeSpecial(f,target))return true;
        if(target!=null&&f.interactingIndex==target.index()&&!forceReengage)
            handleThrall(f,target,next,guard,urgent);
        return true;
    }

    protected boolean tryPureBlowpipeSpecial(FcFrame f,Mob target) {
        long now=System.currentTimeMillis();
        if(f.moving||movement.pending()||f.interactingIndex!=target.index()||forceReengage
            ||!f.weapon.toLowerCase(Locale.ROOT).contains("blowpipe")||f.specialEnergy<500
            ||f.maxHp-f.hp<8||f.specialEnabled!=0||now-lastSpecAt<=1800||!attackInputWindow(f))return false;
        if(!actions.special(()->attackInputWindow(f)&&!Microbot.pauseAllScripts.get()&&!InputArbiter.isHuman()))return false;
        lastSpecAt=now;status="Blowpipe special requested";
        trace.event("special-request","wave="+waves.wave()+" target="+target.index());
        return true;
    }

    protected boolean recoverStalledPureCombat(FcFrame f,long now) {
        boolean roomStalled=combatProgress.due(f.model);
        if((!roomStalled&&now-Math.max(lastProgressAt,lastRecoveryMotionAt)<=6000)||now-lastRecoveryAt<=6000||hasJad(f))return false;
        combatProgress.recovering(f.tick);
        Snapshot model=withTaggedHealer(f.model);
        Protection recoveryProtection=desiredCaveProtection(Protection.NONE);
        Mob shot=PureCombatPolicy.preferredShot(model,recoveryProtection);
        Mob ranger=PureCombatPolicy.rangedTarget(model);
        if(ranger!=null&&shot!=null&&PureCombatPolicy.targetPriority(model,ranger,recoveryProtection)
            >PureCombatPolicy.targetPriority(model,shot,recoveryProtection))shot=null;
        int shotRisk=CombatPlanner.immediateExposure(model,model.player(),recoveryProtection);
        // A watchdog must not interrupt a legal firing position just because a
        // prayer handoff or several zero damage rolls postponed health progress.
        Plan recovery=shot!=null&&!pureLures.hasPendingReturn()?
            new Plan(model.player(),model.player(),recoveryProtection,shot.index(),
                shotRisk==0,0,0,shotRisk,
                "Stalled combat: reacquire reachable attacker from current tile"):
            usesRecordedWave(waves.rotation(),waves.wave())?
            pureLures.recoverRecorded(model,f.recordedAnchor(RecordedLureBook.ITALY),f.recordedAnchor(RecordedLureBook.WEST_PEEK)):null;
        if(recovery==null) {
            // Preserve the return destination even after repeated dispatch failures.
            pureLures.recover();
            recovery=pureLures.hasPendingReturn()?pureLures.finishReturn(model):null;
            if(recovery==null){purePlanner.recover(model);recovery=purePlanner.plan(model,f.recordedAnchor(RecordedLureBook.ITALY));}
        }
        lastPlanTick=-1;lastRecoveryAt=now;forceReengage=true;
        plan=recovery;prayerPlanTick=f.tick;
        if(recovery!=null&&CombatPlanner.actionable(recovery,false)) {
            if(protectInCave(recovery.protection())) {
                if(recovery.nextStep().equals(model.player())&&recovery.targetIndex()>=0) {
                    Mob target=model.mobs().stream().filter(m->m.index()==plan.targetIndex()).findFirst().orElse(null);
                    if(target!=null)dispatchAttack(f,target,true,"watchdog protected attack");
                }else movePlan(f,recovery);
            }
            status=recovery.reason();
        }else status="Stalled combat: rechecking protected reposition route";
        trace.event("recovery","Stuck-monster watchdog: no NPC progress, or no health/death progress for 30 ticks; "+
            (recovery==null?"no protected route yet":recovery.reason()));
        return true;
    }
    protected boolean offenceEnabled(FcFrame f) {
        return offenceEnabled(f,f==null?-1:f.interactingIndex);
    }
    @Override
    protected boolean offenceEnabled(FcFrame f,int targetIndex) {
        return config!=null&&PrayerConservation.offence(config.offensivePrayer(),config.prayerConservation(),
            targetIndex,f==null?null:f.model);
    }
    @Override
    protected boolean earlyConservationGap(FcFrame f) {
        int next=waves.hasSeenMonsters()?Math.min(63,waves.wave()+1):waves.wave();
        return PrayerConservation.earlyGap(config.prayerConservation(),next,f.model);
    }
    @Override
    protected boolean prayerInputAllowed() {
        return enabled&&config!=null&&client.getGameState()==GameState.LOGGED_IN
            &&client.getLocalPlayer()!=null&&state!=State.STOPPED&&state!=State.COMPLETE
            &&!playerDeath&&!Microbot.pauseAllScripts.get()&&!InputArbiter.isHuman();
    }
}
