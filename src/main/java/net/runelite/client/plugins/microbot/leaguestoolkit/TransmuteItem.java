package net.runelite.client.plugins.microbot.leaguestoolkit;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TransmuteItem {
    // Ores
    TIN_ORE("Tin ore", TransmuteCategory.ORES),
    COPPER_ORE("Copper ore", TransmuteCategory.ORES),
    IRON_ORE("Iron ore", TransmuteCategory.ORES),
    COAL("Coal", TransmuteCategory.ORES),
    MITHRIL_ORE("Mithril ore", TransmuteCategory.ORES),
    ADAMANTITE_ORE("Adamantite ore", TransmuteCategory.ORES),
    RUNITE_ORE("Runite ore", TransmuteCategory.ORES),

    // Fish
    RAW_SHRIMPS("Raw shrimps", TransmuteCategory.FISH),
    RAW_SARDINE("Raw sardine", TransmuteCategory.FISH),
    RAW_HERRING("Raw herring", TransmuteCategory.FISH),
    RAW_MACKEREL("Raw mackerel", TransmuteCategory.FISH),
    RAW_TROUT("Raw trout", TransmuteCategory.FISH),
    RAW_COD("Raw cod", TransmuteCategory.FISH),
    RAW_PIKE("Raw pike", TransmuteCategory.FISH),
    RAW_SALMON("Raw salmon", TransmuteCategory.FISH),
    RAW_TUNA("Raw tuna", TransmuteCategory.FISH),
    RAW_LOBSTER("Raw lobster", TransmuteCategory.FISH),
    RAW_BASS("Raw bass", TransmuteCategory.FISH),
    RAW_SWORDFISH("Raw swordfish", TransmuteCategory.FISH),
    RAW_KARAMBWAN("Raw karambwan", TransmuteCategory.FISH),
    RAW_SHARK("Raw shark", TransmuteCategory.FISH),
    RAW_ANGLERFISH("Raw anglerfish", TransmuteCategory.FISH),

    // Gems (transmute order: sapphire → emerald → ruby → diamond → opal → jade → red topaz → dragonstone)
    UNCUT_SAPPHIRE("Uncut sapphire", TransmuteCategory.GEMS),
    UNCUT_EMERALD("Uncut emerald", TransmuteCategory.GEMS),
    UNCUT_RUBY("Uncut ruby", TransmuteCategory.GEMS),
    UNCUT_DIAMOND("Uncut diamond", TransmuteCategory.GEMS),
    UNCUT_OPAL("Uncut opal", TransmuteCategory.GEMS),
    UNCUT_JADE("Uncut jade", TransmuteCategory.GEMS),
    UNCUT_RED_TOPAZ("Uncut red topaz", TransmuteCategory.GEMS),
    UNCUT_DRAGONSTONE("Uncut dragonstone", TransmuteCategory.GEMS),

    // Runes
    AIR_RUNE("Air rune", TransmuteCategory.RUNES),
    WATER_RUNE("Water rune", TransmuteCategory.RUNES),
    EARTH_RUNE("Earth rune", TransmuteCategory.RUNES),
    FIRE_RUNE("Fire rune", TransmuteCategory.RUNES),
    CHAOS_RUNE("Chaos rune", TransmuteCategory.RUNES),
    NATURE_RUNE("Nature rune", TransmuteCategory.RUNES),
    COSMIC_RUNE("Cosmic rune", TransmuteCategory.RUNES),
    LAW_RUNE("Law rune", TransmuteCategory.RUNES),
    DEATH_RUNE("Death rune", TransmuteCategory.RUNES),
    ASTRAL_RUNE("Astral rune", TransmuteCategory.RUNES),
    BLOOD_RUNE("Blood rune", TransmuteCategory.RUNES),
    SOUL_RUNE("Soul rune", TransmuteCategory.RUNES),
    WRATH_RUNE("Wrath rune", TransmuteCategory.RUNES),

    // Logs
    LOGS("Logs", TransmuteCategory.LOGS),
    OAK_LOGS("Oak logs", TransmuteCategory.LOGS),
    WILLOW_LOGS("Willow logs", TransmuteCategory.LOGS),
    TEAK_LOGS("Teak logs", TransmuteCategory.LOGS),
    MAPLE_LOGS("Maple logs", TransmuteCategory.LOGS),
    MAHOGANY_LOGS("Mahogany logs", TransmuteCategory.LOGS),
    YEW_LOGS("Yew logs", TransmuteCategory.LOGS),
    MAGIC_LOGS("Magic logs", TransmuteCategory.LOGS),
    REDWOOD_LOGS("Redwood logs", TransmuteCategory.LOGS),

    // Bones
    BONES("Bones", TransmuteCategory.BONES),
    BAT_BONES("Bat bones", TransmuteCategory.BONES),
    BIG_BONES("Big bones", TransmuteCategory.BONES),
    WYRMLING_BONES("Wyrmling bones", TransmuteCategory.BONES),
    BABY_DRAGON_BONES("Baby dragon bones", TransmuteCategory.BONES),
    WYRM_BONES("Wyrm bones", TransmuteCategory.BONES),
    DRAGON_BONES("Dragon bones", TransmuteCategory.BONES),
    DRAKE_BONES("Drake bones", TransmuteCategory.BONES),
    LAVA_DRAGON_BONES("Lava dragon bones", TransmuteCategory.BONES),
    HYDRA_BONES("Hydra bones", TransmuteCategory.BONES),
    DAGANNOTH_BONES("Dagannoth bones", TransmuteCategory.BONES),
    SUPERIOR_DRAGON_BONES("Superior dragon bones", TransmuteCategory.BONES),

    // Hides
    COWHIDE("Cowhide", TransmuteCategory.HIDES),
    SNAKESKIN("Snakeskin", TransmuteCategory.HIDES),
    GREEN_DRAGONHIDE("Green dragonhide", TransmuteCategory.HIDES),
    BLUE_DRAGONHIDE("Blue dragonhide", TransmuteCategory.HIDES),
    RED_DRAGONHIDE("Red dragonhide", TransmuteCategory.HIDES),
    BLACK_DRAGONHIDE("Black dragonhide", TransmuteCategory.HIDES),

    // Ashes
    ASHES("Ashes", TransmuteCategory.ASHES),
    VOLCANIC_ASH("Volcanic ash", TransmuteCategory.ASHES),
    FIENDISH_ASHES("Fiendish ashes", TransmuteCategory.ASHES),
    VILE_ASHES("Vile ashes", TransmuteCategory.ASHES),
    MALICIOUS_ASHES("Malicious ashes", TransmuteCategory.ASHES),
    ABYSSAL_ASHES("Abyssal ashes", TransmuteCategory.ASHES),
    INFERNAL_ASHES("Infernal ashes", TransmuteCategory.ASHES),

    // Compost
    COMPOST("Compost", TransmuteCategory.COMPOST),
    SUPERCOMPOST("Supercompost", TransmuteCategory.COMPOST),
    ULTRACOMPOST("Ultracompost", TransmuteCategory.COMPOST);

    private final String itemName;
    private final TransmuteCategory category;

    @Override
    public String toString() {
        return itemName;
    }
}
