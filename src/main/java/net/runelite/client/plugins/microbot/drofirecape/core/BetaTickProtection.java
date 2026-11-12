/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape.core;

import java.util.*;

import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;

/** Next-server-tick flick selection: protection on for a due hit, off in a verified gap.
 * The captured event and snapshot share one tick label: chooseNative already looks ahead
 * by one; do not add another tick. */
public final class BetaTickProtection {
    private BetaTickProtection() { }
    static int contactMelee(Snapshot s,int due,int dueMask) {
        Set<Integer> contact=new HashSet<>();
        for(Mob m:s.mobs())if((m.kind()==Kind.MAGER||m.kind()==Kind.RANGER)&&s.grid().melee(m,s.player()))contact.add(m.index());
        if(contact.isEmpty())return due;
        for(Kind kind:new Kind[]{Kind.MAGER,Kind.RANGER}) {
            int style=CombatPlanner.bit(kind==Kind.MAGER?Protection.MAGIC:Protection.RANGE);
            boolean here=false,other=false;
            for(Mob m:s.mobs()) {
                if(contact.contains(m.index())){if(m.kind()==kind)here=true;continue;}
                if((CombatPlanner.threats(s.grid(),m,s.player(),s.jadStyle())&style)!=0)other=true;
            }
            if(here&&!other&&(dueMask&(style|CombatPlanner.bit(Protection.MELEE)))!=0)
                due=(due&~style)|CombatPlanner.bit(Protection.MELEE);
        }
        return due;
    }
    /** A big melee (Yt-MejKot) or a Ket-Zek beside the player: same-tick order Magic, then this, then Range. */
    static boolean bigContact(Snapshot s,Mob m) {
        // Standing under a monster is not contact: it cannot melee until it steps out (live 1.1.39 waves 49-50).
        return (m.kind()==Kind.MELEER||m.kind()==Kind.MAGER)&&!m.occupies(s.player())&&s.grid().melee(m,s.player());
    }
    public static TickProtection.Decision choose(TickProtection clock,Snapshot s,boolean moving,
                                                 Protection held,Protection spawnGuard) {
        return choose(clock,s,moving,held,spawnGuard,false);
    }
    public static TickProtection.Decision choose(TickProtection clock,Snapshot s,boolean moving,
                                                 Protection held,Protection spawnGuard,boolean pure) {
        TickProtection.Decision d=clock.chooseNative(s,moving,held,spawnGuard);
        if(s.mobs().isEmpty()||s.mobs().stream().anyMatch(m->m.kind()==Kind.JAD))return d;
        Protection selected=Protection.NONE;
        // Colosseum rule: only the most certain hits are weighed. A due style whose attack clock is known beats a style
        // that is due only because its clock is not calibrated yet (live 1.1.37 wave 38: an uncalibrated Ket-Zek held
        // Magic through the Tok-Xil's known shots on 185 and 189; their attacks were never on the same tick).
        int certain=d.knownMask&d.dueMask;
        // Colosseum Jaguar rule: a big melee in contact counts as certain even before its clock is known; so does a
        // Ket-Zek in contact, whose melee (max 55) is the biggest hit in the cave.
        // A Yt-MejKot in contact (max 25) never outranks a Ket-Zek's possible magic (max 52): Magic first (user, live
        // 1.1.39 waves 49-50: Melee held through Ket-Zek magic for 38 and 47). A Ket-Zek in contact always counts.
        boolean magicPossible=(d.dueMask&CombatPlanner.bit(Protection.MAGIC))!=0;
        if((d.dueMask&CombatPlanner.bit(Protection.MELEE))!=0&&s.mobs().stream().anyMatch(m->bigContact(s,m)
            &&(m.kind()==Kind.MAGER||!magicPossible)))
            certain|=CombatPlanner.bit(Protection.MELEE);
        int due=certain!=0?certain:d.dueMask;
        // A Tok-Xil or Ket-Zek beside the player only melees (OSRS Wiki: the Ket-Zek's melee, max 55, is triggered
        // when a player gets too close), also while running past it. Its ranged/magic demand becomes Melee unless
        // another monster can attack with that style (then the same-tick order applies). Live 1.1.38: a Tok-Xil
        // contact swing hit 13 after login and a Ket-Zek contact swing landed in wave 42.
        due=contactMelee(s,due,d.dueMask);
        // Keep the authoritative high-consequence ordering when attacks collide.
        // An adjacent ranger can still use either style; never fabricate a melee
        // clock or erase its ranged demand just because it is in contact.
        if((due&CombatPlanner.bit(Protection.MAGIC))!=0)selected=Protection.MAGIC;
        // Pure must retain the clock's next-step big-melee priority. Requiring
        // contact on this frame discards the very tick reserved for its approach.
        else if(pure&&d.protection==Protection.MELEE
            &&(due&CombatPlanner.bit(Protection.MELEE))!=0)selected=Protection.MELEE;
        else if((due&CombatPlanner.bit(Protection.MELEE))!=0&&s.mobs().stream().anyMatch(m->bigContact(s,m)))
            selected=Protection.MELEE;
        else if((due&CombatPlanner.bit(Protection.RANGE))!=0)selected=Protection.RANGE;
        else if((due&CombatPlanner.bit(Protection.MELEE))!=0)selected=Protection.MELEE;
        if(selected==Protection.NONE&&d.uncertainMask!=0) {
            int exposed=0;
            for(Mob mob:s.mobs())exposed|=CombatPlanner.threats(s.grid(),mob,s.player(),s.jadStyle());
            // An attack clock that is not yet calibrated may fire on any tick: hold a useful guard.
            if(held!=null&&(exposed&CombatPlanner.bit(held))!=0)selected=held;
            else if((exposed&CombatPlanner.bit(d.protection))!=0)selected=d.protection;
        }
        // A verified gap flicks the overhead off: it is switched on once before the next hit.
        return new TickProtection.Decision(s.tick(),selected,d.dueMask,d.uncertainMask,
            d.dueMask==0?(selected==Protection.NONE?"Flick: verified gap; protection off":"Flick: uncertain clock; hold guard"):
            "Flick: on for the next hit"+(d.conflict()?"; simultaneous styles require isolation":""));
    }
}
