package net.runelite.client.plugins.microbot.FourtifyFishing.data;

// Access rules of single spots, kept by hand, separate from the generated spot enums: a gate is not a column of them,
// so making the spot enums again leaves this enum as it is.
//
// One constant per requirement, named <TYPE>_<x>_<y>_<n>: the spot's SpotKind constant and the x and y of its centre
// as in the spot enums, and the number of the requirement among those of that spot (a spot with several requirements
// has several constants and needs all of them). The arguments are the SpotKind, x, y and one of:
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
    // The Fishing Guild's lobster and shark docks (Fishing 68)
    BIG_NET_HARPOON_2603_3420_1(SpotKind.BIG_NET_HARPOON, 2603, 3420, Skill.FISHING, 68),
    CAGE_HARPOON_2606_3415_1(SpotKind.CAGE_HARPOON, 2606, 3415, Skill.FISHING, 68),

    // Corsair Cove. The Corsair Curse for both places: the cove's bank and deposit box work only once it is finished,
    // and the only way there for a free-to-play account, Cabin Boy Colin from Rimmington, needs it. The resource
    // area's cage and harpoon spots also need Dragon Slayer I, which lets Haris take a player in.
    CAGE_HARPOON_2456_2891_1(SpotKind.CAGE_HARPOON, 2456, 2891, Quest.DRAGON_SLAYER_I, QuestState.FINISHED),
    CAGE_HARPOON_2456_2891_2(SpotKind.CAGE_HARPOON, 2456, 2891, Quest.THE_CORSAIR_CURSE, QuestState.FINISHED),
    NET_BAIT_2512_2839_1(SpotKind.NET_BAIT, 2512, 2839, Quest.THE_CORSAIR_CURSE, QuestState.FINISHED);

    private final SpotKind type;
    private final int x;
    private final int y;
    private final Gate gate;

    GateData(SpotKind type, int x, int y, Quest quest, QuestState state) {
        this(type, x, y, Gate.of(quest, state));
    }

    GateData(SpotKind type, int x, int y, Skill skill, int level) {
        this(type, x, y, Gate.skill(skill, level));
    }

    GateData(SpotKind type, int x, int y, Gate gate) {
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
