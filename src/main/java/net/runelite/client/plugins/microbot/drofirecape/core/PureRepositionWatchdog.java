/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape.core;

import java.util.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;

/** Bounds Pure-only reposition waits, even when prayer acknowledgement blocks dispatch. */
public final class PureRepositionWatchdog {
    private int since=-1,lastTick=-1;
    private Tile player;
    private Map<Integer,Tile> positions=Collections.emptyMap();
    private Map<Integer,Integer> health=Collections.emptyMap();
    public void reset(){since=lastTick=-1;player=null;positions=Collections.emptyMap();health=Collections.emptyMap();}
    public boolean stalled(Snapshot s,Plan plan,boolean moving) {
        return stalled(s,plan,moving,-1);
    }
    public boolean stalled(Snapshot s,Plan plan,boolean moving,int interactingIndex) {
        if(s.tick()<lastTick)reset();
        if(s.mobs().isEmpty()||moving||plan==null||plan.targetIndex()>=0&&interactingIndex==plan.targetIndex()
            ||s.mobs().stream().anyMatch(m->m.kind()==Kind.JAD||m.kind()==Kind.HEALER)) {
            reset();return false;
        }
        Map<Integer,Tile> nextPositions=new HashMap<>();Map<Integer,Integer> nextHealth=new HashMap<>();
        for(Mob m:s.mobs()){nextPositions.put(m.index(),m.tile());nextHealth.put(m.index(),m.healthRatio());}
        // Approaching enemies can keep moving while dispatch is blocked. Their
        // movement is not acknowledgement of our route or shot. Only our own
        // displacement, a changed live roster or NPC health progress restarts it.
        if(since<0||!s.player().equals(player)||!nextPositions.keySet().equals(positions.keySet())||!nextHealth.equals(health))since=s.tick();
        player=s.player();positions=nextPositions;health=nextHealth;lastTick=s.tick();
        // Requests, repeated plans and incoming hits do not constitute progress.
        return s.tick()-since>=30;
    }
}
