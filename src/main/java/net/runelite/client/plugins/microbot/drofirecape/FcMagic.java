/* Copyright (c) 2026, DRO (droplugins). SPDX-License-Identifier: BSD-2-Clause */
package net.runelite.client.plugins.microbot.drofirecape;

import java.util.Locale;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.util.magic.Rs2CombatSpells;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;

/**
 * Magic weapons come from the Inventory Setup; no configuration is needed. Powered staves cast their
 * built-in spell. Staves, wands and sceptres keep a spell the player already set to autocast; otherwise
 * the best castable Ice (Ancient) or Water (standard) spell is selected.
 */
final class FcMagic {
    enum Weapon { NONE, POWERED, AUTOCAST }
    /** Ancient best first, then the standard Water spells. */
    static final Rs2CombatSpells[] ANCIENT={Rs2CombatSpells.ICE_BARRAGE,Rs2CombatSpells.ICE_BLITZ,
        Rs2CombatSpells.ICE_BURST,Rs2CombatSpells.ICE_RUSH};
    static final Rs2CombatSpells[] STANDARD={Rs2CombatSpells.WATER_SURGE,Rs2CombatSpells.WATER_WAVE,
        Rs2CombatSpells.WATER_BLAST,Rs2CombatSpells.WATER_BOLT,Rs2CombatSpells.WATER_STRIKE};
    private FcMagic(){}

    /** Powered staves cast their own spell; every other staff, wand or sceptre autocasts. */
    private static final String[] POWERED={"trident of the","sanguinesti staff","tumeken's shadow","accursed sceptre",
        "thammaron's sceptre","warped sceptre","eye of ayak","crystal staff","bone staff","starter staff","dawnbringer"};
    static Weapon weapon(String name) {
        if(name==null||name.isEmpty())return Weapon.NONE;
        String w=name.toLowerCase(Locale.ROOT);
        for(String powered:POWERED)if(w.contains(powered))return Weapon.POWERED;
        if(w.contains("staff")||w.contains("wand")||w.contains("sceptre")||w.contains("blue moon spear")||w.equals("void knight mace"))return Weapon.AUTOCAST;
        return Weapon.NONE;
    }
    /** Range in tiles on the default style (OSRS Wiki "Attack range"): every combat spell reaches 10;
     * powered staves vary. Unlisted powered staves use 7. */
    static int range(String name) {
        Weapon kind=weapon(name);
        if(kind==Weapon.AUTOCAST)return 10;
        if(kind!=Weapon.POWERED)return 0;
        String w=name.toLowerCase(Locale.ROOT);
        if(w.contains("tumeken's shadow")||w.contains("bone staff"))return 8;
        if(w.contains("eye of ayak"))return 6;
        return 7;
    }
    static boolean magic(String name){return weapon(name)!=Weapon.NONE;}

    /** Client thread: varbit 276 is non-zero while any autocast spell is selected. */
    static boolean autocastSet(Client client){return client.getVarbitValue(VarbitID.AUTOCAST_SPELL)!=0;}
    /** Client thread: the best Ice or Water spell this spellbook, level and rune supply can cast, or null. */
    static Rs2CombatSpells best(Client client) {
        int book=client.getVarbitValue(VarbitID.SPELLBOOK);
        Rs2CombatSpells[] order=book==1?ANCIENT:book==0?STANDARD:new Rs2CombatSpells[0];
        for(Rs2CombatSpells spell:order)if(Rs2Magic.canCast(spell))return spell;
        return null;
    }
}
