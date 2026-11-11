package net.runelite.client.plugins.microbot.slayer;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.coords.WorldPoint;

import java.util.Arrays;

/**
 * Registry of common slayer task locations with their WorldPoints.
 * Used to translate profile location names to actual coordinates.
 */
@Getter
@RequiredArgsConstructor
public enum SlayerLocation {
    // Catacombs of Kourend - great for multi-combat bursting
    CATACOMBS("catacombs", "Catacombs of Kourend", new WorldPoint(1666, 10049, 0)),
    CATACOMBS_DUST_DEVILS("catacombs dust devils", "Catacombs - Dust Devils", new WorldPoint(1716,10032,0)),
    CATACOMBS_NECHRYAEL("catacombs nechryael", "Catacombs - Nechryael", new WorldPoint(1697, 10077, 0)),
    CATACOMBS_ABYSSAL_DEMONS("catacombs abyssal demons", "Catacombs - Abyssal Demons", new WorldPoint(1678, 10086, 0)),
    CATACOMBS_HELLHOUNDS("catacombs hellhounds", "Catacombs - Hellhounds", new WorldPoint(1642, 10054, 0)),
    CATACOMBS_BLACK_DEMONS("catacombs black demons", "Catacombs - Black Demons", new WorldPoint(1720, 10055, 0)),
    CATACOMBS_GREATER_DEMONS("catacombs greater demons", "Catacombs - Greater Demons", new WorldPoint(1685,10086,0)),
    CATACOMBS_FIRE_GIANTS("catacombs fire giants", "Catacombs - Fire Giants", new WorldPoint(1629,10051,0)),
    CATACOMBS_ANKOU("catacombs ankou", "Catacombs - Ankou", new WorldPoint(1650, 10084, 0)),
    CATACOMBS_DAGANNOTH("catacombs dagannoth", "Catacombs - Dagannoth", new WorldPoint(1674,9997,0)),
    CATACOMBS_MUTATED_BLOODVELDS("catacombs mutated bloodvelds", "Catacombs - Mutated Bloodvelds", new WorldPoint(1691, 10015, 0)),
    CATACOMBS_BRUTAL_BLUE_DRAGONS("catacombs brutal blue dragons", "Catacombs - Brutal Blue Dragons", new WorldPoint(1634, 10075, 0)),

    // Slayer Tower
    SLAYER_TOWER("slayer tower", "Slayer Tower", new WorldPoint(3429, 3534, 0)),
    SLAYER_TOWER_BASEMENT("slayer tower basement", "Slayer Tower Basement", new WorldPoint(3417, 9932, 0)),
    SLAYER_TOWER_GARGOYLES("slayer tower gargoyles", "Slayer Tower - Gargoyles", new WorldPoint(3442, 3543, 2)),
    SLAYER_TOWER_NECHRYAEL("slayer tower nechryael", "Slayer Tower - Nechryael", new WorldPoint(3438, 3558, 2)),
    SLAYER_TOWER_ABYSSAL_DEMONS("slayer tower abyssal demons", "Slayer Tower - Abyssal Demons", new WorldPoint(3420, 3568, 2)),
    SLAYER_TOWER_BLOODVELDS("slayer tower bloodvelds", "Slayer Tower - Bloodvelds", new WorldPoint(3418, 3558, 1)),
    SLAYER_TOWER_ABERRANT_SPECTRES("slayer tower aberrant spectres", "Slayer Tower - Aberrant Spectres", new WorldPoint(3438, 3549, 1)),
    SLAYER_TOWER_CRAWLING_HANDS("slayer tower crawling hands", "Slayer Tower - Crawling Hands", new WorldPoint(3418, 3547, 0)),
    SLAYER_TOWER_INFERNAL_MAGES("slayer tower infernal mages", "Slayer Tower - Infernal Mages", new WorldPoint(3442, 3550, 1)),

    // Stronghold Slayer Cave (Nieve's Cave)
    STRONGHOLD_CAVE("stronghold cave", "Stronghold Slayer Cave", new WorldPoint(2431, 9806, 0)),
    STRONGHOLD_CAVE_BLOODVELDS("stronghold bloodvelds", "Stronghold - Bloodvelds", new WorldPoint(2426, 9820, 0)),
    STRONGHOLD_CAVE_HELLHOUNDS("stronghold hellhounds", "Stronghold - Hellhounds", new WorldPoint(2413, 9785, 0)),
    STRONGHOLD_CAVE_FIRE_GIANTS("stronghold fire giants", "Stronghold - Fire Giants", new WorldPoint(2392, 9782, 0)),
    STRONGHOLD_CAVE_ABERRANT_SPECTRES("stronghold aberrant spectres", "Stronghold - Aberrant Spectres", new WorldPoint(2453, 9793, 0)),
    STRONGHOLD_CAVE_ABYSSAL_DEMONS("stronghold abyssal demons", "Stronghold - Abyssal Demons", new WorldPoint(2465, 9772, 0)),
    STRONGHOLD_CAVE_GREATER_DEMONS("stronghold greater demons", "Stronghold - Greater Demons", new WorldPoint(2467, 9805, 0)),
    STRONGHOLD_CAVE_BLACK_DEMONS("stronghold black demons", "Stronghold - Black Demons", new WorldPoint(2466, 9823, 0)),
    STRONGHOLD_CAVE_DAGANNOTH("stronghold dagannoth", "Stronghold - Dagannoth", new WorldPoint(2446, 9829, 0)),
    STRONGHOLD_CAVE_ANKOU("stronghold ankou", "Stronghold - Ankou", new WorldPoint(2393, 9806, 0)),

    // Karuulm Slayer Dungeon (Mount Karuulm)
    KARUULM("karuulm", "Karuulm Slayer Dungeon", new WorldPoint(1310, 10188, 0)),
    KARUULM_HYDRAS("karuulm hydras", "Karuulm - Hydras", new WorldPoint(1312, 10232, 0)),
    KARUULM_DRAKES("karuulm drakes", "Karuulm - Drakes", new WorldPoint(1298, 10188, 0)),
    KARUULM_WYRMS("karuulm wyrms", "Karuulm - Wyrms", new WorldPoint(1282, 10175, 0)),
    KARUULM_SULPHUR_LIZARDS("karuulm sulphur lizards", "Karuulm - Sulphur Lizards", new WorldPoint(1300, 10158, 0)),

    // Smoke Dungeon
    SMOKE_DUNGEON("smoke dungeon", "Smoke Dungeon", new WorldPoint(3206, 9379, 0)),
    SMOKE_DUNGEON_DUST_DEVILS("smoke dungeon dust devils", "Smoke Dungeon - Dust Devils", new WorldPoint(3218, 9370, 0)),
    SMOKE_DEVIL_DUNGEON("smoke devil dungeon", "Smoke Devil Dungeon", new WorldPoint(2398, 9444, 0)),

    // Chasm of Fire
    CHASM_OF_FIRE("chasm of fire", "Chasm of Fire", new WorldPoint(1435, 10076, 0)),
    CHASM_OF_FIRE_GREATER_DEMONS("chasm greater demons", "Chasm - Greater Demons", new WorldPoint(1451, 10083, 0)),
    CHASM_OF_FIRE_BLACK_DEMONS("chasm black demons", "Chasm - Black Demons", new WorldPoint(1419, 10072, 0)),
    CHASM_OF_FIRE_LESSER_DEMONS("chasm lesser demons", "Chasm - Lesser Demons", new WorldPoint(1419, 10095, 0)),

    // Dragons
    LITHKREN_VAULT("lithkren vault", "Lithkren Vault", new WorldPoint(1556, 5074, 0)),
    BRIMHAVEN_DUNGEON("brimhaven dungeon", "Brimhaven Dungeon", new WorldPoint(2713, 9564, 0)),
    BRIMHAVEN_BLACK_DRAGONS("brimhaven black dragons", "Brimhaven - Black Dragons", new WorldPoint(2717, 9477, 0)),
    BRIMHAVEN_RED_DRAGONS("brimhaven red dragons", "Brimhaven - Red Dragons", new WorldPoint(2683, 9523, 0)),
    BRIMHAVEN_METAL_DRAGONS("brimhaven metal dragons", "Brimhaven - Metal Dragons", new WorldPoint(2690, 9466, 0)),
    BRIMHAVEN_STEEL_DRAGONS("brimhaven steel dragons", "Brimhaven - Steel Dragons", new WorldPoint(2661, 9423, 0)),
    TAVERLEY_DUNGEON("taverley dungeon", "Taverley Dungeon", new WorldPoint(2884, 9798, 0)),
    TAVERLEY_BLACK_DRAGONS("taverley black dragons", "Taverley - Black Dragons", new WorldPoint(2861, 9822, 0)),
    TAVERLEY_BABY_BLACK_DRAGONS("taverley baby black dragons", "Taverley - Baby Black Dragons (Slayer)", new WorldPoint(2820, 9819, 1)),
    TAVERLEY_BABY_BLUE_DRAGONS("taverley baby blue dragons", "Taverley - Baby Blue Dragons", new WorldPoint(2916, 9804, 0)),
    TAVERLEY_BLUE_DRAGONS("taverley blue dragons", "Taverley - Blue Dragons", new WorldPoint(2901, 9799, 0)),
    TAVERLEY_HELLHOUNDS("taverley hellhounds", "Taverley - Hellhounds", new WorldPoint(2874, 9847, 0)),
    ANCIENT_CAVERN("ancient cavern", "Ancient Cavern", new WorldPoint(1768, 5366, 1)),
    ANCIENT_CAVERN_MITHRIL_DRAGONS("ancient cavern mithril dragons", "Ancient Cavern - Mithril Dragons", new WorldPoint(1743, 5329, 0)),

    // Fremennik Slayer Dungeon
    FREMENNIK_CAVE("fremennik cave", "Fremennik Slayer Dungeon", new WorldPoint(2794, 10018, 0)),
    FREMENNIK_CAVE_KURASK("fremennik kurask", "Fremennik - Kurask", new WorldPoint(2702, 10000, 0)),
    FREMENNIK_CAVE_TUROTH("fremennik turoth", "Fremennik - Turoth", new WorldPoint(2717, 10016, 0)),
    FREMENNIK_CAVE_COCKATRICE("fremennik cockatrice", "Fremennik - Cockatrice", new WorldPoint(2792, 10034, 0)),
    FREMENNIK_CAVE_BASILISK("fremennik basilisk", "Fremennik - Basilisk", new WorldPoint(2740, 10008, 0)),
    FREMENNIK_CAVE_JELLY("fremennik jelly", "Fremennik - Jelly", new WorldPoint(2704, 10030, 0)),

    // Kalphite areas
    KALPHITE_LAIR("kalphite lair", "Kalphite Lair", new WorldPoint(3307,9528,0)),
    KALPHITE_CAVE("kalphite cave", "Kalphite Cave", new WorldPoint(3315, 9499, 0)),

    // Fossil Island
    FOSSIL_ISLAND_WYVERNS("fossil island wyverns", "Fossil Island - Wyverns", new WorldPoint(3609, 10278, 0)),

    // Wilderness
    WILDERNESS_SLAYER_CAVE("wilderness slayer cave", "Wilderness Slayer Cave", new WorldPoint(3421, 10116, 0)),

    // Misc locations
    KELDAGRIM("keldagrim", "Keldagrim", new WorldPoint(2866, 10180, 0)),
    DEATH_PLATEAU("death plateau", "Death Plateau", new WorldPoint(2861, 3592, 0)),
    DEATH_PLATEAU_TROLLS("death plateau trolls", "Death Plateau - Trolls", new WorldPoint(2870, 3593, 0)),
    MORYTANIA_SLAYER_TOWER("morytania", "Morytania Slayer Tower", new WorldPoint(3429, 3534, 0)),
    APE_ATOLL("ape atoll", "Ape Atoll", new WorldPoint(2759, 2788, 0)),
    ZANARIS("zanaris", "Zanaris", new WorldPoint(2389, 4435, 0)),
    KRAKEN_COVE("kraken cove", "Kraken Cove", new WorldPoint(2280, 10022, 0)),
    LIGHTHOUSE("lighthouse", "Lighthouse", new WorldPoint(2515, 3619, 0)),
    LIGHTHOUSE_DAGANNOTHS("lighthouse dagannoths", "Lighthouse - Dagannoths", new WorldPoint(2519, 10020, 0)),
    WATERBIRTH_ISLAND("waterbirth island", "Waterbirth Island", new WorldPoint(2521, 3740, 0)),
    ISLE_OF_STONE("isle of stone", "Isle of Stone", new WorldPoint(2464, 3993, 0)),
    ISLE_OF_STONE_DAGANNOTHS("isle of stone dagannoths", "Isle of Stone - Dagannoths", new WorldPoint(2464, 10398, 0)),
    DAGANNOTH_KINGS("dagannoth kings", "Dagannoth Kings Lair", new WorldPoint(2899, 4449, 0)),
    CERBERUS_LAIR("cerberus lair", "Cerberus Lair", new WorldPoint(1310, 1251, 0)),
    THERMONUCLEAR_SMOKE_DEVIL("thermonuclear", "Thermonuclear Smoke Devil", new WorldPoint(2378, 9452, 0)),
    ALCHEMICAL_HYDRA("alchemical hydra", "Alchemical Hydra Lair", new WorldPoint(1364, 10265, 0)),

    // Lunar Isle
    LUNAR_ISLE("lunar isle", "Lunar Isle", new WorldPoint(2100, 3914, 0)),
    LUNAR_ISLE_SUQAHS("lunar isle suqahs", "Lunar Isle - Suqahs", new WorldPoint(2114, 3943, 0)),

    // Mos Le'Harmless
    MOS_LEHARMLESS("mos leharmless", "Mos Le'Harmless", new WorldPoint(3680, 2970, 0)),
    MOS_LEHARMLESS_CAVE_HORRORS("mos leharmless cave horrors", "Mos Le'Harmless - Cave Horrors", new WorldPoint(3740, 9373, 0)),

    // Mourner Tunnels / Dark beasts
    MOURNER_TUNNELS("mourner tunnels", "Mourner Tunnels", new WorldPoint(1991, 4638, 0)),
    MOURNER_TUNNELS_DARK_BEASTS("mourner tunnels dark beasts", "Mourner Tunnels - Dark Beasts", new WorldPoint(1992, 4655, 0)),

    // Prifddinas / Elven areas
    PRIFDDINAS("prifddinas", "Prifddinas", new WorldPoint(3239, 6075, 0)),
    LLETYA("lletya", "Lletya", new WorldPoint(2341, 3171, 0)),
    LLETYA_ELVES("lletya elves", "Lletya - Elves", new WorldPoint(2323, 3155, 0)),

    // Araxyte Cave
    ARAXYTE_CAVE("araxyte cave", "Araxyte Cave", new WorldPoint(3715, 5765, 0)),

    // Asgarnia Ice Dungeon
    ASGARNIA_ICE_DUNGEON("asgarnia ice dungeon", "Asgarnia Ice Dungeon", new WorldPoint(3007, 9550, 0)),
    ASGARNIA_ICE_DUNGEON_WYVERNS("asgarnia ice dungeon wyverns", "Asgarnia - Skeletal Wyverns", new WorldPoint(3028, 9549, 0)),

    // TzHaar City
    TZHAAR_CITY("tzhaar city", "TzHaar City", new WorldPoint(2444, 5170, 0)),

    // Waterfall Dungeon
    WATERFALL_DUNGEON("waterfall dungeon", "Waterfall Dungeon", new WorldPoint(2575, 9861, 0)),
    WATERFALL_DUNGEON_FIRE_GIANTS("waterfall fire giants", "Waterfall - Fire Giants", new WorldPoint(2569, 9888, 0)),

    // God Wars Dungeon
    GOD_WARS_DUNGEON("god wars dungeon", "God Wars Dungeon", new WorldPoint(2871, 5318, 2)),
    GOD_WARS_AVIANSIES("god wars aviansies", "God Wars - Aviansies", new WorldPoint(2871, 5270, 2)),
    GOD_WARS_SPIRITUAL_CREATURES("god wars spiritual creatures", "God Wars - Spiritual Creatures", new WorldPoint(2885, 5310, 2)),

    // Grimstone (Sailing) - Fairy Ring DLP
    GRIMSTONE_DUNGEON("grimstone dungeon", "Grimstone Dungeon", new WorldPoint(2926, 10455, 0)),
    GRIMSTONE_FROST_DRAGONS("grimstone frost dragons", "Grimstone - Frost Dragons", new WorldPoint(2902, 10457, 0)),
    GRIMSTONE_FROST_DRAGONS_TASK("grimstone frost dragons task", "Grimstone - Frost Dragons (Task Only)", new WorldPoint(2902, 10457, 0));

    private final String key;
    private final String displayName;
    private final WorldPoint worldPoint;

    /**
     * Find a SlayerLocation by its key (case-insensitive).
     * @param name The location name from the profile
     * @return The matching SlayerLocation, or null if not found
     */
    public static SlayerLocation fromName(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        String normalized = name.toLowerCase().trim();
        return Arrays.stream(values())
                .filter(loc -> loc.key.equals(normalized) || loc.displayName.equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }
}
