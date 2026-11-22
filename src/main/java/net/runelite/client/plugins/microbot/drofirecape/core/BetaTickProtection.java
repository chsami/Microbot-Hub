/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape.core;

import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;

/** Native next-server-tick selection. The captured event and snapshot share one
 * tick label: chooseNative already looks ahead by one; do not add another tick. */
public final class BetaTickProtection {
    private BetaTickProtection() { }
    public static TickProtection.Decision choose(TickProtection clock,Snapshot s,boolean moving,
                                                 Protection held,Protection spawnGuard) {
        return choose(clock,s,moving,held,spawnGuard,false);
    }
    public static TickProtection.Decision choose(TickProtection clock,Snapshot s,boolean moving,
                                                 Protection held,Protection spawnGuard,boolean pure) {
        TickProtection.Decision d=clock.chooseNative(s,moving,held,spawnGuard);
        if(s.mobs().isEmpty()||s.mobs().stream().anyMatch(m->m.kind()==Kind.JAD))return d;
        Protection selected=Protection.NONE;
        // Keep the authoritative high-consequence ordering when attacks collide.
        // An adjacent ranger can still use either style; never fabricate a melee
        // clock or erase its ranged demand just because it is in contact.
        if((d.dueMask&CombatPlanner.bit(Protection.MAGIC))!=0)selected=Protection.MAGIC;
        // Pure must retain the clock's next-step big-melee priority. Requiring
        // contact on this frame discards the very tick reserved for its approach.
        else if(pure&&d.protection==Protection.MELEE
            &&(d.dueMask&CombatPlanner.bit(Protection.MELEE))!=0)selected=Protection.MELEE;
        else if((d.dueMask&CombatPlanner.bit(Protection.MELEE))!=0&&s.mobs().stream().anyMatch(m->
            m.kind()==Kind.MELEER&&s.grid().melee(m,s.player())))selected=Protection.MELEE;
        else if((d.dueMask&CombatPlanner.bit(Protection.RANGE))!=0)selected=Protection.RANGE;
        else if((d.dueMask&CombatPlanner.bit(Protection.MELEE))!=0)selected=Protection.MELEE;
        if(selected==Protection.NONE) {
            int exposed=0;
            for(Mob mob:s.mobs())exposed|=CombatPlanner.threats(s.grid(),mob,s.player(),s.jadStyle());
            // In a verified gap, hold an already useful style. A same-tick
            // OFF/ON pair resets its drain without an asynchronous off period.
            if(held!=null&&(exposed&CombatPlanner.bit(held))!=0)selected=held;
            else if((exposed&CombatPlanner.bit(d.protection))!=0)selected=d.protection;
        }
        return new TickProtection.Decision(s.tick(),selected,d.dueMask,d.uncertainMask,
            d.dueMask==0?"Native: verified gap; retain useful guard for atomic flick":
            "Native: next-tick protection"+(d.conflict()?"; simultaneous styles require isolation":""));
    }
}
