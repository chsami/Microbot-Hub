/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import net.runelite.api.*;
import net.runelite.client.plugins.microbot.drofirecape.core.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

/**
 * The sole cave prayer writer for every run.
 * GameTick provides server evidence; ClientTick publishes at most one request.
 * By default the background prayer worker opens the book with F-keys and clicks its visible
 * button. Only a changed protection/offence state requests a click; an already
 * active overhead is not reset every tick while another style is approaching.
 * The opt-in native widget transport dispatches on ClientTick without mouse work.
 */
class OptionalTickPrayers extends FcTickPrayers {
    OptionalTickPrayers(Client client,boolean melee,Consumer<String> log){this(client,melee,log,System::nanoTime);}
    OptionalTickPrayers(Client client,boolean melee,Consumer<String> log,LongSupplier nanos) {
        super(client,melee,log,nanos,true);
        nativeCycle=new NativePrayerCycle(slots.size());
    }

    private volatile boolean nativeWidgets;
    private volatile boolean pureTinyPrayers;
    private volatile boolean conservation;
    void conservation(boolean enabled){if(conservation!=enabled){conservation=enabled;decision=null;}}
    private volatile int pureWave;
    private final PureWaveThreats pureWaveThreats=new PureWaveThreats();
    void pureWave(int wave){pureWave=wave;}
    void pureTinyPrayers(boolean enabled){if(pureTinyPrayers!=enabled){pureTinyPrayers=enabled;queued.set(null);decision=null;ready=false;}}
    void nativeWidgets(boolean enabled){if(nativeWidgets!=enabled){nativeWidgets=enabled;queued.set(null);decision=null;ready=false;}}
    private final NativePrayerCycle nativeCycle;
    private volatile boolean prayerExhausted;
    @Override
    boolean prayersOffReady(){return owned&&inputAllowed&&recoveryControl&&recoveryGuard==Protection.NONE
        &&current!=null&&!current.stale()&&current.model.mobs().isEmpty()&&desiredMask==0&&observedMask==0
        &&(nativeWidgets?nativeCycle.pendingMask():cycle.pendingMask())==0&&queued.get()==null;}
    /** Pure keeps fighting at zero prayer. This is permission to act unprotected,
     * never an assertion that an overhead is active. Normal/visible timing stays intact. */
    boolean exhaustedCombatReady(){return pureTinyPrayers&&ownsInput()&&inputAllowed&&prayerExhausted
        &&current!=null&&!current.stale()&&current.prayer==0;}
    @Override
    /** Background input never reads or mutates the client-thread attack clocks. */
    boolean optionalInputWindow(int tick,long budgetMillis) {
        FcFrame f=current;long now=nanos.getAsLong();
        return exhaustedCombatReady()&&f.tick==tick||owned&&ready&&inputAllowed&&f!=null&&f.tick==tick&&!f.stale()
            &&now>=tickReceivedAt&&now+budgetMillis*1_000_000L+100_000_000L<optionalDeadline;
    }
    @Override
    void primeMovement(int tick,Protection guard) {
        FcFrame f=current;
        if(requested()==Protection.MAGIC&&f!=null&&f.model.mobs().stream().anyMatch(m->m.kind()==Kind.MAGER)
            &&!(pureTinyPrayers&&guard!=Protection.MAGIC&&PureMageGuard.allTerrainCovered(f.model)))
            guard=Protection.MAGIC;
        if(motionUntil<tick&&!forecastMoving)ready=false;
        if(!movementPrearm||movementGuard!=guard) {
            movementGuard=guard;movementPrearm=true;ready=false;
        }
        motionUntil=Math.max(motionUntil,tick+1);
    }
    @Override
    boolean movementReady(Protection guard) {
        if(exhaustedCombatReady())return true;
        Protection actual=requested();int bit=CombatPlanner.bit(actual);
        boolean betaGap=nativeWidgets&&decision!=null&&actual==Protection.NONE
            &&decision.dueMask==0&&decision.uncertainMask==0;
        // The route preview can request Melee while the authoritative BETA
        // arbiter protects a due ranger. The caller rechecks that route under
        // this actual overhead before clicking; waiting for Melee can deadlock.
        boolean pureRangedPriority=nativeWidgets&&pureTinyPrayers&&guard==Protection.MELEE
            &&actual==Protection.RANGE&&decision!=null
            &&(decision.dueMask&CombatPlanner.bit(Protection.RANGE))!=0
            &&current!=null&&current.model.mobs().stream().noneMatch(m->m.kind()==Kind.JAD);
        return protectionReady()&&(betaGap||pureRangedPriority||guard==Protection.NONE||actual==guard||actual==Protection.MAGIC)
            &&(nativeWidgets?nativeCycle.committed(observedMask,bit,OVERHEADS):
                actual==Protection.NONE||confirmedOverhead==bit&&(observedMask&OVERHEADS)==bit);
    }
    @Override
    void transition() {
        owned=ready=offenceReady=forecastMoving=wasAllowed=pendingJad=false;
        inputAllowed=false;queued.set(null);skippedMask=0;
        current=null;currentModel=null;decision=null;protection.reset();cycle.reset();nativeCycle.reset();pureWaveThreats.reset();
        optionalDeadline=0;
        movementGuard=forecastGuard=prepareTarget=preparedTarget=Protection.NONE;
        movementPrearm=forecastPrearm=false;confirmedOverhead=0;preparedFrame=null;
        primeAttackUntil=primeTarget=motionUntil=-1;
        lastOffenceUseTick=-1000;
        desiredMask=observedMask=selectedMask=0;status="Waiting for fresh scene";
    }
    @Override
    void gameTick(FcFrame f,Protection nextWaveGuard,long receivedAtNanos,Set<Integer> taggedHealers) {
        if(detached)return;
        if(f==null){owned=ready=offenceReady=inputAllowed=false;current=null;currentModel=null;queued.set(null);cycle.failed();nativeCycle.failed();status="No fresh scene; holding";return;}
        if(!f.cave){if(owned)transition();return;}
        if(current!=null&&!current.sameScene(f))transition();
        if(current!=null&&tickReceivedAt>0) {
            long interval=receivedAtNanos-tickReceivedAt;
            if(interval<400_000_000L||interval>850_000_000L)protection.invalidateCadence();
        }
        current=f;owned=true;ready=false;optionalDeadline=0;spawnGuard=nextWaveGuard;
        currentModel=prayerModel(f.model,taggedHealers);
        if(pureTinyPrayers) {
            pureWaveThreats.observe(pureWave,currentModel);
            spawnGuard=pureWaveThreats.spawnGuard(currentModel,nextWaveGuard);
        }else pureWaveThreats.reset();
        tickReceivedAt=receivedAtNanos;skippedMask=0;
        readState();
        confirmedOverhead=observedMask&OVERHEADS;
        cycle.beginTick(f.tick,receivedAtNanos,observedMask);
        nativeCycle.beginTick(f.tick,receivedAtNanos,observedMask);
        // Input acknowledgement does not invalidate independent NPC evidence.
        protection.beginTick(f.tick,currentModel.mobs());
        protection.spatialEvidence(currentModel);pendingJad=false;
        decision=null;
        log.accept("tick-prayer-evidence tick="+f.tick+" activeMask="+observedMask
            +" pendingMask="+(nativeWidgets?nativeCycle.pendingMask():cycle.pendingMask())
            +" sync="+(nativeWidgets?nativeCycle.synchronised():cycle.synchronised())
            +" resetsSent="+resets+" operationsSent="+operations
            +" missedAck="+(nativeWidgets?nativeCycle.misses():cycle.misses()));
    }
    @Override
    void clientTick(boolean allowed,boolean useOffence,boolean movementPending) {
        if(detached||!owned||current==null)return;
        inputAllowed=allowed;prayerExhausted=false;
        if(!allowed) {
            queued.set(null);
            ready=offenceReady=false;cycle.failed();nativeCycle.failed();protection.invalidateCadence();
            wasAllowed=false;status="Paused / input not owned";return;
        }
        if(!wasAllowed){protection.invalidateCadence();wasAllowed=true;}
        FcFrame f=current;Snapshot scene=currentModel;long now=nanos.getAsLong();
        if(f.stale()||!cycle.fresh(now)){ready=offenceReady=false;status="Stale tick; holding";return;}
        try {
            if(!client.isClientThread())throw new IllegalStateException("Tick prayers require the client thread");
            readState();
            if(client.getBoostedSkillLevel(Skill.PRAYER)<=0&&!(recoveryControl&&recoveryGuard==Protection.NONE&&observedMask==0)) {
                queued.set(null);ready=offenceReady=false;cycle.failed();nativeCycle.failed();
                prayerExhausted=true;
                decision=new TickProtection.Decision(f.tick,Protection.NONE,0,0,"Prayer exhausted");
                desiredMask=0;status=pureTinyPrayers?"Prayer exhausted; continuing combat":"Restore prayer";return;
            }
            boolean paused=recoveryControl&&scene.mobs().isEmpty();
            boolean motion=!paused&&(f.moving||movementPending||motionUntil>=f.tick);
            if(!motion){movementGuard=Protection.NONE;movementPrearm=false;}
            if(decision==null||motion!=forecastMoving||forecastGuard!=movementGuard||forecastPrearm!=movementPrearm) {
                forecastMoving=motion;decision=paused?new TickProtection.Decision(f.tick,recoveryGuard,CombatPlanner.bit(recoveryGuard),0,
                    recoveryGuard==Protection.NONE?"Confirmed recovery pause: prayers off":"Guard next wave before resume"):
                    nativeWidgets?BetaTickProtection.choose(protection,scene,motion,requested(),spawnGuard,pureTinyPrayers):
                    protection.choose(scene,motion,requested(),spawnGuard);
                forecastGuard=movementGuard;forecastPrearm=movementPrearm;
                // Children may attack on their first visible frame. Other trapped
                // monsters do not make the preceding split gap safe to turn off.
                if(pureTinyPrayers&&pureWaveThreats.nearbySplit(scene)
                    &&(decision.protection==Protection.NONE||decision.protection==Protection.MELEE))
                    decision=new TickProtection.Decision(f.tick,Protection.MELEE,
                        decision.dueMask|CombatPlanner.bit(Protection.MELEE),
                        decision.uncertainMask|CombatPlanner.bit(Protection.MELEE),
                        "Pure: pre-arm nearby blob children during split delay");
                boolean exposed=scene.mobs().stream().anyMatch(m->
                    (CombatPlanner.threats(scene.grid(),m,scene.player(),scene.jadStyle())
                        &CombatPlanner.bit(movementGuard))!=0);
                boolean jad=scene.mobs().stream().anyMatch(m->m.kind()==Kind.JAD);
                // Hidden Jad has no current attack demand, but approaching him
                // still requires a confirmed overhead. Otherwise movement waits
                // for Magic while this writer keeps cancelling it as a clear gap.
                // A real Jad wind-up always outranks the saved movement guard.
                if(!paused&&movementGuard!=Protection.NONE&&(movementPrearm||motion&&!exposed)
                    &&(jad?decision.jadProtection==Protection.NONE:
                        nativeWidgets?(decision.dueMask==0||decision.protection==movementGuard):
                            movementGuard==Protection.MAGIC||decision.protection!=Protection.MAGIC))
                    decision=new TickProtection.Decision(f.tick,movementGuard,CombatPlanner.bit(movementGuard),0,
                        "Pre-arm checked movement before entering attack range");
                if(nativeWidgets&&!conservation&&!paused&&scene.mobs().isEmpty()&&decision.protection==Protection.NONE
                    &&(observedMask&OVERHEADS)!=0) {
                    Protection retained=Protection.values()[Integer.numberOfTrailingZeros(observedMask&OVERHEADS)+1];
                    decision=new TickProtection.Decision(f.tick,retained,0,0,"Hold between waves until the next spawn guard");
                }
                // Native prayers never borrow the inventory/attack cursor. Their
                // independent writer continues during a supply or NPC click.
                optionalDeadline=tickReceivedAt+600_000_000L*(nativeWidgets?4:
                    protection.inputWindowTicks(scene,motion,decision.protection));
                prepareTarget=Protection.NONE;
                if(decision.jadReturnProtection!=Protection.NONE) {
                    prepareTarget=decision.jadReturnProtection;
                    optionalDeadline=Math.min(optionalDeadline,tickReceivedAt+600_000_000L);
                } else if(!paused&&!jad&&decision.protection!=Protection.NONE) {
                    Snapshot next=new Snapshot(f.tick+1,scene.player(),scene.grid(),scene.mobs(),
                        scene.runEnergy(),scene.running(),scene.weaponRange(),scene.jadStyle(),scene.meleeMode());
                    Protection following=(nativeWidgets?BetaTickProtection.choose(protection,next,motion,decision.protection,spawnGuard,pureTinyPrayers):
                        protection.choose(next,motion,decision.protection,spawnGuard)).protection;
                    if(following!=Protection.NONE&&following!=decision.protection)prepareTarget=following;
                }
            }
            // Jad's short healer gap uses the same early visible-switch budget.
            // Missing it retains the Jad guard instead of starting a late pair.
            if(decision.jadReturnProtection!=Protection.NONE
                &&(observedMask&OVERHEADS)!=CombatPlanner.bit(Protection.MELEE)
                &&now-tickReceivedAt>MAGIC_RELEASE_DEADLINE_NS) {
                decision=new TickProtection.Decision(f.tick,decision.jadReturnProtection,
                    CombatPlanner.bit(decision.jadReturnProtection),0,"Keep Jad guard: healer switch missed its input deadline");
                prepareTarget=Protection.NONE;queued.set(null);optionalDeadline=0;
            }
            // A late lower-priority click would consume the only time available
            // to get back to Magic. Keep Magic instead of starting that late pair.
            if(!nativeWidgets&&prepareTarget==Protection.MAGIC&&decision.protection!=Protection.MAGIC
                &&(observedMask&OVERHEADS)==CombatPlanner.bit(Protection.MAGIC)
                &&now-tickReceivedAt>MAGIC_RELEASE_DEADLINE_NS) {
                decision=new TickProtection.Decision(f.tick,Protection.MAGIC,decision.dueMask,decision.uncertainMask,
                    "Keep Magic: lower-priority switch missed its input deadline");
                prepareTarget=Protection.NONE;queued.set(null);optionalDeadline=0;
            }
            boolean offence=!paused&&offenceNeeded(f,useOffence,motion);
            desiredMask=(decision.protection==Protection.NONE?0:1<<(decision.protection.ordinal()-1))
                |(offence?selectedMask:0);
            // A style switch uses one ON action, never an OFF-old/ON-new
            // sequence. Stable native same-style flicks are separately gated.
            boolean jadPresent=scene.mobs().stream().anyMatch(m->m.kind()==Kind.JAD);
            boolean allowNativeReset=!motion&&!pendingJad&&!jadPresent&&!decision.conflict();
            List<OneTickPrayerCycle.Command> commands=nativeWidgets?
                nativeCycle.plan(now,observedMask,desiredMask,OVERHEADS,allowNativeReset,true):
                cycle.plan(now,observedMask,desiredMask,OVERHEADS,0,true);
            boolean missing=false;
            // Existence is planning evidence only. The background writer requires
            // the real Prayer tab and rechecks visible button bounds after travel.
            for(int i=0;i<slots.size();i++)if((desiredMask&(1<<i))!=0&&client.getWidget(slots.get(i).getIndex())==null)missing=true;
            for(OneTickPrayerCycle.Command command:commands) {
                if(queued.get()!=null)break;
                if(command.type!=OneTickPrayerCycle.Type.ON&&!(nativeWidgets?
                    nativeCycle.early(nanos.getAsLong()):cycle.early(nanos.getAsLong())))continue;
                if(missing&&command.type!=OneTickPrayerCycle.Type.ON)continue;
                if((skippedMask&(1<<command.channel))!=0&&command.type!=OneTickPrayerCycle.Type.ON)continue;
                Rs2PrayerEnum prayer=slots.get(command.channel);
                if(client.getWidget(prayer.getIndex())==null){missing=true;continue;}
                if(nativeWidgets) {
                    boolean reset=command.type==OneTickPrayerCycle.Type.RESET;
                    boolean on=command.type==OneTickPrayerCycle.Type.ON;
                    if(reset?FcNativePrayer.reset(client,prayer):FcNativePrayer.dispatch(client,prayer,on)) {
                        nativeCycle.sent(command,OVERHEADS);operations+=reset?2:1;if(reset)resets++;
                        log.accept("native-tick-prayer tick="+f.tick+" slot="+prayer.name()+" type="+command.type
                            +" forecast="+decision.protection+" due="+decision.dueMask
                            +" uncertain="+decision.uncertainMask+" elapsedMs="+(now-tickReceivedAt)/1_000_000L);
                    }
                    break;
                }
                queued.compareAndSet(null,new Dispatch(f,decision,command,tickReceivedAt));
            }
            int needed=desiredMask&OVERHEADS;
            ready=!pendingJad&&(nativeWidgets?nativeCycle.committed(observedMask,needed,OVERHEADS):
                needed==0||(observedMask&OVERHEADS)==needed&&cycle.canRelyOn(observedMask,needed));
            offenceReady=nativeWidgets?nativeCycle.observed(observedMask,selectedMask,selectedMask):
                cycle.canRelyOn(observedMask,selectedMask);
            String timingLabel=nativeWidgets?"Native widget":"Visible flick";
            status=missing?"Prayer widget missing; no flick":
                !(nativeWidgets?nativeCycle.synchronised():cycle.synchronised())?"Holding: tick sync / acknowledgement":
                skippedMask!=0?timingLabel+": travel missed window; prayer held":
                decision.conflict()?timingLabel+"; conflicting attack styles":
                decision.protection==Protection.NONE?timingLabel+"; overhead off in clear gap":timingLabel+": "+decision.protection;
            if(missing){cycle.failed();nativeCycle.failed();ready=offenceReady=false;problem("Selected prayer widget missing; protection must be checked in client");}
        } catch(RuntimeException|LinkageError e) {
            cycle.failed();nativeCycle.failed();ready=offenceReady=false;status="Prayer dispatch error; holding";
            problem(e.getClass().getSimpleName()+": "+e.getMessage());
        }
    }
    @Override
    protected boolean offenceNeeded(FcFrame f,boolean enabled,boolean motion) {
        if(!enabled||selectedMask==0||f.model.mobs().isEmpty())return false;
        if(motion)return false;
        if(primeAttackUntil>=f.tick) {
            Mob primed=find(f,primeTarget);
            if(conservation&&primed!=null&&!PrayerConservation.offence(true,true,primeTarget,f.model))return false;
            if(primed!=null&&(!conservation||f.interactingIndex<0||f.interactingIndex==primeTarget)
                &&PrayerConservation.offence(true,conservation,primeTarget,f.model)
                &&CombatPlanner.playerCanAttack(f.model,f.model.player(),primed)) {
                lastOffenceUseTick=f.tick;return true;
            }
        }
        Mob target=find(f,f.interactingIndex);
        if(target==null||!CombatPlanner.playerCanAttack(f.model,f.model.player(),target))
            return !conservation&&(observedMask&selectedMask)==selectedMask&&f.tick-lastOffenceUseTick<=2
                &&f.model.mobs().stream().anyMatch(m->CombatPlanner.playerCanAttack(f.model,f.model.player(),m));
        if(!PrayerConservation.offence(true,conservation,target.index(),f.model))return false;
        // Hold offence throughout actual combat, including Jad and mixed waves.
        // No attack-gap toggles or OFF/ON pairs compete with overhead deadlines.
        lastOffenceUseTick=f.tick;return true;
    }
    @Override
    /** Mouse movement and click-consumption waits never run on the client thread. */
    void pulse(FcActions actions) {
        if(nativeWidgets||detached)return;
        Dispatch dispatch=queued.get();
        if(dispatch==null) {
            FcFrame f=current;Protection next=prepareTarget;
            if(f!=null&&next!=Protection.NONE&&ready&&inputAllowed
                &&(preparedFrame!=f||preparedTarget!=next)) {
                if(actions.prepareTickPrayer(slots.get(next.ordinal()-1),()->!nativeWidgets&&owned&&inputAllowed&&ready
                    &&current==f&&!f.stale()&&queued.get()==null&&prepareTarget==next)) {
                    preparedFrame=f;preparedTarget=next;
                }
            }
            return;
        }
        FcPrayerUi.Result result=FcPrayerUi.Result.WAITING;
        long started=nanos.getAsLong();
        String failure=null;
        try {
            if(valid(dispatch))result=actions.tickPrayer(dispatch.command,slots.get(dispatch.command.channel),
                ()->valid(dispatch)&&early(dispatch),()->valid(dispatch));
        } catch(RuntimeException|LinkageError e) {failure=e.getClass().getSimpleName()+": "+e.getMessage();}
        final FcPrayerUi.Result completed=result;final String error=failure;
        final long completedAt=nanos.getAsLong();
        // Acknowledge on the same thread which owns the tick-cycle state. Queueing
        // a request does not count as a sent prayer or make protection ready.
        onClientThread(()->{
            if(completed==FcPrayerUi.Result.SENT) {
                operations+=dispatch.command.type==OneTickPrayerCycle.Type.RESET?2:1;
                if(dispatch.command.type==OneTickPrayerCycle.Type.RESET)resets++;
                log.accept("tick-prayer-input tick="+dispatch.frame.tick+" type="+dispatch.command.type
                    +" slot="+slots.get(dispatch.command.channel).name()+" visible=true next="+dispatch.decision.protection
                    +" due="+dispatch.decision.dueMask+" uncertain="+dispatch.decision.uncertainMask
                    +" startMs="+(started-dispatch.tickAt)/1_000_000L+" completedMs="+(completedAt-dispatch.tickAt)/1_000_000L
                    +" completedTick="+(current==null?-1:current.tick));
            }
            if(current!=dispatch.frame&&owned&&completed==FcPrayerUi.Result.SENT&&dispatch.command.channel<3) {
                cycle.failed();protection.invalidateCadence();ready=false;decision=null;optionalDeadline=0;
            } else if(current==dispatch.frame&&owned&&queued.get()==dispatch) {
                if(error!=null){cycle.failed();ready=offenceReady=false;problem(error);}
                else if(completed==FcPrayerUi.Result.SENT) {
                    cycle.sent(dispatch.command,OVERHEADS);
                } else if(completed==FcPrayerUi.Result.SKIPPED)skippedMask|=1<<dispatch.command.channel;
            }
            queued.compareAndSet(dispatch,null);return true;
        },false);
    }
    @Override
    protected boolean valid(Dispatch dispatch) {
        long age=nanos.getAsLong()-dispatch.tickAt;
        boolean wanted=(desiredMask&(1<<dispatch.command.channel))!=0;
        boolean matches=dispatch.command.type==OneTickPrayerCycle.Type.OFF?!wanted:wanted;
        boolean releaseMagic=dispatch.command.type==OneTickPrayerCycle.Type.ON&&dispatch.command.channel<2
            &&prepareTarget==Protection.MAGIC&&(observedMask&OVERHEADS)==CombatPlanner.bit(Protection.MAGIC);
        boolean healerGap=dispatch.command.type==OneTickPrayerCycle.Type.ON&&dispatch.command.channel==0
            &&dispatch.decision.jadReturnProtection!=Protection.NONE;
        return !nativeWidgets&&matches&&!detached&&owned&&inputAllowed&&!pendingJad&&!dispatch.frame.stale()
            &&current==dispatch.frame&&decision==dispatch.decision
            &&queued.get()==dispatch&&age>=0&&age<1_200_000_000L
            &&(!(releaseMagic||healerGap)||age<=MAGIC_RELEASE_DEADLINE_NS);
    }}
