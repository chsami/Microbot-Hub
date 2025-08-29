package net.runelite.client.plugins.microbot.jewelleryenchant.util;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.util.magic.Runes;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum ElementalStaff {
    // Single Element Staves
    STAFF_OF_AIR(ItemID.STAFF_OF_AIR, new Runes[]{Runes.AIR}),
    AIR_BATTLESTAFF(ItemID.AIR_BATTLESTAFF, new Runes[]{Runes.AIR}),
    MYSTIC_AIR_STAFF(ItemID.MYSTIC_AIR_STAFF, new Runes[]{Runes.AIR}),
    STAFF_OF_WATER(ItemID.STAFF_OF_WATER, new Runes[]{Runes.WATER}),
    WATER_BATTLESTAFF(ItemID.WATER_BATTLESTAFF, new Runes[]{Runes.WATER}),
    MYSTIC_WATER_STAFF(ItemID.MYSTIC_WATER_STAFF, new Runes[]{Runes.WATER}),
    STAFF_OF_EARTH(ItemID.STAFF_OF_EARTH, new Runes[]{Runes.EARTH}),
    EARTH_BATTLESTAFF(ItemID.EARTH_BATTLESTAFF, new Runes[]{Runes.EARTH}),
    MYSTIC_EARTH_STAFF(ItemID.MYSTIC_EARTH_STAFF, new Runes[]{Runes.EARTH}),
    STAFF_OF_FIRE(ItemID.STAFF_OF_FIRE, new Runes[]{Runes.FIRE}),
    FIRE_BATTLESTAFF(ItemID.FIRE_BATTLESTAFF, new Runes[]{Runes.FIRE}),
    MYSTIC_FIRE_STAFF(ItemID.MYSTIC_FIRE_STAFF, new Runes[]{Runes.FIRE}),

    // Combination Staves
    MUD_BATTLESTAFF(ItemID.MUD_BATTLESTAFF, new Runes[]{Runes.WATER, Runes.EARTH}),
    MYSTIC_MUD_STAFF(ItemID.MYSTIC_MUD_STAFF, new Runes[]{Runes.WATER, Runes.EARTH}),
    LAVA_BATTLESTAFF(ItemID.LAVA_BATTLESTAFF, new Runes[]{Runes.FIRE, Runes.EARTH}),
    MYSTIC_LAVA_STAFF(ItemID.MYSTIC_LAVA_STAFF, new Runes[]{Runes.FIRE, Runes.EARTH}),
    STEAM_BATTLESTAFF(ItemID.STEAM_BATTLESTAFF, new Runes[]{Runes.WATER, Runes.FIRE}),
    MYSTIC_STEAM_STAFF(ItemID.MYSTIC_STEAM_BATTLESTAFF, new Runes[]{Runes.WATER, Runes.FIRE}),
    SMOKE_BATTLESTAFF(ItemID.SMOKE_BATTLESTAFF, new Runes[]{Runes.AIR, Runes.FIRE}),
    MYSTIC_SMOKE_STAFF(ItemID.MYSTIC_SMOKE_BATTLESTAFF, new Runes[]{Runes.AIR, Runes.FIRE});

    private final int itemId;
    private final Runes[] providedRunes;

    public boolean providesRune(Runes rune) {
        return Arrays.stream(providedRunes).anyMatch(r -> r == rune);
    }
}