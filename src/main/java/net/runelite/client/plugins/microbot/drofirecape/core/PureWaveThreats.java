/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape.core;

import java.util.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;

/** Per-wave deaths and the delayed medium-blob split; never creates a live ranged threat. */
public final class PureWaveThreats {
    private int wave=-1,splitUntil=-1;
    private final Map<Integer,Mob> previous=new HashMap<>();
    private final Set<Kind> defeated=EnumSet.noneOf(Kind.class);
    private Mob splitting;
    public void reset(){wave=splitUntil=-1;previous.clear();defeated.clear();splitting=null;}
    public void observe(int currentWave,Snapshot s) {
        if(wave!=currentWave){reset();wave=currentWave;}
        Set<Integer> present=new HashSet<>();
        for(Mob m:s.mobs())present.add(m.index());
        for(Mob old:previous.values())if(!present.contains(old.index())) {
            defeated.add(old.kind());
            if(old.kind()==Kind.BLOB){splitting=old;splitUntil=s.tick()+4;}
        }
        if(s.mobs().stream().anyMatch(m->m.kind()==Kind.BABY)||s.tick()>splitUntil) {
            splitting=null;splitUntil=-1;
        }
        previous.clear();for(Mob m:s.mobs())previous.put(m.index(),m);
    }
    public boolean defeated(Kind kind){return defeated.contains(kind);}
    public boolean nearbySplit(Snapshot s) {
        return splitting!=null&&s.tick()<=splitUntil&&splitting.distance(s.player())<=2;
    }
    /** Empty capture during a split is not permission to pre-arm the next wave.
     * Protect imminent nearby babies; a remote split needs no overhead yet. */
    public Protection spawnGuard(Snapshot s,Protection nextWave) {
        if(!s.mobs().isEmpty()||splitting==null||s.tick()>splitUntil)return nextWave;
        return splitting.distance(s.player())<=2?Protection.MELEE:Protection.NONE;
    }
}
