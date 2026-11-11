/* Copyright (c) 2026, DRO (droplugins). SPDX-License-Identifier: BSD-2-Clause */
package net.runelite.client.plugins.microbot.drofirecape;

import java.util.Locale;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.Kind;

/**
 * Ranged weapon specials used in the cave (user, 2026-11-06); costs from the OSRS Wiki "Special attacks" table.
 * Energy is in the client's tenths (500 = 50%).
 * The toxic blowpipe heals, so it is kept for missing HP; the rosewood blowpipe (Colosseum: 25%, no heal) specs on any
 * target; the others are kept for monsters worth the energy, never bats, split blobs or healers.
 */
final class FcSpecials {
    private FcSpecials(){}
    /** Special energy cost, or -1 when the weapon's special is not used. */
    static int cost(String weapon) {
        if(weapon==null)return -1;
        String w=weapon.toLowerCase(Locale.ROOT);
        if(w.contains("rosewood blowpipe"))return 250;
        if(w.contains("blowpipe"))return 500;
        if(w.contains("magic shortbow (i)"))return 500;
        if(w.contains("magic shortbow"))return 550;
        if(w.contains("zaryte crossbow"))return 750;
        if(w.contains("armadyl crossbow"))return 500;
        if(w.contains("dragon crossbow"))return 600;
        return -1;
    }
    /** The toxic (or blazing) blowpipe special heals half its damage. */
    static boolean heals(String weapon) {
        String w=weapon==null?"":weapon.toLowerCase(Locale.ROOT);
        return w.contains("blowpipe")&&!w.contains("rosewood");
    }
    /** Whether this target is worth the weapon's special. */
    static boolean worth(String weapon,Kind target) {
        String w=weapon==null?"":weapon.toLowerCase(Locale.ROOT);
        if(w.contains("blowpipe"))return true;
        return target!=Kind.BAT&&target!=Kind.BABY&&target!=Kind.HEALER;
    }
}
