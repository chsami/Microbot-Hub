/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape.core;

import java.util.*;

/** Pure selection policy. Names correspond to Microbot's Rs2PrayerEnum.
 * Unlock requirements are checked separately from active prayer points.
 */
public final class OffensivePrayers {
    private OffensivePrayers() { }
    public static List<String> select(boolean melee,int prayer,int defence,
                                      boolean knightWaves,boolean rigour,boolean deadeye) {
        if(!melee) {
            if(rigour&&prayer>=74&&defence>=70)return List.of("RIGOUR");
            if(deadeye&&prayer>=62)return List.of("DEAD_EYE");
            // An unlocked Deadeye replaces the Eagle Eye widget; never click it
            // pretending that the old prayer is still available.
            if(!deadeye&&prayer>=44)return List.of("EAGLE_EYE");
            if(prayer>=26)return List.of("HAWK_EYE");
            if(prayer>=8)return List.of("SHARP_EYE");
            return List.of();
        }
        if(knightWaves&&prayer>=70&&defence>=70)return List.of("PIETY");
        if(knightWaves&&prayer>=60&&defence>=65)return List.of("CHIVALRY");
        List<String> result=new ArrayList<>();
        if(prayer>=31)result.add("ULTIMATE_STRENGTH");
        else if(prayer>=13)result.add("SUPERHUMAN_STRENGTH");
        else if(prayer>=4)result.add("BURST_STRENGTH");
        if(prayer>=34)result.add("INCREDIBLE_REFLEXES");
        else if(prayer>=16)result.add("IMPROVED_REFLEXES");
        else if(prayer>=7)result.add("CLARITY_THOUGHT");
        return List.copyOf(result);
    }
}
