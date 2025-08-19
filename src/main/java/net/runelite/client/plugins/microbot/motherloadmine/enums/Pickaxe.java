package net.runelite.client.plugins.microbot.motherloadmine.enums;

import java.util.Arrays;
import java.util.Comparator;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

@Getter
@AllArgsConstructor
public enum Pickaxe
{

	BRONZE_PICKAXE("bronze pickaxe", ItemID.BRONZE_PICKAXE, 1, 1),
	IRON_PICKAXE("iron pickaxe", ItemID.IRON_PICKAXE, 1, 1),
	STEEL_PICKAXE("steel pickaxe", ItemID.STEEL_PICKAXE, 6, 5),
	BLACK_PICKAXE("black pickaxe", ItemID.BLACK_PICKAXE, 11, 10),
	MITHRIL_PICKAXE("mithril pickaxe", ItemID.MITHRIL_PICKAXE, 21, 20),
	ADAMANT_PICKAXE("adamant pickaxe", ItemID.ADAMANT_PICKAXE, 31, 30),
	RUNE_PICKAXE("rune pickaxe", ItemID.RUNE_PICKAXE, 41, 40),
	DRAGON_PICKAXE("dragon pickaxe", ItemID.DRAGON_PICKAXE, 61, 60),
	CRYSTAL_PICKAXE("crystal pickaxe", ItemID.CRYSTAL_PICKAXE, 71, 70);

	private final String itemName;
	private final int itemID;
	private final int miningLevel;
	private final int attackLevel;

	public static boolean hasItem() {
        return getBestPickaxe() != null;
    }

    /**
     * Gets the best pickaxe available for mining (equipped or in inventory)
     */
    public static Rs2ItemModel getBestPickaxe() {
        Rs2ItemModel equipped = Rs2Equipment.get(EquipmentInventorySlot.WEAPON);
        if (equipped != null && isPickaxe(equipped.getId()) && canUse(equipped.getId())) {
            return equipped;
        }

        // Then check inventory
        return Rs2Inventory.items()
            .filter(item -> isPickaxe(item.getId()) && canUse(item.getId()))
            .max(Comparator.comparingInt(item -> getMiningLevel(item.getId())))
            .orElse(null);
    }

    /**
     * Gets the best pickaxe from bank (for withdrawing)
     */
    public static Rs2ItemModel getBestPickaxeFromBank() {
        return Rs2Bank.getAll(item -> isPickaxe(item.getId()) && canUse(item.getId()))
            .max(Comparator.comparingInt(item -> getMiningLevel(item.getId())))
            .orElse(null);
    }

    private static boolean isPickaxe(int itemId) {
        return Arrays.stream(values())
            .anyMatch(p -> p.itemID == itemId);
    }

    private static boolean canUse(int itemId) {
        return Arrays.stream(values())
            .filter(p -> p.itemID == itemId)
            .findFirst()
            .map(p -> Rs2Player.getSkillRequirement(Skill.MINING, p.miningLevel))
            .orElse(false);
    }

    private static int getMiningLevel(int itemId) {
        return Arrays.stream(values())
            .filter(p -> p.itemID == itemId)
            .mapToInt(Pickaxe::getMiningLevel)
            .findFirst()
            .orElse(0);
    }

    public static boolean hasAttackLevelRequirement(int itemId) {
        return Arrays.stream(values())
            .filter(p -> p.itemID == itemId)
            .findFirst()
            .map(p -> Rs2Player.getSkillRequirement(Skill.ATTACK, p.attackLevel))
            .orElse(false);
    }
}
