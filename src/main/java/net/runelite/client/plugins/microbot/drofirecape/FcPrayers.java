/* Copyright (c) 2026, DRO (droplugins). SPDX-License-Identifier: BSD-2-Clause */
package net.runelite.client.plugins.microbot.drofirecape;

import java.awt.Rectangle;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import net.runelite.api.*;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.drofirecape.core.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

/**
 * The sole cave prayer writer (the Inferno/Colosseum flick system).
 * Protection is switched on once before a predicted hit and off after it; offence is switched on
 * the tick before the player's own attack and off after it. A prayer still wanted on a later tick
 * (an uncertain clock, a movement guard) is 1-tick reset early in that tick, so it never drains.
 * Natural mouse resets only a single held slot. Each change is one prayer-widget menu
 * action whose live varbit is re-checked at delivery. Direct invokes send it on ClientTick; Natural
 * mouse carries the same entry on the background prayer worker. GameTick provides server evidence.
 */
class FcPrayers {
    protected static final Rs2PrayerEnum[] PRAYERS={
        Rs2PrayerEnum.PROTECT_MELEE,Rs2PrayerEnum.PROTECT_RANGE,Rs2PrayerEnum.PROTECT_MAGIC,
        Rs2PrayerEnum.SHARP_EYE,Rs2PrayerEnum.HAWK_EYE,Rs2PrayerEnum.EAGLE_EYE,
        Rs2PrayerEnum.DEAD_EYE,Rs2PrayerEnum.RIGOUR,Rs2PrayerEnum.BURST_STRENGTH,
        Rs2PrayerEnum.SUPERHUMAN_STRENGTH,Rs2PrayerEnum.ULTIMATE_STRENGTH,
        Rs2PrayerEnum.CLARITY_THOUGHT,Rs2PrayerEnum.IMPROVED_REFLEXES,
        Rs2PrayerEnum.INCREDIBLE_REFLEXES,Rs2PrayerEnum.CHIVALRY,Rs2PrayerEnum.PIETY,
        Rs2PrayerEnum.MYSTIC_WILL,Rs2PrayerEnum.MYSTIC_LORE,Rs2PrayerEnum.MYSTIC_MIGHT,
        Rs2PrayerEnum.MYSTIC_VIGOUR,Rs2PrayerEnum.AUGURY};
    protected static final int OVERHEADS=7;
    protected static final long MAGIC_RELEASE_DEADLINE_NS=180_000_000L;
    protected final Client client;
    protected final Consumer<String> log;
    protected final LongSupplier nanos;
    protected final FcInput input;
    protected final BowPrayerClock attackClock;
    protected final ArrayList<Rs2PrayerEnum> slots=new ArrayList<>();
    protected final Map<Rs2PrayerEnum,Integer> channel=new EnumMap<>(Rs2PrayerEnum.class);
    protected final TickProtection protection=new TickProtection(true);
    protected final PrayerLedger ledger;
    protected final PureWaveThreats pureWaveThreats=new PureWaveThreats();
    protected volatile FcFrame current;
    protected Snapshot currentModel;
    protected Protection spawnGuard=Protection.NONE;
    protected volatile TickProtection.Decision decision;
    protected volatile boolean owned,ready,offenceReady,forecastMoving,detached,inputAllowed,pendingJad,prayerExhausted;
    protected volatile Protection movementGuard=Protection.NONE;
    protected Protection forecastGuard=Protection.NONE;
    protected volatile boolean movementPrearm,recoveryControl;
    protected volatile Protection recoveryGuard=Protection.NONE;
    protected boolean forecastPrearm,wasAllowed;
    protected volatile String label="Detecting",status="Awaiting game ticks";
    protected volatile int motionUntil=-1,pureWave;
    protected int selectedMask;
    protected volatile int observedMask,desiredMask;
    protected long operations;
    protected String lastProblem="";
    protected long tickReceivedAt;
    protected volatile long optionalDeadline;
    protected final AtomicReference<Dispatch> queued=new AtomicReference<>();
    protected static final class Dispatch {
        final FcFrame frame;final TickProtection.Decision decision;final PrayerLedger.Command command;final long tickAt;
        Dispatch(FcFrame frame,TickProtection.Decision decision,PrayerLedger.Command command,long tickAt) {
            this.frame=frame;this.decision=decision;this.command=command;this.tickAt=tickAt;
        }
    }

    FcPrayers(Client client,Consumer<String> log,FcInput input,BowPrayerClock attackClock) {
        this(client,log,System::nanoTime,input,attackClock);
    }
    /** Clock injection is only for reproducible offline tests. */
    FcPrayers(Client client,Consumer<String> log,LongSupplier nanos,FcInput input,BowPrayerClock attackClock) {
        this.client=client;this.log=log;this.nanos=nanos;this.input=input;
        this.attackClock=attackClock==null?new BowPrayerClock():attackClock;
        Map<Integer,Integer> byWidget=new LinkedHashMap<>();
        for(Rs2PrayerEnum p:PRAYERS) {
            Integer slot=byWidget.get(p.getIndex());
            if(slot==null){slot=slots.size();byWidget.put(p.getIndex(),slot);slots.add(p);}
            channel.put(p,slot);
        }
        ledger=new PrayerLedger(slots.size());
    }
    boolean ownsInput(){return owned&&!detached;}
    void pureWave(int wave){pureWave=wave;}
    void recoveryPause(boolean controlled,Protection guard) {
        if(recoveryControl==controlled&&recoveryGuard==guard)return;
        if(!controlled&&recoveryControl&&recoveryGuard!=Protection.NONE)spawnGuard=recoveryGuard;
        recoveryControl=controlled;recoveryGuard=guard;decision=null;queued.set(null);ready=false;optionalDeadline=0;
        movementPrearm=false;movementGuard=Protection.NONE;
    }
    boolean prayersOffReady(){return owned&&inputAllowed&&recoveryControl&&recoveryGuard==Protection.NONE
        &&current!=null&&!current.stale()&&current.model.mobs().isEmpty()&&desiredMask==0&&observedMask==0
        &&ledger.pendingMask()==0&&queued.get()==null;}
    boolean protectionReady(){return ownsInput()&&(ready||meleeOnly())&&!pendingJad;}
    /** A melee-only demand never holds an attack or move: Melee is flicked on its own just before
     * the swing, while ranged/magic and Jad still wait for their confirmed overhead. */
    protected boolean meleeOnly() {
        TickProtection.Decision d=decision;FcFrame f=current;
        return d!=null&&f!=null&&d.protection==Protection.MELEE&&d.jadProtection==Protection.NONE
            &&(d.dueMask&~CombatPlanner.bit(Protection.MELEE))==0
            &&f.model.mobs().stream().noneMatch(m->m.kind()==Kind.JAD);
    }
    boolean offenceReady(){return ownsInput()&&offenceReady;}
    Protection requested(){TickProtection.Decision d=decision;return d==null?Protection.NONE:d.protection;}
    String selectedOffence(){return label;}
    String status(){return status;}
    /** Pure keeps fighting at zero prayer: permission to act unprotected, never an active overhead. */
    boolean exhaustedCombatReady(){return ownsInput()&&inputAllowed&&prayerExhausted
        &&current!=null&&!current.stale()&&current.prayer==0;}
    /** Background input never reads or mutates the client-thread attack clocks. */
    boolean optionalInputWindow(int tick,long budgetMillis) {
        FcFrame f=current;long now=nanos.getAsLong();
        return exhaustedCombatReady()&&f.tick==tick||owned&&ready&&inputAllowed&&f!=null&&f.tick==tick&&!f.stale()
            &&now>=tickReceivedAt&&now+budgetMillis*1_000_000L+100_000_000L<optionalDeadline;
    }
    /** A stationary attack may use the confirmed current tick; never borrow time across the next tick. */
    boolean attackInputWindow(int tick,long budgetMillis) {
        FcFrame f=current;long now=nanos.getAsLong();
        if(optionalInputWindow(tick,budgetMillis))return true;
        return owned&&(ready||meleeOnly())&&inputAllowed&&!pendingJad&&decision!=null&&f!=null
            &&f.tick==tick&&!f.stale()&&!f.moving&&!forecastMoving
            &&f.model.mobs().stream().noneMatch(m->m.kind()==Kind.JAD)
            &&now>=tickReceivedAt&&now+budgetMillis*1_000_000L+100_000_000L<tickReceivedAt+600_000_000L;
    }
    void primeMovement(int tick){primeMovement(tick,Protection.NONE);}
    void primeMovement(int tick,Protection guard) {
        FcFrame f=current;guard=armable(f,guard);
        if(requested()==Protection.MAGIC&&f!=null&&f.model.mobs().stream().anyMatch(m->m.kind()==Kind.MAGER)
            &&!(guard!=Protection.MAGIC&&PureMageGuard.allTerrainCovered(f.model)))
            guard=Protection.MAGIC;
        if(motionUntil<tick&&!forecastMoving)ready=false;
        if(!movementPrearm||movementGuard!=guard){movementGuard=guard;movementPrearm=true;ready=false;}
        motionUntil=Math.max(motionUntil,tick+1);
    }
    /** Whether the checked movement guard replaces this tick's forecast. A pre-armed Magic move
     * that has not started also wins over a forecast whose due styles are all uncertain. */
    static boolean movementGuardWins(TickProtection.Decision decision,Protection movementGuard,boolean jad,boolean waitingToMove) {
        if(jad)return decision.jadProtection==Protection.NONE;
        return decision.dueMask==0||decision.protection==movementGuard
            ||waitingToMove&&movementGuard==Protection.MAGIC&&(decision.dueMask&~decision.uncertainMask)==0;
    }
    /** Only a projectile style whose attacker can reach its range during the move is pre-armed.
     * Melee is never pre-armed: it is flicked only before an attacker in contact swings. */
    static Protection armable(FcFrame f,Protection guard) {
        if(f==null||guard!=Protection.RANGE&&guard!=Protection.MAGIC)return Protection.NONE;
        for(Mob m:f.model.mobs())if((m.kind()==Kind.JAD||m.kind().protection==guard)
            &&m.distance(f.model.player())<=m.kind().range+2)return guard;
        return Protection.NONE;
    }
    boolean movementReady(Protection guard) {
        if(exhaustedCombatReady())return true;
        guard=armable(current,guard);
        Protection actual=requested();int bit=CombatPlanner.bit(actual);
        boolean gap=decision!=null&&actual==Protection.NONE&&decision.dueMask==0&&decision.uncertainMask==0;
        // The route preview can request Melee while the arbiter protects a due ranger.
        // The caller rechecks that route under this actual overhead before clicking.
        boolean rangedPriority=guard==Protection.MELEE&&actual==Protection.RANGE&&decision!=null
            &&(decision.dueMask&CombatPlanner.bit(Protection.RANGE))!=0
            &&current!=null&&current.model.mobs().stream().noneMatch(m->m.kind()==Kind.JAD);
        return protectionReady()&&(gap||rangedPriority||guard==Protection.NONE||actual==guard||actual==Protection.MAGIC)
            &&ledger.committed(observedMask,bit,OVERHEADS);
    }
    void movementSent(){movementPrearm=false;}
    void npcAnimation(int index,Kind kind,int animation) {
        protection.animation(index,kind,animation);
        if(kind==Kind.JAD&&TickProtection.attackStyle(kind,animation)!=null){pendingJad=true;ready=false;}
    }
    void npcRemoved(int index){protection.removed(index);}
    void transition() {
        owned=ready=offenceReady=forecastMoving=wasAllowed=pendingJad=false;
        inputAllowed=false;queued.set(null);
        current=null;currentModel=null;decision=null;protection.reset();ledger.reset();pureWaveThreats.reset();
        optionalDeadline=0;movementGuard=forecastGuard=Protection.NONE;movementPrearm=forecastPrearm=false;
        motionUntil=-1;
        desiredMask=observedMask=selectedMask=0;status="Waiting for fresh scene";
    }
    void detach(){detached=true;owned=ready=offenceReady=inputAllowed=false;queued.set(null);}

    void gameTick(FcFrame f,Protection nextWaveGuard){gameTick(f,nextWaveGuard,nanos.getAsLong());}
    void gameTick(FcFrame f,Protection nextWaveGuard,long receivedAtNanos){gameTick(f,nextWaveGuard,receivedAtNanos,Set.of());}
    void gameTick(FcFrame f,Protection nextWaveGuard,long receivedAtNanos,Set<Integer> taggedHealers) {
        if(detached)return;
        if(f==null){owned=ready=offenceReady=inputAllowed=false;current=null;currentModel=null;queued.set(null);ledger.failed();status="No fresh scene; holding";return;}
        if(!f.cave){if(owned)transition();return;}
        if(current!=null&&!current.sameScene(f))transition();
        if(current!=null&&tickReceivedAt>0) {
            long interval=receivedAtNanos-tickReceivedAt;
            if(interval<400_000_000L||interval>850_000_000L)protection.invalidateCadence();
        }
        current=f;owned=true;ready=false;optionalDeadline=0;
        currentModel=prayerModel(f.model,taggedHealers);
        pureWaveThreats.observe(pureWave,currentModel);
        spawnGuard=pureWaveThreats.spawnGuard(currentModel,nextWaveGuard);
        tickReceivedAt=receivedAtNanos;
        readState();
        ledger.beginTick(f.tick,receivedAtNanos,observedMask);
        // Input acknowledgement does not invalidate independent NPC evidence.
        protection.beginTick(f.tick,currentModel.mobs());
        protection.spatialEvidence(currentModel);pendingJad=false;
        decision=null;
        log.accept("tick-prayer-evidence tick="+f.tick+" activeMask="+observedMask+" pendingMask="+ledger.pendingMask()
            +" sync="+ledger.synchronised()+" operationsSent="+operations+" missedAck="+ledger.misses()+" input="+input.mode());
    }
    void clientTick(boolean allowed,boolean movementPending) {
        if(detached||!owned||current==null)return;
        inputAllowed=allowed;prayerExhausted=false;
        if(!allowed) {
            queued.set(null);ready=offenceReady=false;ledger.failed();protection.invalidateCadence();
            wasAllowed=false;status="Paused / input not owned";return;
        }
        if(!wasAllowed){protection.invalidateCadence();wasAllowed=true;}
        FcFrame f=current;Snapshot scene=currentModel;long now=nanos.getAsLong();
        if(f.stale()||!ledger.fresh(now)){ready=offenceReady=false;status="Stale tick; holding";return;}
        try {
            if(!client.isClientThread())throw new IllegalStateException("Prayers require the client thread");
            readState();
            if(client.getBoostedSkillLevel(Skill.PRAYER)<=0&&!(recoveryControl&&recoveryGuard==Protection.NONE&&observedMask==0)) {
                queued.set(null);ready=offenceReady=false;ledger.failed();prayerExhausted=true;
                decision=new TickProtection.Decision(f.tick,Protection.NONE,0,0,"Prayer exhausted");
                desiredMask=0;status="Prayer exhausted; continuing combat";return;
            }
            boolean paused=recoveryControl&&scene.mobs().isEmpty();
            boolean motion=!paused&&(f.walking||movementPending||motionUntil>=f.tick);
            if(!motion){movementGuard=Protection.NONE;movementPrearm=false;}
            boolean jad=scene.mobs().stream().anyMatch(m->m.kind()==Kind.JAD);
            if(decision==null||motion!=forecastMoving||forecastGuard!=movementGuard||forecastPrearm!=movementPrearm) {
                forecastMoving=motion;
                decision=paused?new TickProtection.Decision(f.tick,recoveryGuard,CombatPlanner.bit(recoveryGuard),0,
                    recoveryGuard==Protection.NONE?"Confirmed recovery pause: prayers off":"Guard next wave before resume"):
                    BetaTickProtection.choose(protection,scene,motion,requested(),spawnGuard,true);
                forecastGuard=movementGuard;forecastPrearm=movementPrearm;
                // Children may attack on their first visible frame; a split gap is not safe to flick off.
                if(pureWaveThreats.nearbySplit(scene)
                    &&(decision.protection==Protection.NONE||decision.protection==Protection.MELEE))
                    decision=new TickProtection.Decision(f.tick,Protection.MELEE,
                        decision.dueMask|CombatPlanner.bit(Protection.MELEE),
                        decision.uncertainMask|CombatPlanner.bit(Protection.MELEE),
                        "Pre-arm nearby blob children during split delay");
                boolean exposed=scene.mobs().stream().anyMatch(m->
                    (CombatPlanner.threats(scene.grid(),m,scene.player(),scene.jadStyle())
                        &CombatPlanner.bit(movementGuard))!=0);
                if(!paused&&movementGuard!=Protection.NONE&&(movementPrearm||motion&&!exposed)
                    &&movementGuardWins(decision,movementGuard,jad,movementPrearm&&!f.moving))
                    decision=new TickProtection.Decision(f.tick,movementGuard,CombatPlanner.bit(movementGuard),0,
                        "Pre-arm checked movement before entering attack range");
                // Prayer input never borrows the attack/supply input; it runs independently.
                optionalDeadline=tickReceivedAt+600_000_000L*4;
                if(decision.jadReturnProtection!=Protection.NONE)
                    optionalDeadline=Math.min(optionalDeadline,tickReceivedAt+600_000_000L);
            }
            // Jad's short healer gap: missing the early budget retains the Jad guard instead of a late switch.
            if(decision.jadReturnProtection!=Protection.NONE
                &&(observedMask&OVERHEADS)!=CombatPlanner.bit(Protection.MELEE)
                &&now-tickReceivedAt>MAGIC_RELEASE_DEADLINE_NS) {
                decision=new TickProtection.Decision(f.tick,decision.jadReturnProtection,
                    CombatPlanner.bit(decision.jadReturnProtection),0,"Keep Jad guard: healer switch missed its input deadline");
                queued.set(null);optionalDeadline=0;
            }
            boolean offence=!paused&&offenceNeeded(f,motion);
            desiredMask=(decision.protection==Protection.NONE?0:1<<(decision.protection.ordinal()-1))
                |(offence?selectedMask:0);
            boolean invokes=input.invokes(MenuAction.CC_OP);
            List<PrayerLedger.Command> commands=ledger.plan(now,observedMask,desiredMask,OVERHEADS,invokes?slots.size():1);
            boolean missing=false;
            for(int i=0;i<slots.size();i++)if((desiredMask&(1<<i))!=0&&client.getWidget(slots.get(i).getIndex())==null)missing=true;
            for(PrayerLedger.Command command:commands) {
                if(queued.get()!=null)break;
                Rs2PrayerEnum prayer=slot(command.channel);
                if(client.getWidget(prayer.getIndex())==null){missing=true;continue;}
                if(invokes&&command.type==PrayerLedger.Type.RESET) {
                    if(resetNow(command.mask)) {
                        ledger.sent(command,OVERHEADS);operations+=2L*Integer.bitCount(command.mask);
                        log.accept("prayer-reset tick="+f.tick+" mask="+command.mask+" forecast="+decision.protection
                            +" elapsedMs="+(now-tickReceivedAt)/1_000_000L);
                    }
                    break;
                }
                if(invokes) {
                    boolean on=command.type==PrayerLedger.Type.ON;
                    if(FcInput.invokeNow(client,entry(prayer,on,()->live(prayer,on)))) {
                        ledger.sent(command,OVERHEADS);operations++;
                        log.accept("prayer-invoke tick="+f.tick+" slot="+prayer.name()+" type="+command.type
                            +" forecast="+decision.protection+" due="+decision.dueMask+" uncertain="+decision.uncertainMask
                            +" elapsedMs="+(now-tickReceivedAt)/1_000_000L);
                    }
                    // One action per client tick; the ledger replans on the next one.
                    break;
                }
                // Protection outranks movement; offensive flicks never cancel a walk.
                if(queued.compareAndSet(null,new Dispatch(f,decision,command,tickReceivedAt))
                    &&command.channel<3&&command.type==PrayerLedger.Type.ON)input.protectionQueued();
            }
            int needed=desiredMask&OVERHEADS;
            ready=!pendingJad&&ledger.committed(observedMask,needed,OVERHEADS);
            offenceReady=ledger.observed(observedMask,selectedMask,selectedMask);
            String mode=input.mode().toString();
            status=missing?"Prayer widget missing; no flick":
                !ledger.synchronised()?"Holding: tick sync / acknowledgement":
                decision.conflict()?mode+" flick; conflicting attack styles":
                decision.protection==Protection.NONE?mode+" flick: overhead off":mode+" flick: "+decision.protection;
            if(missing){ledger.failed();ready=offenceReady=false;problem("Selected prayer widget missing; protection must be checked in client");}
        } catch(RuntimeException|LinkageError e) {
            ledger.failed();ready=offenceReady=false;status="Prayer dispatch error; holding";
            problem(e.getClass().getSimpleName()+": "+e.getMessage());
        }
    }
    /** Offence is switched on the tick before each of the player's own shots and off on the shot tick, only at
     * the targets OffenceTargets allows. The first shot after an attack request is never boosted (Inferno). */
    protected boolean offenceNeeded(FcFrame f,boolean motion) {
        if(selectedMask==0||f.model.mobs().isEmpty()||motion)return false;
        Mob target=find(f,f.interactingIndex);
        if(target==null||!OffenceTargets.offence(target.index(),f.model))return false;
        return attackClock.wantOn(f.tick,f.weaponId,f.attackStyle,true,true);
    }
    protected static Mob find(FcFrame f,int index){for(Mob m:f.model.mobs())if(m.index()==index)return m;return null;}
    protected static Snapshot prayerModel(Snapshot scene,Set<Integer> taggedHealers) {
        if(taggedHealers.isEmpty())return scene;
        List<Mob> mobs=new ArrayList<>();
        for(Mob mob:scene.mobs())mobs.add(mob.kind()==Kind.HEALER&&taggedHealers.contains(mob.index())
            &&!mob.attackingPlayer()?new Mob(mob.index(),mob.kind(),mob.tile(),mob.size(),mob.healthRatio(),
                mob.healthScale(),mob.lastAttackTick(),mob.lastStyle(),true):mob);
        return new Snapshot(scene.tick(),scene.player(),scene.grid(),mobs,scene.runEnergy(),scene.running(),
            scene.weaponRange(),scene.jadStyle());
    }
    protected void readState() {
        observedMask=selectedMask=0;
        FcFrame f=current;
        boolean melee=f!=null&&FcFrame.meleeWeapon(f.weapon),magic=!melee&&f!=null&&FcMagic.magic(f.weapon);
        List<String> best=OffensivePrayers.select(melee,magic,client.getRealSkillLevel(Skill.PRAYER),
            client.getRealSkillLevel(Skill.DEFENCE),client.getVarbitValue(VarbitID.KR_KNIGHTWAVES_STATE)==8,
            client.getVarbitValue(VarbitID.PRAYER_RIGOUR_UNLOCKED)>0,
            client.getVarbitValue(VarbitID.PRAYER_DEADEYE_UNLOCKED)>0,
            client.getVarbitValue(VarbitID.PRAYER_AUGURY_UNLOCKED)>0);
        StringJoiner names=new StringJoiner(" + ");
        for(Rs2PrayerEnum p:PRAYERS) {
            int bit=1<<channel.get(p);
            if(client.getVarbitValue(p.getVarbit())==1)observedMask|=bit;
            if(best.contains(p.name())){selectedMask|=bit;names.add(p.getName());}
        }
        label=names.length()==0?"None available":names.toString();
    }
    protected Rs2PrayerEnum slot(int channel){return slots.get(channel);}
    /** Client thread: a slot is active if any prayer sharing its widget is active. */
    protected boolean live(Rs2PrayerEnum prayer,boolean desired) {
        boolean active=false;
        for(Rs2PrayerEnum p:PRAYERS)if(p.getIndex()==prayer.getIndex())active|=client.getVarbitValue(p.getVarbit())==1;
        if(active==desired)return false;
        return !desired||client.getBoostedSkillLevel(Skill.PRAYER)>0;
    }
    static NewMenuEntry entry(Rs2PrayerEnum prayer,boolean on) {
        return new NewMenuEntry().param0(-1).param1(prayer.getIndex()).type(MenuAction.CC_OP)
            .identifier(1).itemId(-1).option(on?"Activate":"Deactivate").target(prayer.getName());
    }
    static FcMenuEntry entry(Rs2PrayerEnum prayer,boolean on,java.util.function.BooleanSupplier live) {
        return FcMenuEntry.guarded(entry(prayer,on),live);
    }
    /** Client thread: a slot's widget prayer is active. */
    protected boolean active(Rs2PrayerEnum prayer) {
        for(Rs2PrayerEnum p:PRAYERS)if(p.getIndex()==prayer.getIndex()&&client.getVarbitValue(p.getVarbit())==1)return true;
        return false;
    }
    /** Client thread: every active slot OFF, then every one back ON, in one invocation. Once any OFF
     * is sent every paired ON is mandatory; no yield or varbit test between the halves. */
    protected boolean resetNow(int mask) {
        List<Rs2PrayerEnum> prayers=new ArrayList<>();
        for(int i=0;i<slots.size();i++)if((mask&(1<<i))!=0)prayers.add(slot(i));
        if(prayers.isEmpty()||!client.isClientThread()||client.getGameState()!=GameState.LOGGED_IN
            ||client.getLocalPlayer()==null||client.getBoostedSkillLevel(Skill.PRAYER)<=0)return false;
        for(Rs2PrayerEnum prayer:prayers)if(client.getWidget(prayer.getIndex())==null||!active(prayer))return false;
        for(Rs2PrayerEnum prayer:prayers)
            client.menuAction(-1,prayer.getIndex(),MenuAction.CC_OP,1,-1,"Deactivate",prayer.getName());
        for(Rs2PrayerEnum prayer:prayers)
            client.menuAction(-1,prayer.getIndex(),MenuAction.CC_OP,1,-1,"Activate",prayer.getName());
        return true;
    }
    /** Natural mouse only: travel and press never run on the client thread. */
    void pulse() {
        if(detached)return;
        Dispatch dispatch=queued.get();
        if(dispatch==null)return;
        boolean sent=false;String failure=null;
        long started=nanos.getAsLong();
        Rs2PrayerEnum prayer=slot(dispatch.command.channel);
        boolean on=dispatch.command.type==PrayerLedger.Type.ON;
        try {
            if(valid(dispatch)) {
                Rectangle bounds=onClientThread(()->{
                    if(!FcInput.openTab(client,InterfaceTab.PRAYER))return null;
                    Widget widget=client.getWidget(prayer.getIndex());
                    return widget==null||widget.isHidden()?null:
                        FcInput.visibleBounds(widget.getBounds(),client.getCanvasWidth(),client.getCanvasHeight());
                },null);
                if(bounds!=null&&dispatch.command.type==PrayerLedger.Type.RESET) {
                    // Both presses must land in the tick the reset was planned for.
                    if(nanos.getAsLong()-dispatch.tickAt<=PrayerLedger.RESET_END_NS
                        &&input.dispatch(entry(prayer,false),bounds,()->valid(dispatch)&&active(prayer))) {
                        sent=input.dispatch(entry(prayer,true),bounds,()->!detached&&owned&&inputAllowed);
                        if(!sent)failure="Reset ON half not delivered";
                    }
                } else if(bounds!=null)sent=input.dispatch(entry(prayer,on),bounds,()->valid(dispatch)&&live(prayer,on));
            }
        } catch(RuntimeException|LinkageError e) {failure=e.getClass().getSimpleName()+": "+e.getMessage();}
        final boolean completed=sent;final String error=failure;final long completedAt=nanos.getAsLong();
        // Acknowledge on the thread which owns the ledger. A press is not a server acknowledgement.
        onClientThread(()->{
            if(completed) {
                operations++;
                log.accept("prayer-mouse tick="+dispatch.frame.tick+" type="+dispatch.command.type
                    +" slot="+prayer.name()+" next="+dispatch.decision.protection
                    +" startMs="+(started-dispatch.tickAt)/1_000_000L+" completedMs="+(completedAt-dispatch.tickAt)/1_000_000L);
            }
            if(current!=dispatch.frame&&owned&&completed&&dispatch.command.channel<3) {
                ledger.failed();protection.invalidateCadence();ready=false;decision=null;optionalDeadline=0;
            } else if(current==dispatch.frame&&owned&&queued.get()==dispatch) {
                if(error!=null){ledger.failed();ready=offenceReady=false;problem(error);}
                else if(completed)ledger.sent(dispatch.command,OVERHEADS);
            }
            queued.compareAndSet(dispatch,null);return true;
        },false);
    }
    protected boolean valid(Dispatch dispatch) {
        long age=nanos.getAsLong()-dispatch.tickAt;
        boolean wanted=(desiredMask&(1<<dispatch.command.channel))!=0;
        boolean matches=dispatch.command.type==PrayerLedger.Type.RESET?(desiredMask&dispatch.command.mask)==dispatch.command.mask
            :dispatch.command.type==PrayerLedger.Type.OFF?!wanted:wanted;
        boolean healerGap=dispatch.command.type==PrayerLedger.Type.ON&&dispatch.command.channel==0
            &&dispatch.decision.jadReturnProtection!=Protection.NONE;
        return matches&&!detached&&owned&&inputAllowed&&!pendingJad&&!dispatch.frame.stale()
            &&current==dispatch.frame&&decision==dispatch.decision
            &&queued.get()==dispatch&&age>=0&&age<1_200_000_000L
            &&(!healerGap||age<=MAGIC_RELEASE_DEADLINE_NS);
    }
    protected void problem(String text){if(!text.equals(lastProblem)){lastProblem=text;log.accept("prayer-warning "+text);}}
    protected <T>T onClientThread(java.util.concurrent.Callable<T> action,T fallback){return FcActions.read(action,fallback);}
}
