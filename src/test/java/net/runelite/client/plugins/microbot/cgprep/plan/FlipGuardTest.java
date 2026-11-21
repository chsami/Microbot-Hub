package net.runelite.client.plugins.microbot.cgprep.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 1.8.3: the flip-flop guard. */
class FlipGuardTest
{
	private final PrepGoal home = PrepGoal.of(PrepGoal.Type.RETURN_TO_START, "depot");
	private final PrepGoal lap = PrepGoal.explore(new int[]{6, 0}, "outer-ring lap");

	@Test
	void holdsTheFirstGoalAfterStrictAlternation()
	{
		FlipGuard f = new FlipGuard();
		int t = 200;
		PrepGoal out = null;
		for (int i = 0; i < 6; i++)
		{
			out = f.filter(i % 2 == 0 ? home : lap, t--, 1);
		}
		assertEquals(PrepGoal.Type.RETURN_TO_START, out.type);
		assertTrue(out.why.startsWith("holding"));
		assertEquals(1, f.holds);
		// keeps holding while the planner says "lap"
		assertEquals(PrepGoal.Type.RETURN_TO_START, f.filter(lap, t--, 1).type);
	}

	@Test
	void letsGoWhenTheBagChangesOrTimePasses()
	{
		FlipGuard f = new FlipGuard();
		int t = 200;
		for (int i = 0; i < 6; i++)
		{
			f.filter(i % 2 == 0 ? home : lap, t--, 1);
		}
		assertEquals(PrepGoal.Type.EXPLORE, f.filter(lap, t, 2).type); // something got picked up
		FlipGuard g = new FlipGuard();
		t = 200;
		for (int i = 0; i < 6; i++)
		{
			g.filter(i % 2 == 0 ? home : lap, t--, 1);
		}
		assertEquals(PrepGoal.Type.EXPLORE, g.filter(lap, t - FlipGuard.HOLD_SECONDS, 1).type);
	}

	@Test
	void normalSequencesPassThrough()
	{
		FlipGuard f = new FlipGuard();
		PrepGoal fish = PrepGoal.gather(net.runelite.client.plugins.microbot.cgprep.data.Resource.RAW_FISH, "fish");
		PrepGoal[] seq = {lap, fish, lap, home, lap, fish, home, home, lap};
		int t = 300;
		for (PrepGoal g : seq)
		{
			assertEquals(g.type, f.filter(g, t--, 1).type);
		}
		assertEquals(0, f.holds);
	}

	@Test
	void sameGoalRepeatedIsNotAFlip()
	{
		FlipGuard f = new FlipGuard();
		for (int i = 0; i < 20; i++)
		{
			assertEquals(PrepGoal.Type.EXPLORE, f.filter(lap, 300 - i, 1).type);
		}
		assertEquals(0, f.holds);
	}
}
