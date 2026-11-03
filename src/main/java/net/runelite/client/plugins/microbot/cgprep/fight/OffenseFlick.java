package net.runelite.client.plugins.microbot.cgprep.fight;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Lazy flicking of the offensive prayer (user, 0.3.24). The boost only counts on the tick our
 * attack is rolled, so the prayer goes ON on the tick BEFORE each swing and OFF again on the tick
 * the swing happens: about one tick of drain per attack instead of a constant drain.
 *
 * Timing: a click made right after game tick N is processed by the server on tick N+1, before
 * combat. So "on" is clicked at tick (next swing - 1) and "off" at the tick the swing shows.
 *
 * The swing is our attack animation (re)starting while we're interacting with the target. Its id
 * is learned per weapon style from the first swing (block / eat / gathering animations excluded),
 * and the interval between swings is learned too (prep weapons: 4 ticks). Until the first swing
 * of a fight is seen, or when a swing is overdue (the target moved), the prayer stays on while
 * the target is in reach, so no hit goes out without it.
 */
public final class OffenseFlick
{
	/** Animations that are never our attack: idle, blocks, eating, gathering. */
	static final Set<Integer> NOT_ATTACK = Set.of(-1, 403, 424, 388, 1156, 829, 8347, 8348, 8324, 8325, 8336, 8337, 2282);

	private final Map<PrayerGuard.Style, Integer> attackAnim = new EnumMap<>(PrayerGuard.Style.class);
	private int target = -1;
	private int lastSwingTick = -1;
	private int interval = 4;
	private int prevAnim = -1;
	private int prevFrame;

	public void reset()
	{
		target = -1;
		lastSwingTick = -1;
		prevAnim = -1;
		prevFrame = 0;
	}

	/** Ticks between our swings (learned; 4 for every prep weapon). */
	public int interval()
	{
		return interval;
	}

	/**
	 * Should the offensive prayer be on for the coming tick? Call once per game tick while we're
	 * deliberately attacking.
	 */
	public boolean onTick(int tick, int anim, int frame, int targetIndex, boolean inRange, PrayerGuard.Style style)
	{
		if (targetIndex != target)
		{
			target = targetIndex;
			lastSwingTick = -1; // new target: first-swing mode
		}
		boolean restarted = anim != prevAnim || frame < prevFrame;
		prevAnim = anim;
		prevFrame = frame;
		Integer known = attackAnim.get(style);
		boolean swing = targetIndex >= 0 && restarted && !NOT_ATTACK.contains(anim) && (known == null || known == anim);
		if (swing)
		{
			if (known == null)
			{
				attackAnim.put(style, anim);
			}
			if (lastSwingTick >= 0)
			{
				int gap = tick - lastSwingTick;
				if (gap >= 3 && gap <= 7)
				{
					interval = gap;
				}
			}
			lastSwingTick = tick;
			return false; // the swing was rolled with the prayer on: off again
		}
		if (lastSwingTick >= 0 && inRange && tick - lastSwingTick > interval + 4)
		{
			// In reach and no swing for far too long: the learned animation was probably wrong
			// (e.g. a block picked up as a swing). Forget it and learn again.
			attackAnim.remove(style);
			lastSwingTick = -1;
		}
		if (lastSwingTick < 0)
		{
			return inRange; // first swing of this fight: on while in reach until it shows
		}
		int next = lastSwingTick + interval;
		if (tick == next - 1)
		{
			return true;
		}
		return tick >= next && inRange; // overdue (target moved / out of reach): hold until it shows
	}
}
