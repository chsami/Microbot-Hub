/* Copyright (c) 2026, DRO (droplugins). SPDX-License-Identifier: BSD-2-Clause */
package net.runelite.client.plugins.microbot.drofirecape.core;

import java.util.*;

/**
 * Prayer flick ledger: one ON before a hit, one OFF after it. A prayer still wanted on a later
 * tick is held with one early same-tick OFF/ON RESET of every active slot (1-tick flicking), so a
 * hold never drains. Client-thread owned; synchronized reads also serve the movement worker.
 * A protection switch is a single ON of the new exclusive overhead. Masks use unique
 * widget slots (Eagle Eye/Deadeye and Mystic Might/Vigour are one slot each).
 */
public final class PrayerLedger {
    public enum Type { ON, OFF, RESET }
    public static final class Command {
        /** RESET: the lowest slot of mask; mask lists every active slot it toggles. */
        public final int channel,mask;public final Type type;
        public Command(int channel,Type type){this(channel,type,1<<channel);}
        public Command(int channel,Type type,int mask){this.channel=channel;this.type=type;this.mask=mask;}
        @Override public String toString(){return type+"["+(type==Type.RESET?"mask="+mask:channel)+"]";}
    }
    /** OFF is only sent early in a tick so it is acknowledged before the next hit's ON. */
    public static final long OFF_START_NS=0L,OFF_END_NS=300_000_000L;
    /** Both RESET halves must reach the server in the same tick. */
    public static final long RESET_START_NS=20_000_000L,RESET_END_NS=160_000_000L;
    private final int channels,allMask;
    private final int[] sentTick;
    private int tick=-1,stable,pending,expected,sentThisTick,misses;
    private long tickAt=-1;
    private boolean failed;
    public PrayerLedger(int channels) {
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
    public synchronized void acknowledge(int observed){pending&=(observed^expected)&allMask;}
    public synchronized void failed(){failed=true;stable=0;}
    public synchronized boolean synchronised(){return stable>=2&&!failed;}
    public synchronized boolean fresh(long now){return tick>=0&&now>=tickAt&&now-tickAt<1_200_000_000L;}
    public synchronized boolean early(long now){return fresh(now)&&now-tickAt>=OFF_START_NS&&now-tickAt<=OFF_END_NS;}
    public synchronized boolean resetWindow(long now){return fresh(now)&&now-tickAt>=RESET_START_NS&&now-tickAt<=RESET_END_NS;}
    public synchronized int pendingMask(){return pending;}
    public synchronized int misses(){return misses;}

    /** Ordered work without holds being reset; the adapter replans after each dispatch. */
    public synchronized List<Command> plan(long now,int observed,int desired,int overheadMask) {
        return plan(now,observed,desired,overheadMask,0);
    }
    /** Ordered work; a hold of at most resetSlots active slots is 1-tick reset once per tick. */
    public synchronized List<Command> plan(long now,int observed,int desired,int overheadMask,int resetSlots) {
        if(!fresh(now))return Collections.emptyList();
        desired&=allMask;observed&=allMask;acknowledge(observed);
        ArrayList<Command> commands=new ArrayList<>();
        int wanted=desired&overheadMask;
        if(wanted!=0) {
            int i=Integer.numberOfTrailingZeros(wanted),bit=1<<i;
            if((observed&bit)==0&&(pending&bit)==0&&(sentThisTick&bit)==0)
                commands.add(new Command(i,Type.ON));
        }
        // Flick off once the hit or shot has passed. A new overhead replaces the old
        // one in game, so an exclusive overhead is never switched OFF before another ON.
        if(early(now)&&synchronised())for(int i=0;i<channels;i++) {
            int bit=1<<i;
            if((observed&bit)==0||(desired&bit)!=0||(pending&bit)!=0||(sentThisTick&bit)!=0)continue;
            if((overheadMask&bit)!=0&&wanted!=0)continue;
            commands.add(new Command(i,Type.OFF));
        }
        // Disable a conflicting offensive slot first; activating its replacement can
        // implicitly disable it and a later OFF would toggle it back ON.
        boolean oldOffence=(observed&~desired&~overheadMask)!=0;
        if(!oldOffence)for(int i=0;i<channels;i++) {
            int bit=1<<i;
            if((overheadMask&bit)==0&&(desired&bit)!=0&&(observed&bit)==0
                &&(pending&bit)==0&&(sentThisTick&bit)==0)commands.add(new Command(i,Type.ON));
        }
        // Resetting one prayer while another stays active does not stop drain: every active slot is
        // toggled, and only when the active set is exactly what is wanted and nothing is in flight.
        if(commands.isEmpty()&&observed!=0&&Integer.bitCount(observed)<=resetSlots&&(observed&~desired)==0
            &&pending==0&&(sentThisTick&observed)==0&&resetWindow(now)&&synchronised())
            commands.add(new Command(Integer.numberOfTrailingZeros(observed),Type.RESET,observed));
        return commands;
    }
    public synchronized void sent(Command command,int overheadMask) {
        // A RESET ends ON: no acknowledgement is awaited, it is only sent once per tick.
        if(command.type==Type.RESET){sentThisTick|=command.mask;return;}
        int bit=1<<command.channel;
        if(command.type==Type.ON&&(bit&overheadMask)!=0){pending&=~overheadMask;expected&=~overheadMask;}
        sentThisTick|=bit;sentTick[command.channel]=tick;
        pending|=bit;
        if(command.type==Type.OFF)expected&=~bit;else expected|=bit;
    }
    /** Observed protection, or an ON sent on this tick. A sent intent is not a server
     * acknowledgement; missing evidence on a later tick revokes this permission. */
    public synchronized boolean committed(int observed,int wanted,int overheadMask) {
        if(failed)return false;
        // No hit due: the flick may still be switching off, which never needs to block input.
        if(wanted==0)return true;
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
