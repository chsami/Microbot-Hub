package net.runelite.client.plugins.microbot.droinfernoprayers.prayer;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GuardPlannerTest
{
	private static Threat t(Guard guard, int tick, int weight)
	{
		return new Threat("mob", guard, tick, weight);
	}

	@Test
	void nothingDueMeansNoOverhead()
	{
		GuardPlanner.Decision d = GuardPlanner.decide(List.of(t(Guard.RANGE, 13, 46)), 11, Guard.RANGE);
		assertEquals(Guard.NONE, d.guard());
		assertNull(d.answered());
	}

	@Test
	void onlyTheAttackCheckedNextTickIsAnswered()
	{
		GuardPlanner.Decision d = GuardPlanner.decide(List.of(t(Guard.MAGIC, 12, 70), t(Guard.RANGE, 11, 46)), 11, Guard.NONE);
		assertEquals(Guard.RANGE, d.guard());
		assertFalse(d.conflict());
	}

	@Test
	void sameTickStylesProtectTheHeavierHit()
	{
		GuardPlanner.Decision d = GuardPlanner.decide(List.of(t(Guard.RANGE, 11, 46), t(Guard.MAGIC, 11, 70)), 11, Guard.RANGE);
		assertEquals(Guard.MAGIC, d.guard());
		assertTrue(d.conflict());
	}

	@Test
	void equalWeightsKeepTheOverheadAlreadyUp()
	{
		GuardPlanner.Decision d = GuardPlanner.decide(List.of(t(Guard.MAGIC, 11, 18), t(Guard.RANGE, 11, 18)), 11, Guard.RANGE);
		assertEquals(Guard.RANGE, d.guard());
	}

	@Test
	void passedAttacksAreIgnored()
	{
		GuardPlanner.Decision d = GuardPlanner.decide(List.of(t(Guard.MAGIC, 10, 70)), 11, Guard.MAGIC);
		assertEquals(Guard.NONE, d.guard());
		assertEquals(Guard.MAGIC, GuardPlanner.upcoming(List.of(t(Guard.MAGIC, 14, 70)), 11).guard());
	}
}
