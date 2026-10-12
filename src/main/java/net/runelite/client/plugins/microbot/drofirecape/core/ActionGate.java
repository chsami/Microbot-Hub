/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape.core;

/** Distinguishes an input dispatch from a server acknowledgement. */
public final class ActionGate {
    private long dispatchedAt;
    private int tick=-1;
    private String key="";
    public boolean ready(String request,long now,int currentTick,long minimumMs) {
        return currentTick!=tick&&(now-dispatchedAt>=minimumMs||!request.equals(key));
    }
    public void dispatched(String request,long now,int currentTick){key=request;dispatchedAt=now;tick=currentTick;}
    public void reset(){dispatchedAt=0;tick=-1;key="";}
}
