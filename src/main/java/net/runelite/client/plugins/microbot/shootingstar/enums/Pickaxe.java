package net.runelite.client.plugins.microbot.shootingstar.enums;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import lombok.Getter;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
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
	GILDED_PICKAXE("gilded pickaxe", 41, 40, ItemID.TRAIL_GILDED_PICKAXE),
	RUNE_PICKAXE("rune pickaxe", 41, 40, ItemID.RUNE_PICKAXE),
	THIRD_AGE_PICKAXE("3rd age pickaxe", 61, 65, ItemID._3A_PICKAXE),
	CRYSTAL_PICKAXE_INACTIVE("crystal pickaxe", 71, 70, ItemID.CRYSTAL_PICKAXE_INACTIVE),
	DRAGON_PICKAXE("dragon pickaxe", 61, 60, ItemID.DRAGON_PICKAXE, ItemID.DRAGON_PICKAXE_PRETTY,
		ItemID.ZALCANO_PICKAXE, ItemID.ZALCANO_PICKAXE_SPECIAL_GLOW,
		ItemID.TRAILBLAZER_PICKAXE_NO_INFERNAL, ItemID.TRAILBLAZER_RELOADED_PICKAXE_NO_INFERNAL),
	INFERNAL_PICKAXE("infernal pickaxe", 61, 60, ItemID.INFERNAL_PICKAXE, ItemID.INFERNAL_PICKAXE_EMPTY,
		ItemID.TRAILBLAZER_PICKAXE, ItemID.TRAILBLAZER_PICKAXE_EMPTY,
		ItemID.TRAILBLAZER_RELOADED_PICKAXE, ItemID.TRAILBLAZER_RELOADED_PICKAXE_EMPTY),
	CRYSTAL_PICKAXE("crystal pickaxe", 71, 70, ItemID.CRYSTAL_PICKAXE, ItemID.CRYSTAL_PICKAXE_GLOW);

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

	public boolean hasRequirements()
	{
		return Rs2Player.getSkillRequirement(Skill.MINING, this.miningLevel) && Rs2Player.getSkillRequirement(Skill.ATTACK, this.attackLevel);
	}

	public boolean isWithdrawableFromBank()
	{
		return this != THIRD_AGE_PICKAXE;
	}

	public static Pickaxe fromItemId(int itemId)
	{
		return Arrays.stream(values())
			.filter(p -> Arrays.stream(p.itemIds).anyMatch(id -> id == itemId))
			.findFirst()
			.orElse(null);
	}

	public static <T> Optional<T> best(Collection<T> items, ToIntFunction<T> idOf, Predicate<Pickaxe> usable)
	{
		return items.stream()
			.filter(Objects::nonNull)
			.filter(item -> {
				Pickaxe pickaxe = fromItemId(idOf.applyAsInt(item));
				return pickaxe != null && usable.test(pickaxe);
			})
			.max(Comparator.comparingInt(item -> fromItemId(idOf.applyAsInt(item)).ordinal()));
	}
}
