/* Copyright (c) 2026, DRO (droplugins). SPDX-License-Identifier: BSD-2-Clause */
package net.runelite.client.plugins.microbot.drofirecape;

import java.util.List;
import java.util.Map;
import net.runelite.client.plugins.microbot.drofirecape.core.BowPrayerClock;
import net.runelite.client.plugins.microbot.drofirecape.core.OffensivePrayers;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Magic weapons from the Inventory Setup: their ranges, autocast/powered kind, prayers and cast animations. */
class FcWeaponRangeTest {
    @Test void magicWeaponsHaveTheirAttackRange() {
        // OSRS Wiki "Attack range", non-longrange column.
        Map<String,Integer> ranges=Map.ofEntries(
            Map.entry("Trident of the swamp",7),Map.entry("Sanguinesti staff",7),Map.entry("Tumeken's shadow",8),Map.entry("Bone staff",8),
            Map.entry("Accursed sceptre",7),Map.entry("Thammaron's sceptre",7),Map.entry("Warped sceptre",7),Map.entry("Dawnbringer",7),
            Map.entry("Eye of ayak",6),Map.entry("Crystal staff (perfected)",7),
            Map.entry("Kodai wand",10),Map.entry("Master wand",10),Map.entry("Harmonised nightmare staff",10),
            Map.entry("Toxic staff of the dead",10),Map.entry("Ancient sceptre",10),Map.entry("Ahrim's staff",10),
            Map.entry("Mystic water staff",10),Map.entry("Void knight mace",10),Map.entry("Blue moon spear",10));
        ranges.forEach((name,range)->assertEquals(range.intValue(),FcFrame.inferRange(name),name));
    }
    @Test void rangedWeaponsKeepTheirRangeAndMeleeStaysUnrecognized() {
        assertEquals(5,FcFrame.inferRange("Toxic blowpipe"));
        assertEquals(10,FcFrame.inferRange("Twisted bow"));
        assertEquals(7,FcFrame.inferRange("Rune crossbow"));
        assertEquals(0,FcFrame.inferRange("Abyssal whip"));
    }
    @Test void poweredStavesCastTheirOwnSpellAndOthersAutocast() {
        assertEquals(FcMagic.Weapon.POWERED,FcMagic.weapon("Tumeken's shadow"));
        assertEquals(FcMagic.Weapon.AUTOCAST,FcMagic.weapon("Kodai wand"));
        assertEquals(FcMagic.Weapon.NONE,FcMagic.weapon("Twisted bow"));
    }
    @Test void magicWeaponsTakeTheMagicOffensivePrayers() {
        assertEquals(List.of("AUGURY"),OffensivePrayers.select(false,true,77,70,false,true,true,true));
        assertEquals(List.of("MYSTIC_MIGHT"),OffensivePrayers.select(false,true,77,70,false,true,true,false));
        assertEquals(List.of("MYSTIC_LORE"),OffensivePrayers.select(false,true,44,1,false,false,false,false));
        assertEquals(List.of("MYSTIC_WILL"),OffensivePrayers.select(false,true,26,1,false,false,false,false));
        assertEquals(List.of("RIGOUR"),OffensivePrayers.select(false,false,77,70,false,true,true,true),"A bow keeps Rigour");
    }
    @Test void autocastAndPoweredStaffCastsAreAttackAnimations() {
        for(int animation:new int[]{711,1161,1162,1167,7855,1978,1979,9493})
            assertTrue(BowPrayerClock.attackAnimation(animation,false),"cast "+animation);
        assertFalse(BowPrayerClock.attackAnimation(711,true),"melee clock ignores casts");
        assertTrue(BowPrayerClock.attackAnimation(5061,false),"blowpipe still recognized");
    }
}
