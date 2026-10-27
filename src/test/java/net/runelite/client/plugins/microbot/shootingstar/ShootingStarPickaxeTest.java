package net.runelite.client.plugins.microbot.shootingstar;

import java.util.Arrays;
import java.util.List;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.shootingstar.enums.Pickaxe;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

public final class ShootingStarPickaxeTest
{
	@Test
	void recognisesVariantPickaxeIds()
	{
		assertEquals(Pickaxe.DRAGON_PICKAXE, Pickaxe.fromItemId(ItemID.DRAGON_PICKAXE_PRETTY));
		assertEquals(Pickaxe.DRAGON_PICKAXE, Pickaxe.fromItemId(ItemID.ZALCANO_PICKAXE));
		assertEquals(Pickaxe.DRAGON_PICKAXE, Pickaxe.fromItemId(ItemID.TRAILBLAZER_RELOADED_PICKAXE_NO_INFERNAL));
		assertEquals(Pickaxe.INFERNAL_PICKAXE, Pickaxe.fromItemId(ItemID.INFERNAL_PICKAXE));
		assertEquals(Pickaxe.INFERNAL_PICKAXE, Pickaxe.fromItemId(ItemID.INFERNAL_PICKAXE_EMPTY));
		assertEquals(Pickaxe.INFERNAL_PICKAXE, Pickaxe.fromItemId(ItemID.TRAILBLAZER_PICKAXE));
		assertEquals(Pickaxe.INFERNAL_PICKAXE, Pickaxe.fromItemId(ItemID.TRAILBLAZER_RELOADED_PICKAXE_EMPTY));
		assertEquals(Pickaxe.THIRD_AGE_PICKAXE, Pickaxe.fromItemId(ItemID._3A_PICKAXE));
		assertEquals(Pickaxe.GILDED_PICKAXE, Pickaxe.fromItemId(ItemID.TRAIL_GILDED_PICKAXE));
		assertEquals(Pickaxe.CRYSTAL_PICKAXE_INACTIVE, Pickaxe.fromItemId(ItemID.CRYSTAL_PICKAXE_INACTIVE));
		assertEquals(Pickaxe.CRYSTAL_PICKAXE, Pickaxe.fromItemId(ItemID.CRYSTAL_PICKAXE_GLOW));
		assertNull(Pickaxe.fromItemId(ItemID.DRAGON_PICKAXE_UPGRADE_KIT));
	}

	@Test
	void picksHighestUsableTierFromVariantIds()
	{
		List<Integer> items = Arrays.asList(ItemID.RUNE_PICKAXE, ItemID.INFERNAL_PICKAXE_EMPTY, ItemID.COINS);

		assertEquals(ItemID.INFERNAL_PICKAXE_EMPTY, Pickaxe.best(items, Integer::intValue, p -> true).orElseThrow());
		assertEquals(ItemID.RUNE_PICKAXE, Pickaxe.best(items, Integer::intValue, p -> p.getMiningLevel() <= 41).orElseThrow());
	}

	@Test
	void prefersActiveCrystalOverInactive()
	{
		List<Integer> items = Arrays.asList(ItemID.CRYSTAL_PICKAXE, ItemID.CRYSTAL_PICKAXE_INACTIVE, ItemID._3A_PICKAXE);

		assertEquals(ItemID.CRYSTAL_PICKAXE, Pickaxe.best(items, Integer::intValue, p -> true).orElseThrow());
	}

	@Test
	void findsNothingWithoutAPickaxe()
	{
		List<Integer> items = Arrays.asList(ItemID.COINS, ItemID.DRAGON_PICKAXE_UPGRADE_KIT);

		assertFalse(Pickaxe.best(items, Integer::intValue, p -> true).isPresent());
	}
}
