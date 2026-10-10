/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape.core;

/** Retain a selected item while required prayer input preempts its inventory tab. */
public final class SupplyPreparation {
    private static final long WINDOW_MS=6000;
    private volatile int item=-1;
    private volatile long started=-1;
    private long retryAt;
    public void reset(){item=-1;started=-1;retryAt=0;}
    public boolean pending(){return item>=0&&started>=0;}
    public int itemId(){return item;}
    /** Once Inventory preparation started, budget only the remaining item click. */
    public long inputBudgetMillis(){return pending()?300:900;}
    public boolean ready(int id,long now) {
        if(item!=id){reset();item=id;}
        if(now<retryAt)return false;
        if(started<0)started=now;
        return true;
    }
    public boolean waiting(long now){return started>=0&&now-started<WINDOW_MS;}
    public void defer(long now){started=-1;retryAt=now+500;}
}
