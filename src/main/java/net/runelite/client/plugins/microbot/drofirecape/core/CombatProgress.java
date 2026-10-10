/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape.core;

import java.util.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;

/** Player movement and click attempts cannot reset a last-monster progress deadline. */
public final class CombatProgress {
    private final Map<Integer,Integer> health=new HashMap<>();
    private int lastTick=-1,lastDamage=-1,lastMotion=-1,lastRecovery=-1000;
    private long motion;
    public void reset(){health.clear();lastTick=lastDamage=lastMotion=-1;lastRecovery=-1000;motion=0;}
    public void observe(Snapshot s) {
        if(s.tick()==lastTick)return;
        if(lastTick>s.tick())reset();
        Map<Integer,Integer> current=new HashMap<>();long nextMotion=0;
        for(Mob m:s.mobs()) {
            current.put(m.index(),m.healthRatio());
            nextMotion+=m.index()*10816L+m.tile().x()*104L+m.tile().y();
        }
        if(lastTick<0||!current.equals(health)){lastDamage=s.tick();health.clear();health.putAll(current);}
        if(lastTick<0||nextMotion!=motion){lastMotion=s.tick();motion=nextMotion;}
        lastTick=s.tick();
    }
    public boolean due(Snapshot s) {
        if(lastTick<0||s.mobs().isEmpty()||s.mobs().stream().anyMatch(m->m.kind()==Kind.JAD||m.kind()==Kind.HEALER))return false;
        return s.tick()-lastRecovery>=10 && (s.tick()-Math.max(lastDamage,lastMotion)>=10
            ||s.tick()-lastDamage>=30);
    }
    public void recovering(int tick){lastRecovery=tick;}
}
