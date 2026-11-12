package net.runelite.client.plugins.microbot.cgprep.plan;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * 1.8.3: stops the planner flip-flopping between two goals. The 8-11 Nov logs had 5 of these, 18 to
 * 111 s each (home <-> explore at the start-room door with a full bag, explore <-> pick up a fish,
 * fight the Bear <-> go home), and two of those runs reached the boss without armour or a bow.
 *
 * When the last {@link #FLIPS} decisions alternate strictly between the same two goals and nothing
 * in the bag changed, the first of the two is held for {@link #HOLD_SECONDS} (or until the bag
 * changes): either one makes progress, switching every tick makes none. The root causes are fixed
 * where found; this is the net under the ones that aren't. Pure, so it's unit-tested.
 */
public final class FlipGuard
{
	/** Decisions in a strict A, B, A, B... alternation before holding (A B A B A B = 5 switches). */
	static final int FLIPS = 6;
	/** Seconds a goal is held for. */
	static final int HOLD_SECONDS = 15;

	private final ArrayDeque<String> recent = new ArrayDeque<>();
	private final Map<String, PrepGoal> lastOf = new HashMap<>();
	private int bagAtRecent = Integer.MIN_VALUE;
	private String holdKey;
	private PrepGoal holdGoal;
	private int holdUntil;
	private int holdBag;
	/** Holds started (for the log). */
	public int holds;

	public void reset()
	{
		recent.clear();
		lastOf.clear();
		bagAtRecent = Integer.MIN_VALUE;
		holdKey = null;
		holdGoal = null;
	}

	/**
	 * The goal to act on. {@code secondsLeft} is the prep clock (counts down; negative = unknown, no
	 * guard), {@code bag} a hash of what we carry (a change is progress).
	 */
	public PrepGoal filter(PrepGoal g, int secondsLeft, int bag)
	{
		if (g == null || secondsLeft < 0)
		{
			return g;
		}
		String key = g.key();
		if (holdKey != null)
		{
			if (bag != holdBag || secondsLeft <= holdUntil || key.equals(holdKey))
			{
				holdKey = null; // progress, time's up, or the planner came back to it by itself
				holdGoal = null;
			}
			else
			{
				return holdGoal;
			}
		}
		if (bag != bagAtRecent)
		{
			recent.clear();
			bagAtRecent = bag;
		}
		lastOf.put(key, g);
		if (recent.isEmpty() || !recent.peekLast().equals(key))
		{
			recent.addLast(key);
		}
		while (recent.size() > FLIPS)
		{
			recent.removeFirst();
		}
		if (recent.size() == FLIPS && alternating())
		{
			String first = recent.peekFirst();
			PrepGoal held = lastOf.get(first);
			if (held != null)
			{
				holds++;
				holdKey = first;
				holdUntil = secondsLeft - HOLD_SECONDS;
				holdBag = bag;
				holdGoal = held.withWhy("holding " + held.why + " (was switching with " + g.type + " every tick)");
				recent.clear();
				return holdGoal;
			}
		}
		return g;
	}

	private boolean alternating()
	{
		String[] k = recent.toArray(new String[0]);
		if (k[0].equals(k[1]))
		{
			return false;
		}
		for (int i = 2; i < k.length; i++)
		{
			if (!k[i].equals(k[i - 2]))
			{
				return false;
			}
		}
		return true;
	}
}
