package net.runelite.client.plugins.microbot.cgprep.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 1.8.2: a death is counted once per run, whichever way it's seen and in whatever order. */
class RunStatsDeathTest
{
	private final List<String> log = new ArrayList<>();

	private RunStats stats()
	{
		return new RunStats(null, null, (k, v) -> log.add(k + " " + v));
	}

	@Test
	void bossDeathSeenThreeWaysCountsOnce()
	{
		RunStats s = stats();
		s.runStarted();
		s.sawBoss();
		s.died(true, "death");                                   // the death event, still inside
		s.onLeftGauntlet(100);
		s.onChat("Oh dear, you are dead!", false, false, 102);   // the message after the teleport
		s.runEnded();
		s.died(true, "the fight saw 0 HP");                      // the fight's check, after runEnded
		assertEquals(1, s.bossDeaths());
		assertEquals(0, s.setupDeaths());
		assertTrue(s.diedThisRun());
	}

	@Test
	void lateMessageAloneCountsTheDeath()
	{
		RunStats s = stats();
		s.runStarted();
		s.sawBoss();
		s.onLeftGauntlet(100);
		s.onChat("Oh dear, you are dead!", false, false, 104);
		assertEquals(1, s.bossDeaths());
	}

	@Test
	void messageLongAfterLeavingIsIgnored()
	{
		RunStats s = stats();
		s.runStarted();
		s.onLeftGauntlet(100);
		s.onChat("Oh dear, you are dead!", false, false, 100 + RunStats.DEATH_GRACE_TICKS + 1);
		assertEquals(0, s.bossDeaths() + s.setupDeaths());
	}

	@Test
	void prepDeathIsASetupDeath()
	{
		RunStats s = stats();
		s.runStarted();
		s.died(false, "death");
		assertEquals(1, s.setupDeaths());
		assertEquals(0, s.bossDeaths());
	}

	@Test
	void nextRunCountsAgain()
	{
		RunStats s = stats();
		s.runStarted();
		s.sawBoss();
		s.died(true, "death");
		s.runEnded();
		s.runStarted();
		s.sawBoss();
		s.died(true, "death");
		assertEquals(2, s.bossDeaths());
		assertEquals(1, s.runs());
	}
}
