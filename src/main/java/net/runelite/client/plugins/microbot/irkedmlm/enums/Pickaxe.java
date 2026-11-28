package net.runelite.client.plugins.microbot.irkedmlm.enums;

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

/**
 * Every pickaxe variant with its Mining and Attack requirement, a tier for ranking (variants mine
 * like their base pickaxe), and which special attack it has. The variant ids follow the Hub's
 * Motherload Mine plugin; levels and specials checked on the OSRS wiki.
 */
@Getter
public enum Pickaxe
{
	BRONZE_PICKAXE("bronze pickaxe", 1, 1, Spec.NONE, ItemID.BRONZE_PICKAXE),
	IRON_PICKAXE("iron pickaxe", 1, 1, Spec.NONE, ItemID.IRON_PICKAXE),
	STEEL_PICKAXE("steel pickaxe", 6, 5, Spec.NONE, ItemID.STEEL_PICKAXE),
	BLACK_PICKAXE("black pickaxe", 11, 10, Spec.NONE, ItemID.BLACK_PICKAXE),
	MITHRIL_PICKAXE("mithril pickaxe", 21, 20, Spec.NONE, ItemID.MITHRIL_PICKAXE),
	ADAMANT_PICKAXE("adamant pickaxe", 31, 30, Spec.NONE, ItemID.ADAMANT_PICKAXE),
	RUNE_PICKAXE("rune pickaxe", 41, 40, Spec.NONE, ItemID.RUNE_PICKAXE),
	GILDED_PICKAXE("gilded pickaxe", 41, 40, Spec.NONE, ItemID.TRAIL_GILDED_PICKAXE),
	DRAGON_PICKAXE("dragon pickaxe", 61, 60, Spec.DRAGON, ItemID.DRAGON_PICKAXE, ItemID.DRAGON_PICKAXE_PRETTY,
		ItemID.ZALCANO_PICKAXE, ItemID.ZALCANO_PICKAXE_SPECIAL_GLOW,
		ItemID.TRAILBLAZER_PICKAXE_NO_INFERNAL, ItemID.TRAILBLAZER_RELOADED_PICKAXE_NO_INFERNAL),
	INFERNAL_PICKAXE("infernal pickaxe", 61, 60, Spec.DRAGON, ItemID.INFERNAL_PICKAXE, ItemID.INFERNAL_PICKAXE_EMPTY,
		ItemID.TRAILBLAZER_PICKAXE, ItemID.TRAILBLAZER_PICKAXE_EMPTY,
		ItemID.TRAILBLAZER_RELOADED_PICKAXE, ItemID.TRAILBLAZER_RELOADED_PICKAXE_EMPTY),
	THIRD_AGE_PICKAXE("3rd age pickaxe", 61, 65, Spec.DRAGON, ItemID._3A_PICKAXE),
	CRYSTAL_PICKAXE_INACTIVE("crystal pickaxe", 71, 70, Spec.NONE, new int[]{ItemID.CRYSTAL_PICKAXE_INACTIVE}, 61),
	CRYSTAL_PICKAXE("crystal pickaxe", 71, 70, Spec.CRYSTAL, ItemID.CRYSTAL_PICKAXE, ItemID.CRYSTAL_PICKAXE_GLOW);

	/** The special attack a pickaxe carries: the dragon family boosts Mining, crystal is its own. */
	public enum Spec { NONE, DRAGON, CRYSTAL }

	private final String itemName;
	private final int miningLevel;
	private final int attackLevel;
	private final Spec spec;
	private final int tier;
	private final int[] itemIds;

	Pickaxe(String itemName, int miningLevel, int attackLevel, Spec spec, int... itemIds)
	{
		this(itemName, miningLevel, attackLevel, spec, itemIds, miningLevel);
	}

	/** For a variant that mines at a lower tier than its requirement suggests (an inactive crystal pickaxe mines like dragon). */
	Pickaxe(String itemName, int miningLevel, int attackLevel, Spec spec, int[] itemIds, int tier)
	{
		this.itemName = itemName;
		this.miningLevel = miningLevel;
		this.attackLevel = attackLevel;
		this.spec = spec;
		this.tier = tier;
		this.itemIds = itemIds;
	}

	public static Pickaxe fromItemId(int itemId)
	{
		return Arrays.stream(values())
			.filter(p -> Arrays.stream(p.itemIds).anyMatch(id -> id == itemId))
			.findFirst()
			.orElse(null);
	}

	public static boolean isListed(int itemId)
	{
		return fromItemId(itemId) != null;
	}

	/** The special the wielded pickaxe has, NONE when it has none or nothing is wielded. */
	public static Spec wieldedSpec()
	{
		Rs2ItemModel weapon = Rs2Equipment.get(EquipmentInventorySlot.WEAPON);
		Pickaxe p = weapon != null ? fromItemId(weapon.getId()) : null;
		return p != null ? p.spec : Spec.NONE;
	}

	public static boolean hasItem()
	{
		return getBestPickaxe() != null;
	}

	/** The best pickaxe the player can mine with, wielded or carried. */
	public static Rs2ItemModel getBestPickaxe()
	{
		Rs2ItemModel equipped = Rs2Equipment.get(EquipmentInventorySlot.WEAPON);
		return bestUsable(Stream.concat(Stream.ofNullable(equipped), Rs2Inventory.items()), Rs2ItemModel::getId,
			level -> Rs2Player.getSkillRequirement(Skill.MINING, level)).orElse(null);
	}

	public static <T> Optional<T> bestUsable(Stream<T> items, ToIntFunction<T> idOf, IntPredicate meetsMiningLevel)
	{
		return items
			.filter(item -> {
				Pickaxe p = fromItemId(idOf.applyAsInt(item));
				return p != null && meetsMiningLevel.test(p.miningLevel);
			})
			.max(Comparator.comparingInt(item -> tierOf(idOf.applyAsInt(item))));
	}

	/** Best usable pickaxe in the bank. Only meaningful while the bank interface is open. */
	public static Rs2ItemModel getBestPickaxeFromBank()
	{
		return Rs2Bank.getAll(item -> canMineWith(item.getId()))
			.max(Comparator.comparingInt(item -> tierOf(item.getId())))
			.orElse(null);
	}

	public static boolean isBetterPickaxe(int candidateId, int currentId)
	{
		return tierOf(candidateId) > tierOf(currentId);
	}

	/** Whether any pickaxe above this one's tier is within the player's Mining level. */
	public static boolean hasUsableBetterTier(int itemId, IntPredicate meetsMiningLevel)
	{
		int currentTier = tierOf(itemId);
		return Arrays.stream(values()).anyMatch(p -> p.tier > currentTier && meetsMiningLevel.test(p.miningLevel));
	}

	public static boolean canWield(int itemId)
	{
		Pickaxe p = fromItemId(itemId);
		return p != null && Rs2Player.getSkillRequirement(Skill.ATTACK, p.attackLevel);
	}

	private static boolean canMineWith(int itemId)
	{
		Pickaxe p = fromItemId(itemId);
		return p != null && Rs2Player.getSkillRequirement(Skill.MINING, p.miningLevel);
	}

	static int tierOf(int itemId)
	{
		Pickaxe p = fromItemId(itemId);
		return p == null ? 0 : p.tier;
	}
}
