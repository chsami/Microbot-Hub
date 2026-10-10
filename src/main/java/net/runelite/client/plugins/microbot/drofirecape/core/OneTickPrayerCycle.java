/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape.core;

import java.util.*;

/**
 * One early OFF/ON pair per selected prayer per observed server tick. No timers
 * free-run between GameTick events, and no late catch-up OFF is ever emitted.
 * The adapter must send BOTH halves of RESET in one background input operation;
 * travel must finish before OFF, and ON must repair an already-started pair.
 * Masks use unique widget slots (Eagle Eye/Deadeye are one slot).
 */
public final class OneTickPrayerCycle {
    public enum Type { ON, OFF, RESET }
    public static final class Command {
        public final int channel;public final Type type;
        public Command(int channel,Type type){this.channel=channel;this.type=type;}
        @Override public String toString(){return type+"["+channel+"]";}
    }
    public static final long EARLY_START_NS=20_000_000L, EARLY_END_NS=180_000_000L;
    private final int channels,allMask;
    private final int[] sentTick;
    private int tick=-1,stable,expected,pending,sentThisTick,misses;
    private long tickAt=-1;
    private boolean failed;
    public OneTickPrayerCycle(int channels) {
        if(channels<1||channels>30)throw new IllegalArgumentException("Prayer slots must be 1..30");
        this.channels=channels;allMask=(1<<channels)-1;sentTick=new int[channels];
    }
    public void reset(){tick=-1;tickAt=-1;stable=expected=pending=sentThisTick=misses=0;failed=false;Arrays.fill(sentTick,-1);}
    public void beginTick(int nextTick,long now,int observed) {
        if(nextTick==tick)return;
        long interval=tick<0?-1:now-tickAt;
        stable=tick>=0&&nextTick==tick+1&&interval>=450_000_000L&&interval<=750_000_000L
            &&!failed?stable+1:0;
        failed=false;tick=nextTick;tickAt=now;sentThisTick=0;
        for(int i=0;i<channels;i++)if((pending&(1<<i))!=0) {
            boolean actual=(observed&(1<<i))!=0,wanted=(expected&(1<<i))!=0;
            if(actual==wanted)pending&=~(1<<i);
            else if(tick-sentTick[i]>=2){pending&=~(1<<i);stable=0;misses++;}
            else stable=0; // acknowledgement uncertainty: hold, do not keep flicking
        }
    }
    public boolean synchronised(){return stable>=2&&!failed;}
    public boolean fresh(long now){return tick>=0&&now-tickAt>=0&&now-tickAt<1_200_000_000L;}
    public boolean early(long now){long phase=now-tickAt;return fresh(now)&&phase>=EARLY_START_NS&&phase<=EARLY_END_NS;}
    public int misses(){return misses;}
    public int pendingMask(){return pending;}
    public int tick(){return tick;}
    public void failed(){failed=true;stable=0;}

    public List<Command> plan(long now,int observed,int desired,int overheadMask,boolean allowed) {
        return plan(now,observed,desired,overheadMask,allMask,allowed);
    }
    /** Held offensive prayers do not spend cursor time on redundant resets. */
    public List<Command> plan(long now,int observed,int desired,int overheadMask,int resetMask,boolean allowed) {
        if(!allowed||!fresh(now))return Collections.emptyList();
        desired&=allMask;observed&=allMask;
        long phase=now-tickAt;
        boolean early=phase>=EARLY_START_NS&&phase<=EARLY_END_NS;
        boolean mayReset=early&&synchronised()&&pending==0;
        ArrayList<Command> commands=new ArrayList<>();
        int needOn=desired&~observed&~pending&~sentThisTick;
        // Protection first. Activating its exclusive slot implicitly replaces the
        // old overhead; never send a separate OFF for the old style on that switch.
        int desiredOverhead=desired&overheadMask;
        for(int i=0;i<channels;i++)if((needOn&overheadMask&(1<<i))!=0)commands.add(new Command(i,Type.ON));
        ArrayList<Command> releases=new ArrayList<>(),resets=new ArrayList<>();
        for(int i=0;i<channels;i++) {
            int bit=1<<i;if((pending&bit)!=0||(sentThisTick&bit)!=0||(observed&bit)==0)continue;
            if((desired&bit)==0) {
                if((overheadMask&bit)!=0&&desiredOverhead!=0)continue;
                // An isolated OFF also needs stable, early tick evidence. On a
                // lag spike retain the prayer rather than risk a late release.
                if(early&&synchronised())releases.add(new Command(i,Type.OFF));
            } else if(mayReset&&(resetMask&bit)!=0)resets.add(new Command(i,Type.RESET));
        }
        // Offensive prayers also have exclusions. Disable unwanted old slots
        // BEFORE activating their replacement: doing it afterwards would toggle
        // an implicitly-disabled old prayer back ON and undo the new selection.
        commands.addAll(releases);
        for(int i=0;i<channels;i++)if((needOn&~overheadMask&(1<<i))!=0)commands.add(new Command(i,Type.ON));
        commands.addAll(resets);
        return commands;
    }
    /** Called ONLY after dispatch. A thrown/rejected dispatch must call failed(). */
    public void sent(Command c,int overheadMask) {
        int bit=1<<c.channel;
        if(c.type==Type.ON&&(bit&overheadMask)!=0) {
            // Track the implicit deactivation as well as the new protection.
            expected&=~overheadMask;pending|=overheadMask;sentThisTick|=overheadMask;
            for(int i=0;i<channels;i++)if((overheadMask&(1<<i))!=0)sentTick[i]=tick;
        }
        pending|=bit;sentThisTick|=bit;sentTick[c.channel]=tick;
        if(c.type==Type.OFF)expected&=~bit;else expected|=bit;
    }
    /** A reset ends ON, so it must not stall attacks/movement awaiting a redundant
     * acknowledgement. A queued OFF must not masquerade as a still-active prayer. */
    public boolean canRelyOn(int observed,int mask) {
        return (observed&mask)==mask&&(pending&mask&~expected)==0;
    }
    public boolean settled(int observed,int desired,int mask) {
        return (pending&mask)==0&&((observed^desired)&mask)==0;
    }
}
