/* Copyright (c) 2026, DRO (droplugins). SPDX-License-Identifier: BSD-2-Clause */
package net.runelite.client.plugins.microbot.drofirecape;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FcWeaponRangeTest {
    @Test void everySupportedWeaponHasAnExplicitRange() {
        // OSRS Wiki "Attack range", non-longrange column.
        Map<String,Integer> ranges=Map.ofEntries(
            Map.entry("Toxic blowpipe",5),Map.entry("Blazing blowpipe",5),Map.entry("Comp ogre bow",5),Map.entry("Phoenix crossbow",5),
            Map.entry("Twisted bow",10),Map.entry("Bow of faerdhinen (c)",10),Map.entry("Crystal bow",10),Map.entry("Dark bow",10),
            Map.entry("Scorching bow",10),Map.entry("Magic longbow",10),Map.entry("Magic comp bow",10),Map.entry("Ogre bow",10),
            Map.entry("3rd age bow",9),Map.entry("Craw's bow",9),Map.entry("Webweaver bow",9),Map.entry("Heavy ballista",9),Map.entry("Black chinchompa",9),
            Map.entry("Armadyl crossbow",8),Map.entry("Zaryte crossbow",8),Map.entry("Karil's crossbow",8),Map.entry("Seercull",8),
            Map.entry("Venator bow",6),Map.entry("Dorgeshuun crossbow",6),Map.entry("Eclipse atlatl",6),
            Map.entry("Rune crossbow",7),Map.entry("Dragon hunter crossbow",7),Map.entry("Magic shortbow (i)",7),
            Map.entry("Toktz-xil-ul",7),Map.entry("Tonalztics of ralos",7),
            Map.entry("Dragon knife",4),Map.entry("Rune thrownaxe",4),Map.entry("Dragon dart",3),
            Map.entry("Trident of the swamp",7),Map.entry("Sanguinesti staff",7),Map.entry("Tumeken's shadow",8),Map.entry("Bone staff",8),
            Map.entry("Accursed sceptre",7),Map.entry("Thammaron's sceptre",7),Map.entry("Warped sceptre",7),Map.entry("Dawnbringer",7),
            Map.entry("Eye of ayak",6),Map.entry("Crystal staff (perfected)",7),
            Map.entry("Kodai wand",10),Map.entry("Master wand",10),Map.entry("Harmonised nightmare staff",10),
            Map.entry("Toxic staff of the dead",10),Map.entry("Ancient sceptre",10),Map.entry("Ahrim's staff",10),
            Map.entry("Mystic water staff",10),Map.entry("Void knight mace",10),Map.entry("Blue moon spear",10));
        ranges.forEach((name,range)->assertEquals(range.intValue(),FcFrame.inferRange(name),name));
        assertEquals(0,FcFrame.inferRange("Abyssal whip"));
    }
    @Test void poweredStavesCastTheirOwnSpellAndOthersAutocast() {
        assertEquals(FcMagic.Weapon.POWERED,FcMagic.weapon("Tumeken's shadow"));
        assertEquals(FcMagic.Weapon.AUTOCAST,FcMagic.weapon("Kodai wand"));
        assertEquals(FcMagic.Weapon.NONE,FcMagic.weapon("Twisted bow"));
    }
}
