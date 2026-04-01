package net.runelite.client.plugins.microbot.aiomagic.enums;

import lombok.Getter;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

@Getter
public enum SuperHeatItem {
    IRON(ItemID.IRON_ORE, 1, 0, 15),
    SILVER(ItemID.SILVER_ORE, 2, 0, 20),
    LEAD(ItemID.LEAD_ORE, 2, 0, 25),
    STEEL(ItemID.IRON_ORE, 1, ItemID.COAL, 2, 30),
    GOLD(ItemID.GOLD_ORE, 1, 0, 40),
    MITHRIL(ItemID.MITHRIL_ORE, 1, ItemID.COAL, 4, 50),
    ADAMANTITE(ItemID.ADAMANTITE_ORE, 1, ItemID.COAL, 6, 70),
    CUPRONICKEL(ItemID.NICKEL_ORE, 1, ItemID.COPPER_ORE, 2, 74),
    RUNITE(ItemID.RUNITE_ORE, 1, ItemID.COAL, 8, 85);
    
    private final int itemID;
    private final int primaryAmount;
    private final int secondaryItemID;
    private final int secondaryAmount;
    private final int levelRequired;

    SuperHeatItem(int itemID, int primaryAmount, int secondaryAmount, int levelRequired) {
        this(itemID, primaryAmount, ItemID.COAL, secondaryAmount, levelRequired);
    }

    SuperHeatItem(int itemID, int primaryAmount, int secondaryItemID, int secondaryAmount, int levelRequired) {
        this.itemID = itemID;
        this.primaryAmount = primaryAmount;
        this.secondaryItemID = secondaryItemID;
        this.secondaryAmount = secondaryAmount;
        this.levelRequired = levelRequired;
    }

    public int getCoalAmount() {
        return secondaryItemID == ItemID.COAL ? secondaryAmount : 0;
    }

    public boolean requiresSecondaryItem() {
        return secondaryAmount > 0;
    }
    
    public boolean hasRequiredLevel() {
        return Rs2Player.getSkillRequirement(Skill.SMITHING, levelRequired);
    }
}
