/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape.core;

import java.util.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.Tile;

/** Bounded request/acknowledgement state for a single minimap destination (including multi-tile paths). */
public final class MovementAck {
    private final boolean expireUndispatchedRetry;
    public MovementAck(){this(false);}
    public MovementAck(boolean expireUndispatchedRetry){this.expireUndispatchedRetry=expireUndispatchedRetry;}
    public enum Result { NONE, WAITING, ARRIVED, PROGRESSED, RETRY_MINIMAP, FAILED, DEVIATED }
    private Tile from,to,last;
    private List<Tile> route=List.of();
    private int routeIndex;
    private int sentTick=-1,progressTick=-1,attempts;
    private Tile failedFrom,failedTo;
    private int failedTick=-1;
    private static final int TIMEOUT_TICKS=3,MAX_ATTEMPTS=3;
    private void clearPending(){from=to=last=null;route=List.of();routeIndex=0;sentTick=progressTick=-1;attempts=0;}
    public synchronized void reset(){clearPending();failedFrom=failedTo=null;failedTick=-1;}
    /** Exhausting retries must not immediately start another identical click loop. */
    public synchronized boolean coolingDown(Tile origin,Tile destination,int tick) {
        return failedRecently(origin,destination,tick)&&tick-failedTick<10;
    }
    public synchronized boolean failedRecently(Tile origin,Tile destination,int tick) {
        return origin!=null&&origin.equals(failedFrom)&&destination!=null&&destination.equals(failedTo)
            &&tick>=failedTick&&tick-failedTick<100;
    }
    public synchronized boolean pending(){return to!=null;}
    public synchronized Tile destination(){return to;}
    public synchronized int attempts(){return attempts;}
    public synchronized void sent(Tile origin,Tile destination,int tick) {
        boolean retry=destination.equals(to);
        if(!retry){from=origin;last=origin;attempts=0;route=List.of();routeIndex=0;}
        to=destination;sentTick=progressTick=tick;attempts++;
    }
    public synchronized void sent(Tile origin,Tile destination,int tick,List<Tile> path) {
        sent(origin,destination,tick);
        route=List.copyOf(path);routeIndex=Math.max(0,route.indexOf(origin));
    }
    public synchronized Result observe(Tile player,int tick) {
        if(to==null)return Result.NONE;
        if(tick<sentTick){reset();return Result.DEVIATED;}
        if(player.equals(to)){clearPending();return Result.ARRIVED;}
        if(!player.equals(last)) {
            int oldDistance=last.distance(to),distance=player.distance(to);
            // Curved routes can initially move away from the endpoint. A later tile
            // on the checked path is progress; request spam is never progress.
            int at=route.indexOf(player);
            // Equivalent shortest-path tie breaks are possible. Also accept a
            // closer tile on a multi-tile command; the caller checks its remaining
            // route against the current scene before letting the run continue.
            boolean progress=!route.isEmpty()?(at>routeIndex||distance<oldDistance):
                distance<oldDistance && player.distance(from)+distance==from.distance(to);
            if(progress) {
                if(at>=0)routeIndex=at;
                last=player;progressTick=tick;return Result.PROGRESSED;
            }
            reset();return Result.DEVIATED;
        }
        if(tick-progressTick<TIMEOUT_TICKS)return Result.WAITING;
        // A retry may never be dispatched: a stationary combat plan or prayer
        // handoff can supersede it. Pending movement must still expire, otherwise
        // it blocks both attacks and the stationary-combat watchdog forever.
        if(attempts>=MAX_ATTEMPTS||expireUndispatchedRetry&&tick-progressTick>=TIMEOUT_TICKS*MAX_ATTEMPTS){
            failedFrom=player;failedTo=to;failedTick=tick;clearPending();return Result.FAILED;
        }
        return Result.RETRY_MINIMAP;
    }
    public synchronized boolean waitingFor(Tile destination){return destination!=null&&destination.equals(to);}
}
