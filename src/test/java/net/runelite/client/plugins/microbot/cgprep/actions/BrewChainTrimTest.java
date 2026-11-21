package net.runelite.client.plugins.microbot.cgprep.actions;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** 1.8.5: brewing bit by bit on short runs. */
class BrewChainTrimTest
{
	@Test
	void aShortRunGetsTheStepsThatFit()
	{
		BrewChain c = new BrewChain();
		// 2 herbs, 2 crushes, 2 potions = 6 ticks in full
		assertEquals(6, c.plan(null, 2, 2, 0, 0, 40, true, 3, 2));
		assertEquals(3, c.plan(null, 2, 2, 0, 0, 40, true, 3, 2, 3)); // herbs + one crush
		assertEquals(1, c.plan(null, 2, 2, 0, 0, 40, true, 3, 2, 1)); // one herb click (it repeats by itself)
		assertEquals(6, c.plan(null, 2, 2, 0, 0, 40, true, 3, 2, 10));
		c.shutdown();
	}
}
