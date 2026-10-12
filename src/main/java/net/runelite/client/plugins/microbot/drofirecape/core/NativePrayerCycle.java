/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape.core;

import java.util.*;
import net.runelite.client.plugins.microbot.drofirecape.core.OneTickPrayerCycle.Command;
import net.runelite.client.plugins.microbot.drofirecape.core.OneTickPrayerCycle.Type;

/** Client-thread-owned native input ledger; synchronized reads also serve the
 * background movement worker. Attack-clock confidence is separate.
 * Protection switches supersede older overhead intents; implicit deactivation
 * does not lock all three exclusive channels for several ticks. */
public final class NativePrayerCycle {
    public static final long RESET_START_NS=20_000_000L,RESET_END_NS=160_000_000L;
    private final int channels,allMask;
    private final int[] sentTick;
    private int tick=-1,stable,pending,expected,sentThisTick,misses;
    private long tickAt=-1;
    private boolean failed;
    public NativePrayerCycle(int channels) {
        if(channels<3||channels>30)throw new IllegalArgumentException("Prayer slots must be 3..30");
        this.channels=channels;allMask=(1<<channels)-1;sentTick=new int[channels];reset();
    }
    public synchronized void reset() {
        tick=-1;tickAt=-1;stable=pending=expected=sentThisTick=misses=0;failed=false;
        Arrays.fill(sentTick,-1);
    }
    public synchronized void beginTick(int next,long now,int observed) {
        if(next==tick)return;
        long interval=tickAt<0?-1:now-tickAt;
        stable=tick>=0&&next==tick+1&&interval>=450_000_000L&&interval<=750_000_000L&&!failed?stable+1:0;
        failed=false;tick=next;tickAt=now;sentThisTick=0;
        acknowledge(observed);
        for(int i=0;i<channels;i++)if((pending&(1<<i))!=0&&tick-sentTick[i]>=2) {
            pending&=~(1<<i);stable=0;misses++;
        }
    }
    public synchronized void acknowledge(int observed) {
        pending&=(observed^expected)&allMask;
    }
    public synchronized void failed(){failed=true;stable=0;}
    public synchronized boolean synchronised(){return stable>=2&&!failed;}
    public synchronized boolean fresh(long now){return tick>=0&&now>=tickAt&&now-tickAt<1_200_000_000L;}
    public synchronized boolean early(long now){return fresh(now)&&now-tickAt>=RESET_START_NS&&now-tickAt<=RESET_END_NS;}
    public synchronized int pendingMask(){return pending;}
    public synchronized int misses(){return misses;}

    /** Returns ordered work; the adapter replans after each successful dispatch.
     * No RESET is permitted while any state change is outstanding. */
    public synchronized List<Command> plan(long now,int observed,int desired,int overheadMask,boolean allowReset,boolean allowOff) {
        if(!fresh(now))return Collections.emptyList();
        desired&=allMask;observed&=allMask;acknowledge(observed);
        ArrayList<Command> commands=new ArrayList<>();
        int wanted=desired&overheadMask;
        if(wanted!=0) {
            int i=Integer.numberOfTrailingZeros(wanted),bit=1<<i;
            if((observed&bit)==0&&(pending&bit)==0&&(sentThisTick&bit)==0)
                commands.add(new Command(i,Type.ON));
        }
        // Never explicitly switch OFF an old overhead before selecting a new one.
        // Standalone OFF is restricted to confirmed clear/recovery conditions.
        if(allowOff&&early(now)&&synchronised())for(int i=0;i<channels;i++) {
            int bit=1<<i;
            if((observed&bit)==0||(desired&bit)!=0||(pending&bit)!=0||(sentThisTick&bit)!=0)continue;
            if((overheadMask&bit)!=0&&wanted!=0)continue;
            commands.add(new Command(i,Type.OFF));
        }
        // Disable a conflicting offensive slot first; otherwise activating its
        // replacement can implicitly disable it and a later OFF toggles it ON.
        boolean oldOffence=(observed&~desired&~overheadMask)!=0;
        if(!oldOffence)for(int i=0;i<channels;i++) {
            int bit=1<<i;
            if((overheadMask&bit)==0&&(desired&bit)!=0&&(observed&bit)==0
                &&(pending&bit)==0&&(sentThisTick&bit)==0)commands.add(new Command(i,Type.ON));
        }
        if(commands.isEmpty()&&allowReset&&early(now)&&synchronised()&&pending==0
            &&(observed&overheadMask)==wanted)for(int i=0;i<channels;i++) {
            int bit=1<<i;
            if((desired&observed&bit)!=0&&(sentThisTick&bit)==0)commands.add(new Command(i,Type.RESET));
        }
        return commands;
    }
    public synchronized void sent(Command command,int overheadMask) {
        int bit=1<<command.channel;
        if(command.type==Type.ON&&(bit&overheadMask)!=0) {
            pending&=~overheadMask;expected&=~overheadMask;
        }
        sentThisTick|=bit;sentTick[command.channel]=tick;
        if(command.type==Type.RESET)return; // paired input ends ON; no redundant acknowledgement gate
        pending|=bit;
        if(command.type==Type.OFF)expected&=~bit;else expected|=bit;
    }
    /** Observed protection, or an ordered ON queued on this client tick. A sent
     * intent is explicitly NOT called a server acknowledgement. Missing evidence
     * on a later tick revokes this permission instead of freezing attack clocks. */
    public synchronized boolean committed(int observed,int wanted,int overheadMask) {
        if(failed)return false;
        if(wanted==0)return (observed&overheadMask)==0&&(pending&overheadMask)==0;
        int i=Integer.numberOfTrailingZeros(wanted);
        boolean conflicting=(pending&overheadMask&expected&~wanted)!=0
            ||(pending&wanted&~expected)!=0;
        return !conflicting&&((observed&overheadMask)==wanted
            ||(pending&expected&wanted)==wanted&&sentTick[i]==tick);
    }
    public synchronized boolean observed(int actual,int desired,int mask) {
        return ((actual^desired)&mask)==0&&(pending&mask&~expected)==0;
    }
}
