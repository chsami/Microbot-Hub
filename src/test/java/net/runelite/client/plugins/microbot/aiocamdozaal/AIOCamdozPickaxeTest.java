package net.runelite.client.plugins.microbot.aiocamdozaal;

import java.util.Arrays;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.ItemID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class AIOCamdozPickaxeTest
{
	private static boolean contains(int[] ids, int id)
	{
		return Arrays.stream(ids).anyMatch(candidate -> candidate == id);
	}

	@Test
	void everyPickaxeOptionHasItemsAndAnimations()
	{
		for (AIOCamdozConfig.Pickaxes pickaxe : AIOCamdozConfig.Pickaxes.values())
		{
			assertTrue(AIOCamdozScript.pickaxeIds(pickaxe).length > 0, pickaxe.name());
			assertTrue(AIOCamdozScript.miningAnimationIds(pickaxe).length > 0, pickaxe.name());
		}
	}

	@Test
	void dragonOptionsMatchEveryDragonVariant()
	{
		for (AIOCamdozConfig.Pickaxes pickaxe : new AIOCamdozConfig.Pickaxes[]{AIOCamdozConfig.Pickaxes.DRAGON_PICKAXE, AIOCamdozConfig.Pickaxes.DRAGON_PICKAXE_OR})
		{
			int[] ids = AIOCamdozScript.pickaxeIds(pickaxe);
			assertTrue(contains(ids, ItemID.DRAGON_PICKAXE));
			assertTrue(contains(ids, ItemID.DRAGON_PICKAXE_PRETTY));
			assertTrue(contains(ids, ItemID.ZALCANO_PICKAXE));
			assertTrue(contains(ids, ItemID.TRAILBLAZER_RELOADED_PICKAXE_NO_INFERNAL));
			assertTrue(contains(AIOCamdozScript.miningAnimationIds(pickaxe), AnimationID.HUMAN_MINING_ZALCANO_PICKAXE_WALL));
		}
	}

	@Test
	void infernalOptionsMatchChargedUnchargedAndTrailblazer()
	{
		for (AIOCamdozConfig.Pickaxes pickaxe : new AIOCamdozConfig.Pickaxes[]{AIOCamdozConfig.Pickaxes.INFERNAL_PICKAXE, AIOCamdozConfig.Pickaxes.INFERNAL_PICKAXE_OR, AIOCamdozConfig.Pickaxes.TRAILBLAZER_PICKAXE})
		{
			int[] ids = AIOCamdozScript.pickaxeIds(pickaxe);
			assertTrue(contains(ids, ItemID.INFERNAL_PICKAXE));
			assertTrue(contains(ids, ItemID.INFERNAL_PICKAXE_EMPTY));
			assertTrue(contains(ids, ItemID.TRAILBLAZER_PICKAXE));
			assertTrue(contains(ids, ItemID.TRAILBLAZER_RELOADED_PICKAXE));
		}
	}

	@Test
	void trailblazerOptionsAcceptTrailblazerWithoutInfernal()
	{
		for (AIOCamdozConfig.Pickaxes pickaxe : new AIOCamdozConfig.Pickaxes[]{AIOCamdozConfig.Pickaxes.INFERNAL_PICKAXE_OR, AIOCamdozConfig.Pickaxes.TRAILBLAZER_PICKAXE})
		{
			int[] ids = AIOCamdozScript.pickaxeIds(pickaxe);
			assertTrue(contains(ids, ItemID.TRAILBLAZER_PICKAXE_NO_INFERNAL));
			assertTrue(contains(ids, ItemID.TRAILBLAZER_RELOADED_PICKAXE_NO_INFERNAL));
			assertEquals(ItemID.TRAILBLAZER_RELOADED_PICKAXE_NO_INFERNAL, ids[ids.length - 1]);
			int[] animations = AIOCamdozScript.miningAnimationIds(pickaxe);
			assertTrue(contains(animations, AnimationID.HUMAN_MINING_TRAILBLAZER_PICKAXE_NO_INFERNAL_WALL));
			assertTrue(contains(animations, AnimationID.HUMAN_MINING_TRAILBLAZER_RELOADED_PICKAXE_NO_INFERNAL_WALL));
		}
	}

	@Test
	void selectedVariantIsWithdrawnFirst()
	{
		assertEquals(ItemID.DRAGON_PICKAXE, AIOCamdozScript.pickaxeIds(AIOCamdozConfig.Pickaxes.DRAGON_PICKAXE)[0]);
		assertEquals(ItemID.DRAGON_PICKAXE_PRETTY, AIOCamdozScript.pickaxeIds(AIOCamdozConfig.Pickaxes.DRAGON_PICKAXE_OR)[0]);
		assertEquals(ItemID.INFERNAL_PICKAXE, AIOCamdozScript.pickaxeIds(AIOCamdozConfig.Pickaxes.INFERNAL_PICKAXE)[0]);
		assertEquals(ItemID.TRAILBLAZER_PICKAXE, AIOCamdozScript.pickaxeIds(AIOCamdozConfig.Pickaxes.INFERNAL_PICKAXE_OR)[0]);
		assertEquals(ItemID.TRAILBLAZER_PICKAXE, AIOCamdozScript.pickaxeIds(AIOCamdozConfig.Pickaxes.TRAILBLAZER_PICKAXE)[0]);
		assertEquals(ItemID.TRAILBLAZER_RELOADED_PICKAXE, AIOCamdozScript.pickaxeIds(AIOCamdozConfig.Pickaxes.TRAILBLAZER_PICKAXE)[1]);
	}

	@Test
	void thirdAgeAndCrystalVariantsAreSelectable()
	{
		assertTrue(contains(AIOCamdozScript.pickaxeIds(AIOCamdozConfig.Pickaxes.THIRD_AGE_PICKAXE), ItemID._3A_PICKAXE));
		assertTrue(contains(AIOCamdozScript.pickaxeIds(AIOCamdozConfig.Pickaxes.CRYSTAL_PICKAXE), ItemID.CRYSTAL_PICKAXE_INACTIVE));
		assertTrue(contains(AIOCamdozScript.pickaxeIds(AIOCamdozConfig.Pickaxes.CRYSTAL_PICKAXE), ItemID.CRYSTAL_PICKAXE_GLOW));
	}
}
