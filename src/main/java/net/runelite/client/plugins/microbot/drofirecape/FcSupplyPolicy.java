/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape;

import java.util.*;
import net.runelite.client.plugins.microbot.drofirecape.core.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;

/** Supply decisions from immutable observations; no input or prayer scheduling. */
final class FcSupplyPolicy {
    static boolean sweet(String name){return "Purple sweets".equalsIgnoreCase(name);}
    static boolean rangedPotion(String name) {
        String n=name.toLowerCase(Locale.ROOT);
        return n.matches("(ranging|bastion) potion\\([1-4]\\)");
    }
    static int rangedDoses(List<FcFrame.ItemSlot> inventory) {
        return inventory.stream().filter(i->rangedPotion(i.name()))
            .mapToInt(i->(i.name().charAt(i.name().length()-2)-'0')*i.quantity()).sum();
    }
    static boolean rangedDoseAllowed(int wave,int level,int base,int doses) {
        // Unknown resumed waves retain the stronger reserve until calibrated.
        int reserve=wave<=0||wave<53?3:wave<63?1:0;
        return level<=base&&doses>reserve;
    }
    static boolean rangedRecoveryReady(int tick,int lastBrewTick,boolean healingNeeded) {
        // Do not spend another boost in the middle of a brew batch. The caller
        // also requires undrained Ranged; reaching base does not need a restore.
        return !healingNeeded&&tick-lastBrewTick>=8;
    }
    static boolean sweetPauseSafe(Snapshot s) {
        if(s.mobs().stream().anyMatch(m->m.kind()==Kind.JAD||m.kind()==Kind.HEALER))return false;
        // A projectile launched before reaching cover can still be in flight.
        for(Mob m:s.mobs())if(m.kind().range>1&&m.lastAttackTick()>=0&&s.tick()-m.lastAttackTick()<8)return false;
        List<Mob> future=s.mobs();
        for(int tick=0;tick<12;tick++) {
            for(Mob m:future)if(CombatPlanner.threats(s.grid(),m,s.player(),s.jadStyle())!=0)return false;
            List<Mob> next=CombatPlanner.advance(s.grid(),future,s.player(),s.jadStyle());
            // Require a settled trap, not a distant monster currently approaching.
            for(int i=0;i<future.size();i++)if(!next.get(i).tile().equals(future.get(i).tile()))return false;
            future=next;
        }
        return true;
    }
    static boolean criticalHealth(Snapshot s,int hp,int max) {
        int hit=s.mobs().stream().filter(m->CaveSafety.active(s,m)).mapToInt(m->m.kind().maxHit).max().orElse(0);
        return hp*3<=max||hit>0&&hp<=hit+8;
    }
}
