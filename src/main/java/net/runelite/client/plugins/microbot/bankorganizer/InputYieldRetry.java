/*
 * Copyright (c) 2026, bgatfa
 * All rights reserved. Redistribution and use in source and binary forms, with
 * or without modification, are permitted provided the copyright notice is kept.
 */
package net.runelite.client.plugins.microbot.bankorganizer;

import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;

final class InputYieldRetry
{
	static final int MAX_ATTEMPTS = 3;
	static final long IDLE_TIMEOUT_MILLIS = 30_000L;
	private static final long POLL_MILLIS = 100L;

	interface Listener
	{
		void waitingForIdle();

		void retrying(int attempt, int maxAttempts);
	}

	private final BooleanSupplier humanOwnsInput;
	private final long idleTimeoutMillis;
	private final long pollMillis;
	private final Listener listener;

	InputYieldRetry(Listener listener)
	{
		this(InputArbiter::isHuman, IDLE_TIMEOUT_MILLIS, POLL_MILLIS, listener);
	}

	InputYieldRetry(BooleanSupplier humanOwnsInput, long idleTimeoutMillis, long pollMillis, Listener listener)
	{
		this.humanOwnsInput = humanOwnsInput;
		this.idleTimeoutMillis = idleTimeoutMillis;
		this.pollMillis = pollMillis;
		this.listener = listener;
	}

	boolean awaitIdle() throws InterruptedException
	{
		if (!humanOwnsInput.getAsBoolean())
		{
			return true;
		}
		listener.waitingForIdle();
		long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(idleTimeoutMillis);
		while (humanOwnsInput.getAsBoolean())
		{
			if (System.nanoTime() - deadline >= 0)
			{
				return false;
			}
			Thread.sleep(pollMillis);
		}
		return true;
	}

	<T> T run(Supplier<T> attempt, Predicate<T> succeeded, Supplier<T> idleTimeout, UnaryOperator<T> keptInterrupting)
		throws InterruptedException
	{
		T result = null;
		for (int i = 0; i < MAX_ATTEMPTS; i++)
		{
			if (i > 0)
			{
				listener.retrying(i + 1, MAX_ATTEMPTS);
			}
			if (!awaitIdle())
			{
				return idleTimeout.get();
			}
			result = attempt.get();
			if (succeeded.test(result) || !humanOwnsInput.getAsBoolean())
			{
				return result;
			}
		}
		return keptInterrupting.apply(result);
	}
}
