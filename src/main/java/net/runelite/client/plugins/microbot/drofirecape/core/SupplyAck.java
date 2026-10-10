/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape.core;

/** A successful UI call is not a consumed dose. Totals cover duplicate item slots. */
public final class SupplyAck {
    public enum Kind { OTHER, BREW, RESTORE }
    public enum Result { NONE, WAITING, CONSUMED, TIMED_OUT }
    private int itemId=-1,amount,tick;
    private Kind kind=Kind.OTHER;
    public boolean pending(){return itemId>=0;}
    public int itemId(){return itemId;}
    public Kind kind(){return kind;}
    public void reset(){itemId=-1;amount=0;tick=-1;kind=Kind.OTHER;}
    public void sent(int id,int count,int now,Kind type) {
        itemId=id;amount=count;tick=now;kind=type;
    }
    public Result observe(int liveCount,int now) {
        if(!pending())return Result.NONE;
        if(now>tick&&liveCount<amount)return Result.CONSUMED;
        return now<tick||now-tick>=5?Result.TIMED_OUT:Result.WAITING;
    }
}
