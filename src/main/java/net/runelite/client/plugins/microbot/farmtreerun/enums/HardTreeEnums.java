package net.runelite.client.plugins.microbot.farmtreerun.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

@Getter
@RequiredArgsConstructor
public enum HardTreeEnums {
    TEAK("Teak sapling", ItemID.PLANTPOT_TEAK_SAPLING, ItemID.LIMPWURT_ROOT, 15,35),
    MAHOGANY("Mahogany sapling", ItemID.PLANTPOT_MAHOGANY_SAPLING, ItemID.YANILLIAN_HOPS, 25,55),
    CAMPHOR("Camphor sapling", ItemID.PLANTPOT_CAMPHOR_SAPLING, ItemID.WHITE_BERRIES, 10,66),
    IRONWOOD("Ironwood sapling", ItemID.PLANTPOT_IRONWOOD_SAPLING, ItemID.CURRY_LEAF, 10,80),
    ROSEWOOD("Rosewood sapling", ItemID.PLANTPOT_ROSEWOOD_SAPLING, ItemID.DRAGONFRUIT, 8,92);


    private final String name;
    private final int saplingId;
    private final int paymentId;
    private final int paymentAmount;
    private final int farmingLevel;

    @Override
    public String toString() {
        return name;
    }

    public boolean hasRequiredLevel() {
        return Rs2Player.getSkillRequirement(Skill.FARMING, this.farmingLevel);
    }
}
