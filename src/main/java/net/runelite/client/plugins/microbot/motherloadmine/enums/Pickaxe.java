package net.runelite.client.plugins.microbot.motherloadmine.enums;

import java.util.Arrays;
import java.util.Comparator;
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
public enum Pickaxe
{

	BRONZE_PICKAXE("bronze pickaxe", 1, 1, ItemID.BRONZE_PICKAXE),
	IRON_PICKAXE("iron pickaxe", 1, 1, ItemID.IRON_PICKAXE),
	STEEL_PICKAXE("steel pickaxe", 6, 5, ItemID.STEEL_PICKAXE),
	BLACK_PICKAXE("black pickaxe", 11, 10, ItemID.BLACK_PICKAXE),
	MITHRIL_PICKAXE("mithril pickaxe", 21, 20, ItemID.MITHRIL_PICKAXE),
	ADAMANT_PICKAXE("adamant pickaxe", 31, 30, ItemID.ADAMANT_PICKAXE),
	RUNE_PICKAXE("rune pickaxe", 41, 40, ItemID.RUNE_PICKAXE),
	GILDED_PICKAXE("gilded pickaxe", 41, 40, ItemID.TRAIL_GILDED_PICKAXE),
	DRAGON_PICKAXE("dragon pickaxe", 61, 60, ItemID.DRAGON_PICKAXE, ItemID.DRAGON_PICKAXE_PRETTY,
		ItemID.ZALCANO_PICKAXE, ItemID.ZALCANO_PICKAXE_SPECIAL_GLOW,
		ItemID.TRAILBLAZER_PICKAXE_NO_INFERNAL, ItemID.TRAILBLAZER_RELOADED_PICKAXE_NO_INFERNAL),
	INFERNAL_PICKAXE("infernal pickaxe", 61, 60, ItemID.INFERNAL_PICKAXE, ItemID.INFERNAL_PICKAXE_EMPTY,
		ItemID.TRAILBLAZER_PICKAXE, ItemID.TRAILBLAZER_PICKAXE_EMPTY,
		ItemID.TRAILBLAZER_RELOADED_PICKAXE, ItemID.TRAILBLAZER_RELOADED_PICKAXE_EMPTY),
	THIRD_AGE_PICKAXE("3rd age pickaxe", 61, 65, ItemID._3A_PICKAXE),
	CRYSTAL_PICKAXE("crystal pickaxe", 71, 70, ItemID.CRYSTAL_PICKAXE, ItemID.CRYSTAL_PICKAXE_INACTIVE,
		ItemID.CRYSTAL_PICKAXE_GLOW);

	private final String itemName;
	private final int miningLevel;
	private final int attackLevel;
	private final int[] itemIds;

	Pickaxe(String itemName, int miningLevel, int attackLevel, int... itemIds)
	{
		this.itemName = itemName;
		this.miningLevel = miningLevel;
		this.attackLevel = attackLevel;
		this.itemIds = itemIds;
	}

	public static Pickaxe fromItemId(int itemId) {
		return Arrays.stream(values())
			.filter(p -> Arrays.stream(p.itemIds).anyMatch(id -> id == itemId))
			.findFirst()
			.orElse(null);
	}

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

    public static boolean isBetterPickaxe(int candidateId, int currentId) {
        return getMiningLevel(candidateId) > getMiningLevel(currentId);
    }

    private static boolean isPickaxe(int itemId) {
        return fromItemId(itemId) != null;
    }

    private static boolean canUse(int itemId) {
        Pickaxe pickaxe = fromItemId(itemId);
        return pickaxe != null && Rs2Player.getSkillRequirement(Skill.MINING, pickaxe.miningLevel);
    }

    private static int getMiningLevel(int itemId) {
        Pickaxe pickaxe = fromItemId(itemId);
        return pickaxe == null ? 0 : pickaxe.miningLevel;
    }

    public static boolean hasAttackLevelRequirement(int itemId) {
        Pickaxe pickaxe = fromItemId(itemId);
        return pickaxe != null && Rs2Player.getSkillRequirement(Skill.ATTACK, pickaxe.attackLevel);
    }
}
