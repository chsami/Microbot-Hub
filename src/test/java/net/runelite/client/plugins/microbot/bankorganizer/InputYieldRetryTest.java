package net.runelite.client.plugins.microbot.bankorganizer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InputYieldRetryTest
{
	private final List<String> events = new ArrayList<>();

	private final InputYieldRetry.Listener listener = new InputYieldRetry.Listener()
	{
		@Override
		public void waitingForIdle()
		{
			events.add("waiting");
		}

		@Override
		public void retrying(int attempt, int maxAttempts)
		{
			events.add("retry " + attempt + "/" + maxAttempts);
		}
	};

	private InputYieldRetry retry(BooleanSupplier humanOwnsInput, long idleTimeoutMillis)
	{
		return new InputYieldRetry(humanOwnsInput, idleTimeoutMillis, 1L, listener);
	}

	@Test
	void waitsForPlayerInputToClearBeforeTheFirstAttempt() throws InterruptedException
	{
		AtomicInteger humanPolls = new AtomicInteger(3);
		List<Boolean> humanAtAttempt = new ArrayList<>();
		InputYieldRetry retry = retry(() -> humanPolls.getAndDecrement() > 0, 5_000L);

		String result = retry.run(() -> {
			humanAtAttempt.add(humanPolls.get() > 0);
			return "ok";
		}, "ok"::equals, () -> "timeout", last -> "kept " + last);

		assertEquals("ok", result);
		assertEquals(List.of(false), humanAtAttempt);
		assertEquals(List.of("waiting"), events);
	}

	@Test
	void retriesWhenAnAttemptFailsWhilePlayerOwnsInput() throws InterruptedException
	{
		AtomicBoolean human = new AtomicBoolean(false);
		AtomicInteger attempts = new AtomicInteger();
		InputYieldRetry retry = retry(() -> human.getAndSet(false), 5_000L);

		String result = retry.run(() -> {
			if (attempts.incrementAndGet() == 1)
			{
				human.set(true);
				return "Tab 1 did not become active.";
			}
			return "ok";
		}, "ok"::equals, () -> "timeout", last -> "kept " + last);

		assertEquals("ok", result);
		assertEquals(2, attempts.get());
		assertEquals(List.of("retry 2/3"), events);
	}

	@Test
	void doesNotRetryAFailureWithoutPlayerInput() throws InterruptedException
	{
		AtomicInteger attempts = new AtomicInteger();
		InputYieldRetry retry = retry(() -> false, 5_000L);

		String result = retry.run(() -> {
			attempts.incrementAndGet();
			return "Tab 1 does not exist.";
		}, "ok"::equals, () -> "timeout", last -> "kept " + last);

		assertEquals("Tab 1 does not exist.", result);
		assertEquals(1, attempts.get());
		assertTrue(events.isEmpty());
	}

	@Test
	void reportsKeptInterruptingAfterMaxAttempts() throws InterruptedException
	{
		AtomicBoolean human = new AtomicBoolean(false);
		AtomicInteger attempts = new AtomicInteger();
		InputYieldRetry retry = retry(() -> human.getAndSet(false), 5_000L);

		String result = retry.run(() -> {
			attempts.incrementAndGet();
			human.set(true);
			return "Tab 1 did not become active.";
		}, "ok"::equals, () -> "timeout", last -> "kept " + last);

		assertEquals("kept Tab 1 did not become active.", result);
		assertEquals(InputYieldRetry.MAX_ATTEMPTS, attempts.get());
		assertEquals(List.of("retry 2/3", "retry 3/3"), events);
	}

	@Test
	void reportsTimeoutWhenPlayerInputNeverClears() throws InterruptedException
	{
		AtomicInteger attempts = new AtomicInteger();
		InputYieldRetry retry = retry(() -> true, 20L);

		String result = retry.run(() -> {
			attempts.incrementAndGet();
			return "ok";
		}, "ok"::equals, () -> "timeout", last -> "kept " + last);

		assertEquals("timeout", result);
		assertEquals(0, attempts.get());
		assertEquals(List.of("waiting"), events);
	}

	@Test
	void awaitIdleReturnsImmediatelyWithoutPlayerInput() throws InterruptedException
	{
		assertTrue(retry(() -> false, 0L).awaitIdle());
		assertTrue(events.isEmpty());
		assertFalse(retry(() -> true, 0L).awaitIdle());
		assertEquals(List.of("waiting"), events);
	}
}
