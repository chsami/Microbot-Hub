package net.runelite.client.plugins.microbot.FourtifyWoodcutting.data;

// Access rules of single spots, kept by hand, separate from the generated spot enums: a gate is not a column of them,
// so making the spot enums again leaves this enum as it is.
//
// One constant per requirement, named <TYPE>_<x>_<y>_<n>: the spot's TreeType constant and the x and y of its centre
// as in the spot enums, and the number of the requirement among those of that spot (a spot with several requirements
// has several constants and needs all of them). The arguments are the TreeType, x, y and one of:
//   Quest.<constant>, QuestState.FINISHED      the quest finished
//   Quest.<constant>, QuestState.IN_PROGRESS   the quest started or further on (not "not started")
//   Skill.<constant>, <level>                  a real (unboosted) level of at least <level> (1 to 126)
// The compiler checks the types, the quest and the skill. core/game/Gates.java groups the constants when the plugin
// loads; Spots stops the plugin on a constant that is no spot of the spot enums.

import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.fourtify.core.game.Gate;
import net.runelite.client.plugins.microbot.fourtify.core.game.GateRow;

enum GateData implements GateRow {
    // ARCTIC_PINE
    ARCTIC_PINE_2312_3801_1(TreeType.ARCTIC_PINE, 2312, 3801, Quest.THE_FREMENNIK_ISLES, QuestState.IN_PROGRESS),
    ARCTIC_PINE_2312_3801_2(TreeType.ARCTIC_PINE, 2312, 3801, Quest.THE_FREMENNIK_TRIALS, QuestState.FINISHED),
    ARCTIC_PINE_2319_3789_1(TreeType.ARCTIC_PINE, 2319, 3789, Quest.THE_FREMENNIK_ISLES, QuestState.IN_PROGRESS),
    ARCTIC_PINE_2319_3789_2(TreeType.ARCTIC_PINE, 2319, 3789, Quest.THE_FREMENNIK_TRIALS, QuestState.FINISHED),
    ARCTIC_PINE_2332_3790_1(TreeType.ARCTIC_PINE, 2332, 3790, Quest.THE_FREMENNIK_ISLES, QuestState.IN_PROGRESS),
    ARCTIC_PINE_2332_3790_2(TreeType.ARCTIC_PINE, 2332, 3790, Quest.THE_FREMENNIK_TRIALS, QuestState.FINISHED),
    ARCTIC_PINE_2350_3805_1(TreeType.ARCTIC_PINE, 2350, 3805, Quest.THE_FREMENNIK_ISLES, QuestState.IN_PROGRESS),
    ARCTIC_PINE_2350_3805_2(TreeType.ARCTIC_PINE, 2350, 3805, Quest.THE_FREMENNIK_TRIALS, QuestState.FINISHED),
    ARCTIC_PINE_2358_3793_1(TreeType.ARCTIC_PINE, 2358, 3793, Quest.THE_FREMENNIK_ISLES, QuestState.IN_PROGRESS),
    ARCTIC_PINE_2358_3793_2(TreeType.ARCTIC_PINE, 2358, 3793, Quest.THE_FREMENNIK_TRIALS, QuestState.FINISHED),
    ARCTIC_PINE_2360_3802_1(TreeType.ARCTIC_PINE, 2360, 3802, Quest.THE_FREMENNIK_ISLES, QuestState.IN_PROGRESS),
    ARCTIC_PINE_2360_3802_2(TreeType.ARCTIC_PINE, 2360, 3802, Quest.THE_FREMENNIK_TRIALS, QuestState.FINISHED),

    // MAGIC
    MAGIC_1578_3487_1(TreeType.MAGIC, 1578, 3487, Skill.WOODCUTTING, 60),
    MAGIC_3231_6099_1(TreeType.MAGIC, 3231, 6099, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),

    // MAHOGANY
    MAHOGANY_2597_3894_1(TreeType.MAHOGANY, 2597, 3894, Quest.ROYAL_TROUBLE, QuestState.FINISHED),
    MAHOGANY_2597_3894_2(TreeType.MAHOGANY, 2597, 3894, Quest.THE_FREMENNIK_TRIALS, QuestState.FINISHED),
    MAHOGANY_2610_3892_1(TreeType.MAHOGANY, 2610, 3892, Quest.ROYAL_TROUBLE, QuestState.FINISHED),
    MAHOGANY_2610_3892_2(TreeType.MAHOGANY, 2610, 3892, Quest.THE_FREMENNIK_TRIALS, QuestState.FINISHED),
    MAHOGANY_3300_6129_1(TreeType.MAHOGANY, 3300, 6129, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    MAHOGANY_3810_3063_1(TreeType.MAHOGANY, 3810, 3063, Quest.CABIN_FEVER, QuestState.FINISHED),
    MAHOGANY_3825_3058_1(TreeType.MAHOGANY, 3825, 3058, Quest.CABIN_FEVER, QuestState.FINISHED),

    // MAPLE
    MAPLE_1616_3493_1(TreeType.MAPLE, 1616, 3493, Skill.WOODCUTTING, 60),
    MAPLE_1641_3491_1(TreeType.MAPLE, 1641, 3491, Skill.WOODCUTTING, 60),
    MAPLE_2210_3427_1(TreeType.MAPLE, 2210, 3427, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    // The Corsair Cove resource area: Haris lets a player in once Dragon Slayer I is finished. The Corsair Curse too:
    // the cove's bank and deposit box work only once it is finished, and the only way there for a free-to-play
    // account, Cabin Boy Colin from Rimmington, needs it.
    MAPLE_2480_2897_1(TreeType.MAPLE, 2480, 2897, Quest.DRAGON_SLAYER_I, QuestState.FINISHED),
    MAPLE_2480_2897_2(TreeType.MAPLE, 2480, 2897, Quest.THE_CORSAIR_CURSE, QuestState.FINISHED),
    MAPLE_2547_3868_1(TreeType.MAPLE, 2547, 3868, Quest.THE_FREMENNIK_TRIALS, QuestState.FINISHED),
    MAPLE_2547_3868_2(TreeType.MAPLE, 2547, 3868, Quest.THRONE_OF_MISCELLANIA, QuestState.IN_PROGRESS),
    MAPLE_2554_3861_1(TreeType.MAPLE, 2554, 3861, Quest.THE_FREMENNIK_TRIALS, QuestState.FINISHED),
    MAPLE_2554_3861_2(TreeType.MAPLE, 2554, 3861, Quest.THRONE_OF_MISCELLANIA, QuestState.IN_PROGRESS),

    // OAK
    OAK_1618_3511_1(TreeType.OAK, 1618, 3511, Skill.WOODCUTTING, 60),
    OAK_2421_4467_1(TreeType.OAK, 2421, 4467, Quest.LOST_CITY, QuestState.FINISHED),

    // REDWOOD
    REDWOOD_1567_3482_1(TreeType.REDWOOD, 1567, 3482, Skill.WOODCUTTING, 60),
    REDWOOD_1567_3493_1(TreeType.REDWOOD, 1567, 3493, Skill.WOODCUTTING, 60),

    // TEAK
    TEAK_2593_3892_1(TreeType.TEAK, 2593, 3892, Quest.ROYAL_TROUBLE, QuestState.FINISHED),
    TEAK_2593_3892_2(TreeType.TEAK, 2593, 3892, Quest.THE_FREMENNIK_TRIALS, QuestState.FINISHED),
    TEAK_2601_3896_1(TreeType.TEAK, 2601, 3896, Quest.ROYAL_TROUBLE, QuestState.FINISHED),
    TEAK_2601_3896_2(TreeType.TEAK, 2601, 3896, Quest.THE_FREMENNIK_TRIALS, QuestState.FINISHED),
    TEAK_3310_6123_1(TreeType.TEAK, 3310, 6123, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TEAK_3809_3058_1(TreeType.TEAK, 3809, 3058, Quest.CABIN_FEVER, QuestState.FINISHED),
    TEAK_3832_3066_1(TreeType.TEAK, 3832, 3066, Quest.CABIN_FEVER, QuestState.FINISHED),

    // TREE
    TREE_1631_3512_1(TreeType.TREE, 1631, 3512, Skill.WOODCUTTING, 60),
    TREE_1644_3512_1(TreeType.TREE, 1644, 3512, Skill.WOODCUTTING, 60),
    TREE_2200_3398_1(TreeType.TREE, 2200, 3398, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_2202_3416_1(TreeType.TREE, 2202, 3416, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_2215_3407_1(TreeType.TREE, 2215, 3407, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_2218_3401_1(TreeType.TREE, 2218, 3401, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_2239_3394_1(TreeType.TREE, 2239, 3394, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_2240_3436_1(TreeType.TREE, 2240, 3436, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_2244_3425_1(TreeType.TREE, 2244, 3425, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_2257_3406_1(TreeType.TREE, 2257, 3406, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_2262_3447_1(TreeType.TREE, 2262, 3447, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_2270_3381_1(TreeType.TREE, 2270, 3381, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_2271_3415_1(TreeType.TREE, 2271, 3415, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_2272_3401_1(TreeType.TREE, 2272, 3401, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_2277_3446_1(TreeType.TREE, 2277, 3446, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_2283_3425_1(TreeType.TREE, 2283, 3425, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_2284_3437_1(TreeType.TREE, 2284, 3437, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_2285_3403_1(TreeType.TREE, 2285, 3403, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_2289_3394_1(TreeType.TREE, 2289, 3394, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_2293_3448_1(TreeType.TREE, 2293, 3448, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_2298_3383_1(TreeType.TREE, 2298, 3383, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_2403_4372_1(TreeType.TREE, 2403, 4372, Quest.LOST_CITY, QuestState.FINISHED),
    TREE_2416_4376_1(TreeType.TREE, 2416, 4376, Quest.LOST_CITY, QuestState.FINISHED),
    TREE_2425_4429_1(TreeType.TREE, 2425, 4429, Quest.LOST_CITY, QuestState.FINISHED),
    TREE_2433_4462_1(TreeType.TREE, 2433, 4462, Quest.LOST_CITY, QuestState.FINISHED),
    TREE_2437_4443_1(TreeType.TREE, 2437, 4443, Quest.LOST_CITY, QuestState.FINISHED),
    TREE_2470_4452_1(TreeType.TREE, 2470, 4452, Quest.LOST_CITY, QuestState.FINISHED),
    TREE_2484_4468_1(TreeType.TREE, 2484, 4468, Quest.LOST_CITY, QuestState.FINISHED),
    TREE_3210_6116_1(TreeType.TREE, 3210, 6116, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_3212_6053_1(TreeType.TREE, 3212, 6053, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_3212_6066_1(TreeType.TREE, 3212, 6066, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_3213_6040_1(TreeType.TREE, 3213, 6040, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_3213_6081_1(TreeType.TREE, 3213, 6081, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_3214_6100_1(TreeType.TREE, 3214, 6100, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_3226_6037_1(TreeType.TREE, 3226, 6037, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_3226_6126_1(TreeType.TREE, 3226, 6126, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_3227_6085_1(TreeType.TREE, 3227, 6085, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_3230_6051_1(TreeType.TREE, 3230, 6051, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_3231_6108_1(TreeType.TREE, 3231, 6108, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_3231_6135_1(TreeType.TREE, 3231, 6135, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_3234_6065_1(TreeType.TREE, 3234, 6065, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_3236_6076_1(TreeType.TREE, 3236, 6076, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_3238_6039_1(TreeType.TREE, 3238, 6039, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_3240_6125_1(TreeType.TREE, 3240, 6125, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_3242_6057_1(TreeType.TREE, 3242, 6057, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_3244_6092_1(TreeType.TREE, 3244, 6092, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_3245_6107_1(TreeType.TREE, 3245, 6107, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_3247_6071_1(TreeType.TREE, 3247, 6071, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_3253_6096_1(TreeType.TREE, 3253, 6096, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_3253_6118_1(TreeType.TREE, 3253, 6118, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_3253_6132_1(TreeType.TREE, 3253, 6132, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_3260_6033_1(TreeType.TREE, 3260, 6033, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_3260_6048_1(TreeType.TREE, 3260, 6048, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_3263_6102_1(TreeType.TREE, 3263, 6102, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_3266_6067_1(TreeType.TREE, 3266, 6067, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_3268_6027_1(TreeType.TREE, 3268, 6027, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_3271_6054_1(TreeType.TREE, 3271, 6054, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_3272_6128_1(TreeType.TREE, 3272, 6128, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_3274_6095_1(TreeType.TREE, 3274, 6095, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_3280_6030_1(TreeType.TREE, 3280, 6030, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_3280_6046_1(TreeType.TREE, 3280, 6046, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_3282_6072_1(TreeType.TREE, 3282, 6072, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_3282_6090_1(TreeType.TREE, 3282, 6090, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_3284_6108_1(TreeType.TREE, 3284, 6108, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_3289_6126_1(TreeType.TREE, 3289, 6126, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_3293_6037_1(TreeType.TREE, 3293, 6037, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_3296_6072_1(TreeType.TREE, 3296, 6072, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_3298_6104_1(TreeType.TREE, 3298, 6104, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_3300_6047_1(TreeType.TREE, 3300, 6047, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_3303_6031_1(TreeType.TREE, 3303, 6031, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_3306_6090_1(TreeType.TREE, 3306, 6090, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_3306_6115_1(TreeType.TREE, 3306, 6115, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_3311_6052_1(TreeType.TREE, 3311, 6052, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_3313_6073_1(TreeType.TREE, 3313, 6073, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_3315_6101_1(TreeType.TREE, 3315, 6101, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_3316_6041_1(TreeType.TREE, 3316, 6041, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    TREE_3322_6135_1(TreeType.TREE, 3322, 6135, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),

    // WILLOW
    WILLOW_1634_3496_1(TreeType.WILLOW, 1634, 3496, Skill.WOODCUTTING, 60),

    // YEW
    YEW_1584_3498_1(TreeType.YEW, 1584, 3498, Skill.WOODCUTTING, 60),
    YEW_1589_3487_1(TreeType.YEW, 1589, 3487, Skill.WOODCUTTING, 60),
    YEW_1626_3510_1(TreeType.YEW, 1626, 3510, Skill.WOODCUTTING, 60),
    YEW_1641_3495_1(TreeType.YEW, 1641, 3495, Skill.WOODCUTTING, 60),
    YEW_1646_3507_1(TreeType.YEW, 1646, 3507, Skill.WOODCUTTING, 60),
    YEW_2412_4462_1(TreeType.YEW, 2412, 4462, Quest.LOST_CITY, QuestState.FINISHED),
    YEW_2464_4426_1(TreeType.YEW, 2464, 4426, Quest.LOST_CITY, QuestState.FINISHED),
    // The Corsair Cove resource area, as its maples
    YEW_2474_2888_1(TreeType.YEW, 2474, 2888, Quest.DRAGON_SLAYER_I, QuestState.FINISHED),
    YEW_2474_2888_2(TreeType.YEW, 2474, 2888, Quest.THE_CORSAIR_CURSE, QuestState.FINISHED),
    YEW_2491_4423_1(TreeType.YEW, 2491, 4423, Quest.LOST_CITY, QuestState.FINISHED),
    YEW_3289_6064_1(TreeType.YEW, 3289, 6064, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED),
    YEW_3299_6028_1(TreeType.YEW, 3299, 6028, Quest.SONG_OF_THE_ELVES, QuestState.FINISHED);

    private final TreeType type;
    private final int x;
    private final int y;
    private final Gate gate;

    GateData(TreeType type, int x, int y, Quest quest, QuestState state) {
        this(type, x, y, Gate.of(quest, state));
    }

    GateData(TreeType type, int x, int y, Skill skill, int level) {
        this(type, x, y, Gate.skill(skill, level));
    }

    GateData(TreeType type, int x, int y, Gate gate) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.gate = gate;
    }

    @Override
    public String typeName() {
        return type.name();
    }

    @Override
    public int x() {
        return x;
    }

    @Override
    public int y() {
        return y;
    }

    @Override
    public Gate gate() {
        return gate;
    }
}
