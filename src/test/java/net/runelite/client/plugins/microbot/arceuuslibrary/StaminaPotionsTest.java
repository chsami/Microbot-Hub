package net.runelite.client.plugins.microbot.arceuuslibrary;

import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaminaPotionsTest
{
	@Test
	void anyDoseOfSupportedPotionsCounts()
	{
		for (int d = 1; d <= 4; d++)
		{
			assertEquals(StaminaPotions.RANK_EXTENDED_STAMINA, StaminaPotions.rank("Extended stamina potion(" + d + ")"));
			assertEquals(StaminaPotions.RANK_STAMINA, StaminaPotions.rank("Stamina potion(" + d + ")"));
			assertEquals(StaminaPotions.RANK_SUPER_ENERGY, StaminaPotions.rank("Super energy(" + d + ")"));
			assertEquals(StaminaPotions.RANK_ENERGY, StaminaPotions.rank("Energy potion(" + d + ")"));
		}
		assertFalse(StaminaPotions.isPotion("Prayer potion(4)"));
		assertFalse(StaminaPotions.isPotion("Vial"));
		assertFalse(StaminaPotions.isPotion(null));
	}

	@Test
	void preferenceOrderAndLowestDose()
	{
		List<String> names = Arrays.asList("Energy potion(4)", "Super energy(4)", "Stamina potion(4)",
				"Extended stamina potion(4)", "Extended stamina potion(1)");
		assertEquals(4, StaminaPotions.bestIndex(names), "extended first, lowest dose");
		assertEquals(2, StaminaPotions.bestIndex(Arrays.asList("Energy potion(2)", "Super energy(1)", "Stamina potion(3)")));
		assertEquals(-1, StaminaPotions.bestIndex(Collections.singletonList("Shark")));
	}

	@Test
	void drinkDecision()
	{
		assertTrue(StaminaPotions.shouldDrink(true, 30, 30, false, true, true));
		assertFalse(StaminaPotions.shouldDrink(true, 31, 30, false, true, true), "above threshold");
		assertFalse(StaminaPotions.shouldDrink(true, 10, 30, true, true, true), "stamina effect running");
		assertTrue(StaminaPotions.shouldDrink(true, 10, 30, true, true, false), "plain energy ignores stamina effect");
		assertFalse(StaminaPotions.shouldDrink(false, 5, 30, false, true, true), "disabled");
		assertFalse(StaminaPotions.shouldDrink(true, 5, 30, false, false, true), "no potion");
	}

	@Test
	void runToggleAndRestock()
	{
		assertTrue(StaminaPotions.shouldEnableRun(false, 11));
		assertFalse(StaminaPotions.shouldEnableRun(false, 10));
		assertFalse(StaminaPotions.shouldEnableRun(true, 90));
		assertTrue(StaminaPotions.shouldRestock(true, 2, 0, 20, 30, false));
		assertFalse(StaminaPotions.shouldRestock(true, 2, 0, 80, 30, false), "energy still fine");
		assertFalse(StaminaPotions.shouldRestock(true, 2, 1, 20, 30, false), "still holding one");
		assertFalse(StaminaPotions.shouldRestock(true, 0, 0, 20, 30, false), "restock disabled");
		assertFalse(StaminaPotions.shouldRestock(true, 2, 0, 20, 30, true), "gave up");
	}

	@Test
	void withdrawPlan()
	{
		List<StaminaPotions.Stock> bank = Arrays.asList(
				new StaminaPotions.Stock(1, "Extended stamina potion(4)", 16),
				new StaminaPotions.Stock(2, "Extended stamina potion(1)", 1),
				new StaminaPotions.Stock(3, "Super energy(4)", 5));
		assertEquals(Arrays.asList(2, 1), StaminaPotions.planWithdraw(bank, 2));
		assertEquals(Arrays.asList(2, 1, 1, 1), StaminaPotions.planWithdraw(bank, 4));
		assertEquals(0, StaminaPotions.planWithdraw(Collections.emptyList(), 2).size());
	}

	@Test
	void rewardAutoPicksHigherXp()
	{
		assertEquals(RewardXp.MAGIC, RewardXp.AUTO.resolve(1, 1));
		assertEquals(RewardXp.MAGIC, RewardXp.AUTO.resolve(50, 60));
		assertEquals(RewardXp.RUNECRAFT, RewardXp.AUTO.resolve(10, 99));
		assertEquals(RewardXp.RUNECRAFT, RewardXp.RUNECRAFT.resolve(99, 1));
	}

	@Test
	void walkRetargetDedup()
	{
		WorldPoint a = new WorldPoint(1624, 3802, 0);
		assertFalse(LibraryTravel.shouldRetarget(a, new WorldPoint(1626, 3803, 0)), "customer drifted a little");
		assertFalse(LibraryTravel.shouldRetarget(a, a));
		assertTrue(LibraryTravel.shouldRetarget(a, new WorldPoint(1640, 3802, 0)));
		assertTrue(LibraryTravel.shouldRetarget(a, new WorldPoint(1624, 3802, 1)));
		assertFalse(LibraryTravel.shouldRetarget(null, a));
	}
}
