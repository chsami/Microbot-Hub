package net.runelite.client.plugins.microbot.drocolosseumprayers.prayer;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TickPrayerLedgerTest
{
	private static final long TICK = 600_000_000L;
	private static final long EARLY = 50_000_000L;
	private static final long LATE = 400_000_000L;

	/** Three ticks on cadence: the ledger is synchronised. */
	private static TickPrayerLedger steady(Guard observed)
	{
		TickPrayerLedger ledger = new TickPrayerLedger();
		for (int i = 0; i < 3; i++)
		{
			ledger.beginTick(10 + i, i * TICK, observed);
		}
		assertTrue(ledger.synchronised());
		return ledger;
	}

	private static long tickStart()
	{
		return 2 * TICK;
	}

	@Test
	void switchesWithOneOnAndNeverAnOffFirst()
	{
		TickPrayerLedger ledger = steady(Guard.RANGE);
		List<TickPrayerLedger.Command> plan = ledger.plan(tickStart() + LATE, Guard.RANGE, Guard.MAGIC, true);
		assertEquals(1, plan.size());
		assertEquals(TickPrayerLedger.Type.ON, plan.get(0).type());
		assertEquals(Guard.MAGIC, plan.get(0).guard());
	}

	@Test
	void onIsSentOnceUntilConfirmed()
	{
		TickPrayerLedger ledger = steady(Guard.NONE);
		TickPrayerLedger.Command on = ledger.plan(tickStart() + EARLY, Guard.NONE, Guard.MELEE, true).get(0);
		ledger.sent(on);
		assertTrue(ledger.plan(tickStart() + LATE, Guard.NONE, Guard.MELEE, true).isEmpty());
		assertTrue(ledger.committed(Guard.NONE, Guard.MELEE), "an ON sent this tick counts until the next tick");
		ledger.beginTick(13, tickStart() + TICK, Guard.MELEE);
		assertFalse(ledger.waiting());
	}

	@Test
	void unconfirmedInputIsAMissAndDropsSync()
	{
		TickPrayerLedger ledger = steady(Guard.NONE);
		ledger.sent(new TickPrayerLedger.Command(Guard.MAGIC, TickPrayerLedger.Type.ON));
		ledger.beginTick(13, tickStart() + TICK, Guard.NONE);
		ledger.beginTick(14, tickStart() + 2 * TICK, Guard.NONE);
		assertEquals(1, ledger.misses());
		assertFalse(ledger.synchronised());
	}

	@Test
	void releaseOnlyEarlyInASteadyTick()
	{
		TickPrayerLedger ledger = steady(Guard.MAGIC);
		assertTrue(ledger.plan(tickStart() + LATE, Guard.MAGIC, Guard.NONE, true).isEmpty(), "never a late OFF");
		List<TickPrayerLedger.Command> plan = ledger.plan(tickStart() + EARLY, Guard.MAGIC, Guard.NONE, true);
		assertEquals(TickPrayerLedger.Type.OFF, plan.get(0).type());
	}

	@Test
	void heldOverheadGetsOnePairEarlyInTheTick()
	{
		TickPrayerLedger ledger = steady(Guard.RANGE);
		List<TickPrayerLedger.Command> plan = ledger.plan(tickStart() + EARLY, Guard.RANGE, Guard.RANGE, true);
		assertEquals(TickPrayerLedger.Type.PAIR, plan.get(0).type());
		ledger.sent(plan.get(0));
		assertTrue(ledger.plan(tickStart() + EARLY + 1, Guard.RANGE, Guard.RANGE, true).isEmpty(), "one pair per tick");
		assertFalse(ledger.waiting(), "a pair ends on, nothing to confirm");
	}

	@Test
	void noPairWhenNotAllowedLateOrUnsynchronised()
	{
		TickPrayerLedger ledger = steady(Guard.RANGE);
		assertTrue(ledger.plan(tickStart() + EARLY, Guard.RANGE, Guard.RANGE, false).isEmpty());
		assertTrue(ledger.plan(tickStart() + LATE, Guard.RANGE, Guard.RANGE, true).isEmpty());
		TickPrayerLedger fresh = new TickPrayerLedger();
		fresh.beginTick(1, 0, Guard.RANGE);
		assertTrue(fresh.plan(EARLY, Guard.RANGE, Guard.RANGE, true).isEmpty());
	}

	@Test
	void unevenTickBreaksTheCadence()
	{
		TickPrayerLedger ledger = steady(Guard.NONE);
		ledger.beginTick(13, tickStart() + 1_000_000_000L, Guard.NONE);
		assertFalse(ledger.synchronised());
	}

	@Test
	void staleTickPlansNothing()
	{
		TickPrayerLedger ledger = steady(Guard.NONE);
		assertTrue(ledger.plan(tickStart() + 2_000_000_000L, Guard.NONE, Guard.MAGIC, true).isEmpty());
	}
}
