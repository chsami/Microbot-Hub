package net.runelite.client.plugins.microbot.droinfernoprayers.prayer;

import java.util.Collection;

/**
 * Decides the overhead for the next server tick, 1-tick style: an overhead is only wanted on a tick an attack is
 * checked on. Monsters whose attack clock is unknown or overdue are reported as due on every tick, so their
 * overhead stays up (and is held with same-tick pairs) until their rhythm is seen.
 *
 * <p>When attacks of different styles are checked on the same tick, a known attack beats a guessed one (an unknown or
 * overdue clock, or a monster pre-armed out of sight), so a guess never leaves a real hit unprotected. Between attacks
 * of the same kind the one that can hit hardest is protected; on an equal weight the overhead already up is kept.</p>
 */
public final class GuardPlanner
{
	private GuardPlanner()
	{
	}

	public static final class Decision
	{
		public static final Decision CLEAR = new Decision(Guard.NONE, null, false);

		private final Guard guard;
		private final Threat answered;
		private final boolean conflict;

		Decision(Guard guard, Threat answered, boolean conflict)
		{
			this.guard = guard;
			this.answered = answered;
			this.conflict = conflict;
		}

		public Guard guard()
		{
			return guard;
		}

		/** The attack this decision protects against, or null when nothing is due. */
		public Threat answered()
		{
			return answered;
		}

		/** Two styles are checked on the same tick: one of them lands. */
		public boolean conflict()
		{
			return conflict;
		}
	}

	/**
	 * @param threats  every known upcoming attack
	 * @param nextTick the server tick an input sent now first takes effect on
	 * @param current  the overhead up now
	 */
	public static Decision decide(Collection<Threat> threats, int nextTick, Guard current)
	{
		Threat best = null;
		boolean conflict = false;
		for (Threat t : threats)
		{
			if (t.tick() != nextTick || t.guard() == Guard.NONE)
			{
				continue;
			}
			if (best == null)
			{
				best = t;
				continue;
			}
			if (t.guard() != best.guard())
			{
				conflict = true;
			}
			if (heavier(t, best, current))
			{
				best = t;
			}
		}
		return best == null ? Decision.CLEAR : new Decision(best.guard(), best, conflict);
	}

	/** The first attack after {@code nextTick}, for display only. */
	public static Threat upcoming(Collection<Threat> threats, int nextTick)
	{
		Threat first = null;
		for (Threat t : threats)
		{
			if (t.tick() > nextTick && t.guard() != Guard.NONE && (first == null || t.tick() < first.tick()))
			{
				first = t;
			}
		}
		return first;
	}

	private static boolean heavier(Threat a, Threat b, Guard current)
	{
		if (a.guessed() != b.guessed())
		{
			return !a.guessed();
		}
		if (a.weight() != b.weight())
		{
			return a.weight() > b.weight();
		}
		return a.guard() == current && b.guard() != current;
	}
}
