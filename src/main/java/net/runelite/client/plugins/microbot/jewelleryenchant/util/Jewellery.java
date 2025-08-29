package net.runelite.client.plugins.microbot.jewelleryenchant.util;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.ItemID;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spellbook;
import net.runelite.client.plugins.microbot.util.magic.Runes;
import net.runelite.client.plugins.microbot.util.magic.Spell;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import java.util.HashMap;

@Getter
@RequiredArgsConstructor
public enum Jewellery implements Spell {

    // --- Level 1 Enchant (Lvl 7 Magic) ---
    SAPPHIRE_RING("Sapphire ring", ItemID.SAPPHIRE_RING, 7, MagicAction.ENCHANT_SAPPHIRE_JEWELLERY, Runes.WATER,
            new HashMap<>() {{ put(Runes.COSMIC, 1); put(Runes.WATER, 1); }}),
    SAPPHIRE_AMULET("Sapphire amulet", ItemID.SAPPHIRE_AMULET, 7, MagicAction.ENCHANT_SAPPHIRE_JEWELLERY, Runes.WATER,
            new HashMap<>() {{ put(Runes.COSMIC, 1); put(Runes.WATER, 1); }}),
    SAPPHIRE_NECKLACE("Sapphire necklace", ItemID.SAPPHIRE_NECKLACE, 7, MagicAction.ENCHANT_SAPPHIRE_JEWELLERY, Runes.WATER,
            new HashMap<>() {{ put(Runes.COSMIC, 1); put(Runes.WATER, 1); }}),
    SAPPHIRE_BRACELET("Sapphire bracelet", ItemID.SAPPHIRE_BRACELET, 7, MagicAction.ENCHANT_SAPPHIRE_JEWELLERY, Runes.WATER,
            new HashMap<>() {{ put(Runes.COSMIC, 1); put(Runes.WATER, 1); }}),

    // NEW: Lvl-1 Enchant Additions
    OPAL_RING("Opal ring", ItemID.OPAL_RING, 7, MagicAction.ENCHANT_SAPPHIRE_JEWELLERY, Runes.WATER,
            new HashMap<>() {{ put(Runes.COSMIC, 1); put(Runes.WATER, 1); }}),
    OPAL_AMULET("Opal amulet", ItemID.OPAL_AMULET, 7, MagicAction.ENCHANT_SAPPHIRE_JEWELLERY, Runes.WATER,
            new HashMap<>() {{ put(Runes.COSMIC, 1); put(Runes.WATER, 1); }}),
    OPAL_NECKLACE("Opal necklace", ItemID.OPAL_NECKLACE, 7, MagicAction.ENCHANT_SAPPHIRE_JEWELLERY, Runes.WATER,
            new HashMap<>() {{ put(Runes.COSMIC, 1); put(Runes.WATER, 1); }}),
    OPAL_BRACELET("Opal bracelet", ItemID.OPAL_BRACELET, 7, MagicAction.ENCHANT_SAPPHIRE_JEWELLERY, Runes.WATER,
            new HashMap<>() {{ put(Runes.COSMIC, 1); put(Runes.WATER, 1); }}),

    // --- Level 2 Enchant (Lvl 27 Magic) ---
    EMERALD_RING("Emerald ring", ItemID.EMERALD_RING, 27, MagicAction.ENCHANT_EMERALD_JEWELLERY, Runes.AIR,
            new HashMap<>() {{ put(Runes.COSMIC, 1); put(Runes.AIR, 3); }}),
    EMERALD_AMULET("Emerald amulet", ItemID.EMERALD_AMULET, 27, MagicAction.ENCHANT_EMERALD_JEWELLERY, Runes.AIR,
            new HashMap<>() {{ put(Runes.COSMIC, 1); put(Runes.AIR, 3); }}),
    EMERALD_NECKLACE("Emerald necklace", ItemID.EMERALD_NECKLACE, 27, MagicAction.ENCHANT_EMERALD_JEWELLERY, Runes.AIR,
            new HashMap<>() {{ put(Runes.COSMIC, 1); put(Runes.AIR, 3); }}),
    EMERALD_BRACELET("Emerald bracelet", ItemID.EMERALD_BRACELET, 27, MagicAction.ENCHANT_EMERALD_JEWELLERY, Runes.AIR,
            new HashMap<>() {{ put(Runes.COSMIC, 1); put(Runes.AIR, 3); }}),

    // NEW: Lvl-2 Enchant Additions
    JADE_RING("Jade ring", ItemID.JADE_RING, 27, MagicAction.ENCHANT_EMERALD_JEWELLERY, Runes.AIR,
            new HashMap<>() {{ put(Runes.COSMIC, 1); put(Runes.AIR, 3); }}),
    JADE_AMULET("Jade amulet", ItemID.JADE_AMULET, 27, MagicAction.ENCHANT_EMERALD_JEWELLERY, Runes.AIR,
            new HashMap<>() {{ put(Runes.COSMIC, 1); put(Runes.AIR, 3); }}),
    JADE_NECKLACE("Jade necklace", ItemID.JADE_NECKLACE, 27, MagicAction.ENCHANT_EMERALD_JEWELLERY, Runes.AIR,
            new HashMap<>() {{ put(Runes.COSMIC, 1); put(Runes.AIR, 3); }}),
    JADE_BRACELET("Jade bracelet", ItemID.JADE_BRACELET, 27, MagicAction.ENCHANT_EMERALD_JEWELLERY, Runes.AIR,
            new HashMap<>() {{ put(Runes.COSMIC, 1); put(Runes.AIR, 3); }}),

    // --- Level 3 Enchant (Lvl 49 Magic) ---
    RUBY_RING("Ruby ring", ItemID.RUBY_RING, 49, MagicAction.ENCHANT_RUBY_JEWELLERY, Runes.FIRE,
            new HashMap<>() {{ put(Runes.COSMIC, 1); put(Runes.FIRE, 5); }}),
    RUBY_AMULET("Ruby amulet", ItemID.RUBY_AMULET, 49, MagicAction.ENCHANT_RUBY_JEWELLERY, Runes.FIRE,
            new HashMap<>() {{ put(Runes.COSMIC, 1); put(Runes.FIRE, 5); }}),
    RUBY_NECKLACE("Ruby necklace", ItemID.RUBY_NECKLACE, 49, MagicAction.ENCHANT_RUBY_JEWELLERY, Runes.FIRE,
            new HashMap<>() {{ put(Runes.COSMIC, 1); put(Runes.FIRE, 5); }}),
    RUBY_BRACELET("Ruby bracelet", ItemID.RUBY_BRACELET, 49, MagicAction.ENCHANT_RUBY_JEWELLERY, Runes.FIRE,
            new HashMap<>() {{ put(Runes.COSMIC, 1); put(Runes.FIRE, 5); }}),

    // NEW: Lvl-3 Enchant Additions
    TOPAZ_RING("Topaz ring", ItemID.TOPAZ_RING, 49, MagicAction.ENCHANT_RUBY_JEWELLERY, Runes.FIRE,
            new HashMap<>() {{ put(Runes.COSMIC, 1); put(Runes.FIRE, 5); }}),
    TOPAZ_AMULET("Topaz amulet", ItemID.TOPAZ_AMULET, 49, MagicAction.ENCHANT_RUBY_JEWELLERY, Runes.FIRE,
            new HashMap<>() {{ put(Runes.COSMIC, 1); put(Runes.FIRE, 5); }}),
    TOPAZ_NECKLACE("Topaz necklace", ItemID.TOPAZ_NECKLACE, 49, MagicAction.ENCHANT_RUBY_JEWELLERY, Runes.FIRE,
            new HashMap<>() {{ put(Runes.COSMIC, 1); put(Runes.FIRE, 5); }}),
    TOPAZ_BRACELET("Topaz bracelet", ItemID.TOPAZ_BRACELET, 49, MagicAction.ENCHANT_RUBY_JEWELLERY, Runes.FIRE,
            new HashMap<>() {{ put(Runes.COSMIC, 1); put(Runes.FIRE, 5); }}),

    // --- Level 4 Enchant (Lvl 57 Magic) ---
    DIAMOND_RING("Diamond ring", ItemID.DIAMOND_RING, 57, MagicAction.ENCHANT_DIAMOND_JEWELLERY, Runes.EARTH,
            new HashMap<>() {{ put(Runes.COSMIC, 1); put(Runes.EARTH, 10); }}),
    DIAMOND_AMULET("Diamond amulet", ItemID.DIAMOND_AMULET, 57, MagicAction.ENCHANT_DIAMOND_JEWELLERY, Runes.EARTH,
            new HashMap<>() {{ put(Runes.COSMIC, 1); put(Runes.EARTH, 10); }}),
    DIAMOND_NECKLACE("Diamond necklace", ItemID.DIAMOND_NECKLACE, 57, MagicAction.ENCHANT_DIAMOND_JEWELLERY, Runes.EARTH,
            new HashMap<>() {{ put(Runes.COSMIC, 1); put(Runes.EARTH, 10); }}),
    DIAMOND_BRACELET("Diamond bracelet", ItemID.DIAMOND_BRACELET, 57, MagicAction.ENCHANT_DIAMOND_JEWELLERY, Runes.EARTH,
            new HashMap<>() {{ put(Runes.COSMIC, 1); put(Runes.EARTH, 10); }}),

    // --- Level 5 Enchant (Lvl 68 Magic) ---
    DRAGONSTONE_RING("Dragonstone ring", ItemID.DRAGONSTONE_RING, 68, MagicAction.ENCHANT_DRAGONSTONE_JEWELLERY, Runes.EARTH,
            new HashMap<>() {{ put(Runes.COSMIC, 1); put(Runes.WATER, 15); put(Runes.EARTH, 15); }}),
    DRAGONSTONE_AMULET("Dragonstone amulet", ItemID.DRAGONSTONE_AMULET, 68, MagicAction.ENCHANT_DRAGONSTONE_JEWELLERY, Runes.EARTH,
            new HashMap<>() {{ put(Runes.COSMIC, 1); put(Runes.WATER, 15); put(Runes.EARTH, 15); }}),
    DRAGON_NECKLACE("Dragon necklace", ItemID.DRAGON_NECKLACE, 68, MagicAction.ENCHANT_DRAGONSTONE_JEWELLERY, Runes.EARTH,
            new HashMap<>() {{ put(Runes.COSMIC, 1); put(Runes.WATER, 15); put(Runes.EARTH, 15); }}),
    DRAGONSTONE_BRACELET("Dragonstone bracelet", ItemID.DRAGONSTONE_BRACELET, 68, MagicAction.ENCHANT_DRAGONSTONE_JEWELLERY, Runes.EARTH,
            new HashMap<>() {{ put(Runes.COSMIC, 1); put(Runes.WATER, 15); put(Runes.EARTH, 15); }}),

    // --- Level 6 Enchant (Lvl 87 Magic) ---
    ONYX_RING("Onyx ring", ItemID.ONYX_RING, 87, MagicAction.ENCHANT_ONYX_JEWELLERY, Runes.FIRE,
            new HashMap<>() {{ put(Runes.COSMIC, 1); put(Runes.FIRE, 20); put(Runes.EARTH, 20); }}),
    ONYX_AMULET("Onyx amulet", ItemID.ONYX_AMULET, 87, MagicAction.ENCHANT_ONYX_JEWELLERY, Runes.FIRE,
            new HashMap<>() {{ put(Runes.COSMIC, 1); put(Runes.FIRE, 20); put(Runes.EARTH, 20); }}),
    ONYX_NECKLACE("Onyx necklace", ItemID.ONYX_NECKLACE, 87, MagicAction.ENCHANT_ONYX_JEWELLERY, Runes.FIRE,
            new HashMap<>() {{ put(Runes.COSMIC, 1); put(Runes.FIRE, 20); put(Runes.EARTH, 20); }}),
    ONYX_BRACELET("Onyx bracelet", ItemID.ONYX_BRACELET, 87, MagicAction.ENCHANT_ONYX_JEWELLERY, Runes.FIRE,
            new HashMap<>() {{ put(Runes.COSMIC, 1); put(Runes.FIRE, 20); put(Runes.EARTH, 20); }});

    // --- Fields for our custom data ---
    private final String name;
    private final int unenchantedId;

    // --- Fields required by the Spell interface ---
    private final int requiredLevel;
    private final MagicAction magicAction;
    private final Runes elementalRune;
    private final HashMap<Runes, Integer> requiredRunes;

    // --- Methods we MUST implement to satisfy the Spell contract ---
    @Override
    public Rs2Spellbook getSpellbook() {
        return Rs2Spellbook.MODERN;
    }

    // --- Helper method for the UI ---
    @Override
    public String toString() {
        return name;
    }
}