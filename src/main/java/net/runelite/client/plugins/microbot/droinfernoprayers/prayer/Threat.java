package net.runelite.client.plugins.microbot.droinfernoprayers.prayer;

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

	public Threat(String source, Guard guard, int tick, int weight)
	{
		this.source = source;
		this.guard = guard;
		this.tick = tick;
		this.weight = weight;
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

	@Override
	public String toString()
	{
		return source + " " + guard + "@" + tick;
	}
}
