package net.runelite.client.plugins.microbot.cgprep.actions;

import java.util.function.BooleanSupplier;
import net.runelite.client.plugins.microbot.cgprep.fight.WorldScan;
import net.runelite.client.plugins.microbot.util.Global;

/**
 * Waits for the result of a click that walks us somewhere first (a doorway node, a station, a
 * fishing spot). Like sleepUntil, but it gives up as soon as the player has stopped: no tile moved
 * and no animation for about two game ticks, once the click has had time to start the walk. Then
 * the walk was interrupted (an inventory click, being attacked) or the click never went out, and
 * the loop clicks again right away instead of standing there until the timeout (0.4.6: up to 10 s
 * idle at a doorway). Script thread only.
 */
public final class Waits
{
	/** Time for a click to show as movement: it's processed on the next tick and seen the one after. */
	static final long START_MS = 1800;
	/** Standing still this long after we did move = the walk ended or was cancelled. */
	static final long STILL_MS = 1300;

	private Waits()
	{
	}

	/** True once {@code done} holds; false on timeout, or early once we've clearly stopped. */
	public static boolean untilOrStopped(BooleanSupplier done, long timeoutMs)
	{
		long start = System.currentTimeMillis();
		long end = start + timeoutMs;
		while (true)
		{
			if (done.getAsBoolean())
			{
				return true;
			}
			long now = System.currentTimeMillis();
			if (now >= end)
			{
				return false;
			}
			long active = WorldScan.lastActiveMs();
			boolean movedSince = active > start;
			long stillFor = now - Math.max(start, active);
			if (now - start >= START_MS && stillFor >= (movedSince ? STILL_MS : START_MS))
			{
				return done.getAsBoolean();
			}
			if (!Global.sleepUntil(done, (int) Math.min(120, end - now)))
			{
				if (Thread.currentThread().isInterrupted())
				{
					return false;
				}
			}
		}
	}
}
