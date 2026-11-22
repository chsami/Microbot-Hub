package net.runelite.client.plugins.microbot.droinfernoprayers.prayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 1-tick overhead ledger, client-thread owned.
 *
 * <ul>
 *     <li>Switching to a new overhead is a single ON of that overhead; the game drops the old one itself, so an old
 *     overhead is never switched OFF before its replacement.</li>
 *     <li>An overhead nothing needs any more is released with one OFF, only early in a tick and only while ticks
 *     arrive on a steady cadence; on a late or uneven tick the prayer is kept rather than released late.</li>
 *     <li>An overhead still wanted on the next tick is held with one OFF/ON pair sent in the same client
 *     invocation early in the tick, so it stays on without draining. Pairs are only sent while nothing else is in
 *     flight.</li>
 *     <li>Every ON/OFF waits for the varbit to confirm it. An input that is not confirmed within two ticks counts
 *     as missed and drops the cadence back to unsynchronised.</li>
 * </ul>
 *
 * Channels are the three overheads: 0 melee, 1 missiles, 2 magic.
 */
public final class TickPrayerLedger
{
	public enum Type
	{
		ON, OFF, PAIR
	}

	public static final class Command
	{
		private final Guard guard;
		private final Type type;

		public Command(Guard guard, Type type)
		{
			this.guard = guard;
			this.type = type;
		}

		public Guard guard()
		{
			return guard;
		}

		public Type type()
		{
			return type;
		}

		@Override
		public String toString()
		{
			return type + " " + guard;
		}
	}

	/** OFF and PAIR are only sent inside this part of a tick, measured from the GameTick event. */
	public static final long EARLY_FROM_NS = 20_000_000L;
	public static final long EARLY_UNTIL_NS = 160_000_000L;
	private static final long MIN_TICK_NS = 450_000_000L;
	private static final long MAX_TICK_NS = 750_000_000L;
	private static final long STALE_NS = 1_200_000_000L;
	private static final int STEADY_TICKS = 2;

	private int tick = -1;
	private long tickAt = -1;
	private int steady;
	private boolean failed;
	private int pending;
	private int expected;
	private int sentThisTick;
	private final int[] sentTick = {-1, -1, -1};
	private int misses;

	public void reset()
	{
		tick = -1;
		tickAt = -1;
		steady = 0;
		failed = false;
		pending = 0;
		expected = 0;
		sentThisTick = 0;
		misses = 0;
		for (int i = 0; i < sentTick.length; i++)
		{
			sentTick[i] = -1;
		}
	}

	/** Called on every GameTick with the overheads the varbits show. */
	public void beginTick(int next, long now, Guard observed)
	{
		if (next == tick)
		{
			return;
		}
		long gap = tickAt < 0 ? -1 : now - tickAt;
		boolean onCadence = tick >= 0 && next == tick + 1 && gap >= MIN_TICK_NS && gap <= MAX_TICK_NS && !failed;
		steady = onCadence ? steady + 1 : 0;
		failed = false;
		tick = next;
		tickAt = now;
		sentThisTick = 0;
		confirm(observed);
		for (int i = 0; i < 3; i++)
		{
			int bit = 1 << i;
			if ((pending & bit) != 0 && tick - sentTick[i] >= 2)
			{
				pending &= ~bit;
				steady = 0;
				misses++;
			}
		}
	}

	/** Drops the confirmation wait for every channel the varbits now agree with. */
	public void confirm(Guard observed)
	{
		pending &= (mask(observed) ^ expected);
	}

	public void failed()
	{
		failed = true;
		steady = 0;
	}

	public boolean synchronised()
	{
		return steady >= STEADY_TICKS && !failed;
	}

	public boolean fresh(long now)
	{
		return tick >= 0 && now >= tickAt && now - tickAt < STALE_NS;
	}

	public boolean early(long now)
	{
		return fresh(now) && now - tickAt >= EARLY_FROM_NS && now - tickAt <= EARLY_UNTIL_NS;
	}

	public int tick()
	{
		return tick;
	}

	public int misses()
	{
		return misses;
	}

	public boolean waiting()
	{
		return pending != 0;
	}

	/**
	 * The work for this client tick, in order. The caller sends each command and reports it with {@link #sent}.
	 *
	 * @param allowPair whether a held overhead may be kept up with a same-tick pair this tick
	 */
	public List<Command> plan(long now, Guard observed, Guard wanted, boolean allowPair)
	{
		if (!fresh(now))
		{
			return Collections.emptyList();
		}
		confirm(observed);
		List<Command> commands = new ArrayList<>(1);
		int on = mask(observed);
		if (wanted != Guard.NONE)
		{
			int bit = mask(wanted);
			if ((on & bit) == 0)
			{
				if ((pending & bit) == 0 && (sentThisTick & bit) == 0)
				{
					commands.add(new Command(wanted, Type.ON));
				}
				return commands;
			}
			if (allowPair && pending == 0 && (sentThisTick & bit) == 0 && early(now) && synchronised())
			{
				commands.add(new Command(wanted, Type.PAIR));
			}
			return commands;
		}
		if (observed != Guard.NONE)
		{
			int bit = mask(observed);
			if ((pending & bit) == 0 && (sentThisTick & bit) == 0 && early(now) && synchronised())
			{
				commands.add(new Command(observed, Type.OFF));
			}
		}
		return commands;
	}

	public void sent(Command command)
	{
		int bit = mask(command.guard());
		sentThisTick |= bit;
		if (command.type() == Type.PAIR)
		{
			// A pair ends ON, the state the varbits already show: nothing to confirm.
			return;
		}
		int channel = channel(command.guard());
		sentTick[channel] = tick;
		if (command.type() == Type.ON)
		{
			// Turning one overhead on turns the others off.
			pending &= ~ALL;
			expected = bit;
			pending |= bit;
			return;
		}
		pending |= bit;
		expected &= ~bit;
	}

	/** The wanted overhead is on, or was switched on this tick and not contradicted since. */
	public boolean committed(Guard observed, Guard wanted)
	{
		if (failed)
		{
			return false;
		}
		if (wanted == Guard.NONE)
		{
			return true;
		}
		int bit = mask(wanted);
		if (observed == wanted)
		{
			return (pending & bit & ~expected) == 0;
		}
		return (pending & expected & bit) != 0 && sentTick[channel(wanted)] == tick;
	}

	private static final int ALL = 7;

	static int mask(Guard guard)
	{
		return guard == Guard.NONE ? 0 : 1 << channel(guard);
	}

	static int channel(Guard guard)
	{
		switch (guard)
		{
			case MELEE:
				return 0;
			case RANGE:
				return 1;
			case MAGIC:
				return 2;
			default:
				throw new IllegalArgumentException("No channel for " + guard);
		}
	}
}
