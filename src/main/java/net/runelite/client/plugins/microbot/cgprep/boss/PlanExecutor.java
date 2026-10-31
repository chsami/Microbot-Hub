package net.runelite.client.plugins.microbot.cgprep.boss;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a {@link FightPlan} (wanted state) into the clicks to send this tick, in priority order,
 * comparing with what the last observation shows. A click takes effect on the NEXT server tick, so
 * the state we see may not show it yet. Toggles (prayers) and consumables (food, potion) are not
 * repeated for {@link #RETRY_TICKS} ticks: a second prayer click before the first registers turns it
 * back OFF, a second eat click eats two fish. Attack and wield are harmless to repeat, so they go
 * out again every tick until they show; a walk click is repeated when we didn't move.
 */
public final class PlanExecutor
{
	public static final int RETRY_TICKS = 2;

	private final Map<Click.Type, Integer> lastTick = new EnumMap<>(Click.Type.class);
	private int prevPx = -1;
	private int prevPy = -1;
	private boolean stuck;
	private final Map<Click.Type, String> lastArg = new EnumMap<>(Click.Type.class);

	public void reset()
	{
		lastTick.clear();
		lastArg.clear();
	}

	public List<Click> clicks(FightPlan p, FightObs o, int tick, int max)
	{
		List<Click> out = new ArrayList<>();
		stuck = o.px == prevPx && o.py == prevPy;
		prevPx = o.px;
		prevPy = o.py;
		// 1. protection
		Style have = o.activeProtection();
		if (p.protect != have && o.prayer > 0)
		{
			add(out, tick, p.protect == null ? new Click(Click.Type.PROTECT_OFF, null, 0, 0) : new Click(Click.Type.PROTECT, p.protect, 0, 0));
		}
		// Weapon: before moving/attacking when the boss's overhead blocks the one in hand (every
		// swing with it is wasted, and swapping doesn't stop the attack), unless the move is an
		// escape (urgent); otherwise after.
		boolean blocked = p.weapon != null && p.weapon != o.weapon && o.bossProtects == o.weapon && !(p.moving() && p.urgent);
		if (blocked)
		{
			add(out, tick, new Click(Click.Type.WIELD, p.weapon, 0, 0));
		}
		// 2. move or attack
		if (p.moving())
		{
			if (o.px != p.moveX || o.py != p.moveY)
			{
				add(out, tick, new Click(Click.Type.MOVE, null, p.moveX, p.moveY));
			}
		}
		else if (p.attack)
		{
			add(out, tick, new Click(Click.Type.ATTACK, null, 0, 0));
		}
		// 3. weapon
		if (!blocked && p.weapon != null && p.weapon != o.weapon)
		{
			add(out, tick, new Click(Click.Type.WIELD, p.weapon, 0, 0));
		}
		// 4./5. food, potion
		if (p.eat)
		{
			add(out, tick, new Click(Click.Type.EAT, null, 0, 0));
		}
		if (p.drink)
		{
			add(out, tick, new Click(Click.Type.DRINK, null, 0, 0));
		}
		// 6. offensive prayer, in the style of the weapon we'll be holding
		Style wantOff = p.offense ? (p.weapon != null ? p.weapon : o.weapon) : null;
		if (wantOff == Style.MELEE)
		{
			wantOff = null;
		}
		if (wantOff != o.offenseStyle && o.prayer > 0)
		{
			add(out, tick, wantOff == null ? new Click(Click.Type.OFFENSE_OFF, null, 0, 0) : new Click(Click.Type.OFFENSE_ON, wantOff, 0, 0));
		}
		if (out.size() > max)
		{
			out = new ArrayList<>(out.subList(0, max));
		}
		return out;
	}

	/** Record that this click really went out (not every planned click fits in a tick). */
	public void sent(Click c, int tick)
	{
		lastTick.put(c.type, tick);
		lastArg.put(c.type, c.toString());
	}

	/** Plan the clicks and mark them all sent (the instant-input path, where they all go out). */
	public List<Click> clicksAllSent(FightPlan p, FightObs o, int tick, int max)
	{
		List<Click> out = clicks(p, o, tick, max);
		for (Click c : out)
		{
			sent(c, tick);
		}
		return out;
	}

	private void add(List<Click> out, int tick, Click c)
	{
		Integer last = lastTick.get(c.type);
		String arg = lastArg.get(c.type);
		boolean same = arg != null && arg.equals(c.toString());
		boolean idempotent = c.type == Click.Type.ATTACK || c.type == Click.Type.WIELD;
		if (same && last != null && tick - last < RETRY_TICKS && c.type != Click.Type.MOVE && !idempotent)
		{
			return; // a toggle sent last tick that isn't visible yet: a second click would undo it
		}
		if (c.type == Click.Type.MOVE && same && last != null && tick - last < RETRY_TICKS && !stuck)
		{
			return; // already walking there (we moved since the click)
		}
		out.add(c);
	}
}
