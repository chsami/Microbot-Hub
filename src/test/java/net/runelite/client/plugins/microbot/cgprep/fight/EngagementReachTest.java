package net.runelite.client.plugins.microbot.cgprep.fight;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.Set;
import net.runelite.client.plugins.microbot.cgprep.data.Monster;
import org.junit.jupiter.api.Test;

/** 1.8.1: a melee monster that can't reach us isn't prayed against, unless it swings. */
class EngagementReachTest
{
	private static boolean meleeThreat(Engagement e, boolean cantReach, boolean attacked)
	{
		Set<Integer> alive = Collections.singleton(1);
		e.tick(Collections.singletonList(new Engagement.Seen(1, Monster.WEAK, 1, true, attacked, cantReach)), alive);
		return e.engagedMelee() > 0;
	}

	@Test
	void reachableMeleeIsAThreat()
	{
		assertTrue(meleeThreat(new Engagement(), false, false));
	}

	@Test
	void stuckMeleeIsNot()
	{
		Engagement e = new Engagement();
		assertFalse(meleeThreat(e, true, false));
		assertTrue(e.stuckMelee() == 1);
	}

	@Test
	void stuckMeleeThatSwingsAnywayIs()
	{
		assertTrue(meleeThreat(new Engagement(), true, true));
	}

	@Test
	void stuckThenWeMoveIsAThreatAgain()
	{
		Engagement e = new Engagement();
		assertFalse(meleeThreat(e, true, false));
		assertTrue(meleeThreat(e, false, false));
	}

	@Test
	void stuckBearIsNotPrayedAgainst()
	{
		Engagement e = new Engagement();
		e.tick(Collections.singletonList(new Engagement.Seen(1, Monster.BEAR, 1, true, false, true)), Collections.singleton(1));
		assertTrue(e.engagedDemi() == null);
		e.tick(Collections.singletonList(new Engagement.Seen(1, Monster.BEAR, 1, true, false, false)), Collections.singleton(1));
		assertTrue(e.engagedDemi() == Monster.BEAR);
	}
}
