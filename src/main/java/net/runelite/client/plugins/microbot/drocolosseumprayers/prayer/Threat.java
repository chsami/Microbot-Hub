package net.runelite.client.plugins.microbot.drocolosseumprayers.prayer;

/**
 * One attack the player has to be protected from: which guard, on which server tick the game checks it,
 * and how much it can hit for (used to settle two different styles checked on the same tick).
 */
public final class Threat
{
	private final String source;
	private final Guard guard;
	private final int tick;
	private final int weight;
	private final boolean guessed;

	/** An attack whose tick is known: seen in its rhythm, or committed by an animation. */
	public Threat(String source, Guard guard, int tick, int weight)
	{
		this(source, guard, tick, weight, false);
	}

	/**
	 * @param guessed the tick is a guess: the monster's clock is unknown or overdue, or it is pre-armed while out of
	 *                sight or reach, so it may attack on any tick
	 */
	public Threat(String source, Guard guard, int tick, int weight, boolean guessed)
	{
		this.source = source;
		this.guard = guard;
		this.tick = tick;
		this.weight = weight;
		this.guessed = guessed;
	}

	public String source()
	{
		return source;
	}

	public Guard guard()
	{
		return guard;
	}

	public int tick()
	{
		return tick;
	}

	public int weight()
	{
		return weight;
	}

	public boolean guessed()
	{
		return guessed;
	}

	@Override
	public String toString()
	{
		return source + " " + guard + "@" + tick + (guessed ? "?" : "");
	}
}
