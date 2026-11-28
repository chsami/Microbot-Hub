package net.runelite.client.plugins.microbot.bankorganizer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InputYieldRetryTest
{
	@Test
	void waitsForPlayerInputToClearBeforeTheFirstAttempt() throws InterruptedException
	{
		AtomicInteger humanPolls = new AtomicInteger(3);
		List<Boolean> humanAtAttempt = new ArrayList<>();
		InputYieldRetry retry = new InputYieldRetry(() -> humanPolls.getAndDecrement() > 0, 5_000L, 1L);

		String result = retry.run(() -> {
			humanAtAttempt.add(humanPolls.get() > 0);
			return "ok";
		}, "ok"::equals, () -> "timeout");

		assertEquals("ok", result);
		assertEquals(List.of(false), humanAtAttempt);
	}

	@Test
	void retriesWhenAnAttemptFailsWhilePlayerOwnsInput() throws InterruptedException
	{
		AtomicBoolean human = new AtomicBoolean(false);
		AtomicInteger attempts = new AtomicInteger();
		InputYieldRetry retry = new InputYieldRetry(() -> human.getAndSet(false), 5_000L, 1L);

		String result = retry.run(() -> {
			if (attempts.incrementAndGet() == 1)
			{
				human.set(true);
				return "Tab 1 did not become active.";
			}
			return "ok";
		}, "ok"::equals, () -> "timeout");

		assertEquals("ok", result);
		assertEquals(2, attempts.get());
	}

	@Test
	void doesNotRetryAFailureWithoutPlayerInput() throws InterruptedException
	{
		AtomicInteger attempts = new AtomicInteger();
		InputYieldRetry retry = new InputYieldRetry(() -> false, 5_000L, 1L);

		String result = retry.run(() -> {
			attempts.incrementAndGet();
			return "Tab 1 does not exist.";
		}, "ok"::equals, () -> "timeout");

		assertEquals("Tab 1 does not exist.", result);
		assertEquals(1, attempts.get());
	}

	@Test
	void stopsAfterMaxAttemptsWhenPlayerKeepsInterrupting() throws InterruptedException
	{
		AtomicBoolean human = new AtomicBoolean(false);
		AtomicInteger attempts = new AtomicInteger();
		InputYieldRetry retry = new InputYieldRetry(() -> human.getAndSet(false), 5_000L, 1L);

		String result = retry.run(() -> {
			attempts.incrementAndGet();
			human.set(true);
			return "Tab 1 did not become active.";
		}, "ok"::equals, () -> "timeout");

		assertEquals("Tab 1 did not become active.", result);
		assertEquals(InputYieldRetry.MAX_ATTEMPTS, attempts.get());
	}

	@Test
	void reportsTimeoutWhenPlayerInputNeverClears() throws InterruptedException
	{
		AtomicInteger attempts = new AtomicInteger();
		InputYieldRetry retry = new InputYieldRetry(() -> true, 20L, 1L);

		String result = retry.run(() -> {
			attempts.incrementAndGet();
			return "ok";
		}, "ok"::equals, () -> "timeout");

		assertEquals("timeout", result);
		assertEquals(0, attempts.get());
	}

	@Test
	void awaitIdleReturnsImmediatelyWithoutPlayerInput() throws InterruptedException
	{
		assertTrue(new InputYieldRetry(() -> false, 0L, 1L).awaitIdle());
		assertFalse(new InputYieldRetry(() -> true, 0L, 1L).awaitIdle());
	}
}
