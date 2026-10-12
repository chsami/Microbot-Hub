/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape.core;

import java.util.HashSet;
import java.util.Set;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;

/** Observed healer aggro owns the whole tag, pull, then fight sequence. */
public final class HealerGroup {
    public enum Phase { IDLE, TAGGING, LURING, FIGHTING }
    public enum Ack { NONE, CONFIRMED, GONE, TIMED_OUT }
    private final Set<Integer> living=new HashSet<>(),confirmed=new HashSet<>();
    private Phase phase=Phase.IDLE;
    private int pending=-1,pendingTick=-1,allTaggedTick=-1,changedAt=-1,lastTick=-1;

    public synchronized void reset() {
        living.clear();confirmed.clear();phase=Phase.IDLE;
        pending=pendingTick=allTaggedTick=changedAt=lastTick=-1;
    }
    public synchronized Phase phase(){return phase;}
    public synchronized int pending(){return pending;}
    public synchronized int remaining(){return living.size()-confirmed.size();}
    public synchronized String progress(){return confirmed.size()+"/"+living.size();}
    public synchronized boolean confirmed(int index){return confirmed.contains(index);}
    public synchronized Set<Integer> confirmedIndices(){return Set.copyOf(confirmed);}
    public synchronized boolean fresh(int tick){return tick>changedAt&&tick>=lastTick;}

    public synchronized Ack observe(Snapshot scene,Set<Integer> targetingJad) {
        // An event can invalidate an index while the background loop still has
        // the previous frame. That frame must not confirm the replacement NPC.
        if(scene.tick()<=changedAt||scene.tick()<lastTick)return Ack.NONE;
        lastTick=scene.tick();
        Set<Integer> alive=new HashSet<>();
        boolean jad=false;
        for(Mob mob:scene.mobs()) {
            if(mob.kind()==Kind.HEALER)alive.add(mob.index());
            if(mob.kind()==Kind.JAD)jad=true;
        }
        if(!jad||alive.isEmpty()) {
            Ack result=pending>=0?Ack.GONE:Ack.NONE;
            living.clear();confirmed.clear();pending=pendingTick=allTaggedTick=-1;phase=Phase.IDLE;
            return result;
        }
        boolean newHealer=!living.containsAll(alive);
        living.clear();living.addAll(alive);confirmed.retainAll(alive);
        for(Mob mob:scene.mobs())if(mob.kind()==Kind.HEALER) {
            if(targetingJad.contains(mob.index()))confirmed.remove(mob.index());
            else if(mob.attackingPlayer())confirmed.add(mob.index());
        }
        Ack result=Ack.NONE;
        if(pending>=0) {
            if(!alive.contains(pending))result=Ack.GONE;
            else if(confirmed.contains(pending))result=Ack.CONFIRMED;
            else if(scene.tick()-pendingTick>=8)result=Ack.TIMED_OUT;
            if(result!=Ack.NONE){pending=-1;pendingTick=-1;}
        }
        if(newHealer||confirmed.size()!=living.size()||pending>=0) {
            phase=Phase.TAGGING;allTaggedTick=-1;
        } else if(phase==Phase.TAGGING||phase==Phase.IDLE) {
            // One fresh complete frame after the final acknowledgement also
            // catches another healer joining or returning before we commit.
            if(allTaggedTick<0)allTaggedTick=scene.tick();
            else if(scene.tick()>allTaggedTick)phase=Phase.LURING;
        }
        return result;
    }
    public synchronized void tagRequested(int index,int tick) {
        if(phase!=Phase.TAGGING||pending>=0||!living.contains(index)||confirmed.contains(index))return;
        pending=index;pendingTick=tick;allTaggedTick=-1;
    }
    public synchronized void spawned(int index,int tick) {
        invalidate(index,tick);living.add(index);phase=Phase.TAGGING;
    }
    public synchronized void removed(int index,int tick) {
        if(!living.contains(index)&&pending!=index)return;
        invalidate(index,tick);living.remove(index);
        if(living.isEmpty())phase=Phase.IDLE;
        else if(confirmed.size()!=living.size())phase=Phase.TAGGING;
    }
    private void invalidate(int index,int tick) {
        confirmed.remove(index);allTaggedTick=-1;changedAt=Math.max(changedAt,tick);
        if(pending==index){pending=-1;pendingTick=-1;}
    }
    public synchronized void lureComplete(){if(phase==Phase.LURING)phase=Phase.FIGHTING;}
}
