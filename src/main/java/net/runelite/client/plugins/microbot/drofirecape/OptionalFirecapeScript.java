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
    @Override


    public boolean run(DroFirecapeConfig configuration) {
        enabled=false;
        if(mainScheduledFuture!=null)mainScheduledFuture.cancel(true);
        if(scheduledFuture!=null)scheduledFuture.cancel(true);
        FcTickPrayers previous=tickPrayers;if(previous!=null)previous.detach();
        config=configuration;lastObservationTick=-1;lastObservedControls=lastObservedSettings=lastHealerLureState="";pureHealerLure.reset();healerPullLease.reset();openingBats.reset();keybindings.reset();if(pointerLease!=null)pointerLease.close();
        meleeMode=config.meleeCape();
        pointerLease=null;pureRunAttempt=-1000;pureWave=pureFailedMoveTick=-1;pureResetAfterReturn=false;lureResetAfterReturn=false;rangePrepot.reset();sweetCancelTick=sweetProgressTick=sweetRetryTick=-1;supplyAck.reset();brewHealing.reset();supplyPreparation.reset();jadActions.reset();lastFailedMoveTick=-1;movement.reset();lures.reset();pureSpacing.reset();purePlanner.reset();pureLures.reset();bowPrayer.reset();requestedCaveProtection=Protection.NONE;caveBaseX=caveBaseY=Integer.MIN_VALUE;cavePlane=-1;actions.reset();actions.observeOnly(false);
        tickPrayers=new OptionalTickPrayers(client,meleeMode,events::add);
        actions.tickPrayerDriver(tickPrayers);
        final FcTickPrayers prayerOwner=tickPrayers;
        entryGate.reset();entryPrediction=null;entryGateStatus="Waiting for built-in spawn predictor";
        lastPredictorLogAt=lastExitDiagnosticAt=lastExitClickAt=lastDispatchDiagnosticAt=0;lastPredictorLog="";
        plannedRotation=-1;predictorWave=-1;lastInstancePredictorAt=0;
        lastWaveDiagnosticAt=lastAttackDiagnosticAt=lastAttackRequestAt=lastAttackProgressAt=0;
        pendingAttackIndex=-1;attackAttempts=0;pendingAttackAcknowledged=false;
        stopCamera();lastCameraPivotMonster=-1;lastMonsterCameraTurnAt=0;magicThreatUntil=-1;enabled=true;initialized=false;hadCave=false;cameraSet=false;failed=false;exitRequested=false;
        pauseConfirmed=false;jadDeath=false;rewardConfirmed=false;playerDeath=false;forceReengage=false;
        warning="Experimental — in-game validation required";teleportAt=0;
        initialCapeCount=0;expectedHop=-1;enteredAt=0;emptyAt=0;lastSupplyAt=0;
        lastMoveTick=-1;lastPlanTick=-1;lastSavedWave=-1;preparedWave=-1;prepositioned=false;
        healers.reset();healerCancelTick=healerCancelIndex=-1;healerRetreat=null;exitTile=null;frame=null;plan=null;
        waves.reset();energyPause.reset();lastSavedPause="";lastDangerTick=-1000;recoverySpawnObserved=false;recoverySupplyMisses=0;recoveryRetryAt=0;camps.clear();dead.clear();attackTicks.clear();attackStyles.clear();damage.set(0);lastActionAt=lastSpecAt=lastPlanAt=0;resumeWave=-1;failureReason="";
        planner.reset();prayerPlanTick=-1;transitionGuardUntil=-1;
        prepositionStarted=-1;cancelAttackTick=-1;brewDebt=0;lastBrewTick=-1000;lastObservedWave=-1;controllerErrors=0;
        jadStyle=Protection.MAGIC;jadAttackTick=-1000;lastRecoveryAt=0;lastHealthFingerprint=lastMotionFingerprint=Long.MIN_VALUE;lastRecoveryMotionAt=0;
        lastPrayerRegenAttemptAt=prayerRegenFallbackUntil=nextThrallAttemptAt=nextThrallUiAt=0;
        pendingPrayerRegenDoses=-1;thrallUiMisses=0;sawPrayerRegenTimer=false;
        startedAt=System.currentTimeMillis();lastProgressAt=startedAt;
        lastStatusLogAt=0;lastLoggedStatus="";
        trace.open(config.recordRun());setState(State.START,"Waiting for logged-in scene");
        mainScheduledFuture=scheduledExecutorService.scheduleWithFixedDelay(()->{
            try{loop();controllerErrors=0;}catch(Exception|AssertionError e){
                trace.event("exception",e+" "+Arrays.toString(e.getStackTrace()));
                Microbot.log("[Dro Firecape] "+e+" state="+state);
                warning="Controller error: "+e.getClass().getSimpleName();
                if(++controllerErrors==3)fail("Repeated controller error: "+e.getClass().getSimpleName());
                FcFrame f=frame;
                if(f!=null&&f.cave&&!f.stale())protectInCave(jadAttackTick>=f.tick-4?jadStyle:requestedCaveProtection);
                else status="Controller error; see trace";
            }finally{logStatus();}
        },0,80,TimeUnit.MILLISECONDS);
        scheduledFuture=scheduledExecutorService.scheduleWithFixedDelay(()->{
            try{if(!cameraAllowed())stopCamera();FcFrame f=frame;if(enabled&&f!=null&&f.cave&&!f.stale()&&Microbot.isLoggedIn()
                &&state!=State.COMPLETE&&state!=State.STOPPED
                &&!Microbot.pauseAllScripts.get()&&!InputArbiter.isHuman()) {
                    synchronized(optionalInputLock) {
                        if(tickPrayers==prayerOwner&&prayerOwner.ownsInput())prayerOwner.pulse(actions);
                    }
                }}
            catch(Exception ignored){}
        },0,10,TimeUnit.MILLISECONDS);
        Microbot.log("[Dro Firecape] Full-run controller "+DroFirecapePlugin.version+" (recorded rock lures / visible protection switches / tick prayers / retained startup, supplies and thralls) started. Trace: "+trace.location()+"; Record run="+config.recordRun()+" (collision maps saved after cave decisions)");
        return true;
    }
    @Override
    protected void loop() {
        if(!enabled||Thread.currentThread().isInterrupted())return;
        for(String e;(e=events.poll())!=null;)trace.event("game",e);
        if(state==State.STOPPED||state==State.COMPLETE)return;
        if(!Microbot.isLoggedIn()) {
            status="Waiting for login / world transition; preserving run";
            if(!false)super.run();
            return;
        }
        FcFrame f=frame;
        if(f==null||f.stale()) {
            f=FcActions.read(()->FcFrame.captureOptional(client,0,meleeMode,jadStyle,attackTicks,attackStyles,dead),null);
            if(f==null){status=FcActions.read(this::sceneWaitReason,"Waiting for client-thread scene snapshot");return;}
            frame=f;
        }
        if(Microbot.pauseAllScripts.get()||InputArbiter.isHuman()){
            stopCamera();
            if(pureHealerLure.active()){pureHealerLure.abandon("User/pause took control during the pull");movement.reset();}
            status="User input/pause owns controls";captureObservation(f,"USER_OR_PAUSE");return;
        }
        if(f.cave&&!false&&pointerLease==null)
            pointerLease=FcCanvasGuard.acquire();
        if(!f.cave&&pointerLease!=null){pointerLease.close();pointerLease=null;}
        if(!false)baseProfile.tick(false,false);
        if(!initialized){
            if(!false) {
                if(!keybindings.step(f.cave)){status=keybindings.status();return;}
                trace.event("startup-keybindings",keybindings.result());
            }
            initialize(f);if(!initialized||state==State.STOPPED)return;
        }
        if(playerDeath){fail("Player died; attempt stopped");setState(State.STOPPED,failureReason);return;}
        if(f.cave){
            try {if(!false)maybeTurnCameraTowardMonster(f);cave(f);}
            finally {captureObservation(f,"SCRIPT");}
            return;
        }
        if(hadCave) {
            if(f.tick<=transitionGuardUntil){status="Waiting for instance reconstruction after world transition";return;}

            if(rewardConfirmed||(jadDeath&&f.count(6570)>initialCapeCount)){complete();return;}
            if(state==State.RESUMING||energyPause.state()==EnergyPause.State.HOPPING){
                if(energyPause.hopExpired(System.currentTimeMillis())){energyPause.hopFailed(System.currentTimeMillis());savePause();
                    warning="Resume hop did not restore a ready cave; retaining saved pause";}
                status="Waiting for the saved Fight Cave after resume hop; recovery is not complete";return;
            }
            if(jadDeath&&System.currentTimeMillis()-lastProgressAt<15_000){status="Waiting for cape reward confirmation";return;}
            fail(failed?failureReason:"Exited cave without a confirmed cape reward");setState(State.STOPPED,failureReason);return;
        }
        if(!false&&!super.run())return;
        outside(f);
    }
    private int lastObservationTick=-1;
    private String lastObservedControls="",lastObservedSettings="";
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
    @Override
    protected boolean cameraAllowed() {
        FcFrame current=frame;
        return enabled&&config!=null&&!false&&current!=null&&current.cave
            &&!current.stale()&&!Microbot.pauseAllScripts.get()&&!InputArbiter.isHuman()
            &&state!=State.STOPPED&&state!=State.COMPLETE&&state!=State.ENERGY_REST
            &&!movement.pending()&&!supplyPreparation.pending()&&!energyPause.recovering()&&current.model.mobs().stream().noneMatch(m->m.kind()==Kind.JAD);
    }
    @Override
    /** Only recover a completely off-screen attack target; never pivot on target change alone. */
    protected synchronized void maybeTurnCameraTowardMonster(FcFrame f) {
        if(!cameraAllowed()||f.moving||cameraTurning.get()||cameraTurn!=null&&!cameraTurn.isDone())return;
        Mob target=null;Plan decision=plan;
        if(decision!=null&&decision.targetIndex()>=0)
            target=f.model.mobs().stream().filter(m->m.index()==decision.targetIndex()).findFirst().orElse(null);
        if(target==null&&f.interactingIndex>=0)
            target=f.model.mobs().stream().filter(m->m.index()==f.interactingIndex).findFirst().orElse(null);
        if(target==null)target=f.model.mobs().stream().filter(Mob::attackingPlayer)
            .filter(m->m.distance(f.model.player())<=Math.max(m.kind().range,f.model.weaponRange()))
            .min(Comparator.comparingInt(m->m.distance(f.model.player()))).orElse(null);
        long now=System.currentTimeMillis();
        if(target==null||now-lastMonsterCameraTurnAt<6000
            ||!config.pureMode()&&!actions.npcCompletelyOutOfView(target.index(),target.kind().name))return;
        // Do not turn towards an out-of-range spawn while waiting for it to approach.
        if(target.distance(f.model.player())>Math.max(target.kind().range,f.model.weaponRange()))return;
        final int index=target.index();final String name=target.kind().name;
        final long generation=cameraGeneration.get();
        lastCameraPivotMonster=index;lastMonsterCameraTurnAt=now;
        cameraTurn=scheduledExecutorService.submit(()->{
            if(!cameraAllowed()||generation!=cameraGeneration.get()||!cameraTurning.compareAndSet(false,true))return;
            try {
                NPC npc=FcActions.read(()->Microbot.getRs2NpcCache().query().withName(name)
                    .where(n->n.getIndex()==index).toList().stream().map(n->n.getNpc())
                    .filter(n->n!=null&&n.getWorldView()==client.getTopLevelWorldView()&&!n.isDead())
                    .findFirst().orElse(null),null);
                // Recheck visibility immediately before the asynchronous turn.
                if(npc!=null&&cameraAllowed()&&generation==cameraGeneration.get()
                    &&(config.pureMode()||actions.npcCompletelyOutOfView(index,name)))
                    net.runelite.client.plugins.microbot.util.camera.Rs2Camera.turnTo(npc,70);
            }finally{cameraTurning.set(false);}
        });
    }
    @Override

    protected void outside(FcFrame f) {
        long now=System.currentTimeMillis();
        if(false){status="Observe only: outside-cave preparation would run here";return;}
        switch(state) {
            case TELEPORT: {
                if(f.inTzhaar){teleportAt=0;setState(State.WALK_BANK,"Walking to TzHaar bank");return;}
                if(teleportAt>0){if(now-teleportAt<25_000){status="Waiting for minigame teleport landing";return;}fail("Minigame teleport did not land; check cooldown, combat, teleblock, or restricted area");return;}
                if(elapsed(90_000)){fail("Minigame teleport unavailable from this location/interface");return;}
                status=actions.minigameStep();if("TELEPORT_SENT".equals(status)){teleportAt=now;status="TzHaar teleport sent; waiting for landing";}
                break;
            }
            case WALK_BANK: {
                if(f.location.distanceTo2D(BANK)<=6){setup=new Rs2InventorySetup(config.inventorySetup(),mainScheduledFuture);setState(State.BANK_EQUIPMENT,"Opening TzHaar bank");return;}
                travel(f,BANK);if(elapsed(90_000))fail("Unable to reach the TzHaar bank");
                break;
            }
            case BANK_EQUIPMENT:
            case BANK_INVENTORY:
            case BANK_VERIFY: {
                if(elapsed(180_000)){fail("Inventory Setup could not be completed; check missing items");return;}
                if(!Rs2Bank.isOpen()){actions.openTzhaarBank();return;}
                if(!actionGap(1400))return;
                didAction();
                if(state==State.BANK_EQUIPMENT){if(setup.loadEquipment())setState(State.BANK_INVENTORY,"Loading saved inventory");}
                else if(state==State.BANK_INVENTORY){if(setup.loadInventory())setState(State.BANK_VERIFY,"Verifying and equipping setup");}
                else if(setup.wearEquipment()){setState(State.PREPOT,"Preparing ranged pre-dose and full potion refill");}
                break;
            }
            case PREPOT: {
                if(meleeMode||!true){setState(State.CAMERA,"Closing bank; checking combat loadout");return;}
                boolean done=rangePrepot.step(f.tick,f.ranged,f.baseRanged,f.inventory,new FcRangePrepot.Input() {
                    public boolean bankOpen(){return Rs2Bank.isOpen();}
                    public void closeBank(){actions.closeBank();}
                    public void openBank(){actions.openTzhaarBank();}
                    public FcActions.ItemResult drink(FcFrame.ItemSlot item){return actions.itemStep(item,"Drink");}
                    public boolean stocked(String name){return Rs2Bank.hasItem(name,true);}
                    public void deposit(int id){Rs2Bank.depositOne(id);}
                    public void withdraw(String name){Rs2Bank.withdrawOne(name,true);}
                });
                status=rangePrepot.status();
                if(rangePrepot.failed()){fail(status);return;}
                if(done){actions.releaseTab();setState(State.CAMERA,"Pre-dose and full inventory confirmed");}
                return;
            }
            case CAMERA: {
                if(Rs2Bank.isOpen()){actions.closeBank();if(elapsed(20_000))fail("Bank interface did not close after gearing");return;}
                if(!validateEquipment(f))return;
                if(!cameraSet){actions.restoreZoom();cameraSet=true;return;}
                if(!actions.zoomConfirmed()){actions.restoreZoom();status="Waiting for confirmed zoom 150";if(elapsed(20_000))fail("Camera zoom 150 not confirmed after UI load");return;}
                if(f.autoRetaliate){actions.autoRetaliateOff();return;}
                if(!f.running&&f.rawEnergy>Math.max(0,Microbot.runEnergyThreshold)){actions.enableRun();return;}
                initialCapeCount=f.count(6570);setState(State.WALK_ENTRANCE,"Walking to Fight Cave entrance");
                break;
            }
            case WALK_ENTRANCE: {
                if(f.location.distanceTo2D(ENTRANCE)<=3){resetClock();setState(State.ROTATION_WAIT,"Reading server rotation clock");return;}
                travel(f,ENTRANCE);if(elapsed(75_000))fail("Unable to reach Fight Cave entrance");
                break;
            }
            case ROTATION_WAIT: { rotationWait(f); break; }
            case ENTERING: {
                status="Entry dispatched; waiting for instance";
                if(elapsed(20_000)){setState(State.ROTATION_WAIT,"Entry not observed; rechecking clock before retry");resetClock();}
                break;
            }
            default: { break; }
        }
    }
    @Override
    protected boolean validateEquipment(FcFrame f) {
        if(meleeMode) {
            String weapon=f.weapon.toLowerCase(Locale.ROOT);
            if(FcFrame.inferRange(f.weapon)>0||weapon.contains("trident")||weapon.contains("sanguinesti")||weapon.contains("tumeken")) {
                fail("Melee cape is enabled, but the setup equipped '"+f.weapon+"'. Select a melee setup or turn Melee cape off.");return false;
            }
            if(false) {
                fail("Melee cape requires Strict zero-exposure OFF; contact and Tz-Kek recoil are not damage-free.");return false;
            }
        } else if(FcFrame.inferRange(f.weapon)==0&&0==0){fail("Unrecognized ranged weapon '"+f.weapon+"'; set its actual range or use a recognized ranged weapon");return false;}
        if(f.weaponId<=0){fail("No weapon equipped after Inventory Setup");return false;}
        boolean selfDamageAmmo=FcActions.read(()->{
            ItemContainer gear=client.getItemContainer(InventoryID.EQUIPMENT);Item ammo=gear==null?null:gear.getItem(13);
            return ammo!=null&&ammo.getId()>0&&client.getItemDefinition(ammo.getId()).getName().toLowerCase(Locale.ROOT).contains("ruby");
        },false);
        if(!meleeMode&&selfDamageAmmo){fail("Ruby ammunition can self-damage; remove it from the zero-damage setup");return false;}
        if(f.inventory.stream().noneMatch(i->isPrayerPotion(i.name()))){fail("Inventory Setup has no prayer potion or super restore doses");return false;}
        return true;
    }
    @Override
    protected void rotationWait(FcFrame f) {
        long now=System.currentTimeMillis();
        // A restore/boost can be sent before entry. Acknowledgement must happen
        // here too; waiting until cave() otherwise blocks entry on its own dose.
        combatProgress.observe(f.model);
        observeSupply(f);
        // Read the embedded predictor directly from the server clock. An unreadable
        // or stale clock must never authorize entry.
        FcPredictorGate.Sample sample=actions.predictor(false);entryPrediction=sample;
        now=System.currentTimeMillis(); // capture can wait for the client thread
        boolean ready=entryGate.entryReady(sample,now,config.entryRotation());
        entryGateStatus=entryGate.reason(sample,now,config.entryRotation());status=entryGateStatus;
        String logKey=(sample.source==null?"none":sample.source.getClass().getName())+":"+sample.rotation+":"+sample.ready+":"+sample.safety;
        if(!logKey.equals(lastPredictorLog)||now-lastPredictorLogAt>15_000) {
            lastPredictorLog=logKey;lastPredictorLogAt=now;
            String detail=sample.diagnostic()+" rawFrame="+f.serverMinute+":"+f.serverSecond+" gate="+ready
                +" selected="+config.entryRotation()+" supplyPending="+supplyAck.pending();
            trace.event("predictor-entry-gate",detail);Microbot.log("[Dro Firecape] Predictor gate: "+detail);
        }
        if(!actions.zoomConfirmed()){actions.restoreZoom();status="Restoring zoom 150 before checking entry";return;}
        if(sample.world!=f.world||f.stale()){status="World changed; reacquiring predictor and scene";resetClock();return;}
        if(ready) {
            if(f.moving){status="Rotation "+sample.rotation+" confirmed; waiting to settle beside entrance";return;}
            if(!WaveBook.entryPrayerReady(f.prayer)){emergencyPrayer(f);status="Restoring starting prayer before cave entry";return;}
            if(supplies(f,Protection.NONE))return;
            if(!actions.entryProtectionReady(config.prayerConservation())){status="Rotation "+sample.rotation+" confirmed; confirming the configured entry prayer state";return;}
            waves.begin(sample.rotation);camps.clear();planner.reset();clearSaved();lastSavedWave=-1;predictorWave=-1;
            setState(State.ENTERING,"Rechecking built-in spawn predictor immediately before entry");
            if(actions.enterPredictedRotation(entryGate,sample.rotation,config.prayerConservation())) {
                trace.event("entry-dispatched",sample.diagnostic());
                Microbot.log("[Dro Firecape] Enter dispatched: rotation="+sample.rotation+"; waiting through cave introduction for spawn evidence");
            } else setState(State.ROTATION_WAIT,"Entry not dispatched; rechecking predictor before retry");
            return;
        }
        actions.idlePrayersOff();
        warning=sample.ready?"Waiting here for the selected entry window; no rotation-search hopping":sample.detail;
    }
    @Override


    protected Protection desiredCaveProtection(Protection protection) {
        FcFrame current=frame;
        if(current!=null&&current.cave&&!current.stale()) {
            if(meleeMode) {
                FcModel.Tile destination=movement.pending()?movement.destination():
                    plan!=null&&prayerPlanTick==current.tick?plan.nextStep():current.model.player();
                protection=current.model.mobs().isEmpty()
                    ?(protection!=Protection.NONE?protection:requestedCaveProtection)
                    :MeleeProtection.choose(withTaggedHealer(current.model),destination,requestedCaveProtection);
            } else if(current.tick-jadAttackTick<=5||hasJad(current))protection=jadStyle;
            else {
                // Opt-in remote/empty idle gaps may release a merely held prayer.
                // Explicit route guards and every mage/Jad safeguard remain intact.
                if(!(config.prayerConservation()&&protection==Protection.NONE&&PrayerConservation.remote(current.model)))
                    protection=config.pureMode()?PureMageGuard.held(current.model,protection,requestedCaveProtection):
                        HeldProtection.choose(current.model,protection,requestedCaveProtection);
                // Retain the existing magic-flight guard as well as continuous overheads.
                protection=MagicProtection.choose(current.model,current.model.player(),magicThreatUntil,protection);
            }
        }
        requestedCaveProtection=protection;
        return protection;
    }
    @Override
    protected void cave(FcFrame f) {
        long now=System.currentTimeMillis();
        if(caveBaseX!=f.baseX||caveBaseY!=f.baseY||cavePlane!=f.plane) {
            if(caveBaseX!=Integer.MIN_VALUE&&cavePlane==f.plane) {
                lures.rebase(caveBaseX-f.baseX,caveBaseY-f.baseY);
                pureLures.rebase(caveBaseX-f.baseX,caveBaseY-f.baseY);
                resetPlanning(false);
            }else resetPlanning();
            caveBaseX=f.baseX;caveBaseY=f.baseY;cavePlane=f.plane;exitTile=null;
            trace.event("scene-rebase","Rebased committed lure return; rebuilt transient scene plans");
        }
        if(!hadCave){hadCave=true;enteredAt=now;lastProgressAt=now;
            if(!exitRequested)setState(State.FIGHTING,"Inside cave; waiting for first-wave spawns / rotation "+rotationLabel());}
        combatProgress.observe(f.model);
        observeSupply(f);
        MovementAck.Result moveResult=movement.observe(f.model.player(),f.tick);
        if(moveResult==MovementAck.Result.ARRIVED||moveResult==MovementAck.Result.PROGRESSED) {
            lastProgressAt=now;
            trace.event("move-observed","tick="+f.tick+" player="+f.model.player()+" result="+moveResult);
        }
        if(moveResult==MovementAck.Result.FAILED||moveResult==MovementAck.Result.DEVIATED) {
            lastFailedMoveTick=f.tick;lures.movementFailed();planner.widenSearch();lastPlanTick=-1;forceReengage=true;
            trace.event("move-recovery","tick="+f.tick+" result="+moveResult+" player="+f.model.player());
        }
        if(exitTile==null)exitTile=actions.exitTile(f);
        if(rewardConfirmed||(jadDeath&&f.count(6570)>initialCapeCount)){complete();return;}
        synchronizeWave(f);
        if(waves.mismatch())warning=waves.status(); // Suspend previews, not live combat or the attempt.
        acknowledgeAttack(f);
        if(!exitRequested&&waves.wave()!=lastSavedWave&&waves.wave()>0) {
            save(false);lastSavedWave=waves.wave();
            if(waves.wave()!=lastObservedWave)lastProgressAt=now;
        }
        if(lastObservedWave!=waves.wave()) {
            lastObservedWave=waves.wave();prepositioned=false;prepositionStarted=-1;
            healers.reset();pureHealerLure.reset();healerPullLease.reset();healerRetreat=null;cancelAttackTick=-1;planner.reset();
            if(lures.hasPendingReturn())lureResetAfterReturn=true;else lures.reset();
            plan=null;lastPlanTick=-1;
        }
        observeHealers(f);
        if(lureResetAfterReturn&&!lures.hasPendingReturn()){lures.reset();lureResetAfterReturn=false;}
        if(f.autoRetaliate&&!false){actions.autoRetaliateOff();return;}
        if(pauseConfirmed){pauseConfirmed=false;energyPause.confirmation();}
        energyPause.observe(waves.wave(),f.model.mobs().isEmpty(),f.tick,lastDangerTick,now);
        if(exitRequested&&energyPause.state()!=EnergyPause.State.HOPPING)energyPause.reset();
        savePause();
        if(energyPause.recovering()) {
            if(f.model.mobs().isEmpty()&&!recoverySpawnObserved){rest(f);return;}
            // Any live spawn restores combat immediately, including Jad after a
            // hop whose dialogue was released by the client itself.
            int next=energyPause.nextWave();energyPause.resumed();savePause();
            if(waves.wave()<next)waves.restore(next);
            if(tickPrayers!=null)tickPrayers.recoveryPause(false,Protection.NONE);
            recoverySpawnObserved=false;recoverySupplyMisses=0;recoveryRetryAt=0;supplyPreparation.reset();actions.releaseTab();resetPlanning();
            setState(State.FIGHTING,"Live spawn ended recovery; protecting current monsters");
        }
        if(energyPause.state()==EnergyPause.State.REQUESTED&&f.model.mobs().isEmpty()) {
            status=energyPause.confirmed()?"Wave pause acknowledged; verifying clear arena / last projectiles":
                "Waiting for wave-pause acknowledgement; Logout will not be repeated";
            if(energyPause.requestExpired(now))warning="Wave-pause acknowledgement missing; retaining one-click request";
            return;
        }
        if(usesRecordedWave(waves.rotation(),waves.wave()))
            lures.observeFight(f.model,f.recordedAnchor(RecordedLureBook.ITALY),f.interactingIndex);
        Protection urgent=f.tick-jadAttackTick<=4?jadStyle:Protection.NONE;
        if(f.prayer==0&&emergencyPrayer(f))return;
        // With even one prayer point, confirm Jad protection before touching supplies.
        if(f.prayer>0&&urgent!=Protection.NONE&&!protectInCave(urgent)){status="Jad: "+urgent+" — confirming overhead";return;}
        if(f.prayer<=1&&emergencyPrayer(f))return;
        if(!exitRequested&&(!config.pureMode()||FcSupplyPolicy.criticalHealth(f.model,f.hp,f.maxHp))
            &&healingNeeded(f)&&supplies(f,urgent))return;
        // Preserve the final trapped monster before immediate Pure shots can finish the wave.
        if(!exitRequested&&config.pureMode()&&healWithSweets(f,urgent))return;
        if(!exitRequested&&(cancelHealerRetreat(f)||cancelTaggedHealerAttack(f)))return;
        if(!exitRequested&&config.pureMode()&&!meleeMode
            &&f.model.mobs().stream().noneMatch(m->m.kind()==Kind.JAD)&&handlePureCombat(f,urgent))return;
        if(!exitRequested&&tryMageEscape(f))return;
        if(!exitRequested&&(f.prayer<=config.restorePrayer()||f.hp*100<=f.maxHp*config.eatPercent())
            &&(f.prayer<=1||protectInCave(urgent))&&supplies(f,urgent))return;
        if(!exitRequested)requestRecoveryPause(f,urgent);
        if(!exitRequested&&healWithSweets(f,urgent))return;
        if(!exitRequested&&tryBatAttack(f))return;
        if(!exitRequested&&!lures.hasPendingReturn()&&tryImmediateAttack(f,withTaggedHealer(f.model),urgent))return;
        // Supplies cannot depend on a successful lure, movement or attack plan.
        // Keep the currently required overhead held while changing tabs.
        if(!exitRequested&&(f.prayer<=1||protectInCave(urgent))
            &&supplies(f,urgent))return;
        if(exitRequested){retreat(f,urgent);return;}
        // The introduction and first spawn may arrive after 25 seconds. Missing
        // chat is never grounds for abandoning a valid cave or suppressing attacks.
        if(!exitRequested&&actions.continueCaveDialogue()) {
            forceReengage=true;status="Continuing cave introduction/dialogue";return;
        }
        if(!exitRequested&&handleHealers(f,urgent))return;
        if(!movement.pending()&&!f.moving&&!f.model.mobs().isEmpty()&&recoverStalledCombat(f,now))return;
        if(movement.pending()) {
            Snapshot live=withTaggedHealer(f.model);
            Protection overhead=desiredCaveProtection(urgent);
            Plan pending=MinimapMovement.checked(live,movement.destination(),movement.destination(),overhead,
                "Continue minimap destination");
            if(!CombatPlanner.actionable(pending,false)) {
                // The old long click still exists in the client until cancelled.
                // Stop once on the minimap before replacing an unsafe command.
                if(protectInCave(overhead)&&actions.move(f,live.player())) {
                    movement.reset();lastMoveTick=f.tick;lastPlanTick=-1;forceReengage=true;
                    trace.event("minimap-route-cancelled",pending.reason());
                }
                return;
            }
            plan=pending;prayerPlanTick=f.tick;
            if(protectInCave(overhead)) {
                if(supplies(f,urgent))return;
                if(moveResult==MovementAck.Result.RETRY_MINIMAP)movePlan(f,pending);
            }
            status="Running to minimap destination "+movement.destination()+" (attempt "+movement.attempts()+")";
            return;
        }
        if(waves.wave()<1&&f.model.mobs().isEmpty()) {
            protectInCave(Protection.NONE);
            status="Waiting for cave introduction / first spawn";return;
        }
        // Starting mid-wave (including Jad) uses the same live combat/healer path.
        // An unknown wave number disables previews, never attacks or supplies.
        if(f.model.mobs().isEmpty()&&lures.hasPendingReturn()) {
            Plan returning=lures.finishReturn(f.model);
            if(returning!=null) {
                plan=returning;prayerPlanTick=f.tick;
                if(protectInCave(returning.protection())) {
                    if(!returning.destination().equals(f.model.player()))movePlan(f,returning);
                }
                status=returning.reason();return;
            }
        }
        if(f.model.mobs().isEmpty()) {
            plan=null;prayerPlanTick=-1;
            if(emptyAt==0)emptyAt=now;
            if(jadDeath){status="Jad defeated; waiting for reward";if(now-emptyAt>25_000)fail("Jad death seen, but reward/exit not confirmed");return;}
            int next=waves.hasSeenMonsters()?Math.min(63,waves.wave()+1):waves.wave();
            Snapshot predicted=predictedWave(f,next);
            if(predicted!=null) {
                FcModel.Tile camp=campFor(f,next);Plan p=planner.route(f.model,camp);
                // Arm before the spawn is visible/in range, not after a projectile launch.
                Protection spawnPrayer=earlyConservationGap(f)?Protection.NONE:HeldProtection.choose(predicted,Protection.NONE,requestedCaveProtection);
                p=new Plan(p.destination(),p.nextStep(),spawnPrayer,p.targetIndex(),p.safe(),p.blockedMobs(),p.exposedStyles(),p.risk(),p.reason());
                plan=p;prayerPlanTick=f.tick; // The fast prayer worker must retain this pre-spawn guard.
                if(!protectInCave(spawnPrayer))return;
                movePlan(f,p);
            }
            if(predicted==null)protectInCave(Protection.NONE);
            status=waves.hasSeenMonsters()?"Wave "+waves.wave()+" clear; positioning for "+next:"Wave "+waves.wave()+" armed; waiting for its first spawn";
            if(now-emptyAt>90_000)warning="No spawn yet; checking cave dialogue / manual wave pause (not abandoning run)";
            return;
        }
        emptyAt=0;
        if(!f.running&&f.rawEnergy>Math.max(0,Microbot.runEnergyThreshold)&&urgent==Protection.NONE&&!false){actions.enableRun();return;}
        Snapshot model=withTaggedHealer(f.model);
        if(!usesRecordedWave(waves.rotation(),waves.wave())&&(waves.wave()<=1||(forceReengage&&(plan==null||model.player().equals(plan.nextStep()))))
            &&tryImmediateAttack(f,model,urgent))return;
        if(f.tick!=lastPlanTick||now-lastPlanAt>1200) {
            long planningStarted=System.nanoTime();
            boolean recorded=usesRecordedWave(waves.rotation(),waves.wave());
            Plan next=recorded?lures.decide(model,planner,f.interactingIndex,italyCandidates(f,waves.wave()),bowPrayer.ticksUntilShot(f.tick),
                f.recordedAnchor(RecordedLureBook.ITALY),f.recordedAnchor(RecordedLureBook.PULL),
                f.recordedAnchor(RecordedLureBook.NORTHWEST),f.recordedAnchor(RecordedLureBook.WEST_PEEK),
                f.recordedAnchor(RecordedLureBook.MELEE_WALL)):null;
            if(next!=null&&!CombatPlanner.actionable(next,false)) {
                lures.movementFailed();
                next=lures.recoverBlockedRoute(model,f.recordedAnchor(RecordedLureBook.ITALY));
                if(next!=null&&!CombatPlanner.actionable(next,false))next=null;
                trace.event("lure-route-rejected","tick="+f.tick+"; recover with bat/ranger priority");
            }
            if(next==null)next=planner.plan(model,f.recordedAnchor(RecordedLureBook.ITALY));
            else planner.clearRoute();
            long planningMs=(System.nanoTime()-planningStarted)/1_000_000L;
            if(planningMs>250)trace.event("slow-plan","tick="+f.tick+" ms="+planningMs);
            plan=next;prayerPlanTick=f.tick;lastPlanTick=f.tick;lastPlanAt=now;trace.decision(f,waves.wave(),next);
        }
        Plan p=plan;if(p==null)return;
        Protection protection=desiredCaveProtection(urgent!=Protection.NONE?urgent:p.protection());
        if(!protectInCave(protection)){status="Confirming "+protection+" overhead";return;}
        if(supplies(f,urgent)){bowPrayer.interrupt();return;}
        if(!CombatPlanner.actionable(p,false)) {
            status=p.reason();warning=false?"Strict planner: no zero-exposure action":"No reachable firing route in current scene";
            if(now-lastRecoveryAt>5000){lures.movementFailed();planner.widenSearch();lastPlanTick=-1;lastRecoveryAt=now;}
            return;
        }
        combatPhase=p.reason().toLowerCase(Locale.ROOT).contains("peek")||p.reason().startsWith("Return to the same lure")?"Peek / return lure":
            p.reason().startsWith("Hold")?"Hold / lure":p.reason().startsWith("Short step")?"Kite between shots":
            p.safe()?"Isolate / protected attack":"Resolve conflicting threats";
        warning=p.safe()?"Live validation pending; damage is monitored":"Nonzero modeled exposure: "+p.risk()+" (not a promised HP amount)";
        if(!usesRecordedWave(waves.rotation(),waves.wave())&&!meleeMode&&waves.predictionReady()&&waves.wave()>0&&waves.wave()<63&&model.mobs().size()==1&&!prepositioned) {
            if(preparedWave!=waves.wave()) {
                preparedWave=waves.wave();prepositionStarted=f.tick;
                Snapshot upcoming=predictedWave(f,waves.wave()+1);
                if(upcoming!=null)camps.put(waves.wave()+1,campFor(f,waves.wave()+1));
            }
            FcModel.Tile camp=campFor(f,waves.wave()+1);
            Mob survivor=model.mobs().get(0);
            boolean canFinish=camp!=null&&CombatPlanner.playerCanAttack(model,camp,survivor);
            if(canFinish&&!model.player().equals(camp)&&f.tick-prepositionStarted<24) {
                Plan route=planner.route(model,camp);
                if(route.safe()) {
                    // An attack already in flight cannot be undone; cancel subsequent auto-attacks.
                    if(f.interactingIndex>=0&&cancelAttackTick<0) {
                        if(actions.move(f,model.player()))cancelAttackTick=f.tick;
                        status="Stopping auto-attack before next-wave positioning";return;
                    }
                    plan=route;prayerPlanTick=f.tick;
                    if(protectInCave(urgent!=Protection.NONE?urgent:route.protection()))movePlan(f,route);
                    status="Positioning to finish wave "+waves.wave()+" at wave "+(waves.wave()+1)+" cover";
                    combatPhase="Prepare next-wave spawn";return;
                }
            }
            prepositioned=true; // Never deadlock the final kill on an unreachable camp.
        }
        if(!p.nextStep().equals(f.model.player())){movePlan(f,p);return;}
        if(p.targetIndex()<0) {status=p.reason();combatPhase="Hold / observe lure";return;}
        Mob target=model.mobs().stream().filter(m->m.index()==p.targetIndex()).findFirst().orElse(null);
        if(target==null||(f.moving&&target.kind()!=Kind.BAT)||!planner.attackAllowed(model,target,protection,false)){status="Rechecking stationary range, target, and protection";return;}
        if(true&&f.weapon.toLowerCase(Locale.ROOT).contains("blowpipe")&&f.specialEnergy>=500&&f.specialEnabled==0
            &&f.interactingIndex==target.index()&&now-lastSpecAt>1800&&urgent==Protection.NONE&&actions.special()) {
            lastSpecAt=now;forceReengage=true;status="Blowpipe special; re-engaging";return;
        }
        if(f.interactingIndex!=target.index()||forceReengage) {
            if(dispatchAttack(f,target,forceReengage,"planned target")) {
                forceReengage=false;status="Attacking "+target.kind().name+" with "+protection;
                trace.event("attack","wave="+waves.wave()+" index="+target.index()+" kind="+target.kind());
            }
        }else status="Protected attack: "+target.kind().name;
        if(f.interactingIndex==target.index()&&!forceReengage
            &&handleThrall(f,target,p,protection,urgent)){bowPrayer.interrupt();return;}

    }
    @Override
    /** A returning/new healer invalidates an already-issued group pull too. */
    protected boolean cancelHealerRetreat(FcFrame f) {
        if(healers.phase()==HealerGroup.Phase.LURING||healerRetreat==null)return false;
        if(!movement.pending()&&!f.moving){healerRetreat=null;lastPlanTick=-1;return false;}
        status="Stopping obsolete healer pull; rechecking the live group";
        if(!protectInCave(jadProtection(f))||false)return true;
        synchronized(optionalInputLock) {
            FcFrame current=dispatchFrame(f);
            if(current!=null&&actions.overheadActive(jadProtection(current))
                &&actions.move(current,current.model.player(),false)) {
                movement.reset();healerRetreat=null;lastPlanTick=-1;lastMoveTick=current.tick;
                forceReengage=true;bowPrayer.interrupt();
            }
        }
        return true;
    }
    @Override
    /** Stop subsequent shots after the tag lands while another healer still heals Jad. */
    protected boolean cancelTaggedHealerAttack(FcFrame f) {
        if(healers.phase()!=HealerGroup.Phase.TAGGING||healers.remaining()==0
            ||!healers.confirmed(f.interactingIndex))return false;
        if(healerCancelIndex==f.interactingIndex&&f.tick-healerCancelTick<3)return false;
        if(!protectInCave(jadProtection(f))||false)return true;
        synchronized(optionalInputLock) {
            FcFrame current=dispatchFrame(f);
            if(current==null||!healers.confirmed(current.interactingIndex)
                ||!actions.overheadActive(jadProtection(current)))return true;
            if(actions.move(current,current.model.player(),false)) {
                healerCancelIndex=current.interactingIndex;healerCancelTick=current.tick;
                forceReengage=true;bowPrayer.interrupt();
                status="Healer aggro confirmed; stopping extra shots before the next tag";
            }
        }
        return true;
    }
    @Override
    /** Group collection always runs before the ordinary target/kill planner. */
    protected boolean handleHealers(FcFrame f,Protection urgent) {
        HealerGroup.Phase phase=healers.phase();
        if(phase==HealerGroup.Phase.IDLE){healerRetreat=null;pureHealerLure.reset();healerPullLease.reset();return false;}
        if(phase==HealerGroup.Phase.FIGHTING)return false;
        Snapshot model=withTaggedHealer(f.model);
        if(!healers.fresh(f.tick)) {status="Waiting for a fresh healer group after spawn/despawn";return true;}
        if(phase==HealerGroup.Phase.TAGGING) {
            combatPhase="Tag all healers";
            if(healers.pending()>=0) {
                status="Maintaining Jad prayer while healer tag reaches the server";return true;
            }
            if(healers.remaining()==0) {status="All healer tags observed; confirming the whole group before pulling";return true;}
            if(movement.pending())return false; // Continue a checked tag approach, never an old pull.
            if(f.moving){status="Finishing healer tag approach";return true;}
            Plan tag=lastPlanTick==f.tick&&plan!=null&&plan.reason().contains("remaining healer")?plan:
                HealerTactics.tag(model,false);
            plan=tag;prayerPlanTick=lastPlanTick=f.tick;
            if(tag==null){status="Finding a protected approach to the remaining healers";return true;}
            Protection protection=urgent!=Protection.NONE?urgent:tag.protection();
            if(!protectInCave(protection))return true;
            if(!tag.nextStep().equals(model.player())) {
                movePlan(f,tag);status="Approaching remaining healer; group pull waits for every tag";return true;
            }
            Mob target=model.mobs().stream().filter(m->m.index()==tag.targetIndex()).findFirst().orElse(null);
            if(target!=null&&!target.attackingPlayer()&&dispatchAttack(f,target,true,"healer group tag")) {
                healers.tagRequested(target.index(),f.tick);forceReengage=true;
                status="Healer tag sent; waiting for observed aggro";
            }
            return true;
        }
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
        combatPhase="Pull tagged healer group";
        if(healerRetreat==null) {
            Plan retreat=new CombatPlanner().plan(model,null);
            if(!CombatPlanner.actionable(retreat,false)) {
                status="All healers tagged; finding a protected group pull";return true;
            }
            healerRetreat=retreat.destination();lastPlanTick=-1;
        }
        if(model.player().equals(healerRetreat)) {
            healers.lureComplete();healerRetreat=null;lastPlanTick=-1;forceReengage=true;
            events.add("healer-group FIGHTING confirmed="+healers.progress());
            status="Healer group collected; finishing healers";return false;
        }
        if(movement.pending())return false;
        Plan retreat=planner.route(model,healerRetreat);plan=retreat;prayerPlanTick=f.tick;
        if(config.pureMode()&&!healerPullLease.allow(f.tick,model.player(),healerRetreat,
            CombatPlanner.actionable(retreat,false))) {
            trace.event("healer-pull-expired","tick="+f.tick+" goal="+healerRetreat+" reason="+retreat.reason());
            movement.reset();healerRetreat=null;healers.lureComplete();healerPullLease.reset();
            lastPlanTick=-1;forceReengage=true;return false;
        }
        if(CombatPlanner.actionable(retreat,false)
            &&protectInCave(urgent!=Protection.NONE?urgent:retreat.protection()))movePlan(f,retreat);
        status="All healers tagged; pulling the group behind cover";return true;
    }
    @Override
    protected boolean dispatchAttack(FcFrame f,Mob target,boolean force,String context) {
        long now=System.currentTimeMillis();
        FcFrame current=dispatchFrame(f);
        if(current==null){dispatchDeferred(f,"Attack: scene, player or frame age changed");return false;}
        if(movement.pending()||Microbot.pauseAllScripts.get()||InputArbiter.isHuman())return false;
        Mob live=current.model.mobs().stream().filter(m->m.index()==target.index()&&m.kind()==target.kind()).findFirst().orElse(null);
        if(hasJad(current)&&(!healers.fresh(current.tick)||healers.phase()==HealerGroup.Phase.TAGGING
            &&(live==null||live.kind()!=Kind.HEALER||healers.confirmed(live.index()))))return false;
        Protection protection=current.tick-jadAttackTick<=4?jadStyle:
            plan==null?CombatPlanner.protectionForNextTick(current.model,current.model.player()):plan.protection();
        protection=actions.attackProtection(desiredCaveProtection(protection));
        Protection liveProtection=desiredCaveProtection(CombatPlanner.protectionForNextTick(withTaggedHealer(current.model),current.model.player()));
        boolean batFirst=!config.pureMode()&&live!=null&&live.kind()==Kind.BAT&&!hasJad(current)
            &&CombatPlanner.playerCanAttack(current.model,current.model.player(),live)
            &&liveProtection!=Protection.MAGIC&&liveProtection!=Protection.RANGE;
        // A legal bat shot must not wait for an optional melee flick or potion.
        // Ranged/magic threats and Jad still require their actual protection.
        if(live==null||(current.moving&&live.kind()!=Kind.BAT)||(!batFirst&&!actions.overheadActive(protection))
            ||(!batFirst&&!(config.pureMode()&&!meleeMode&&!hasJad(current)?
                purePlanner.attackAllowed(withTaggedHealer(current.model),live,protection,false):
                planner.attackAllowed(withTaggedHealer(current.model),live,protection,false)))) {
            status="Rechecking current attack range / threats";lastPlanTick=-1;return false;
        }
        if(target.index()==pendingAttackIndex&&!pendingAttackAcknowledged&&now-lastAttackRequestAt<1200)return false;
        if(live.kind()!=Kind.BAT&&!hasJad(current)&&!attackInputWindow(current)) {
            status="Protecting upcoming attack before target click";return false;
        }
        boolean healerTag=live.kind()==Kind.HEALER&&!live.attackingPlayer()&&hasJad(current);
        if(healerTag&&!jadActions.available(current.tick,jadAttackTick,actions.overheadActive(jadProtection(current)))) {
            status="Healer tag: waiting for confirmed Jad prayer window";return false;
        }

        boolean sent;
        synchronized(optionalInputLock) {
            // Acquiring the shared input lock may wait behind a prayer click.
            // Recheck the same deadline/acknowledgement before starting mouse travel.
            if(!batFirst&&!hasJad(current)&&(!attackInputWindow(current)
                ||!actions.overheadActive(actions.attackProtection(protection))))return false;
            // A Jad animation may have arrived while an earlier snapshot was being checked.
            if(hasJad(current)&&!actions.overheadActive(jadProtection(current)))return false;
            if(hasJad(current)&&(!healers.fresh(current.tick)||healers.phase()==HealerGroup.Phase.TAGGING
                &&(live.kind()!=Kind.HEALER||healers.confirmed(live.index()))))return false;
            if(healerTag&&!jadActions.available(current.tick,jadAttackTick,true))return false;
            if(current.interactingIndex!=live.index())bowPrayer.interrupt();
            boolean useOffence=offenceEnabled(current,live.index());
            if(current.prayer>0&&(useOffence||config.prayerConservation())
                &&tickPrayers!=null&&tickPrayers.ownsInput())tickPrayers.primeAttack(current.tick,live.index());
            if(useOffence&&current.prayer>0) {
                if(!actions.offenceReady()) {
                    // Optional offence must not introduce an endless combat hold if
                    // its widget is unavailable. Keep trying without blocking protection.
                    warning="Offensive prayer not acknowledged; attacking while activation retries";
                }
            }
            sent=actions.attack(live.index(),live.kind().name,force,current);
            if(sent&&healerTag)jadActions.consumed(jadAttackTick);
        }
        if(sent) {
            if(usesRecordedWave(waves.rotation(),waves.wave()))
                lures.observeFight(current.model,current.recordedAnchor(RecordedLureBook.ITALY),target.index());
            if(pendingAttackIndex!=target.index()){attackAttempts=0;lastAttackProgressAt=now;}
            pendingAttackIndex=target.index();pendingAttackAcknowledged=false;lastAttackRequestAt=now;attackAttempts++;forceReengage=false;
            trace.event("attack-request",context+" wave="+waves.wave()+" index="+target.index()+" attempt="+attackAttempts);
        }
        if(!sent||attackAttempts==1||now-lastAttackDiagnosticAt>5000) {
            if(now-lastAttackDiagnosticAt>1500) {
                lastAttackDiagnosticAt=now;
                Microbot.log("[Dro Firecape] "+actions.lastAttackResult()+" interaction="+f.interactingIndex+" wave="+waves.wave()+" "+context);
            }
        }
        return sent;
    }
    @Override
    /** Kill an accessible prayer-draining bat before optional UI work or lures. */
    protected boolean tryBatAttack(FcFrame f) {
        if(false||f.prayer<=0||movement.pending()||lures.hasPendingReturn()||hasJad(f))return false;
        Snapshot priorities=f.model.atWave(waves.wave());
        if(priorities.mobs().stream().anyMatch(m->CaveSafety.lateAttackingRanger(priorities,m)))return false;
        Mob bat=f.model.mobs().stream().filter(m->m.kind()==Kind.BAT
            &&CombatPlanner.playerCanAttack(f.model,f.model.player(),m))
            .min(Comparator.comparingInt(m->m.distance(f.model.player()))).orElse(null);
        if(bat==null||f.interactingIndex==bat.index()&&!forceReengage)return false;
        Protection protection=desiredCaveProtection(CombatPlanner.protectionForNextTick(f.model,f.model.player()));
        if((protection==Protection.MAGIC||protection==Protection.RANGE)&&!actions.overheadActive(protection))return false;
        plan=new Plan(f.model.player(),f.model.player(),protection,bat.index(),true,0,0,0,"Immediate bat attack before optional inputs");
        prayerPlanTick=f.tick;
        if(!dispatchAttack(f,bat,true,"immediate bat priority"))return false;
        status="Attacking bat immediately";combatPhase="Kill prayer-draining bat";
        return true;
    }
    @Override
    /** Fast path for a reachable shot under the selected protection and exposure policy. */
    protected boolean tryImmediateAttack(FcFrame f,Snapshot model,Protection urgent) {
        if(f.moving||movement.pending()||lures.hasPendingReturn()||model.mobs().isEmpty()||model.mobs().stream().anyMatch(m->m.kind()==Kind.JAD||m.kind()==Kind.HEALER))return false;
        Mob target=null;int best=Integer.MIN_VALUE;
        Protection protect=actions.attackProtection(desiredCaveProtection(urgent!=Protection.NONE?urgent:CombatPlanner.protectionForNextTick(model,model.player())));
        for(Mob m:model.mobs())if(CombatPlanner.playerCanAttack(model,model.player(),m)) {
            int score=CaveSafety.targetPriority(model,m,protect)+(m.index()==f.interactingIndex?15:0)-m.distance(model.player());
            if(score>best){target=m;best=score;}
        }
        if(target==null||f.interactingIndex==target.index()&&!forceReengage)return false;
        Mob ranged=CaveSafety.rangedTarget(model);
        Mob retained=CaveSafety.retainedSafeShot(model,f.interactingIndex);
        if(ranged!=null&&target.kind()!=Kind.BAT&&CaveSafety.targetPriority(model,ranged,protect)
            >CaveSafety.targetPriority(model,target,protect)&&(retained==null||retained.index()!=target.index()))return false;
        // Apply the configured exposure policy under the tick controller's overhead.
        if(!planner.attackAllowed(model,target,protect,false))return false;
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
        boolean sent=dispatchAttack(ready,target,true,"immediate protected shot");
        status=sent?"Attack requested: "+target.kind().name:"Checking attack acknowledgement / current NPC";
        combatPhase="Protected attack";
        return true;
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
    protected boolean usesRecordedWave(int rotation,int wave) {
        return !meleeMode&&true&&wave>=0&&wave<63;
    }
    @Override
    /** General no-progress recovery, including plans which cannot find a legal action. */
    protected boolean recoverStalledCombat(FcFrame f,long now) {
        boolean roomStalled=combatProgress.due(f.model);
        if((!roomStalled&&now-Math.max(lastProgressAt,lastRecoveryMotionAt)<=6000)||now-lastRecoveryAt<=6000||hasJad(f))return false;
        combatProgress.recovering(f.tick);
        Snapshot model=withTaggedHealer(f.model);
        Protection recoveryProtection=desiredCaveProtection(Protection.NONE);
        Mob shot=CaveSafety.preferredShot(model,recoveryProtection);
        Mob ranger=CaveSafety.rangedTarget(model);
        if(ranger!=null&&ranger.kind()==Kind.RANGER&&shot!=null&&shot.kind()!=Kind.BAT&&shot.kind()!=Kind.RANGER)shot=null;
        int shotRisk=CombatPlanner.immediateExposure(model,model.player(),recoveryProtection);
        // A watchdog must not interrupt a legal firing position just because a
        // prayer handoff or several zero damage rolls postponed health progress.
        Plan recovery=shot!=null&&!lures.hasPendingReturn()?
            new Plan(model.player(),model.player(),recoveryProtection,shot.index(),
                shotRisk==0,0,0,shotRisk,
                "Stalled combat: reacquire reachable attacker from current tile"):
            usesRecordedWave(waves.rotation(),waves.wave())?
            lures.recoverRecorded(model,f.recordedAnchor(RecordedLureBook.ITALY),f.recordedAnchor(RecordedLureBook.WEST_PEEK)):null;
        if(recovery==null) {
            // Preserve the return destination even after repeated dispatch failures.
            lures.recover();
            recovery=lures.hasPendingReturn()?lures.finishReturn(model):null;
            if(recovery==null){planner.recover(model);recovery=planner.plan(model,f.recordedAnchor(RecordedLureBook.ITALY));}
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
    @Override
    protected void movePlan(FcFrame f,Plan p) {
        if(!CombatPlanner.actionable(p,exitRequested?false:false)||p.nextStep()==null
            ||p.nextStep().equals(f.model.player()))return;
        FcFrame current=dispatchFrame(f);
        if(current==null){dispatchDeferred(f,"Move: scene, player or frame age changed");return;}
        boolean healerPull=healerRetreat!=null;
        if(healerPull&&healers.phase()!=HealerGroup.Phase.LURING)return;
        if(lastMoveTick==current.tick||lastFailedMoveTick==current.tick||false
            ||Microbot.pauseAllScripts.get()||InputArbiter.isHuman())return;
        MovementAck.Result ack=movement.observe(current.model.player(),current.tick);
        if(ack==MovementAck.Result.FAILED||ack==MovementAck.Result.DEVIATED) {
            lastFailedMoveTick=current.tick;lures.movementFailed();planner.widenSearch();lastPlanTick=-1;return;
        }
        boolean retry=ack==MovementAck.Result.RETRY_MINIMAP;
        if(movement.pending()&&!retry)return;
        Snapshot live=withTaggedHealer(current.model);
        Protection protection=earlyConservationGap(current)?Protection.NONE:desiredCaveProtection(p.protection());
        boolean strict=!exitRequested&&false;
        boolean spacingPure=config.pureMode()&&!meleeMode&&!hasJad(current)&&pureSpacing.owns(p);
        boolean recoveringPure=config.pureMode()&&!meleeMode&&!hasJad(current)&&!spacingPure&&pureCombatRecovery.owns(p);
        boolean exposedHealerPull=config.pureMode()&&!meleeMode&&hasJad(current)&&pureHealerLure.owns(p);
        Plan checked=exposedHealerPull?
            MinimapMovement.healerChecked(live,p.destination(),retry?movement.destination():p.nextStep(),protection,p.reason()):
            retry?MinimapMovement.checked(live,p.destination(),movement.destination(),protection,p.reason()):
            (recoveringPure||spacingPure)?MinimapMovement.checked(live,p.destination(),p.nextStep(),protection,p.reason()):
            MinimapMovement.route(live,p.destination(),p.nextStep(),protection,strict,p.reason());
        if(!CombatPlanner.actionable(checked,strict)) {
            movement.reset();lures.movementFailed();planner.widenSearch();lastPlanTick=-1;
            if(spacingPure)pureSpacing.yieldToRecovery(current.tick);
            if(config.pureMode()&&!meleeMode&&!hasJad(current)&&!exitRequested) {
                pureCombatRecovery.rejected(live,p);lastFailedMoveTick=current.tick;
            }
            trace.event("minimap-route-rejected",checked.reason());return;
        }
        if(meleeMode) {
            // The minimap command can reach farther than the planner's two-tile
            // preview. Arm for its actual endpoint before entering contact range.
            protection=MeleeProtection.choose(live,checked.nextStep(),requestedCaveProtection);
            checked=exposedHealerPull?MinimapMovement.healerChecked(live,checked.destination(),checked.nextStep(),protection,checked.reason()):
                MinimapMovement.checked(live,checked.destination(),checked.nextStep(),protection,checked.reason());
            if(!CombatPlanner.actionable(checked,strict)){lastPlanTick=-1;return;}
        }
        if(config.pureMode()&&!meleeMode&&!hasJad(current)&&!exitRequested
            &&!(recoveringPure?pureCombatRecovery.routeAllowed(live,checked.nextStep()):PureSafety.routeAllowed(live,checked.nextStep()))) {
            pureLures.movementFailed();purePlanner.widenSearch();lastPlanTick=-1;
            pureCombatRecovery.rejected(live,checked);lastFailedMoveTick=current.tick;
            if(spacingPure)pureSpacing.yieldToRecovery(current.tick);
            status="Pure: rejecting newly exposed lane / contact endpoint";
            trace.event("pure-route-rejected",checked.reason());return;
        }
        Protection routeGuard=MinimapMovement.routeProtection(live,checked.nextStep(),protection);
        if(routeGuard!=protection) {
            protection=routeGuard;
            checked=exposedHealerPull?MinimapMovement.healerChecked(live,checked.destination(),checked.nextStep(),protection,checked.reason()):
                MinimapMovement.checked(live,checked.destination(),checked.nextStep(),protection,checked.reason());
            if(!CombatPlanner.actionable(checked,strict)){lastPlanTick=-1;return;}
        }
        stopCamera();
        if(cameraTurning.get())return;
        if(movement.coolingDown(current.model.player(),checked.nextStep(),current.tick)) {
            status="Movement did not arrive; waiting before retrying "+checked.nextStep();return;
        }
        plan=checked;prayerPlanTick=current.tick;
        FcTickPrayers driver=tickPrayers;
        if(driver!=null&&driver.ownsInput()) {
            driver.primeMovement(current.tick,protection);
            if(!driver.movementReady(protection)&&!(exitRequested&&current.prayer==0)) {
                status="Pre-arming "+protection+" before entering attack range";return;
            }
            if(driver.requested()!=Protection.NONE&&driver.requested()!=protection) {
                protection=driver.requested();
                checked=exposedHealerPull?MinimapMovement.healerChecked(live,checked.destination(),checked.nextStep(),protection,checked.reason()):
                MinimapMovement.checked(live,checked.destination(),checked.nextStep(),protection,checked.reason());
                if(!CombatPlanner.actionable(checked,strict))return;
                plan=checked;
            }
        }
        if(!protectInCave(protection)&&!(exitRequested&&current.prayer==0))return;

        stopCamera(); // Do not rotate the minimap underneath a click.
        if(cameraTurning.get())return; // Wait for the cancelled yaw key to be released.
        FcModel.Tile destination=checked.nextStep();
        if(movement.coolingDown(current.model.player(),destination,current.tick)) {
            status="Movement did not arrive; waiting before retrying "+destination;return;
        }
        boolean sent;
        boolean minimapFirst=current.model.player().distance(destination)>4;
        synchronized(optionalInputLock) {
            if(hasJad(current)&&!actions.overheadActive(meleeMode?protection:jadStyle)&&current.prayer>0)return;
            if(healerPull&&(healers.phase()!=HealerGroup.Phase.LURING||!healers.fresh(current.tick)))return;
            sent=actions.move(current,destination,minimapFirst);
            if(!sent&&!minimapFirst) {
                minimapFirst=true;sent=actions.move(current,destination,true);
            }
        }
        if(sent) {
            movement.sent(current.model.player(),destination,current.tick,MinimapMovement.path(live,destination));
            if(driver!=null&&driver.ownsInput())driver.movementSent();
            lastMoveTick=current.tick;forceReengage=true;bowPrayer.interrupt();status=p.reason();
            trace.event("move-request","tick="+current.tick+" from="+current.model.player()+" minimap="+destination
                +" intent="+p.destination()+" attempt="+movement.attempts()+" prayer="+protection
                +" input="+(minimapFirst?"minimap":"visible recorded step"));
        } else {
            lastMoveTick=current.tick;
            // Failed dispatches do not acknowledge arrival or claim that a move occurred.
            if(retry)movement.sent(current.model.player(),destination,current.tick,MinimapMovement.path(live,destination));
            else {lures.movementFailed();lastPlanTick=-1;}
            trace.event("move-dispatch-failed","tick="+current.tick+" destination="+destination+" retry="+retry);
        }
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
    @Override
    /** Zero prayer must not deadlock while waiting for an overhead that cannot activate. */
    protected boolean emergencyPrayer(FcFrame f) {
        if(false||supplyAck.pending()||System.currentTimeMillis()-lastSupplyAt<1400)return false;
        FcFrame.ItemSlot dose=f.inventory.stream().filter(i->isPrayerPotion(i.name())).findFirst().orElse(null);
        if(dose==null){if(!config.pureMode())fail("Prayer exhausted: no restore dose remains");return false;}
        long now=System.currentTimeMillis();
        if(!supplyPreparation.ready(dose.id(),now))return false;
        FcActions.ItemResult result;
        synchronized(optionalInputLock) {
            if(hasJad(f)&&f.prayer>0&&!actions.overheadActive(jadProtection(f)))return false;
            result=actions.itemStep(dose,"Drink");
        }
        if(result==FcActions.ItemResult.SENT){supplyPreparation.reset();supplyRequested(f,dose);lastSupplyAt=now;forceReengage=true;
            status="Emergency prayer restore before overhead acknowledgement";trace.event("supply",dose.name());return true;}
        return preparingSupply(dose,"Drink",result,now);
    }
    @Override
    protected boolean combatBoostNeeded(FcFrame f,String name) {
        String n=name.toLowerCase(Locale.ROOT);
        if(!meleeMode)return FcSupplyPolicy.rangedPotion(n)&&!combatStatsDrained(f)
            &&FcSupplyPolicy.rangedRecoveryReady(f.tick,lastBrewTick,
                healingNeeded(f))
            &&FcSupplyPolicy.rangedDoseAllowed(f.cave?waves.wave():63,f.ranged,f.baseRanged,FcSupplyPolicy.rangedDoses(f.inventory));
        if(n.startsWith("super combat potion(")||n.startsWith("combat potion("))
            return f.attack<=f.baseAttack||f.strength<=f.baseStrength;
        if(n.startsWith("super attack(")||n.startsWith("attack potion("))return f.attack<=f.baseAttack;
        if(n.startsWith("super strength(")||n.startsWith("strength potion("))return f.strength<=f.baseStrength;
        return false;
    }
    protected boolean pureSweetsAvailable(FcFrame f) {
        return f.cave&&config.pureMode()&&config.usePurpleSweets()
            &&f.inventory.stream().anyMatch(i->FcSupplyPolicy.sweet(i.name())&&i.quantity()>0&&i.hasAction("Eat"));
    }
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
    protected boolean supplies(FcFrame f,Protection urgent) {
        long now=System.currentTimeMillis();if(now-lastSupplyAt<1400||false)return false;
        if(supplyAck.pending()){status="Confirming consumed supply";return true;}
        boolean sweets=config.usePurpleSweets()&&f.inventory.stream().anyMatch(i->FcSupplyPolicy.sweet(i.name()));
        boolean healing=healingNeeded(f);
        boolean jad=hasJad(f),critical=healing||brewDebt>0||f.prayer<=8||FcSupplyPolicy.criticalHealth(f.model,f.hp,f.maxHp);
        if(supplyPreparation.pending()&&!supplyPreparation.waiting(now)) {
            actions.releaseTab();supplyPreparation.defer(now);return false;
        }
        if(!jad&&!critical&&!optionalInputWindow(f,supplyPreparation.inputBudgetMillis()))return supplyPreparation.pending();
        if(jad&&!critical&&!jadActions.available(f.tick,jadAttackTick,actions.overheadActive(jadProtection(f))))return false;
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
            if(item==null&&f.prayer<=3&&!config.pureMode()){fail("Prayer supplies exhausted");return false;}
        }
        if(item==null&&healing) {
            if(brewAllowed(f))item=f.inventory.stream()
                .filter(i->i.name().toLowerCase(Locale.ROOT).startsWith("saradomin brew(")).findFirst().orElse(null);
            if(item==null) {
                item=f.inventory.stream().filter(i->i.hasAction("Eat")&&!FcSupplyPolicy.sweet(i.name())).findFirst().orElse(null);
                if(item!=null)action="Eat";
            }
        }
        if(item==null&&!jad&&f.rawEnergy<=2000) {
            item=f.inventory.stream().filter(i->i.name().toLowerCase(Locale.ROOT).startsWith("stamina potion(")&&!actions.staminaActive()
                ||i.name().toLowerCase(Locale.ROOT).startsWith("super energy(")).findFirst().orElse(null);action="Drink";
        }
        if(item==null&&true&&(urgent==Protection.NONE||!meleeMode&&jad)) {
            item=f.inventory.stream().filter(i->combatBoostNeeded(f,i.name())).findFirst().orElse(null);action="Drink";
        }
        if(item==null&&!jad&&urgent==Protection.NONE&&!f.moving&&!exitRequested
            &&state==State.FIGHTING&&healers.pending()<0&&healerRetreat==null
            &&plan!=null&&plan.safe()&&plan.nextStep()!=null&&plan.nextStep().equals(f.model.player())) {
            FcFrame.ItemSlot regen=f.inventory.stream()
                .filter(i->isPrayerRegenPotion(i.name())&&i.hasAction("Drink")).findFirst().orElse(null);
            if(regen!=null&&prayerRegenerationDue(f,now)){item=regen;action="Drink";}
        }
        FcActions.ItemResult result=FcActions.ItemResult.UNAVAILABLE;
        if(item!=null&&!supplyPreparation.ready(item.id(),now))return false;
        if(item!=null) synchronized(optionalInputLock) {
            FcTickPrayers owner=tickPrayers;
            if(f.prayer>0&&owner!=null&&owner.ownsInput()&&!owner.protectionReady())return true;
            if(jad&&f.prayer>0&&!actions.overheadActive(jadProtection(f)))return false;
            if(jad&&!critical&&!jadActions.available(f.tick,jadAttackTick,true))return false;
            result=actions.itemStep(item,action);
            if(result==FcActions.ItemResult.SENT&&jad)jadActions.consumed(jadAttackTick);
        }
        if(result==FcActions.ItemResult.SENT){
            supplyPreparation.reset();supplyRequested(f,item);
            if(isPrayerRegenPotion(item.name())) {
                lastPrayerRegenAttemptAt=now;
                pendingPrayerRegenDoses=prayerRegenerationDoses(f);
            }
            lastSupplyAt=now;forceReengage=true;status=action+" "+item.name();trace.event("supply",item.name());return true;}
        if(item!=null)return preparingSupply(item,action,result,now);
        supplyPreparation.reset();
        return false;
    }
    @Override
    protected boolean healWithSweets(FcFrame f,Protection urgent) {
        FcFrame.ItemSlot sweet=f.inventory.stream().filter(i->FcSupplyPolicy.sweet(i.name())&&i.hasAction("Eat")).findFirst().orElse(null);
        if(!config.usePurpleSweets()||false||sweet==null||f.hp>=f.maxHp||f.moving
            ||movement.pending()||(!config.pureMode()&&lures.hasPendingReturn())||hasJad(f)||!FcSupplyPolicy.sweetPauseSafe(f.model)) {
            sweetCancelTick=sweetProgressTick=-1;return false;
        }
        if(f.tick<sweetRetryTick)return false;
        if(sweetProgressTick<0||f.hp>sweetHp||sweet.quantity()<sweetCount) {
            sweetProgressTick=f.tick;sweetHp=f.hp;sweetCount=sweet.quantity();
        }
        if(f.tick-sweetProgressTick>12) {
            sweetRetryTick=f.tick+20;sweetCancelTick=sweetProgressTick=-1;
            actions.releaseTab();return false;
        }

        status="Safe pause: healing to full with purple sweets";
        if(f.interactingIndex>=0) {
            // Cancel auto-attacking once; do not kill the last trapped monster while healing.
            if(sweetCancelTick<0&&optionalInputWindow(f,900))synchronized(optionalInputLock) {
                if(actions.move(f,f.model.player()))sweetCancelTick=f.tick;
            }
            if(sweetCancelTick<0||f.tick-sweetCancelTick<3)return true;
            // Some clients retain an interaction briefly; no repeated ground clicks.
        }
        if(supplyAck.pending()||System.currentTimeMillis()-lastSupplyAt<1800||!optionalInputWindow(f,900))return true;
        long now=System.currentTimeMillis();
        if(!supplyPreparation.ready(sweet.id(),now))return true;
        synchronized(optionalInputLock) {
            FcFrame live=frame;
            if(live==null||live.stale()||!live.sameScene(f)||live.moving||live.hp>=live.maxHp
                ||!FcSupplyPolicy.sweetPauseSafe(live.model)||!optionalInputWindow(live,900))return false;
            FcActions.ItemResult result=actions.itemStep(sweet,"Eat");
            if(result==FcActions.ItemResult.SENT) {
                supplyPreparation.reset();supplyRequested(f,sweet);lastSupplyAt=now;
                forceReengage=true;trace.event("supply",sweet.name());
            }else preparingSupply(sweet,"Eat",result,now);
        }
        return true;
    }
    @Override
    protected boolean handleThrall(FcFrame f,Mob target,Plan p,Protection protection,Protection urgent) {
        long now=System.currentTimeMillis();
        if(!true||false||exitRequested||state!=State.FIGHTING
            ||hasJad(f)||urgent!=Protection.NONE||f.tick-jadAttackTick<=4||f.moving||f.stale()
            ||frame==null||frame.tick!=f.tick||frame.world!=f.world||now<nextThrallAttemptAt
            ||now<nextThrallUiAt||now-lastSupplyAt<1400||supplyPreparation.pending()||healers.pending()>=0||healerRetreat!=null
            ||energyPause.state()!=EnergyPause.State.OFF||!p.safe()
            ||p.nextStep()==null||!p.nextStep().equals(f.model.player())
            ||target.kind()==Kind.HEALER||f.model.mobs().stream().anyMatch(m->m.kind()==Kind.HEALER))return false;
        // Summoning can wait until the ranger is dead / melee trapped. It must
        // not occupy the spellbook across a visible protection-switch deadline.
        if(!optionalInputWindow(f,1400))return false;
        Protection castProtection=actions.attackProtection(protection);
        // The most expensive thrall costs six points. Keep the configured restore
        // reserve after summoning rather than trading the overhead for extra DPS.
        int requiredPrayer=Math.max(7,config.restorePrayer())+6;
        if(f.prayer<requiredPrayer)return false;
        Rs2Thrall thrall=FcActions.read(()->{
            if(client.getGameState()!=GameState.LOGGED_IN||client.getLocalPlayer()==null
                ||client.getBoostedSkillLevel(Skill.PRAYER)<requiredPrayer||Rs2Thrall.isActive())return null;
            for(Rs2Thrall candidate:new Rs2Thrall[]{Rs2Thrall.GREATER_GHOST,Rs2Thrall.SUPERIOR_GHOST,Rs2Thrall.LESSER_GHOST})
                if(Rs2Thrall.canCast(candidate))return candidate;
            return null;
        },null);
        if(thrall==null){nextThrallAttemptAt=now+1000;return false;}
        // Reserve the spellbook across pulses. Protection ON can preempt it,
        // but optional reset flicks cannot steal the tab before the cast.
        if(!actions.overheadActive(castProtection))return false;
        if(!actions.spellbook()) {
            if(++thrallUiMisses>8) {
                thrallUiMisses=0;nextThrallAttemptAt=now+30_000;actions.releaseTab();
                trace.event("thrall-skipped","Spellbook not acknowledged: "+actions.prayerUiStatus());
                return false; // Optional summoning must never indefinitely suppress attacks.
            }
            status="Opening spellbook for thrall: "+actions.prayerUiStatus();return true;
        }
        int[] spell=FcActions.read(()->{
            Widget root=client.getWidget(218,0);
            if(root==null||root.getStaticChildren()==null)return null;
            List<Widget> children=new ArrayList<>();
            for(Widget child:root.getStaticChildren())if(child!=null)children.add(child);
            Widget widget=Rs2Widget.findWidget(thrall.getMagicAction().getName(),children);
            if(widget==null||widget.isHidden())return null;
            Rectangle bounds=widget.getBounds();
            if(bounds==null||bounds.width<=0||bounds.height<=0)return null;
            return new int[]{widget.getId(),bounds.x,bounds.y,bounds.width,bounds.height};
        },null);
        synchronized(optionalInputLock) {
            // Recheck after acquiring input, because a Jad animation can arrive
            // while a spell/widget snapshot is being read on the client thread.
            if(!enabled||exitRequested||Microbot.pauseAllScripts.get()||InputArbiter.isHuman()
                ||f.stale()||frame==null||frame.tick!=f.tick||frame.world!=f.world
                ||f.tick-jadAttackTick<=4||!actions.overheadActive(castProtection))return false;
            if(spell==null) {
                if(++thrallUiMisses>3) {
                    thrallUiMisses=0;nextThrallAttemptAt=now+30_000;actions.releaseTab();
                    trace.event("thrall-skipped","Spell widget unavailable; check Arceuus spell filters");
                    return false;
                }
                nextThrallUiAt=now+80;
                status="Waiting for visible thrall spell";
                return true;
            }
            boolean ready=FcActions.read(()->client.getGameState()==GameState.LOGGED_IN
                &&client.getWorld()==f.world&&client.getLocalPlayer()!=null
                &&client.getBoostedSkillLevel(Skill.PRAYER)>=requiredPrayer
                &&Rs2Thrall.canCast(thrall),false);
            if(!ready){actions.releaseTab();return false;}
            // A click is a request; active/cooldown flags still govern recasting.
            if(!actions.spell(spell[0],thrall.getName()))return true;
            nextThrallAttemptAt=now+OPTIONAL_RETRY_MS;thrallUiMisses=0;
            forceReengage=true;lastPlanTick=-1;
            status="Summoning "+thrall.getName()+"; re-engaging target";
            trace.event("thrall-cast-request",thrall.getName());
            return true;
        }
    }
    @Override

    protected void requestRecoveryPause(FcFrame f,Protection urgent) {
        int wave=waves.wave();
        if(config.pureMode()&&!PrayerConservation.recoveryNeeded(f.hp,f.maxHp))return;
        if(false||urgent!=Protection.NONE||hasJad(f)||supplyAck.pending()||supplyPreparation.pending()
            ||f.moving||movement.pending()||f.hp*100<=f.maxHp*config.eatPercent()
            ||f.prayer<=config.restorePrayer()||!optionalInputWindow(f,300)
            ||!energyPause.shouldRequest(config.energyPause(),config.recoveryStartWave(),wave,
                f.interactingIndex>=0&&!f.model.mobs().isEmpty()))return;
        synchronized(optionalInputLock) {
            if(actions.requestWavePause(f.world,()->enabled&&!exitRequested&&!false
                &&waves.wave()==wave&&energyPause.state()==EnergyPause.State.OFF
                &&frame!=null&&!frame.stale()&&frame.world==f.world
                &&frame.model.mobs().stream().anyMatch(m->!dead.contains(m.index())))) {
                energyPause.requested(wave,f.world,System.currentTimeMillis());savePause();
                trace.event("pause-request","wave="+wave+" HP="+f.hp+"/"+f.maxHp+" prayer="+f.prayer+"/"+f.maxPrayer
                    +" pureNeedsRecovery="+config.pureMode()+"; one Logout click, continue fighting");
            }
        }
    }
    @Override
    protected void rest(FcFrame f) {
        long now=System.currentTimeMillis();stopCamera();plan=null;prayerPlanTick=-1;
        Protection guard=recoveryGuard(f);
        if(tickPrayers!=null)tickPrayers.recoveryPause(true,guard);
        if(energyPause.state()==EnergyPause.State.HOPPING||energyPause.arrivedWorld(f.world)) {
            // A rejected helper may still be followed by a delayed accepted hop.
            // Retain the expected world through backoff and observe that arrival
            // before considering another hop from the newly loaded world.
            setState(State.RESUMING,"Waiting for fresh cave and next-wave protection after hop");
            boolean ready=energyPause.resumedScene(!f.stale()&&f.cave&&f.containersReady&&f.tick>transitionGuardUntil,
                f.world,f.tick,f.capturedAt);
            if(ready) {
                if(!recoveryProtectionReady(f,guard)) {
                    status="Confirming "+guard+" before releasing resumed cave dialogue";
                    if(energyPause.hopExpired(now))warning="Resume world loaded; next-wave prayer acknowledgement is still missing";return;
                }
                if(!false)actions.continueCaveDialogue();
                finishResume();return;
            }
            if(energyPause.hopExpired(now)) {
                energyPause.hopFailed(now);savePause();warning="Resume hop timed out; retrying after backoff";
            }
            return;
        }
        if(recoverySpawnObserved||!energyPause.clear(f.tick,lastDangerTick)||f.stale()||!f.containersReady) {
            status="Verifying paused cave / fresh containers before recovery";return;
        }
        if(energyPause.state()==EnergyPause.State.RESTING) {
            boolean newlyResting=state!=State.ENERGY_REST;
            setState(State.ENERGY_REST,"Confirmed wave pause; switching owned prayers off");
            if(newlyResting){resetPlanning();recoverySupplyMisses=0;recoveryRetryAt=0;save(true);}
            if(false)return;
            if(!actions.prayersObservedOff())return;
            OptionalRecoveryPolicy.Decision recovery=OptionalRecoveryPolicy.choose(f,meleeMode,config.usePurpleSweets(),
                config.recoveryOverbrew()&&energyPause.nextWave()>=53,40,config.recoveryPrayerPercent(),true,
                true,energyPause.nextWave(),brewDebt,supplyAck.pending(),config.pureMode());
            status=recovery.status;
            if(recovery.ready) {
                trace.event("recovery-ready",recovery.status+"; HP="+f.hp+"/"+f.maxHp+" prayer="+f.prayer+"/"+f.maxPrayer);
                energyPause.arm();savePause();setState(State.RESUMING,"Recovery complete; arming next-wave protection before hop");return;
            }
            if(recoverySupplyMisses>=3){warning="Recovery consumption was not acknowledged three times; staying paused for supply/UI correction";return;}
            if(recovery.item==null||supplyAck.pending()||now<recoveryRetryAt||now-lastSupplyAt<1800)return;
            if(!supplyPreparation.ready(recovery.item.id(),now))return;
            FcActions.ItemResult result;
            synchronized(optionalInputLock) {
                result=actions.itemStep(recovery.item,recovery.action,()->enabled&&!exitRequested&&!recoverySpawnObserved
                    &&energyPause.state()==EnergyPause.State.RESTING&&frame!=null&&!frame.stale()
                    &&frame.cave&&frame.world==f.world&&frame.model.mobs().isEmpty());
            }
            if(result==FcActions.ItemResult.SENT) {
                supplyPreparation.reset();supplyRequested(f,recovery.item);lastSupplyAt=now;
                trace.event("recovery-supply",recovery.item.name());
            } else if(!preparingSupply(recovery.item,recovery.action,result,now)) {
                recoveryRetryAt=now+3000;warning="Recovery supply UI unavailable; staying paused and retrying";
            }
            return;
        }
        setState(State.RESUMING,"Confirming "+guard+" before recovery resume hop");
        if(false)return;
        if(!recoveryProtectionReady(f,guard))return;
        if(supplyAck.pending())return;
        if(energyPause.hopBlocked()){warning="Three resume hops failed; paused run retained for manual recovery or restart";status=warning;return;}
        OptionalRecoveryPolicy.Decision ready=OptionalRecoveryPolicy.choose(f,meleeMode,config.usePurpleSweets(),
            config.recoveryOverbrew()&&energyPause.nextWave()>=53,40,config.recoveryPrayerPercent(),true,true,
            energyPause.nextWave(),brewDebt,supplyAck.pending(),config.pureMode());
        if(!ready.ready){energyPause.recoverAgain();savePause();status="Recovery target changed before hop: "+ready.status;return;}
        if(!energyPause.canHop(now)){status="Waiting before retrying the resume world hop";return;}
        // Clear only the cave continue dialogue after recovery and protection are
        // observed. The only resume action below is a world hop, never Logout.
        if(actions.continueCaveDialogue())return;
        int target=actions.resumeWorld(f.world);
        if(target<0){status="Recovery complete; normal member world list unavailable";return;}
        resumeWave=energyPause.nextWave();expectedHop=target;energyPause.hopping(target,now);savePause();
        boolean accepted=actions.resumePausedWave(target);
        // The helper return is dispatch information only. Game-state and fresh
        // scene evidence on later ticks establish a successful return.
        if(!accepted&&frame!=null&&frame.world==f.world) {
            energyPause.hopFailed(System.currentTimeMillis());savePause();warning="Resume hop rejected; bounded retry queued";
        }
    }
    protected boolean recoveryProtectionReady(FcFrame f,Protection guard) {
        if(config.pureMode()&&f.prayer==0&&tickPrayers!=null&&tickPrayers.exhaustedCombatReady())return true;
        return actions.overheadActive(guard)
            &&(tickPrayers==null||!tickPrayers.ownsInput()||tickPrayers.protectionReady());
    }
    @Override
    protected void finishResume(){
        energyPause.resumed();savePause();resetPlanning();
        if(tickPrayers!=null)tickPrayers.recoveryPause(false,Protection.NONE);
        if(waves.wave()<resumeWave)waves.restore(resumeWave);
        recoverySpawnObserved=false;recoverySupplyMisses=0;recoveryRetryAt=0;
        save(false);setState(State.FIGHTING,config.pureMode()&&frame!=null&&frame.prayer==0?
            "Fresh cave observed; continuing Pure run with exhausted prayer":
            "Fresh cave and next-wave guard observed; continuing rotation "+rotationLabel());
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
    @Override
    protected void resetPlanning(boolean resetLure){openingBats.reset();pureHealerLure.reset();healerPullLease.reset();pureTraceTick=-1;pureCombatRecovery.reset();pureRepositionWatchdog.reset();pureSpacing.reset();purePlanner.reset();if(resetLure){pureLures.reset();pureWave=pureFailedMoveTick=-1;pureResetAfterReturn=false;}combatProgress.reset();movement.reset();if(resetLure){lures.reset();lureResetAfterReturn=false;}bowPrayer.interrupt();planner.reset();camps.clear();plan=null;lastPlanTick=-1;lastMoveTick=-1;prepositioned=false;healerRetreat=null;}
    @Override

    // GameTick captures server evidence. The separate ClientTick prayer path
    // uses short widget operations only; neither event handler ever sleeps.
    public void onGameTick() {
        if(!enabled)return;
        long prayerTickAt=System.nanoTime(); // timestamp BEFORE potentially expensive scene capture
        FcFrame captured=FcFrame.captureOptional(client,0,meleeMode,jadStyle,attackTicks,attackStyles,dead);
        if(captured!=null&&captured.cave)observeHealers(captured);
        FcTickPrayers driver=tickPrayers;
        if(driver!=null) {
            driver.nativeWidgets(config!=null&&config.nativeTickPrayers());
            driver.pureTinyPrayers(config!=null&&config.pureMode());
            driver.conservation(config!=null&&config.prayerConservation());
            driver.pureWave(waves.wave());
            try{
                driver.gameTick(captured,tickSpawnGuard(captured),prayerTickAt,healers.confirmedIndices());
                if(config!=null&&config.nativeTickPrayers())
                    driver.clientTick(prayerInputAllowed(),config!=null&&config.offensivePrayer(),movement.pending());
            }
            catch(RuntimeException|LinkageError e){events.add("tick-prayer-capture-error "+e);driver.gameTick(null,Protection.NONE);}
        }
        if(captured!=null){
            frame=captured;
            if(captured.cave){
                observeRecoveryMotion(captured);
                waves.observe(captured.tick,captured.model.mobs(),predictorWave);
                waves.verify(captured.tick);
                long hpFingerprint=19;
                for(Mob m:captured.model.mobs())hpFingerprint=hpFingerprint*31+m.index()*79L+m.healthRatio();
                if(hpFingerprint!=lastHealthFingerprint){lastHealthFingerprint=hpFingerprint;lastProgressAt=System.currentTimeMillis();}
            }
        }
    }
    protected boolean offenceEnabled(FcFrame f) {
        return offenceEnabled(f,f==null?-1:f.interactingIndex);
    }
    protected boolean offenceEnabled(FcFrame f,int targetIndex) {
        return config!=null&&PrayerConservation.offence(config.offensivePrayer(),config.prayerConservation(),
            targetIndex,f==null?null:f.model);
    }
    protected boolean earlyConservationGap(FcFrame f) {
        int next=waves.hasSeenMonsters()?Math.min(63,waves.wave()+1):waves.wave();
        return PrayerConservation.earlyGap(config.prayerConservation(),next,f.model);
    }
    @Override
    protected Protection tickSpawnGuard(FcFrame f) {
        if(f==null||!f.cave||!f.model.mobs().isEmpty()||jadDeath)return Protection.NONE;
        if(energyPause.recovering())return recoveryGuard(f);
        if(earlyConservationGap(f))return Protection.NONE;
        if(waves.predictionReady()&&waves.wave()>0&&waves.wave()<63) {
            int next=waves.hasSeenMonsters()?Math.min(63,waves.wave()+1):waves.wave();
            Snapshot prediction=f.predictedWave(waves.rotation(),next);
            if(prediction!=null)return HeldProtection.choose(prediction,Protection.NONE,Protection.NONE);
        }
        // Entry is verified with Melee already active in the unchanged baseline.
        return waves.wave()<1?Protection.MELEE:Protection.NONE;
    }
    protected boolean prayerInputAllowed() {
        return enabled&&config!=null&&client.getGameState()==GameState.LOGGED_IN
            &&client.getLocalPlayer()!=null&&state!=State.STOPPED&&state!=State.COMPLETE
            &&!playerDeath&&!Microbot.pauseAllScripts.get()&&!InputArbiter.isHuman();
    }
    @Override
    public void onClientTick() {
        FcTickPrayers driver=tickPrayers;
        if(driver==null)return;
        driver.nativeWidgets(config!=null&&config.nativeTickPrayers());
        driver.pureTinyPrayers(config!=null&&config.pureMode());
        driver.conservation(config!=null&&config.prayerConservation());
        driver.clientTick(prayerInputAllowed(),config!=null&&config.offensivePrayer(),movement.pending());
    }
    @Override
    public void onChatMessage(ChatMessage e) {
        if(!enabled)return;String message=e.getMessage();if(message==null)return;
        WorldPoint where=client.getLocalPlayer()==null?null:WorldPoint.fromLocalInstance(client,client.getLocalPlayer().getLocalLocation());
        boolean caveNow=where!=null&&where.getRegionID()==WaveBook.REGION;
        String type=e.getType()==null?"":e.getType().name();
        boolean serverMessage=type.equals("GAMEMESSAGE")||type.equals("SPAM")||type.equals("MESBOX");
        if((caveNow||state==State.ENTERING)&&serverMessage) {
            if(waves.chat(message,client.getTickCount())){events.add(message);lastProgressAt=System.currentTimeMillis();}
            if(message.contains("The Fight Cave has been paused")){pauseConfirmed=true;events.add(message);}
        }
        if(e.getType()==ChatMessageType.GAMEMESSAGE&&hadCave&&(message.contains("Your TzTok-Jad kill count")||message.toLowerCase(Locale.ROOT).contains("you are awarded a fire cape"))){rewardConfirmed=true;events.add(message);}
        if(e.getType()==ChatMessageType.GAMEMESSAGE&&(message.toLowerCase(Locale.ROOT).contains("not enough ammo")||message.toLowerCase(Locale.ROOT).contains("run out of darts")
            ||message.toLowerCase(Locale.ROOT).contains("run out of scales"))){events.add("AMMUNITION FAILURE: "+message);if(!config.pureMode()){exitRequested=true;failed=true;failureReason="Weapon ammunition/charges exhausted";}
            else warning="Weapon ammunition/charges exhausted; staying in the cave";}
    }
    @Override
    public void onAnimationChanged(AnimationChanged e) {
        if(!enabled)return;
        if(e.getActor()==client.getLocalPlayer()) {
            FcFrame current=frame;
            if(current!=null&&current.cave&&!current.stale()) {
                int animation=e.getActor().getAnimation();
                if(BowPrayerClock.attackAnimation(animation,meleeMode))
                    bowPrayer.swing(client.getTickCount(),current.weaponId,current.attackStyle,animation,meleeMode);
                else if(animation>=0)bowPrayer.interrupt();
            }
            return;
        }
        if(!(e.getActor() instanceof NPC))return;
        NPC npc=(NPC)e.getActor();NPCComposition composition=npc.getTransformedComposition();
        if(composition==null)return;
        Kind kind=Kind.identify(npc.getName(),FcFrame.npcTileSize(npc));if(kind==null)return;
        if(tickPrayers!=null)tickPrayers.npcAnimation(npc.getIndex(),kind,npc.getAnimation());
        Protection observed=AttackClock.animationWithTiny(kind,npc.getAnimation());if(observed==null)return;
        int tick=client.getTickCount();lastDangerTick=tick;
        if(kind==Kind.MAGER&&observed==Protection.MAGIC&&npc.getInteracting()==client.getLocalPlayer())magicThreatUntil=tick+5;
        attackTicks.put(npc.getIndex(),tick);attackStyles.put(npc.getIndex(),observed);
        if(kind==Kind.JAD){jadStyle=observed;jadAttackTick=tick;}
        events.add("attack-clock "+kind+" index="+npc.getIndex()+" style="+observed+" tick="+tick);
    }
    @Override
    public void onGameStateChanged(GameStateChanged e) {
        if(!enabled)return;
        if(tickPrayers!=null&&e.getGameState()!=GameState.LOGGED_IN)tickPrayers.transition();
        if(e.getGameState()==GameState.HOPPING||e.getGameState()==GameState.LOGIN_SCREEN||e.getGameState()==GameState.CONNECTION_LOST){
            frame=null;movement.reset();supplyAck.reset();brewHealing.reset();supplyPreparation.reset();jadActions.reset();bowPrayer.interrupt();attackTicks.clear();attackStyles.clear();healers.reset();pureHealerLure.reset();healerPullLease.reset();healerRetreat=null;dead.clear();waves.sceneReloaded();
            if(!hadCave)resetClock();
            transitionGuardUntil=client.getTickCount()+6;events.add("state="+e.getGameState());
        }
        if(e.getGameState()==GameState.LOGGED_IN)transitionGuardUntil=client.getTickCount()+2;
    }
    @Override
    String plannerMode(){return config!=null&&false?"Strict zero-exposure":"Full 63-wave / minimize exposure";}}
