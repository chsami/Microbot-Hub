/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import net.runelite.api.*;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.drofirecape.core.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

/**
 * The sole cave prayer writer for every run.
 * GameTick provides server evidence; ClientTick publishes at most one request.
 * The background prayer worker opens the book with F-keys and clicks its visible
 * button. Only a changed protection/offence state requests a click; an already
 * active overhead is not reset every tick while another style is approaching.
 */
class FcTickPrayers {
    protected static final Rs2PrayerEnum[] PRAYERS={
        Rs2PrayerEnum.PROTECT_MELEE,Rs2PrayerEnum.PROTECT_RANGE,Rs2PrayerEnum.PROTECT_MAGIC,
        Rs2PrayerEnum.SHARP_EYE,Rs2PrayerEnum.HAWK_EYE,Rs2PrayerEnum.EAGLE_EYE,
        Rs2PrayerEnum.DEAD_EYE,Rs2PrayerEnum.RIGOUR,Rs2PrayerEnum.BURST_STRENGTH,
        Rs2PrayerEnum.SUPERHUMAN_STRENGTH,Rs2PrayerEnum.ULTIMATE_STRENGTH,
        Rs2PrayerEnum.CLARITY_THOUGHT,Rs2PrayerEnum.IMPROVED_REFLEXES,
        Rs2PrayerEnum.INCREDIBLE_REFLEXES,Rs2PrayerEnum.CHIVALRY,Rs2PrayerEnum.PIETY};
    protected static final int OVERHEADS=7;
    protected static final long MAGIC_RELEASE_DEADLINE_NS=180_000_000L;
    protected final Client client;
    protected final Consumer<String> log;
    protected final LongSupplier nanos;
    protected final boolean melee;
    protected final ArrayList<Rs2PrayerEnum> slots=new ArrayList<>();
    protected final Map<Rs2PrayerEnum,Integer> channel=new EnumMap<>(Rs2PrayerEnum.class);
    protected final TickProtection protection;
    protected final OneTickPrayerCycle cycle;
    protected volatile FcFrame current;
    protected Snapshot currentModel;
    protected Protection spawnGuard=Protection.NONE;
    protected volatile TickProtection.Decision decision;
    protected volatile boolean owned,ready,offenceReady,forecastMoving,detached;
    protected volatile Protection movementGuard=Protection.NONE,prepareTarget=Protection.NONE;
    protected Protection forecastGuard=Protection.NONE;
    protected volatile boolean movementPrearm;
    protected volatile boolean recoveryControl;
    protected volatile Protection recoveryGuard=Protection.NONE;
    protected boolean forecastPrearm;
    protected volatile int confirmedOverhead;
    protected volatile FcFrame preparedFrame;
    protected volatile Protection preparedTarget=Protection.NONE;
    protected volatile String label="Detecting",status="Awaiting game ticks";
    protected volatile int primeAttackUntil=-1,primeTarget=-1,motionUntil=-1;
    protected int selectedMask;
    protected int lastOffenceUseTick=-1000;
    protected volatile int observedMask;
    protected volatile int desiredMask;
    protected volatile boolean pendingJad;
    protected boolean wasAllowed;
    protected long resets,operations;
    protected String lastProblem="";
    protected volatile boolean inputAllowed;
    protected long tickReceivedAt;
    protected volatile long optionalDeadline;
    protected int skippedMask;
    protected final AtomicReference<Dispatch> queued=new AtomicReference<>();
    protected static final class Dispatch {
        final FcFrame frame;
        final TickProtection.Decision decision;
        final OneTickPrayerCycle.Command command;
        final long tickAt;
        Dispatch(FcFrame frame,TickProtection.Decision decision,OneTickPrayerCycle.Command command,long tickAt) {
            this.frame=frame;this.decision=decision;this.command=command;this.tickAt=tickAt;
        }
    }

    FcTickPrayers(Client client,boolean melee,Consumer<String> log) {
        this(client,melee,log,System::nanoTime);
    }
    /** Clock injection is only for reproducible offline adapter tests. */
    FcTickPrayers(Client client,boolean melee,Consumer<String> log,LongSupplier nanos) {
        this(client,melee,log,nanos,false);
    }
    protected FcTickPrayers(Client client,boolean melee,Consumer<String> log,LongSupplier nanos,boolean optionalCadence) {
        protection=new TickProtection(optionalCadence);
        this.client=client;this.melee=melee;this.log=log;this.nanos=nanos;
        Map<Integer,Integer> byWidget=new LinkedHashMap<>();
        for(Rs2PrayerEnum p:PRAYERS) {
            Integer slot=byWidget.get(p.getIndex());
            if(slot==null){slot=slots.size();byWidget.put(p.getIndex(),slot);slots.add(p);}
            channel.put(p,slot);
        }
        cycle=new OneTickPrayerCycle(slots.size());
    }
    boolean ownsInput(){return owned&&!detached;}
    void recoveryPause(boolean controlled,Protection guard) {
        if(recoveryControl==controlled&&recoveryGuard==guard)return;
        if(!controlled&&recoveryControl&&recoveryGuard!=Protection.NONE)spawnGuard=recoveryGuard;
        recoveryControl=controlled;recoveryGuard=guard;decision=null;queued.set(null);ready=false;optionalDeadline=0;
        movementPrearm=false;movementGuard=prepareTarget=Protection.NONE;
    }
    boolean prayersOffReady(){return owned&&inputAllowed&&recoveryControl&&recoveryGuard==Protection.NONE
        &&current!=null&&!current.stale()&&current.model.mobs().isEmpty()&&desiredMask==0&&observedMask==0
        &&cycle.pendingMask()==0&&queued.get()==null;}
    boolean protectionReady(){return ownsInput()&&ready&&!pendingJad;}
    boolean offenceReady(){return ownsInput()&&offenceReady;}
    Protection requested(){TickProtection.Decision d=decision;return d==null?Protection.NONE:d.protection;}
    String selectedOffence(){return label;}
    String status(){return status;}
    /** Background input never reads or mutates the client-thread attack clocks. */
    boolean optionalInputWindow(int tick,long budgetMillis) {
        FcFrame f=current;long now=nanos.getAsLong();
        return owned&&ready&&inputAllowed&&f!=null&&f.tick==tick&&!f.stale()
            &&now>=tickReceivedAt&&now+budgetMillis*1_000_000L+100_000_000L<optionalDeadline;
    }
    /** A stationary attack may use the confirmed current tick even when a contact
     * monster's next style is uncertain. Longer inventory work still needs the
     * original forecast. Never borrow time across the next tick boundary. */
    boolean attackInputWindow(int tick,long budgetMillis) {
        FcFrame f=current;long now=nanos.getAsLong();
        if(optionalInputWindow(tick,budgetMillis))return true;
        return owned&&ready&&inputAllowed&&!pendingJad&&decision!=null&&f!=null
            &&f.tick==tick&&!f.stale()&&!f.moving&&!forecastMoving
            &&f.model.mobs().stream().noneMatch(m->m.kind()==Kind.JAD)
            &&now>=tickReceivedAt&&now+budgetMillis*1_000_000L+100_000_000L<tickReceivedAt+600_000_000L;
    }
    void primeAttack(int tick,int target){primeTarget=target;primeAttackUntil=Math.max(primeAttackUntil,tick+1);}
    void primeMovement(int tick) {
        primeMovement(tick,Protection.NONE);
    }
    void primeMovement(int tick,Protection guard) {
        FcFrame f=current;
        if(requested()==Protection.MAGIC&&f!=null&&f.model.mobs().stream().anyMatch(m->m.kind()==Kind.MAGER))
            guard=Protection.MAGIC;
        if(motionUntil<tick&&!forecastMoving)ready=false;
        if(!movementPrearm||movementGuard!=guard) {
            movementGuard=guard;movementPrearm=true;ready=false;
        }
        motionUntil=Math.max(motionUntil,tick+1);
    }
    boolean movementReady(Protection guard) {
        Protection actual=requested();int bit=CombatPlanner.bit(actual);
        return protectionReady()&&(guard==Protection.NONE||actual==guard||actual==Protection.MAGIC)
            &&(actual==Protection.NONE||confirmedOverhead==bit&&(observedMask&OVERHEADS)==bit);
    }
    void movementSent(){movementPrearm=false;}
    void npcAnimation(int index,Kind kind,int animation) {
        protection.animation(index,kind,animation);
        if(kind==Kind.JAD&&TickProtection.attackStyle(kind,animation)!=null){pendingJad=true;ready=false;}
    }
    void npcRemoved(int index){protection.removed(index);}
    void transition() {
        owned=ready=offenceReady=forecastMoving=wasAllowed=pendingJad=false;
        inputAllowed=false;queued.set(null);skippedMask=0;
        current=null;currentModel=null;decision=null;protection.reset();cycle.reset();
        optionalDeadline=0;
        movementGuard=forecastGuard=prepareTarget=preparedTarget=Protection.NONE;
        movementPrearm=forecastPrearm=false;confirmedOverhead=0;preparedFrame=null;
        primeAttackUntil=primeTarget=motionUntil=-1;
        lastOffenceUseTick=-1000;
        desiredMask=observedMask=selectedMask=0;status="Waiting for fresh scene";
    }
    void detach(){detached=true;owned=ready=offenceReady=inputAllowed=false;queued.set(null);}

    void gameTick(FcFrame f,Protection nextWaveGuard) {
        gameTick(f,nextWaveGuard,nanos.getAsLong());
    }
    void gameTick(FcFrame f,Protection nextWaveGuard,long receivedAtNanos) {
        gameTick(f,nextWaveGuard,receivedAtNanos,Set.of());
    }
    void gameTick(FcFrame f,Protection nextWaveGuard,long receivedAtNanos,Set<Integer> taggedHealers) {
        if(detached)return;
        if(f==null){owned=ready=offenceReady=inputAllowed=false;current=null;currentModel=null;queued.set(null);cycle.failed();status="No fresh scene; holding";return;}
        if(!f.cave){if(owned)transition();return;}
        if(current!=null&&!current.sameScene(f))transition();
        current=f;owned=true;ready=false;optionalDeadline=0;spawnGuard=nextWaveGuard;
        currentModel=prayerModel(f.model,taggedHealers);
        tickReceivedAt=receivedAtNanos;skippedMask=0;
        readState();
        confirmedOverhead=observedMask&OVERHEADS;
        cycle.beginTick(f.tick,receivedAtNanos,observedMask);
        if(!cycle.synchronised())protection.invalidateCadence();
        protection.beginTick(f.tick,currentModel.mobs());pendingJad=false;
        decision=null;
        log.accept("tick-prayer-evidence tick="+f.tick+" activeMask="+observedMask
            +" pendingMask="+cycle.pendingMask()+" sync="+cycle.synchronised()
            +" resetsSent="+resets+" operationsSent="+operations+" missedAck="+cycle.misses());
    }
    void clientTick(boolean allowed,boolean useOffence,boolean movementPending) {
        if(detached||!owned||current==null)return;
        inputAllowed=allowed;
        if(!allowed) {
            queued.set(null);
            ready=offenceReady=false;cycle.failed();protection.invalidateCadence();
            wasAllowed=false;status="Paused / input not owned";return;
        }
        if(!wasAllowed){protection.invalidateCadence();wasAllowed=true;}
        FcFrame f=current;Snapshot scene=currentModel;long now=nanos.getAsLong();
        if(f.stale()||!cycle.fresh(now)){ready=offenceReady=false;status="Stale tick; holding";return;}
        try {
            if(!client.isClientThread())throw new IllegalStateException("Tick prayers require the client thread");
            readState();
            if(client.getBoostedSkillLevel(Skill.PRAYER)<=0&&!(recoveryControl&&recoveryGuard==Protection.NONE&&observedMask==0)) {
                queued.set(null);ready=offenceReady=false;cycle.failed();status="Restore prayer";return;
            }
            boolean paused=recoveryControl&&scene.mobs().isEmpty();
            boolean motion=!paused&&(f.moving||movementPending||motionUntil>=f.tick);
            if(!motion){movementGuard=Protection.NONE;movementPrearm=false;}
            if(decision==null||motion!=forecastMoving||forecastGuard!=movementGuard||forecastPrearm!=movementPrearm) {
                forecastMoving=motion;decision=paused?new TickProtection.Decision(f.tick,recoveryGuard,CombatPlanner.bit(recoveryGuard),0,
                    recoveryGuard==Protection.NONE?"Confirmed recovery pause: prayers off":"Guard next wave before resume"):
                    protection.choose(scene,motion,requested(),spawnGuard);
                forecastGuard=movementGuard;forecastPrearm=movementPrearm;
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
                        movementGuard==Protection.MAGIC||decision.protection!=Protection.MAGIC))
                    decision=new TickProtection.Decision(f.tick,movementGuard,CombatPlanner.bit(movementGuard),0,
                        "Pre-arm checked movement before entering attack range");
                optionalDeadline=tickReceivedAt+600_000_000L*protection.inputWindowTicks(scene,motion,decision.protection);
                prepareTarget=Protection.NONE;
                if(decision.jadReturnProtection!=Protection.NONE) {
                    prepareTarget=decision.jadReturnProtection;
                    optionalDeadline=Math.min(optionalDeadline,tickReceivedAt+600_000_000L);
                } else if(!paused&&!jad&&decision.protection!=Protection.NONE) {
                    Snapshot next=new Snapshot(f.tick+1,scene.player(),scene.grid(),scene.mobs(),
                        scene.runEnergy(),scene.running(),scene.weaponRange(),scene.jadStyle(),scene.meleeMode());
                    Protection following=protection.choose(next,motion,decision.protection,spawnGuard).protection;
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
            if(prepareTarget==Protection.MAGIC&&decision.protection!=Protection.MAGIC
                &&(observedMask&OVERHEADS)==CombatPlanner.bit(Protection.MAGIC)
                &&now-tickReceivedAt>MAGIC_RELEASE_DEADLINE_NS) {
                decision=new TickProtection.Decision(f.tick,Protection.MAGIC,decision.dueMask,decision.uncertainMask,
                    "Keep Magic: lower-priority switch missed its input deadline");
                prepareTarget=Protection.NONE;queued.set(null);optionalDeadline=0;
            }
            boolean offence=!paused&&offenceNeeded(f,useOffence,motion);
            desiredMask=(decision.protection==Protection.NONE?0:1<<(decision.protection.ordinal()-1))
                |(offence?selectedMask:0);
            // Tick switches use one ON click on the new exclusive overhead.
            // Repeated OFF/ON pairs waste the same cursor time needed for switches.
            List<OneTickPrayerCycle.Command> commands=cycle.plan(now,observedMask,desiredMask,OVERHEADS,0,true);
            boolean missing=false;
            // Existence is planning evidence only. The background writer requires
            // the real Prayer tab and rechecks visible button bounds after travel.
            for(int i=0;i<slots.size();i++)if((desiredMask&(1<<i))!=0&&client.getWidget(slots.get(i).getIndex())==null)missing=true;
            for(OneTickPrayerCycle.Command command:commands) {
                if(queued.get()!=null)break;
                if(command.type!=OneTickPrayerCycle.Type.ON&&!cycle.early(nanos.getAsLong()))continue;
                if(missing&&command.type!=OneTickPrayerCycle.Type.ON)continue;
                if((skippedMask&(1<<command.channel))!=0&&command.type!=OneTickPrayerCycle.Type.ON)continue;
                Rs2PrayerEnum prayer=slots.get(command.channel);
                if(client.getWidget(prayer.getIndex())==null){missing=true;continue;}
                queued.compareAndSet(null,new Dispatch(f,decision,command,tickReceivedAt));
            }
            int needed=desiredMask&OVERHEADS;
            ready=!pendingJad&&(needed==0||(observedMask&OVERHEADS)==needed&&cycle.canRelyOn(observedMask,needed));
            offenceReady=cycle.canRelyOn(observedMask,selectedMask);
            status=missing?"Prayer widget missing; no flick":
                !cycle.synchronised()?"Holding: tick sync / acknowledgement":
                skippedMask!=0?"Visible flick: travel missed window; prayer held":
                decision.conflict()?"Visible flick; conflicting attack styles":
                decision.protection==Protection.NONE?"Visible flick; overhead off in clear gap":"Visible flick: "+decision.protection;
            if(missing){cycle.failed();problem("Selected prayer widget missing; protection must be checked in client");}
        } catch(RuntimeException|LinkageError e) {
            cycle.failed();ready=offenceReady=false;status="Prayer dispatch error; holding";
            problem(e.getClass().getSimpleName()+": "+e.getMessage());
        }
    }
    protected boolean offenceNeeded(FcFrame f,boolean enabled,boolean motion) {
        if(!enabled||selectedMask==0||f.model.mobs().isEmpty())return false;
        if(motion)return false;
        if(primeAttackUntil>=f.tick) {
            Mob primed=find(f,primeTarget);
            if(primed!=null&&CombatPlanner.playerCanAttack(f.model,f.model.player(),primed)) {
                lastOffenceUseTick=f.tick;return true;
            }
        }
        Mob target=find(f,f.interactingIndex);
        if(target==null||!CombatPlanner.playerCanAttack(f.model,f.model.player(),target))
            return (observedMask&selectedMask)==selectedMask&&f.tick-lastOffenceUseTick<=2
                &&f.model.mobs().stream().anyMatch(m->CombatPlanner.playerCanAttack(f.model,f.model.player(),m));
        // Hold offence throughout actual combat, including Jad and mixed waves.
        // No attack-gap toggles or OFF/ON pairs compete with overhead deadlines.
        lastOffenceUseTick=f.tick;return true;
    }
    protected static Mob find(FcFrame f,int index){for(Mob m:f.model.mobs())if(m.index()==index)return m;return null;}
    protected static Snapshot prayerModel(Snapshot scene,Set<Integer> taggedHealers) {
        if(taggedHealers.isEmpty())return scene;
        List<Mob> mobs=new ArrayList<>();
        for(Mob mob:scene.mobs())mobs.add(mob.kind()==Kind.HEALER&&taggedHealers.contains(mob.index())
            &&!mob.attackingPlayer()?new Mob(mob.index(),mob.kind(),mob.tile(),mob.size(),mob.healthRatio(),
                mob.healthScale(),mob.lastAttackTick(),mob.lastStyle(),true):mob);
        return new Snapshot(scene.tick(),scene.player(),scene.grid(),mobs,scene.runEnergy(),scene.running(),
            scene.weaponRange(),scene.jadStyle(),scene.meleeMode());
    }
    protected void readState() {
        observedMask=selectedMask=0;
        List<String> best=OffensivePrayers.select(melee,client.getRealSkillLevel(Skill.PRAYER),
            client.getRealSkillLevel(Skill.DEFENCE),client.getVarbitValue(VarbitID.KR_KNIGHTWAVES_STATE)==8,
            client.getVarbitValue(VarbitID.PRAYER_RIGOUR_UNLOCKED)>0,
            client.getVarbitValue(VarbitID.PRAYER_DEADEYE_UNLOCKED)>0);
        StringJoiner names=new StringJoiner(" + ");
        for(Rs2PrayerEnum p:PRAYERS) {
            int bit=1<<channel.get(p);
            if(client.getVarbitValue(p.getVarbit())==1)observedMask|=bit;
            if(best.contains(p.name())){selectedMask|=bit;names.add(p.getName());}
        }
        label=names.length()==0?"None available":names.toString();
    }
    /** Mouse movement and click-consumption waits never run on the client thread. */
    void pulse(FcActions actions) {
        if(detached)return;
        Dispatch dispatch=queued.get();
        if(dispatch==null) {
            FcFrame f=current;Protection next=prepareTarget;
            if(f!=null&&next!=Protection.NONE&&ready&&inputAllowed
                &&(preparedFrame!=f||preparedTarget!=next)) {
                if(actions.prepareTickPrayer(slots.get(next.ordinal()-1),()->owned&&inputAllowed&&ready
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
    protected boolean valid(Dispatch dispatch) {
        long age=nanos.getAsLong()-dispatch.tickAt;
        boolean wanted=(desiredMask&(1<<dispatch.command.channel))!=0;
        boolean matches=dispatch.command.type==OneTickPrayerCycle.Type.OFF?!wanted:wanted;
        boolean releaseMagic=dispatch.command.type==OneTickPrayerCycle.Type.ON&&dispatch.command.channel<2
            &&prepareTarget==Protection.MAGIC&&(observedMask&OVERHEADS)==CombatPlanner.bit(Protection.MAGIC);
        boolean healerGap=dispatch.command.type==OneTickPrayerCycle.Type.ON&&dispatch.command.channel==0
            &&dispatch.decision.jadReturnProtection!=Protection.NONE;
        return matches&&!detached&&owned&&inputAllowed&&!pendingJad&&!dispatch.frame.stale()
            &&current==dispatch.frame&&decision==dispatch.decision
            &&queued.get()==dispatch&&age>=0&&age<1_200_000_000L
            &&(!(releaseMagic||healerGap)||age<=MAGIC_RELEASE_DEADLINE_NS);
    }
    protected boolean early(Dispatch dispatch) {
        long age=nanos.getAsLong()-dispatch.tickAt;
        return age>=OneTickPrayerCycle.EARLY_START_NS&&age<=OneTickPrayerCycle.EARLY_END_NS;
    }
    protected void problem(String text) {if(!text.equals(lastProblem)){lastProblem=text;log.accept("tick-prayer-warning "+text);}}    void conservation(boolean enabled) { }
    void pureWave(int wave) { }
    void pureTinyPrayers(boolean enabled) { }
    void nativeWidgets(boolean enabled) { }
    boolean exhaustedCombatReady(){return false;}
    protected <T>T onClientThread(java.util.concurrent.Callable<T> action,T fallback) {
        return FcActions.read(action,fallback);
    }
}
