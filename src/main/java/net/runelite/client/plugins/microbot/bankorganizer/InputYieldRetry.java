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
import net.runelite.client.plugins.microbot.util.input.InputArbiter;

final class InputYieldRetry
{
	static final int MAX_ATTEMPTS = 3;
	static final long IDLE_TIMEOUT_MILLIS = 30_000L;
	private static final long POLL_MILLIS = 100L;

	private final BooleanSupplier humanOwnsInput;
	private final long idleTimeoutMillis;
	private final long pollMillis;

	InputYieldRetry()
	{
		this(InputArbiter::isHuman, IDLE_TIMEOUT_MILLIS, POLL_MILLIS);
	}

	InputYieldRetry(BooleanSupplier humanOwnsInput, long idleTimeoutMillis, long pollMillis)
	{
		this.humanOwnsInput = humanOwnsInput;
		this.idleTimeoutMillis = idleTimeoutMillis;
		this.pollMillis = pollMillis;
	}

	boolean awaitIdle() throws InterruptedException
	{
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

	<T> T run(Supplier<T> attempt, Predicate<T> succeeded, Supplier<T> idleTimeout) throws InterruptedException
	{
		T result = null;
		for (int i = 0; i < MAX_ATTEMPTS; i++)
		{
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
		return result;
	}
}
