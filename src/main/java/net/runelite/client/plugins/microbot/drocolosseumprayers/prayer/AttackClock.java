package net.runelite.client.plugins.microbot.drocolosseumprayers.prayer;

import java.util.HashMap;
import java.util.Map;

/** Last observed attack tick of every monster, keyed by NPC index. */
public final class AttackClock
{
	private final Map<Integer, Integer> lastAttack = new HashMap<>();

	public void attacked(int npcIndex, int tick)
	{
		lastAttack.put(npcIndex, tick);
	}

	/**
	 * The next tick this monster can be expected to attack on, never earlier than {@code nextTick}. A monster never
	 * seen attacking, or one that has gone quiet for a long time, may attack on the very next tick.
	 */
	public int nextAttack(int npcIndex, int speed, int nextTick)
	{
		Integer last = lastAttack.get(npcIndex);
		if (last == null)
		{
			return nextTick;
		}
		int due = last + speed;
		return Math.max(nextTick, due);
	}

	/** The last attack tick, or null if this monster has not been seen attacking. */
	public Integer lastAttack(int npcIndex)
	{
		return lastAttack.get(npcIndex);
	}

	public boolean attackedWithin(int npcIndex, int tick, int ticks)
	{
		Integer last = lastAttack.get(npcIndex);
		return last != null && tick - last <= ticks;
	}

	public void forget(int npcIndex)
	{
		lastAttack.remove(npcIndex);
	}

	public void clear()
	{
		lastAttack.clear();
	}
}
