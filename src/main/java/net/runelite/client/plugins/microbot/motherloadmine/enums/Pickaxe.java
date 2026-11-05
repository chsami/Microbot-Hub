package net.runelite.client.plugins.microbot.motherloadmine.enums;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.IntPredicate;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;
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
	CRYSTAL_PICKAXE_INACTIVE("crystal pickaxe", 71, 70, DRAGON_PICKAXE, ItemID.CRYSTAL_PICKAXE_INACTIVE),
	CRYSTAL_PICKAXE("crystal pickaxe", 71, 70, ItemID.CRYSTAL_PICKAXE, ItemID.CRYSTAL_PICKAXE_GLOW);

	private final String itemName;
	private final int miningLevel;
	private final int attackLevel;
	private final int tier;
	private final int[] itemIds;

	Pickaxe(String itemName, int miningLevel, int attackLevel, int... itemIds)
	{
		this.itemName = itemName;
		this.miningLevel = miningLevel;
		this.attackLevel = attackLevel;
		this.tier = miningLevel;
		this.itemIds = itemIds;
	}

	Pickaxe(String itemName, int miningLevel, int attackLevel, Pickaxe minesLike, int... itemIds)
	{
		this.itemName = itemName;
		this.miningLevel = miningLevel;
		this.attackLevel = attackLevel;
		this.tier = minesLike.tier;
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
        final Rs2ItemModel equipped = Rs2Equipment.get(EquipmentInventorySlot.WEAPON);
        return bestUsable(Stream.concat(Stream.ofNullable(equipped), Rs2Inventory.items()), Rs2ItemModel::getId,
            level -> Rs2Player.getSkillRequirement(Skill.MINING, level))
            .orElse(null);
    }

    public static <T> Optional<T> bestUsable(Stream<T> items, ToIntFunction<T> idOf, IntPredicate meetsMiningLevel) {
        return items
            .filter(item -> {
                final Pickaxe pickaxe = fromItemId(idOf.applyAsInt(item));
                return pickaxe != null && meetsMiningLevel.test(pickaxe.miningLevel);
            })
            .max(Comparator.comparingInt(item -> tierOf(idOf.applyAsInt(item))));
    }

    /**
     * Gets the best pickaxe from bank (for withdrawing)
     */
    public static Rs2ItemModel getBestPickaxeFromBank() {
        return Rs2Bank.getAll(item -> isPickaxe(item.getId()) && canUse(item.getId()))
            .max(Comparator.comparingInt(item -> tierOf(item.getId())))
            .orElse(null);
    }

    public static boolean isBetterPickaxe(int candidateId, int currentId) {
        return tierOf(candidateId) > tierOf(currentId);
    }

    public static boolean hasUsableBetterTier(int itemId) {
        return hasUsableBetterTier(itemId, level -> Rs2Player.getSkillRequirement(Skill.MINING, level));
    }

    public static boolean hasUsableBetterTier(int itemId, IntPredicate meetsMiningLevel) {
        final int currentTier = tierOf(itemId);
        return Arrays.stream(values())
            .anyMatch(p -> p.tier > currentTier && meetsMiningLevel.test(p.miningLevel));
    }

    private static boolean isPickaxe(int itemId) {
        return fromItemId(itemId) != null;
    }

    private static boolean canUse(int itemId) {
        Pickaxe pickaxe = fromItemId(itemId);
        return pickaxe != null && Rs2Player.getSkillRequirement(Skill.MINING, pickaxe.miningLevel);
    }

    private static int tierOf(int itemId) {
        Pickaxe pickaxe = fromItemId(itemId);
        return pickaxe == null ? 0 : pickaxe.tier;
    }

    public static boolean hasAttackLevelRequirement(int itemId) {
        Pickaxe pickaxe = fromItemId(itemId);
        return pickaxe != null && Rs2Player.getSkillRequirement(Skill.ATTACK, pickaxe.attackLevel);
    }
}
