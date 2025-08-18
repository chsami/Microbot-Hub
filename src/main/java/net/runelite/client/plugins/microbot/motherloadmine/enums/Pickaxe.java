package net.runelite.client.plugins.microbot.motherloadmine.enums;

import java.util.Arrays;
import java.util.Comparator;
import java.util.function.Predicate;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.Getter;
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
        return getBestPickaxe(true) != null || getBestPickaxe(false) != null;
    }

    private static Predicate<Rs2ItemModel> usablePickaxePredicate(boolean inventory) {
        return item -> Arrays.stream(Pickaxe.values())
            .filter(p -> p.getItemID() == item.getId())
            .anyMatch(p -> Rs2Player.getSkillRequirement(Skill.MINING, p.getMiningLevel()) &&
                          (!inventory || Rs2Player.getSkillRequirement(Skill.ATTACK, p.getAttackLevel())));
    }

    public static Rs2ItemModel getBestPickaxe(boolean inventory) {
        Stream<Rs2ItemModel> items = inventory ? Rs2Inventory.items() : Rs2Equipment.all();
        return items.filter(usablePickaxePredicate(inventory))
            .max(Comparator.comparingInt(item -> Arrays.stream(Pickaxe.values())
                .filter(p -> p.getItemID() == item.getId())
                .mapToInt(Pickaxe::getMiningLevel)
                .findFirst().orElse(0)))
            .orElse(null);
    }

    public static Rs2ItemModel getBestBankedPickaxe(boolean inventory) {
        return Rs2Bank.getAll(usablePickaxePredicate(inventory))
            .max(Comparator.comparingInt(item -> Arrays.stream(Pickaxe.values())
                .filter(p -> p.getItemID() == item.getId())
                .mapToInt(Pickaxe::getMiningLevel)
                .findFirst().orElse(0)))
            .orElse(null);
    }

    public static boolean hasAttackLevelRequirement(int itemId) {
        return Arrays.stream(Pickaxe.values())
            .filter(p -> p.getItemID() == itemId)
            .findFirst()
            .map(p -> Rs2Player.getSkillRequirement(Skill.ATTACK, p.getAttackLevel()))
            .orElse(false);
    }
}
