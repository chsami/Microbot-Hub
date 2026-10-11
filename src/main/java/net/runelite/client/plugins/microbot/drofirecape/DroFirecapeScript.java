/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Rectangle;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.util.magic.thralls.Rs2Thrall;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.*;
import net.runelite.api.coords.*;
import net.runelite.api.events.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import net.runelite.client.plugins.microbot.drofirecape.core.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;

@Singleton
public class DroFirecapeScript extends Script {
    public DroFirecapeScript(){this(false);}
    protected DroFirecapeScript(boolean optionalController) {
        actions=optionalController?new OptionalActions():new FcActions();
        trace=optionalController?new OptionalTelemetry():new FcTelemetry();
        movement=new MovementAck(optionalController);
        keybindings=new FcKeybindings(()->enabled&&config!=null&&(optionalController||!config.observeOnly())
            &&!Microbot.pauseAllScripts.get()&&!InputArbiter.isHuman()&&!Thread.currentThread().isInterrupted());
    }

    enum State { START,TELEPORT,WALK_BANK,BANK_EQUIPMENT,BANK_INVENTORY,BANK_VERIFY,PREPOT,CAMERA,
        WALK_ENTRANCE,ROTATION_WAIT,ENTERING,FIGHTING,ENERGY_REST,RESUMING,RETREAT,COMPLETE,STOPPED }
    static final WorldPoint BANK=new WorldPoint(2445,5178,0),ENTRANCE=new WorldPoint(2438,5168,0);
    // One native NPC click fits inside a single verified prayer interval. Supplies
    // retain their longer budgets; the prayer driver's 100 ms guard is unchanged.
    static final long ATTACK_INPUT_BUDGET_MS=250;
    @Inject protected Client client;
    @Inject protected ConfigManager configManager;
    protected final FcActions actions;
    protected volatile FcTickPrayers tickPrayers;
    protected volatile Future<?> cameraTurn;
    protected final java.util.concurrent.atomic.AtomicBoolean cameraTurning=new java.util.concurrent.atomic.AtomicBoolean();
    protected final java.util.concurrent.atomic.AtomicLong cameraGeneration=new java.util.concurrent.atomic.AtomicLong();
    protected long lastMonsterCameraTurnAt;
    protected int lastCameraPivotMonster=-1;
    protected final FcPredictorGate entryGate=new FcPredictorGate();
    protected volatile FcPredictorGate.Sample entryPrediction;
    protected volatile String entryGateStatus="Waiting for built-in spawn predictor";
    protected long lastPredictorLogAt,lastExitDiagnosticAt,lastExitClickAt,lastDispatchDiagnosticAt;
    protected String lastPredictorLog="";
    protected int plannedRotation=-1;
    protected long lastWaveDiagnosticAt,lastAttackDiagnosticAt,lastAttackRequestAt;
    protected volatile long lastAttackProgressAt;
    protected volatile int pendingAttackIndex=-1;
    protected int attackAttempts;
    protected boolean pendingAttackAcknowledged;
    protected volatile int predictorWave=-1;
    protected long lastInstancePredictorAt;
    protected FcCanvasGuard pointerLease;
    protected final FcTelemetry trace;
    protected final CombatPlanner planner=new CombatPlanner();
    protected final LureController lures=new LureController();
    protected final CombatProgress combatProgress=new CombatProgress();
    protected final MovementAck movement;
    protected final SupplyAck supplyAck=new SupplyAck();
    protected final FcRangePrepot rangePrepot=new FcRangePrepot();
    protected int sweetCancelTick=-1,sweetProgressTick=-1,sweetHp,sweetCount,sweetRetryTick=-1;
    protected final JadActionWindow jadActions=new JadActionWindow();
    protected int lastFailedMoveTick=-1;
    protected final BowPrayerClock bowPrayer=new BowPrayerClock();
    protected final HealerGroup healers=new HealerGroup();
    protected int healerCancelTick=-1,healerCancelIndex=-1;
    protected volatile Protection requestedCaveProtection=Protection.NONE;
    protected int caveBaseX=Integer.MIN_VALUE,caveBaseY=Integer.MIN_VALUE,cavePlane=-1;
    protected boolean meleeMode;
    protected final WaveTracker waves=new WaveTracker();
    protected final EnergyPause energyPause=new EnergyPause();
    protected final Map<Integer,Integer> attackTicks=new ConcurrentHashMap<>();
    protected final Map<Integer,Protection> attackStyles=new ConcurrentHashMap<>();
    protected volatile int prayerPlanTick=-1, transitionGuardUntil=-1;
    protected int prepositionStarted=-1, cancelAttackTick=-1;
    protected int brewDebt, lastBrewTick=-1000, lastObservedWave=-1, controllerErrors;
    protected long lastHealthFingerprint=Long.MIN_VALUE,lastRecoveryAt;
    protected long lastMotionFingerprint=Long.MIN_VALUE;
    protected volatile long lastRecoveryMotionAt;
    protected volatile String combatPhase="Preparation";
    protected final Set<Integer> dead=ConcurrentHashMap.newKeySet();
    protected final Map<Integer,FcModel.Tile> camps=new HashMap<>();
    protected final Queue<String> events=new ConcurrentLinkedQueue<>();
    protected final AtomicInteger damage=new AtomicInteger();
    protected volatile FcFrame frame;
    protected volatile Plan plan;
    protected volatile State state=State.STOPPED;
    protected volatile String status="Stopped",warning="Experimental — in-game validation required";
    protected volatile Protection jadStyle=Protection.MAGIC;
    protected volatile int jadAttackTick=-1000;
    protected volatile boolean pauseConfirmed,jadDeath,rewardConfirmed,playerDeath,enabled;
    protected DroFirecapeConfig config;
    protected final FcKeybindings keybindings;
    protected Rs2InventorySetup setup;
    protected long stateAt,startedAt,lastActionAt,teleportAt,enteredAt,emptyAt,lastSupplyAt,lastSpecAt,lastPlanAt;
    protected volatile long lastProgressAt;
    protected int resumeWave=-1,recoverySupplyMisses;
    protected volatile int lastDangerTick=-1000;
    protected volatile boolean recoverySpawnObserved;
    protected long recoveryRetryAt;
    protected String lastSavedPause="";
    protected int initialCapeCount,expectedHop=-1,lastMoveTick=-1,lastPlanTick=-1,preparedWave=-1,lastSavedWave=-1;
    protected boolean hadCave,initialized,cameraSet,forceReengage,prepositioned,lureResetAfterReturn;
    protected volatile boolean failed,exitRequested;
    protected FcModel.Tile exitTile,healerRetreat;
    protected String failureReason="";

    // Prayer regeneration: the live buff timer is authoritative. The eight-minute
    // fallback starts only after an observed dose decrease on servers without it.
    protected static final long PRAYER_REGEN_DURATION_MS=8L*60_000L;
    protected static final long OPTIONAL_RETRY_MS=5_000L;
    protected long lastPrayerRegenAttemptAt,prayerRegenFallbackUntil,nextThrallAttemptAt,nextThrallUiAt;
    protected int pendingPrayerRegenDoses=-1,thrallUiMisses;
    protected boolean sawPrayerRegenTimer;
    // Serializes the additional spell/tab click with the existing prayer worker.
    // No sleep or wait-for-cast is performed while this lock is held.
    protected final Object optionalInputLock=new Object();
    protected final SupplyPreparation supplyPreparation=new SupplyPreparation();

    protected final net.runelite.client.plugins.microbot.drofirecape.profile.BaseProfileDro baseProfile =
        new net.runelite.client.plugins.microbot.drofirecape.profile.BaseProfileDro(
            new net.runelite.client.plugins.microbot.drofirecape.profile.BaseProfileDro.Settings()
                .parkSide(net.runelite.client.plugins.microbot.drofirecape.profile.BaseProfileDro.AfkParkSide.NONE)
                .customBreaksEnabled(false).overlayEnabled(false).startupCamera(150,275,356));
    protected volatile int magicThreatUntil=-1;
    protected long lastStatusLogAt;
    protected String lastLoggedStatus="";

    // This controller already checks the logged-in player, world and collision map.
    // An empty pre-bank inventory/equipment container must not suppress its loops.
    // Kept without @Override for standalone builds against older client versions.
    protected boolean usesSharedLoginReadiness(){return false;}


    public boolean run(DroFirecapeConfig configuration) {
        enabled=false;
        if(mainScheduledFuture!=null)mainScheduledFuture.cancel(true);
        if(scheduledFuture!=null)scheduledFuture.cancel(true);
        FcTickPrayers previous=tickPrayers;if(previous!=null)previous.detach();
        config=configuration;keybindings.reset();if(pointerLease!=null)pointerLease.close();
        meleeMode=config.meleeCape();
        pointerLease=null;resetVariantRun();lureResetAfterReturn=false;rangePrepot.reset();sweetCancelTick=sweetProgressTick=sweetRetryTick=-1;supplyAck.reset();supplyPreparation.reset();jadActions.reset();lastFailedMoveTick=-1;movement.reset();lures.reset();bowPrayer.reset();requestedCaveProtection=Protection.NONE;caveBaseX=caveBaseY=Integer.MIN_VALUE;cavePlane=-1;actions.reset();actions.observeOnly(observeOnly());
        tickPrayers=createTickPrayers();
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
        trace.open(traceEnabled());setState(State.START,"Waiting for logged-in scene");
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
        Microbot.log("[Dro Firecape] Full-run controller "+DroFirecapePlugin.version+" (recorded rock lures / visible protection switches / tick prayers / retained startup, supplies and thralls) started. Trace: "+trace.location()+startupDetail());
        return true;
    }
    protected void setState(State next,String text){if(state!=next){state=next;stateAt=System.currentTimeMillis();trace.event("state",next+": "+text);}status=text;}
    protected void logStatus() {
        long now=System.currentTimeMillis();
        String current=state+": "+status;
        if(now-lastStatusLogAt<10_000&&(current.equals(lastLoggedStatus)||now-lastStatusLogAt<2_000))return;
        lastStatusLogAt=now;lastLoggedStatus=current;
        String detail=FcDiagnostics.describe(this);
        Microbot.log("[Dro Firecape] "+detail);
        trace.event("status",detail);
    }
    protected boolean elapsed(long ms){return System.currentTimeMillis()-stateAt>=ms;}
    protected boolean actionGap(long ms){return System.currentTimeMillis()-lastActionAt>=ms;}
    protected void didAction(){lastActionAt=System.currentTimeMillis();}
    protected void fail(String reason) {
        if(!failed){failed=true;failureReason=reason;trace.event("attempt-failed",reason);Microbot.log("[Dro Firecape] "+reason);}
        warning=reason;exitRequested=true;
        if(frame!=null&&frame.cave){clearSaved();setState(State.RETREAT,"Seeking a protected exit: "+reason);}
        else setState(State.STOPPED,reason);
    }
    protected void loop() {
        if(!enabled||Thread.currentThread().isInterrupted())return;
        for(String e;(e=events.poll())!=null;)trace.event("game",e);
        if(state==State.STOPPED||state==State.COMPLETE)return;
        if(!Microbot.isLoggedIn()) {
            status="Waiting for login / world transition; preserving run";
            if(!observeOnly())super.run();
            return;
        }
        FcFrame f=frame;
        if(f==null||f.stale()) {
            f=FcActions.read(this::captureFrame,null);
            if(f==null){status=FcActions.read(this::sceneWaitReason,"Waiting for client-thread scene snapshot");return;}
            frame=f;
        }
        if(Microbot.pauseAllScripts.get()||InputArbiter.isHuman()){
            stopCamera();userTookControl();
            status="User input/pause owns controls";captureObservation(f,"USER_OR_PAUSE");return;
        }
        if(f.cave&&!observeOnly()&&pointerLease==null)
            pointerLease=FcCanvasGuard.acquire();
        if(!f.cave&&pointerLease!=null){pointerLease.close();pointerLease=null;}
        if(!observeOnly())baseProfile.tick(false,false);
        if(!initialized){
            if(!observeOnly()) {
                if(!keybindings.step(f.cave)){status=keybindings.status();return;}
                trace.event("startup-keybindings",keybindings.result());
            }
            initialize(f);if(!initialized||state==State.STOPPED)return;
        }
        if(playerDeath){fail("Player died; attempt stopped");setState(State.STOPPED,failureReason);return;}
        if(f.cave){
            try {if(!observeOnly())maybeTurnCameraTowardMonster(f);cave(f);}
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
        if(!observeOnly()&&!super.run())return;
        outside(f);
    }
    /** Client-thread explanation of a missing immutable frame, including valid empty loadouts. */
    protected String sceneWaitReason() {
        if(client==null)return "Waiting for game client";
        if(client.getGameState()!=GameState.LOGGED_IN)return "Waiting for game state: "+client.getGameState();
        Player player=client.getLocalPlayer();
        if(player==null||player.getLocalLocation()==null)return "Waiting for local player position";
        WorldView view=client.getTopLevelWorldView();
        if(view==null)return "Waiting for loaded world view";
        CollisionData[] maps=view.getCollisionMaps();int plane=view.getPlane();
        if(maps==null||plane<0||plane>=maps.length||maps[plane]==null)return "Waiting for collision map";
        return "Waiting for scene snapshot; see client-thread errors in log";
    }
    protected boolean cameraAllowed() {
        FcFrame current=frame;
        return enabled&&config!=null&&!observeOnly()&&current!=null&&current.cave
            &&!current.stale()&&!Microbot.pauseAllScripts.get()&&!InputArbiter.isHuman()
            &&state!=State.STOPPED&&state!=State.COMPLETE&&state!=State.ENERGY_REST
            &&!movement.pending()&&!supplyPreparation.pending()&&!energyPause.recovering()&&current.model.mobs().stream().noneMatch(m->m.kind()==Kind.JAD);
    }
    protected synchronized void stopCamera() {
        cameraGeneration.incrementAndGet();
        Future<?> turn=cameraTurn;
        if(turn!=null&&!turn.isDone())turn.cancel(true);
        cameraTurn=null;
    }
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
            ||cameraNeedsOffscreenTarget()&&!actions.npcCompletelyOutOfView(target.index(),target.kind().name))return;
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
                    &&(!cameraNeedsOffscreenTarget()||actions.npcCompletelyOutOfView(index,name)))
                    net.runelite.client.plugins.microbot.util.camera.Rs2Camera.turnTo(npc,70);
            }finally{cameraTurning.set(false);}
        });
    }

    protected void initialize(FcFrame f) {
        if(config.inventorySetup()==null&&!f.cave){setState(State.STOPPED,"Select a Microbot Inventory Setup first");return;}
        if(f.maxPrayer<43){setState(State.STOPPED,"Requires level 43 Prayer for all three protections");return;}
        initialCapeCount=f.count(6570);initialized=true;
        if(f.cave) {
            if(meleeMode&&!validateEquipment(f)) {
                setState(State.STOPPED,failureReason+"; correct the setup before resuming");return;
            }
            String saved=configManager.getRSProfileConfiguration(DroFirecapeConfig.GROUP,"activeRun");
            int savedWave=parseSavedWave(saved);
            String paused=configManager.getRSProfileConfiguration(DroFirecapeConfig.GROUP,"recoveryPause");
            if(energyPause.restore(paused,System.currentTimeMillis())
                &&(f.model.mobs().isEmpty()||energyPause.state()==EnergyPause.State.REQUESTED)) {
                savedWave=energyPause.requestedWave();resumeWave=energyPause.nextWave();expectedHop=energyPause.targetWorld();
            } else {energyPause.reset();configManager.unsetRSProfileConfiguration(DroFirecapeConfig.GROUP,"recoveryPause");}
            FcPredictorGate.Sample inside=actions.predictor(true);
            if(savedWave>=1){waves.restore(parseSavedRotation(saved),savedWave);damage.set(savedDamage(saved));}
            else {waves.reset();waves.hintRotation(inside.ready?inside.rotation:0);}
            hadCave=true;enteredAt=System.currentTimeMillis();
            predictorWave=actions.predictorWave(inside);
            waves.hintRotation(inside.ready?inside.rotation:0);
            waves.observe(f.tick,f.model.mobs(),predictorWave);
            setState(energyPause.recovering()?State.ENERGY_REST:State.FIGHTING,"Resuming live cave / rotation "+rotationLabel()+"; acquiring wave evidence");
            if(tickPrayers!=null)tickPrayers.recoveryPause(energyPause.recovering(),recoveryGuard(f));
        } else {configManager.unsetRSProfileConfiguration(DroFirecapeConfig.GROUP,"recoveryPause");
            setState(State.TELEPORT,"Preparing TzHaar Fight Pit minigame teleport");}
    }
    protected int parseSavedWave(String saved) {
        try {
            if(saved==null)return -1;String[] p=saved.split(":");
            if(p.length<3||!WaveBook.validRotation(Integer.parseInt(p[0])))return -1;
            int w=Integer.parseInt(p[1]);
            if(Boolean.parseBoolean(p[2]))w++;
            return w>=1&&w<=63?w:-1;
        }catch(NumberFormatException e){return -1;}
    }
    protected int parseSavedRotation(String saved) {
        try{int r=Integer.parseInt(saved.split(":")[0]);return WaveBook.validRotation(r)?r:0;}
        catch(RuntimeException e){return 0;}
    }
    protected int savedDamage(String saved) {
        try {String[] parts=saved.split(":");return parts.length>=4?Math.max(0,Integer.parseInt(parts[3])):0;}
        catch(RuntimeException e){return 0;}
    }
    protected void save(boolean cleared) {
        if(waves.wave()<1||failed||exitRequested||!waves.predictionReady())return;
        configManager.setRSProfileConfiguration(DroFirecapeConfig.GROUP,"activeRun",waves.rotation()+":"+waves.wave()+":"+cleared+":"+damage.get());
        lastSavedWave=waves.wave();
    }
    protected void savePause() {
        String saved=energyPause.saved();if(saved.equals(lastSavedPause))return;
        if(saved.isEmpty())configManager.unsetRSProfileConfiguration(DroFirecapeConfig.GROUP,"recoveryPause");
        else configManager.setRSProfileConfiguration(DroFirecapeConfig.GROUP,"recoveryPause",saved);
        lastSavedPause=saved;
    }
    protected void clearSaved(){configManager.unsetRSProfileConfiguration(DroFirecapeConfig.GROUP,"activeRun");
        configManager.unsetRSProfileConfiguration(DroFirecapeConfig.GROUP,"recoveryPause");lastSavedPause="";}
    protected void complete(){setState(State.COMPLETE,"Fire cape reward confirmed");clearSaved();actions.prayersOff();combatPhase="Cape confirmed";trace.event("complete","damage="+damage.get());}

    protected void outside(FcFrame f) {
        long now=System.currentTimeMillis();
        if(observeOnly()){status="Observe only: outside-cave preparation would run here";return;}
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
                if(meleeMode||!rangingPotion()){setState(State.CAMERA,"Closing bank; checking combat loadout");return;}
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
    protected boolean validateEquipment(FcFrame f) {
        if(meleeMode) {
            String weapon=f.weapon.toLowerCase(Locale.ROOT);
            if(FcFrame.inferRange(f.weapon)>0||weapon.contains("trident")||weapon.contains("sanguinesti")||weapon.contains("tumeken")) {
                fail("Melee cape is enabled, but the setup equipped '"+f.weapon+"'. Select a melee setup or turn Melee cape off.");return false;
            }
            if(strictZeroExposure()) {
                fail("Melee cape requires Strict zero-exposure OFF; contact and Tz-Kek recoil are not damage-free.");return false;
            }
        } else if(FcFrame.inferRange(f.weapon)==0&&weaponRangeSetting()==0){fail("Unrecognized ranged weapon '"+f.weapon+"'; set its actual range or use a recognized ranged weapon");return false;}
        if(f.weaponId<=0){fail("No weapon equipped after Inventory Setup");return false;}
        boolean selfDamageAmmo=FcActions.read(()->{
            ItemContainer gear=client.getItemContainer(InventoryID.EQUIPMENT);Item ammo=gear==null?null:gear.getItem(13);
            return ammo!=null&&ammo.getId()>0&&client.getItemDefinition(ammo.getId()).getName().toLowerCase(Locale.ROOT).contains("ruby");
        },false);
        if(!meleeMode&&selfDamageAmmo){fail("Ruby ammunition can self-damage; remove it from the zero-damage setup");return false;}
        if(f.inventory.stream().noneMatch(i->isPrayerPotion(i.name()))){fail("Inventory Setup has no prayer potion or super restore doses");return false;}
        return true;
    }
    protected void travel(FcFrame f,WorldPoint target) {
        FcModel.Tile dest=new FcModel.Tile(target.getX()-f.baseX,target.getY()-f.baseY);
        List<FcModel.Tile> path=f.model.grid().path(f.model.player(),dest,List.of());
        if(path.size()>1&&f.tick!=lastMoveTick) {
            int stride=Math.min(6,path.size()-1);actions.move(f,path.get(stride));lastMoveTick=f.tick;
        }
    }
    protected void resetClock(){entryGate.reset();entryPrediction=null;entryGateStatus="Acquiring built-in spawn predictor";}
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
            if(!entryProtectionReady()){status="Rotation "+sample.rotation+" confirmed; "+entryProtectionStatus();return;}
            waves.begin(sample.rotation);camps.clear();planner.reset();clearSaved();lastSavedWave=-1;predictorWave=-1;
            setState(State.ENTERING,"Rechecking built-in spawn predictor immediately before entry");
            if(enterPredictedRotation(sample.rotation)) {
                trace.event("entry-dispatched",sample.diagnostic());
                Microbot.log("[Dro Firecape] Enter dispatched: rotation="+sample.rotation+"; waiting through cave introduction for spawn evidence");
            } else setState(State.ROTATION_WAIT,"Entry not dispatched; rechecking predictor before retry");
            return;
        }
        actions.idlePrayersOff();
        warning=sample.ready?"Waiting here for the selected entry window; no rotation-search hopping":sample.detail;
    }

    /** Refresh optional instance hints; never use the OUTSIDE clock to change an active run. */
    protected void synchronizeWave(FcFrame f) {
        long now=System.currentTimeMillis();
        if(now-lastInstancePredictorAt>=1000) {
            lastInstancePredictorAt=now;
            FcPredictorGate.Sample inside=actions.predictor(true);
            if(inside.ready)waves.hintRotation(inside.rotation);
            predictorWave=actions.predictorWave(inside);
        }
        waves.observe(f.tick,f.model.mobs(),predictorWave);
        waves.verify(f.tick);
        if(plannedRotation!=waves.rotation()) {
            plannedRotation=waves.rotation();resetPlanning();preparedWave=-1;lastSavedWave=-1;
            trace.event("rotation-resolved","rotation="+rotationLabel()+" wave="+waves.wave()+" candidates="+waves.candidates());
        }
        if(now-lastWaveDiagnosticAt>=10_000) {
            lastWaveDiagnosticAt=now;
            String detail="wave="+waves.wave()+" rotation="+rotationLabel()+" predictorWave="+predictorWave
                +" liveNPCs="+f.model.mobs().size()+" evidence="+waves.status();
            trace.event("wave-sync",detail);Microbot.log("[Dro Firecape] "+detail);
        }
    }
    protected Snapshot predictedWave(FcFrame f,int wave) {
        return waves.predictionReady()?f.predictedWave(waves.rotation(),wave):null;
    }
    protected String rotationLabel(){return waves.rotation()>0?String.valueOf(waves.rotation()):"resolving";}


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
                protection=heldCaveProtection(current.model,protection);
                // Retain the existing magic-flight guard as well as continuous overheads.
                protection=MagicProtection.choose(current.model,current.model.player(),magicThreatUntil,protection);
            }
        }
        requestedCaveProtection=protection;
        return protection;
    }
    protected boolean protectInCave(Protection protection) {
        return actions.combatProtect(desiredCaveProtection(protection));
    }
    protected Protection jadProtection(FcFrame f) {
        return meleeMode&&f!=null?MeleeProtection.choose(f.model,f.model.player(),requestedCaveProtection):jadStyle;
    }
    protected void cave(FcFrame f) {
        long now=System.currentTimeMillis();
        if(caveBaseX!=f.baseX||caveBaseY!=f.baseY||cavePlane!=f.plane) {
            if(caveBaseX!=Integer.MIN_VALUE&&cavePlane==f.plane) {
                lures.rebase(caveBaseX-f.baseX,caveBaseY-f.baseY);
                rebaseVariant(caveBaseX-f.baseX,caveBaseY-f.baseY);
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
            healers.reset();resetVariantHealers();healerRetreat=null;cancelAttackTick=-1;planner.reset();
            if(lures.hasPendingReturn())lureResetAfterReturn=true;else lures.reset();
            plan=null;lastPlanTick=-1;
        }
        observeHealers(f);
        if(lureResetAfterReturn&&!lures.hasPendingReturn()){lures.reset();lureResetAfterReturn=false;}
        if(damage.get()>0&&exitOnDamage())fail("Damage-free target failed: "+damage.get()+" HP damage observed");
        if(f.autoRetaliate&&!observeOnly()){actions.autoRetaliateOff();return;}
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
        if(!exitRequested&&beforeHealerCancellation(f,urgent))return;
        if(!exitRequested&&(cancelHealerRetreat(f)||cancelTaggedHealerAttack(f)))return;
        if(!exitRequested&&variantCombat(f,urgent))return;
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
            if(!CombatPlanner.actionable(pending,strictZeroExposure())) {
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
        if(!f.running&&f.rawEnergy>Math.max(0,Microbot.runEnergyThreshold)&&urgent==Protection.NONE&&!observeOnly()){actions.enableRun();return;}
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
        if(!CombatPlanner.actionable(p,strictZeroExposure())) {
            status=p.reason();warning=strictZeroExposure()?"Strict planner: no zero-exposure action":"No reachable firing route in current scene";
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
        if(target==null||(f.moving&&target.kind()!=Kind.BAT)||!planner.attackAllowed(model,target,protection,strictZeroExposure())){status="Rechecking stationary range, target, and protection";return;}
        if(blowpipeSpecial()&&f.weapon.toLowerCase(Locale.ROOT).contains("blowpipe")&&f.specialEnergy>=500&&f.specialEnabled==0
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
    protected void observeHealers(FcFrame f) {
        for(int index:f.healersTargetingJad)if(healers.confirmed(index))
            events.add("Healer "+index+" returned to Jad; group re-tag required");
        int pending=healers.pending();HealerGroup.Phase previous=healers.phase();
        HealerGroup.Ack result=healers.observe(f.model,f.healersTargetingJad);
        if(result!=HealerGroup.Ack.NONE) {
            lastPlanTick=-1;forceReengage=true;
            events.add("healer-tag-ack index="+pending+" result="+result+" confirmed="+healers.progress());
        }
        if(previous!=healers.phase()) {
            lastPlanTick=-1;
            events.add("healer-group "+healers.phase()+" confirmed="+healers.progress());
        }
    }
    /** A returning/new healer invalidates an already-issued group pull too. */
    protected boolean cancelHealerRetreat(FcFrame f) {
        if(healers.phase()==HealerGroup.Phase.LURING||healerRetreat==null)return false;
        if(!movement.pending()&&!f.moving){healerRetreat=null;lastPlanTick=-1;return false;}
        status="Stopping obsolete healer pull; rechecking the live group";
        if(!protectInCave(jadProtection(f))||observeOnly())return true;
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
    /** Stop subsequent shots after the tag lands while another healer still heals Jad. */
    protected boolean cancelTaggedHealerAttack(FcFrame f) {
        if(healers.phase()!=HealerGroup.Phase.TAGGING||healers.remaining()==0
            ||!healers.confirmed(f.interactingIndex))return false;
        if(healerCancelIndex==f.interactingIndex&&f.tick-healerCancelTick<3)return false;
        if(!protectInCave(jadProtection(f))||observeOnly())return true;
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
    /** Group collection always runs before the ordinary target/kill planner. */
    protected boolean handleHealers(FcFrame f,Protection urgent) {
        HealerGroup.Phase phase=healers.phase();
        if(phase==HealerGroup.Phase.IDLE){healerRetreat=null;resetVariantHealers();return false;}
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
                HealerTactics.tag(model,strictZeroExposure());
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
        Boolean variant=variantHealerPull(f,model,urgent);
        if(variant!=null)return variant;
        combatPhase="Pull tagged healer group";
        if(healerRetreat==null) {
            Plan retreat=new CombatPlanner().plan(model,null);
            if(!CombatPlanner.actionable(retreat,strictZeroExposure())) {
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
        if(healerPullExpired(f,model,retreat))return false;
        if(CombatPlanner.actionable(retreat,strictZeroExposure())
            &&protectInCave(urgent!=Protection.NONE?urgent:retreat.protection()))movePlan(f,retreat);
        status="All healers tagged; pulling the group behind cover";return true;
    }
    protected void acknowledgeAttack(FcFrame f) {
        if(pendingAttackIndex<0)return;
        long now=System.currentTimeMillis();
        if(!f.presentNpcIndices.contains(pendingAttackIndex)){pendingAttackIndex=-1;attackAttempts=0;pendingAttackAcknowledged=false;return;}
        if(f.interactingIndex==pendingAttackIndex) {
            pendingAttackAcknowledged=true;
            if(now-lastProgressAt<1800)lastAttackProgressAt=now; // Not eating, turning, or arbitrary animations.
            if(now-lastAttackProgressAt>5000&&now-lastAttackRequestAt>1800)forceReengage=true;
        } else if(now-lastAttackRequestAt>=1200)forceReengage=true;
    }
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
        boolean batFirst=batFirstAllowed()&&live!=null&&live.kind()==Kind.BAT&&!hasJad(current)
            &&CombatPlanner.playerCanAttack(current.model,current.model.player(),live)
            &&liveProtection!=Protection.MAGIC&&liveProtection!=Protection.RANGE;
        // A legal bat shot must not wait for an optional melee flick or potion.
        // Ranged/magic threats and Jad still require their actual protection.
        if(live==null||(current.moving&&live.kind()!=Kind.BAT)||(!batFirst&&!actions.overheadActive(protection))
            ||(!batFirst&&!attackPlanner(current).attackAllowed(withTaggedHealer(current.model),live,protection,strictZeroExposure()))) {
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
            if(current.prayer>0&&(useOffence||primesWithoutOffence())
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
    /** Contact safety precedes stationary attacks and optional supply work. */
    protected boolean tryMageEscape(FcFrame f) {
        Plan escape=CaveSafety.escapeMage(f.model);
        if(escape==null)return false;
        if(movement.pending()) {
            // Replace the old destination; a pending return must not drive us back into contact.
            movement.reset();
        }
        plan=escape;prayerPlanTick=f.tick;lastPlanTick=-1;forceReengage=true;
        if(protectInCave(escape.protection()))movePlan(f,escape);
        status=escape.reason();return true;
    }
    /** Kill an accessible prayer-draining bat before optional UI work or lures. */
    protected boolean tryBatAttack(FcFrame f) {
        if(observeOnly()||f.prayer<=0||movement.pending()||lures.hasPendingReturn()||hasJad(f))return false;
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
        if(!planner.attackAllowed(model,target,protect,strictZeroExposure()))return false;
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
    protected Mob aggro(Mob m){return new Mob(m.index(),m.kind(),m.tile(),m.size(),m.healthRatio(),m.healthScale(),m.lastAttackTick(),m.lastStyle(),true);}
    protected Snapshot withTaggedHealer(Snapshot s) {
        ArrayList<Mob> mobs=new ArrayList<>();for(Mob m:s.mobs())mobs.add(m.kind()==Kind.HEALER&&healers.confirmed(m.index())?aggro(m):m);
        return new Snapshot(s.tick(),s.player(),s.grid(),mobs,s.runEnergy(),s.running(),s.weaponRange(),jadStyle,s.meleeMode()).atWave(waves.wave());
    }
    protected boolean usesRecordedWave(int rotation,int wave) {
        return !meleeMode&&demonstrationLures()&&wave>=0&&wave<63;
    }
    protected List<FcModel.Tile> italyCandidates(FcFrame f,int wave) {
        FcModel.Tile italy=f.recordedAnchor(RecordedLureBook.ITALY);
        if(italy==null)return List.of();
        // Use the demonstrated maneuver, not an unconditional replay of every
        // stopping tile in a wave. Live trap evidence chooses the temporary peek.
        return List.of(italy);
    }
    protected FcModel.Tile campFor(FcFrame f,int wave) {
        // Canonical template-relative positions from the existing recordings, mapped
        // through WorldPoint.toLocalInstance by FcFrame after every scene rebase.
        for(FcModel.Tile template:List.of(RecordedLureBook.ITALY,RecordedLureBook.SOUTH_FACE,
            RecordedLureBook.WEST_PEEK)) {
            FcModel.Tile italy=f.recordedAnchor(template);
            if(italy!=null&&f.model.grid().open(italy)
                &&f.model.mobs().stream().noneMatch(m->m.occupies(italy))
                &&TacticalMovement.clearOfJad(f.model,f.model.mobs(),italy))return italy;
        }
        // Do not silently pick another rock when Italy is temporarily blocked.
        return f.model.player();
    }
    /** A tick boundary is normal. Revalidate on the newest frame in the same scene. */
    protected FcFrame dispatchFrame(FcFrame original) {
        FcFrame current=frame;
        if(original==null||current==null||current.stale()
            ||!original.sameScene(current)||current.tick<original.tick||current.tick-original.tick>2
            ||!original.model.player().equals(current.model.player()))return null;
        return current;
    }
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
        if(recovery!=null&&CombatPlanner.actionable(recovery,strictZeroExposure())) {
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
    /** Approaching monsters and a manual unblocking step are real progress too. */
    protected void observeRecoveryMotion(FcFrame f) {
        long fingerprint=f.model.player().x()*104L+f.model.player().y();
        for(Mob m:f.model.mobs())fingerprint=fingerprint*31+m.index()*10816L+m.tile().x()*104L+m.tile().y();
        if(fingerprint!=lastMotionFingerprint){lastMotionFingerprint=fingerprint;lastRecoveryMotionAt=System.currentTimeMillis();}
    }
    protected void dispatchDeferred(FcFrame planned,String reason) {
        status=reason;long now=System.currentTimeMillis();
        if(now-lastDispatchDiagnosticAt<2000)return;
        lastDispatchDiagnosticAt=now;FcFrame current=frame;
        trace.event("dispatch-deferred",reason+" plannedTick="+(planned==null?-1:planned.tick)
            +" currentTick="+(current==null?-1:current.tick)
            +" planAgeMs="+(planned==null?-1:now-planned.capturedAt));
    }
    protected void movePlan(FcFrame f,Plan p) {
        if(!CombatPlanner.actionable(p,exitRequested?false:strictZeroExposure())||p.nextStep()==null
            ||p.nextStep().equals(f.model.player()))return;
        FcFrame current=dispatchFrame(f);
        if(current==null){dispatchDeferred(f,"Move: scene, player or frame age changed");return;}
        boolean healerPull=healerRetreat!=null;
        if(healerPull&&healers.phase()!=HealerGroup.Phase.LURING)return;
        if(lastMoveTick==current.tick||lastFailedMoveTick==current.tick||observeOnly()
            ||Microbot.pauseAllScripts.get()||InputArbiter.isHuman())return;
        MovementAck.Result ack=movement.observe(current.model.player(),current.tick);
        if(ack==MovementAck.Result.FAILED||ack==MovementAck.Result.DEVIATED) {
            lastFailedMoveTick=current.tick;lures.movementFailed();planner.widenSearch();lastPlanTick=-1;return;
        }
        boolean retry=ack==MovementAck.Result.RETRY_MINIMAP;
        if(movement.pending()&&!retry)return;
        Snapshot live=withTaggedHealer(current.model);
        Protection protection=earlyConservationGap(current)?Protection.NONE:desiredCaveProtection(p.protection());
        boolean strict=!exitRequested&&strictZeroExposure();
        MoveOwner owner=moveOwner(current,p);
        Plan checked=owner.healerPull?
            MinimapMovement.healerChecked(live,p.destination(),retry?movement.destination():p.nextStep(),protection,p.reason()):
            retry?MinimapMovement.checked(live,p.destination(),movement.destination(),protection,p.reason()):
            owner.checkedRoute?MinimapMovement.checked(live,p.destination(),p.nextStep(),protection,p.reason()):
            MinimapMovement.route(live,p.destination(),p.nextStep(),protection,strict,p.reason());
        if(!CombatPlanner.actionable(checked,strict)) {
            movement.reset();lures.movementFailed();planner.widenSearch();lastPlanTick=-1;
            moveRejected(current,live,p,owner);
            trace.event("minimap-route-rejected",checked.reason());return;
        }
        if(meleeMode) {
            // The minimap command can reach farther than the planner's two-tile
            // preview. Arm for its actual endpoint before entering contact range.
            protection=MeleeProtection.choose(live,checked.nextStep(),requestedCaveProtection);
            checked=owner.check(live,checked,protection);
            if(!CombatPlanner.actionable(checked,strict)){lastPlanTick=-1;return;}
        }
        if(variantRouteRejected(current,live,checked,owner))return;
        Protection routeGuard=MinimapMovement.routeProtection(live,checked.nextStep(),protection);
        if(routeGuard!=protection) {
            protection=routeGuard;
            checked=owner.check(live,checked,protection);
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
                checked=owner.check(live,checked,protection);
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
    protected void retreat(FcFrame f,Protection urgent) {
        long now=System.currentTimeMillis();combatPhase="Protected exit / live-threat recovery";
        FcModel.Tile observedExit=actions.exitTile(f);if(observedExit!=null)exitTile=observedExit;
        if(lastExitClickAt>0&&now-lastExitClickAt<15_000&&actions.confirmCaveExit()) {
            status="Confirming cave exit";return;
        }
        if(exitTile!=null&&f.model.player().distance(exitTile)<=2) {
            plan=null;prayerPlanTick=-1;
            Protection p=urgent!=Protection.NONE?urgent:CombatPlanner.protectionForNextTick(f.model,f.model.player());
            if(p==Protection.NONE&&f.model.mobs().stream().anyMatch(m->m.kind().range==1))p=Protection.MELEE;
            boolean protectedNow=protectInCave(p);
            if((protectedNow||f.prayer==0)&&actions.exitCave()){lastExitClickAt=now;status="Exit sent; retaining protection until outside";return;}
            if(lastExitClickAt>0&&now-lastExitClickAt<3600){status="Waiting for exit arrival; protection remains active";return;}
            recoveryCombat(f,urgent,"Exit not yet acknowledged");return;
        }
        FcModel.Tile goal=exitTile==null?null:nearestOpen(f,exitTile);
        if(goal!=null) {
            Plan route=planner.route(f.model,goal);plan=route;prayerPlanTick=f.tick;
            if(CombatPlanner.actionable(route,false)&&route.nextStep()!=null&&!route.nextStep().equals(f.model.player())) {
                boolean protectedNow=protectInCave(urgent!=Protection.NONE?urgent:route.protection());
                if(supplies(f,urgent))return;
                if(protectedNow||f.prayer==0)movePlan(f,route);
                status="Moving to cave exit under live-threat protection";return;
            }
        }
        if(now-lastExitDiagnosticAt>10_000) {
            lastExitDiagnosticAt=now;
            String detail="exit="+exitTile+" player="+f.model.player()+" objects="+actions.exitDiagnostic();
            trace.event("exit-recovery",detail);Microbot.log("[Dro Firecape] Exit recovery: "+detail);
        }
        // Do not abandon the character just because an exit object/route is unavailable.
        // This uses ONLY current NPCs: no Rotation-5 camp or next-wave assumption.
        recoveryCombat(f,urgent,exitTile==null?"Reacquiring cave exit":"Clearing live obstruction to exit");
    }
    protected void recoveryCombat(FcFrame f,Protection urgent,String reason) {
        if(f.model.mobs().isEmpty()) {
            plan=null;prayerPlanTick=-1;supplies(f,urgent);status=reason+"; rescanning loaded scene";return;
        }
        Plan recovery=planner.plan(f.model,null);plan=recovery;prayerPlanTick=f.tick;
        Protection protection=urgent!=Protection.NONE?urgent:recovery.protection();
        if(!protectInCave(protection)&&f.prayer>0){status=reason+"; confirming "+protection;return;}
        if(supplies(f,urgent))return;
        if(!CombatPlanner.actionable(recovery,false)) {
            planner.widenSearch();status=reason+"; expanding protected recovery search";return;
        }
        if(recovery.nextStep()!=null&&!recovery.nextStep().equals(f.model.player())) {
            movePlan(f,recovery);status=reason+"; repositioning around live threats";return;
        }
        Mob target=f.model.mobs().stream().filter(m->m.index()==recovery.targetIndex()).findFirst().orElse(null);
        if(target!=null&&!f.moving&&!f.stale()
            &&planner.attackAllowed(f.model,target,protection,false)) {
            if(f.interactingIndex!=target.index()||forceReengage) {
                if(dispatchAttack(f,target,true,"live target")){forceReengage=false;trace.event("recovery-attack",target.kind()+" index="+target.index());}
            }
            status=reason+"; defending against "+target.kind().name;
        } else {status=reason+"; checking current target/range";planner.widenSearch();}
    }
    protected FcModel.Tile nearestOpen(FcFrame f,FcModel.Tile object) {
        FcModel.Tile best=null;int length=Integer.MAX_VALUE;
        for(int x=-2;x<=2;x++)for(int y=-2;y<=2;y++) {
            FcModel.Tile candidate=object.add(x,y);if(!f.model.grid().open(candidate))continue;
            List<FcModel.Tile> path=f.model.grid().path(f.model.player(),candidate,f.model.mobs());
            if(!path.isEmpty()&&path.size()<length){length=path.size();best=candidate;}
        }
        return best;
    }
    protected static boolean isPrayerPotion(String n){String s=n.toLowerCase(Locale.ROOT);return s.startsWith("prayer potion(")||s.startsWith("super restore(");}
    protected static boolean hasJad(FcFrame f) {
        return f!=null&&f.model.mobs().stream().anyMatch(m->m.kind()==Kind.JAD);
    }
    protected void observeSupply(FcFrame f) {
        if(!supplyAck.pending())return;
        SupplyAck.Result result=supplyAck.observe(f.count(supplyAck.itemId()),f.tick);
        if(result==SupplyAck.Result.CONSUMED) {
            if(supplyAck.kind()==SupplyAck.Kind.BREW){brewDebt++;lastBrewTick=f.tick;}
            if(supplyAck.kind()==SupplyAck.Kind.RESTORE)brewDebt=0;
            recoverySupplyMisses=0;
            trace.event("supply-observed","tick="+f.tick+" item="+supplyAck.itemId()+" brewDebt="+brewDebt);
            supplyAck.reset();forceReengage=true;
        } else if(result==SupplyAck.Result.TIMED_OUT) {
            trace.event("supply-unconfirmed","tick="+f.tick+" item="+supplyAck.itemId()+"; debt unchanged, retry allowed");
            if(energyPause.recovering()){recoverySupplyMisses++;recoveryRetryAt=System.currentTimeMillis()+3000;}
            supplyAck.reset();
        }
    }
    protected void supplyRequested(FcFrame f,FcFrame.ItemSlot item) {
        String name=item.name().toLowerCase(Locale.ROOT);
        SupplyAck.Kind kind=name.startsWith("saradomin brew(")?SupplyAck.Kind.BREW:
            name.startsWith("super restore(")?SupplyAck.Kind.RESTORE:SupplyAck.Kind.OTHER;
        supplyAck.sent(item.id(),f.count(item.id()),f.tick,kind);
    }
    /** Zero prayer must not deadlock while waiting for an overhead that cannot activate. */
    protected boolean emergencyPrayer(FcFrame f) {
        if(observeOnly()||supplyAck.pending()||System.currentTimeMillis()-lastSupplyAt<1400)return false;
        FcFrame.ItemSlot dose=f.inventory.stream().filter(i->isPrayerPotion(i.name())).findFirst().orElse(null);
        if(dose==null){if(!keepsFightingWhenDepleted())fail("Prayer exhausted: no restore dose remains");return false;}
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
    protected boolean preparingSupply(FcFrame.ItemSlot item,String action,FcActions.ItemResult result,long now) {
        if(result==FcActions.ItemResult.PREPARING&&supplyPreparation.waiting(now)) {
            status="Preparing "+action+" "+item.name()+": "+actions.prayerUiStatus();return true;
        }
        String detail=actions.prayerUiStatus();
        actions.releaseTab();supplyPreparation.defer(now);
        warning="Supply click unavailable: "+detail;
        trace.event("supply-dispatch-deferred",action+" slot="+item.slot()+" "+detail);
        return false; // No dose was sent: entry/attacks must not be permanently held.
    }

    protected boolean combatStatsDrained(FcFrame f) {
        return meleeMode?f.attack<f.baseAttack||f.strength<f.baseStrength:f.ranged<f.baseRanged;
    }
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
    protected boolean supplies(FcFrame f,Protection urgent) {
        long now=System.currentTimeMillis();if(now-lastSupplyAt<1400||observeOnly())return false;
        if(supplyAck.pending()){status="Confirming consumed supply";return true;}
        boolean sweets=config.usePurpleSweets()&&f.inventory.stream().anyMatch(i->FcSupplyPolicy.sweet(i.name()));
        boolean healing=healingNeeded(f);
        boolean jad=hasJad(f),critical=supplyCritical(f,healing);
        if(supplyPreparation.pending()&&!supplyPreparation.waiting(now)) {
            actions.releaseTab();supplyPreparation.defer(now);return false;
        }
        if(!jad&&!critical&&!optionalInputWindow(f,supplyPreparation.inputBudgetMillis()))return supplyPreparation.pending();
        if(jad&&!critical&&!jadActions.available(f.tick,jadAttackTick,actions.overheadActive(jadProtection(f))))return false;
        SupplyChoice choice=prioritySupply(f,sweets,healing);
        if(choice==null)return false;
        FcFrame.ItemSlot item=choice.item;String action=choice.action;
        if(item==null&&!jad&&f.rawEnergy<=2000) {
            item=f.inventory.stream().filter(i->i.name().toLowerCase(Locale.ROOT).startsWith("stamina potion(")&&!actions.staminaActive()
                ||i.name().toLowerCase(Locale.ROOT).startsWith("super energy(")).findFirst().orElse(null);action="Drink";
        }
        if(item==null&&rangingPotion()&&(urgent==Protection.NONE||!meleeMode&&jad)) {
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
    protected boolean healWithSweets(FcFrame f,Protection urgent) {
        FcFrame.ItemSlot sweet=f.inventory.stream().filter(i->FcSupplyPolicy.sweet(i.name())&&i.hasAction("Eat")).findFirst().orElse(null);
        if(!config.usePurpleSweets()||observeOnly()||sweet==null||f.hp>=f.maxHp||f.moving
            ||movement.pending()||(sweetsWaitForLureReturn()&&lures.hasPendingReturn())||hasJad(f)||!FcSupplyPolicy.sweetPauseSafe(f.model)) {
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
    protected static boolean isPrayerRegenPotion(String name) {
        return name!=null&&name.toLowerCase(Locale.ROOT).matches("prayer regeneration potion\\s*\\([1-4]\\)");
    }
    protected static int prayerRegenerationDoses(FcFrame f) {
        int total=0;
        for(FcFrame.ItemSlot item:f.inventory)if(isPrayerRegenPotion(item.name())) {
            int digit=item.name().charAt(item.name().lastIndexOf('(')+1)-'0';
            total+=digit*item.quantity();
        }
        return total;
    }
    protected boolean prayerRegenerationDue(FcFrame f,long now) {
        int timer=FcActions.read(()->client.getVarbitValue(VarbitID.PRAYER_REGENERATION_POTION_TIMER),-1);
        if(timer>0) {
            sawPrayerRegenTimer=true;pendingPrayerRegenDoses=-1;prayerRegenFallbackUntil=0;
            return false; // Includes an effect already active before this script started.
        }
        if(timer==0&&sawPrayerRegenTimer) {
            sawPrayerRegenTimer=false;prayerRegenFallbackUntil=0;
        }
        if(pendingPrayerRegenDoses>=0) {
            if(prayerRegenerationDoses(f)<pendingPrayerRegenDoses) {
                prayerRegenFallbackUntil=lastPrayerRegenAttemptAt+PRAYER_REGEN_DURATION_MS;
                pendingPrayerRegenDoses=-1;
                trace.event("prayer-regeneration","Dose consumed; eight-minute fallback timer started");
            } else if(now-lastPrayerRegenAttemptAt<OPTIONAL_RETRY_MS)return false;
            else pendingPrayerRegenDoses=-1; // Failed click: retry, not an invented eight-minute effect.
        }
        return now>=prayerRegenFallbackUntil&&now-lastPrayerRegenAttemptAt>=OPTIONAL_RETRY_MS;
    }

    /** Optional mage thralls, following DroZulrah's snapshot-then-dispatch casting. */
    protected boolean attackInputWindow(FcFrame f) {
        FcTickPrayers driver=tickPrayers;
        return driver!=null&&driver.ownsInput()&&driver.attackInputWindow(f.tick,ATTACK_INPUT_BUDGET_MS);
    }
    protected boolean optionalInputWindow(FcFrame f,long budgetMillis) {
        FcTickPrayers driver=tickPrayers;
        return driver!=null&&driver.ownsInput()&&driver.optionalInputWindow(f.tick,budgetMillis);
    }
    protected boolean handleThrall(FcFrame f,Mob target,Plan p,Protection protection,Protection urgent) {
        long now=System.currentTimeMillis();
        if(!useThralls()||observeOnly()||exitRequested||state!=State.FIGHTING
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

    protected void requestRecoveryPause(FcFrame f,Protection urgent) {
        int wave=waves.wave();
        if(recoveryPauseUnneeded(f))return;
        if(observeOnly()||urgent!=Protection.NONE||hasJad(f)||supplyAck.pending()||supplyPreparation.pending()
            ||f.moving||movement.pending()||f.hp*100<=f.maxHp*config.eatPercent()
            ||f.prayer<=config.restorePrayer()||!optionalInputWindow(f,300)
            ||!energyPause.shouldRequest(config.energyPause(),config.recoveryStartWave(),wave,
                f.interactingIndex>=0&&!f.model.mobs().isEmpty()))return;
        synchronized(optionalInputLock) {
            if(actions.requestWavePause(f.world,()->enabled&&!exitRequested&&!observeOnly()
                &&waves.wave()==wave&&energyPause.state()==EnergyPause.State.OFF
                &&frame!=null&&!frame.stale()&&frame.world==f.world
                &&frame.model.mobs().stream().anyMatch(m->!dead.contains(m.index())))) {
                energyPause.requested(wave,f.world,System.currentTimeMillis());savePause();
                trace.event("pause-request","wave="+wave+pauseRequestDetail(f)+"; one Logout click, continue fighting");
            }
        }
    }
    protected Protection recoveryGuard(FcFrame f) {
        if(!energyPause.recovering()||energyPause.state()==EnergyPause.State.RESTING&&!recoverySpawnObserved)return Protection.NONE;
        int next=energyPause.nextWave();Snapshot prediction=predictedWave(f,next);
        return prediction!=null?HeldProtection.choose(prediction,Protection.NONE,Protection.NONE):
            next>=31?Protection.MAGIC:next>=7?Protection.RANGE:Protection.MELEE;
    }
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
                if(!observeOnly())actions.continueCaveDialogue();
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
            if(observeOnly())return;
            if(!actions.prayersObservedOff())return;
            FcRecoveryPolicy.Decision recovery=recoveryDecision(f);
            status=recovery.status;
            if(recovery.ready) {
                recoveryReady(f,recovery.status);
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
        if(observeOnly())return;
        if(!recoveryProtectionReady(f,guard))return;
        if(supplyAck.pending())return;
        if(energyPause.hopBlocked()){warning="Three resume hops failed; paused run retained for manual recovery or restart";status=warning;return;}
        FcRecoveryPolicy.Decision ready=recoveryDecision(f);
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
    protected void finishResume(){
        energyPause.resumed();savePause();resetPlanning();
        if(tickPrayers!=null)tickPrayers.recoveryPause(false,Protection.NONE);
        if(waves.wave()<resumeWave)waves.restore(resumeWave);
        recoverySpawnObserved=false;recoverySupplyMisses=0;recoveryRetryAt=0;
        save(false);setState(State.FIGHTING,resumeStatus());
    }
    protected boolean observeOnly(){return config.observeOnly();}
    protected boolean strictZeroExposure(){return config.strictZeroExposure();}
    protected int weaponRangeSetting(){return config.weaponRange();}
    protected boolean rangingPotion(){return config.rangingPotion();}
    protected boolean demonstrationLures(){return config.demonstrationLures();}
    protected boolean blowpipeSpecial(){return config.blowpipeSpecial();}
    protected boolean useThralls(){return config.useThralls();}
    protected boolean exitOnDamage(){return config.exitOnDamage();}
    protected boolean traceEnabled(){return config.diagnostics();}
    protected String startupDetail(){return "";}
    protected FcTickPrayers createTickPrayers(){return new FcTickPrayers(client,meleeMode,events::add);}
    protected FcFrame captureFrame(){return FcFrame.capture(client,weaponRangeSetting(),meleeMode,jadStyle,attackTicks,attackStyles,dead);}
    protected void resetVariantRun(){}
    protected void resetVariantHealers(){}
    protected void resetVariantTransition(){}
    protected void resetVariantPlanning(boolean resetLure){}
    protected void rebaseVariant(int dx,int dy){}
    protected void userTookControl(){}
    protected void captureObservation(FcFrame f,String controls){}
    protected boolean cameraNeedsOffscreenTarget(){return true;}
    protected boolean entryProtectionReady(){return actions.entryProtectionReady();}
    protected String entryProtectionStatus(){return "preparing first-wave melee protection";}
    protected boolean enterPredictedRotation(int rotation){return actions.enterPredictedRotation(entryGate,rotation);}
    protected Protection heldCaveProtection(Snapshot model,Protection protection){return HeldProtection.choose(model,protection,requestedCaveProtection);}
    protected boolean earlyConservationGap(FcFrame f){return false;}
    protected boolean beforeHealerCancellation(FcFrame f,Protection urgent){return false;}
    protected boolean variantCombat(FcFrame f,Protection urgent){return false;}
    protected Boolean variantHealerPull(FcFrame f,Snapshot model,Protection urgent){return null;}
    protected boolean healerPullExpired(FcFrame f,Snapshot model,Plan retreat){return false;}
    protected boolean batFirstAllowed(){return true;}
    protected CombatPlanner attackPlanner(FcFrame current){return planner;}
    protected boolean offenceEnabled(FcFrame f,int targetIndex){return config.offensivePrayer();}
    protected boolean primesWithoutOffence(){return false;}
    protected MoveOwner moveOwner(FcFrame current,Plan p){return MoveOwner.DEFAULT;}
    protected void moveRejected(FcFrame current,Snapshot live,Plan p,MoveOwner owner){}
    protected boolean variantRouteRejected(FcFrame current,Snapshot live,Plan checked,MoveOwner owner){return false;}
    protected boolean keepsFightingWhenDepleted(){return false;}
    protected boolean healingNeeded(FcFrame f) {
        return f.hp*100<=f.maxHp*config.eatPercent()&&f.inventory.stream().anyMatch(i->i.name().toLowerCase(Locale.ROOT).startsWith("saradomin brew("));
    }
    protected boolean supplyCritical(FcFrame f,boolean healing){return f.prayer<=8||FcSupplyPolicy.criticalHealth(f.model,f.hp,f.maxHp);}
    protected SupplyChoice prioritySupply(FcFrame f,boolean sweets,boolean healing) {
        FcFrame.ItemSlot item=null;String action="Drink";
        // Keep the chosen dose through required prayer/tab preemption. A critical
        // prayer shortage may replace a pending heal; optional boosts may not.
        if(f.prayer>8&&supplyPreparation.pending()) {
            item=f.inventory.stream().filter(i->i.id()==supplyPreparation.itemId()).findFirst().orElse(null);
            if(item!=null)action=item.hasAction("Drink")?"Drink":"Eat";
        }
        boolean healingFinished=f.hp*100>f.maxHp*config.eatPercent()
            ||f.inventory.stream().noneMatch(i->i.name().toLowerCase(Locale.ROOT).startsWith("saradomin brew("));
        if(item==null&&(f.prayer<=config.restorePrayer()||(combatStatsDrained(f)&&(brewDebt>=3||healingFinished||sweets&&!FcSupplyPolicy.criticalHealth(f.model,f.hp,f.maxHp))&&f.inventory.stream().anyMatch(i->i.name().toLowerCase(Locale.ROOT).startsWith("super restore("))))) {
            // Prefer a super restore after brews, but never ignore remaining prayer
            // potions when prayer is low simply because ranged stats are also drained.
            if(combatStatsDrained(f))item=f.inventory.stream()
                .filter(i->i.name().toLowerCase(Locale.ROOT).startsWith("super restore("))
                .findFirst().orElse(null);
            if(item==null&&f.prayer<=config.restorePrayer())item=f.inventory.stream()
                .filter(i->isPrayerPotion(i.name())).findFirst().orElse(null);
            if(item==null&&f.prayer<=3){fail("Prayer supplies exhausted");return null;}
        }
        if(item==null&&f.hp*100<=f.maxHp*config.eatPercent()) {
            item=f.inventory.stream().filter(i->i.hasAction("Eat")&&!FcSupplyPolicy.sweet(i.name())).findFirst().orElse(null);action="Eat";
            if(item==null&&(!sweets||FcSupplyPolicy.criticalHealth(f.model,f.hp,f.maxHp))){item=f.inventory.stream().filter(i->i.name().toLowerCase(Locale.ROOT).startsWith("saradomin brew(")).findFirst().orElse(null);action="Drink";}
        }
        return new SupplyChoice(item,action);
    }
    protected boolean sweetsWaitForLureReturn(){return true;}
    protected boolean recoveryPauseUnneeded(FcFrame f){return false;}
    protected String pauseRequestDetail(FcFrame f){return "";}
    protected boolean recoveryProtectionReady(FcFrame f,Protection guard) {
        return actions.overheadActive(guard)&&(tickPrayers==null||!tickPrayers.ownsInput()||tickPrayers.protectionReady());
    }
    protected FcRecoveryPolicy.Decision recoveryDecision(FcFrame f) {
        return FcRecoveryPolicy.choose(f,meleeMode,config.usePurpleSweets(),
            config.recoveryOverbrew(),config.resumeEnergy(),config.recoveryPrayerPercent(),config.rangingPotion(),
            true,energyPause.nextWave(),brewDebt,supplyAck.pending());
    }
    protected void recoveryReady(FcFrame f,String status){}
    protected String resumeStatus(){return "Fresh cave and next-wave guard observed; continuing rotation "+rotationLabel();}
    protected void prepareTickDriver(FcTickPrayers driver){}
    protected void afterTickDriver(FcTickPrayers driver){}
    protected void configureClientTickDriver(FcTickPrayers driver){}
    protected boolean prayerInputAllowed() {
        return enabled&&config!=null&&!config.observeOnly()&&client.getGameState()==GameState.LOGGED_IN
            &&client.getLocalPlayer()!=null&&state!=State.STOPPED&&state!=State.COMPLETE
            &&!playerDeath&&!Microbot.pauseAllScripts.get()&&!InputArbiter.isHuman();
    }
    protected Protection attackAnimation(Kind kind,int animation){return AttackClock.animation(kind,animation);}
    protected static final class SupplyChoice {
        final FcFrame.ItemSlot item;
        final String action;
        SupplyChoice(FcFrame.ItemSlot item,String action){this.item=item;this.action=action;}
    }
    protected static final class MoveOwner {
        static final MoveOwner DEFAULT=new MoveOwner(false,false,false);
        final boolean spacing,recovering,healerPull,checkedRoute;
        MoveOwner(boolean spacing,boolean recovering,boolean healerPull) {
            this.spacing=spacing;this.recovering=recovering;this.healerPull=healerPull;checkedRoute=recovering||spacing;
        }
        Plan check(Snapshot live,Plan checked,Protection protection) {
            return healerPull?MinimapMovement.healerChecked(live,checked.destination(),checked.nextStep(),protection,checked.reason()):
                MinimapMovement.checked(live,checked.destination(),checked.nextStep(),protection,checked.reason());
        }
    }
    protected void resetPlanning(){resetPlanning(true);}
    protected void resetPlanning(boolean resetLure){resetVariantPlanning(resetLure);combatProgress.reset();movement.reset();if(resetLure){lures.reset();lureResetAfterReturn=false;}bowPrayer.interrupt();planner.reset();camps.clear();plan=null;lastPlanTick=-1;lastMoveTick=-1;prepositioned=false;healerRetreat=null;}

    // GameTick captures server evidence. The separate ClientTick prayer path
    // uses short widget operations only; neither event handler ever sleeps.
    public void onGameTick() {
        if(!enabled)return;
        long prayerTickAt=System.nanoTime(); // timestamp BEFORE potentially expensive scene capture
        FcFrame captured=captureFrame();
        if(captured!=null&&captured.cave)observeHealers(captured);
        FcTickPrayers driver=tickPrayers;
        if(driver!=null) {
            prepareTickDriver(driver);
            try{
                driver.gameTick(captured,tickSpawnGuard(captured),prayerTickAt,healers.confirmedIndices());
                afterTickDriver(driver);
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
    public void onClientTick() {
        FcTickPrayers driver=tickPrayers;
        if(driver==null)return;
        configureClientTickDriver(driver);
        driver.clientTick(prayerInputAllowed(),config!=null&&config.offensivePrayer(),movement.pending());
    }
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
            ||message.toLowerCase(Locale.ROOT).contains("run out of scales"))){events.add("AMMUNITION FAILURE: "+message);if(!keepsFightingWhenDepleted()){exitRequested=true;failed=true;failureReason="Weapon ammunition/charges exhausted";}
            else warning="Weapon ammunition/charges exhausted; staying in the cave";}
    }
    public void onNpcSpawned(NpcSpawned e) {
        if(!enabled)return;NPC n=e.getNpc();if(tickPrayers!=null)tickPrayers.npcRemoved(n.getIndex());healers.removed(n.getIndex(),client.getTickCount());dead.remove(n.getIndex());attackTicks.remove(n.getIndex());attackStyles.remove(n.getIndex());
        NPCComposition comp=n.getTransformedComposition();if(comp==null)return;
        Kind kind=Kind.identify(n.getName(),FcFrame.npcTileSize(n));if(kind==null)return;
        WorldArea a=n.getWorldArea();if(a==null)return;LocalPoint local=LocalPoint.fromWorld(client.getTopLevelWorldView(),a.toWorldPoint());if(local==null)return;
        WorldPoint template=WorldPoint.fromLocalInstance(client,local);if(template==null||template.getRegionID()!=WaveBook.REGION)return;
        if(energyPause.recovering()) {
            recoverySpawnObserved=true;lastDangerTick=client.getTickCount();
            FcFrame current=frame;
            if(tickPrayers!=null&&current!=null)tickPrayers.recoveryPause(true,recoveryGuard(current));
        }
        if(kind==Kind.HEALER) {
            healers.spawned(n.getIndex(),client.getTickCount());
            events.add("healer-group TAGGING after spawn index="+n.getIndex()+" confirmed="+healers.progress());
        }
        waves.spawned(new WaveTracker.SpawnEvidence(client.getTickCount(),n.getIndex(),kind,template.getX(),template.getY()));
        events.add("spawn "+kind+" index="+n.getIndex()+" template="+template);
    }
    public void onNpcDespawned(NpcDespawned e){if(!enabled)return;if(tickPrayers!=null)tickPrayers.npcRemoved(e.getNpc().getIndex());healers.removed(e.getNpc().getIndex(),client.getTickCount());attackTicks.remove(e.getNpc().getIndex());attackStyles.remove(e.getNpc().getIndex());dead.remove(e.getNpc().getIndex());}
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
        Protection observed=attackAnimation(kind,npc.getAnimation());if(observed==null)return;
        int tick=client.getTickCount();lastDangerTick=tick;
        if(kind==Kind.MAGER&&observed==Protection.MAGIC&&npc.getInteracting()==client.getLocalPlayer())magicThreatUntil=tick+5;
        attackTicks.put(npc.getIndex(),tick);attackStyles.put(npc.getIndex(),observed);
        if(kind==Kind.JAD){jadStyle=observed;jadAttackTick=tick;}
        events.add("attack-clock "+kind+" index="+npc.getIndex()+" style="+observed+" tick="+tick);
    }
    public void onActorDeath(ActorDeath e) {
        if(!enabled)return;
        if(e.getActor()==client.getLocalPlayer()){playerDeath=true;return;}
        if(e.getActor() instanceof NPC){NPC n=(NPC)e.getActor();dead.add(n.getIndex());healers.removed(n.getIndex(),client.getTickCount());lastProgressAt=System.currentTimeMillis();if("TzTok-Jad".equalsIgnoreCase(n.getName())){jadDeath=true;lastProgressAt=System.currentTimeMillis();}}
    }
    public void onHitsplatApplied(HitsplatApplied e) {
        if(!enabled||client.getLocalPlayer()==null)return;
        FcFrame current=frame;
        if(e.getActor() instanceof NPC&&current!=null&&current.cave) {
            NPC npc=(NPC)e.getActor();int type=e.getHitsplat().getHitsplatType();
            if(current.presentNpcIndices.contains(npc.getIndex())&&type!=HitsplatID.HEAL
                &&type!=HitsplatID.CYAN_UP&&type!=HitsplatID.CYAN_DOWN&&type!=HitsplatID.PRAYER_DRAIN) {
                // A zero-damage attack is still server-confirmed combat, unlike a UI click.
                long now=System.currentTimeMillis();lastProgressAt=now;
                if(npc.getIndex()==pendingAttackIndex)lastAttackProgressAt=now;
            }
            return;
        }
        if(e.getActor()!=client.getLocalPlayer())return;
        WorldPoint hitLocation=WorldPoint.fromLocalInstance(client,client.getLocalPlayer().getLocalLocation());
        if(hitLocation==null||hitLocation.getRegionID()!=WaveBook.REGION||!client.getTopLevelWorldView().isInstance())return;
        int type=e.getHitsplat().getHitsplatType(),amount=e.getHitsplat().getAmount();
        if(amount<=0||type==HitsplatID.HEAL||type==HitsplatID.PRAYER_DRAIN||type==HitsplatID.CYAN_UP||type==HitsplatID.CYAN_DOWN)return;
        damage.addAndGet(amount);save(false);events.add("PLAYER DAMAGE="+amount+" type="+type+" wave="+waves.wave()+" plan="+plan);
    }
    public void onGameStateChanged(GameStateChanged e) {
        if(!enabled)return;
        if(tickPrayers!=null&&e.getGameState()!=GameState.LOGGED_IN)tickPrayers.transition();
        if(e.getGameState()==GameState.HOPPING||e.getGameState()==GameState.LOGIN_SCREEN||e.getGameState()==GameState.CONNECTION_LOST){
            frame=null;movement.reset();supplyAck.reset();supplyPreparation.reset();jadActions.reset();bowPrayer.interrupt();attackTicks.clear();attackStyles.clear();healers.reset();resetVariantTransition();healerRetreat=null;dead.clear();waves.sceneReloaded();
            if(!hadCave)resetClock();
            transitionGuardUntil=client.getTickCount()+6;events.add("state="+e.getGameState());
        }
        if(e.getGameState()==GameState.LOGGED_IN)transitionGuardUntil=client.getTickCount()+2;
    }
    @Override public void shutdown() {
        enabled=false;FcTickPrayers driver=tickPrayers;tickPrayers=null;
        if(driver!=null)driver.detach();actions.tickPrayerDriver(null);
        stopCamera();baseProfile.shutdown();if(pointerLease!=null){pointerLease.close();pointerLease=null;}trace.event("shutdown","wave="+waves.wave()+" damage="+damage.get());trace.close();
        // Preserve the current protective overhead when a user stops mid-cave.
        if(frame==null||!frame.cave)actions.prayersOff();
        super.shutdown();state=State.STOPPED;status="Stopped";
    }
    FcFrame frame(){return frame;} Plan plan(){return plan;} int wave(){return waves.wave();}
    String status(){return status;} String warning(){return warning;} State state(){return state;}
    int damage(){return damage.get();} String rotationStatus(){return waves.status();}
    int rotation(){return waves.rotation();} boolean predictionReady(){return waves.predictionReady();} Protection jadStyle(){return jadStyle;}
    String entryRotationText(){FcPredictorGate.Sample s=entryPrediction;return s!=null&&s.ready?s.rotation+" / "+(config.entryRotation()==0?"any":config.entryRotation()):"Waiting";}
    String entryClockText(){FcPredictorGate.Sample s=entryPrediction;return s!=null&&s.ready?s.second+"s"+(s.safety?" (safety lock)":""):"Not calibrated";}
    String entryGateStatus(){return entryGateStatus;}
    long runtime(){return startedAt==0?0:System.currentTimeMillis()-startedAt;}
    String combatPhase(){return combatPhase;}
    Protection requestedProtection(){return tickPrayers!=null&&tickPrayers.ownsInput()?tickPrayers.requested():requestedCaveProtection;}
    String prayerTickStatus(){String ui=actions.prayerUiStatus();return !ui.isEmpty()?ui:tickPrayers==null?"Tick prayers stopped":tickPrayers.status();}
    String offensivePrayerName(){return actions.selectedOffence();}
    String combatModeName(){return meleeMode?"Melee":"Ranged";}
    String bowCadence(){return bowPrayer.learned()?bowPrayer.period()+" ticks (observed)":"Observing shots";}
    String plannerMode(){return config!=null&&strictZeroExposure()?"Strict zero-exposure":"Full 63-wave / minimize exposure";}
    String pauseStatus(){return config==null||!config.energyPause()?"OFF":energyPause.state().toString();}}
