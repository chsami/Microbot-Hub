/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape.core;

/** Per-prayer acknowledgement guard for non-idempotent toggle menu operations. */
public final class PrayerSwitchGuard {
    private final boolean[] pending,expected;
    private final int[] sentTick;
    private final long[] sentAt;
    public PrayerSwitchGuard(){this(4);}
    public PrayerSwitchGuard(int channels){
        if(channels<1)throw new IllegalArgumentException("Prayer channels");
        pending=new boolean[channels];expected=new boolean[channels];
        sentTick=new int[channels];sentAt=new long[channels];
    }
    public synchronized void cancel(int channel){pending[channel]=false;}
    public synchronized void reset(){java.util.Arrays.fill(pending,false);}
    public synchronized void acknowledge(int channel,boolean observed) {
        if(channel<0||channel>=pending.length)throw new IllegalArgumentException("Prayer channel");
        if(pending[channel]&&observed==expected[channel])pending[channel]=false;
    }
    public synchronized boolean shouldToggle(int channel,boolean observed,boolean desired,int tick,long now) {
        if(channel<0||channel>=pending.length)throw new IllegalArgumentException("Prayer channel");
        if(pending[channel]) {
            if(observed==expected[channel])pending[channel]=false;
            else if(tick-sentTick[channel]<3||now-sentAt[channel]<1800)return false;
            else pending[channel]=false; // timed-out/lost input; retry only from actual observed state
        }
        if(observed==desired)return false;
        pending[channel]=true;expected[channel]=desired;sentTick[channel]=tick;sentAt[channel]=now;
        return true;
    }
    public synchronized boolean settled(int channel,boolean observed,boolean desired) {
        if(pending[channel]&&observed==expected[channel])pending[channel]=false;
        return !pending[channel]&&observed==desired;
    }
}
