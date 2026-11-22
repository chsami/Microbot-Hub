package net.runelite.client.plugins.microbot.drocolosseumprayers.prayer;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * How one monster attacks, as far as protection prayers are concerned.
 *
 * <ul>
 *     <li><b>Steady</b> attackers attack every {@code speed} ticks once they can reach the player; the prayer is
 *     checked on the attack tick plus {@code checkDelay}.</li>
 *     <li><b>Reactive</b> attacks pick their style at the animation; the prayer is then wanted on each listed tick
 *     after the animation (for example a projectile still in the air).</li>
 * </ul>
 */
public final class MobSpec
{
	public final String name;
	public final Guard guard;
	public final int speed;
	public final int range;
	public final int weight;
	private boolean steady = true;
	private boolean contactOnly;
	private boolean meleeInContact;
	private boolean targetingOnly;
	private int checkDelay;
	private final Map<Integer, Guard> attacks = new HashMap<>();
	private final Map<Integer, Integer> quietFor = new HashMap<>();
	private final Map<Integer, Reaction> reactions = new HashMap<>();

	public static final class Reaction
	{
		public final Guard guard;
		public final int[] ticks;

		Reaction(Guard guard, int[] ticks)
		{
			this.guard = guard;
			this.ticks = ticks;
		}
	}

	private MobSpec(String name, Guard guard, int speed, int range, int weight)
	{
		this.name = name;
		this.guard = guard;
		this.speed = speed;
		this.range = range;
		this.weight = weight;
	}

	public static MobSpec steady(String name, Guard guard, int speed, int range, int weight)
	{
		return new MobSpec(name, guard, speed, range, weight);
	}

	/** A monster with no steady rhythm the plugin can answer; only its reactions are prayed against. */
	public static MobSpec reactive(String name, int weight)
	{
		MobSpec spec = new MobSpec(name, Guard.NONE, 0, 0, weight);
		spec.steady = false;
		return spec;
	}

	/** Animation that is an attack: sets the clock. */
	public MobSpec attack(int animation)
	{
		attacks.put(animation, guard);
		return this;
	}

	/** Animation that is not an attack but means the next attack comes {@code ticks} later. */
	public MobSpec quiet(int animation, int ticks)
	{
		quietFor.put(animation, ticks);
		return this;
	}

	/** Animation that commits an attack of {@code style}, checked {@code ticks} after the animation tick. */
	public MobSpec reaction(int animation, Guard style, int... ticks)
	{
		reactions.put(animation, new Reaction(style, ticks));
		return this;
	}

	/** Only attacks while touching the player (diagonals included). */
	public MobSpec contactOnly()
	{
		contactOnly = true;
		return this;
	}

	/** Switches to melee while touching the player (diagonals included). */
	public MobSpec meleeInContact()
	{
		meleeInContact = true;
		return this;
	}

	/** Only a threat once it is targeting the player. */
	public MobSpec targetingOnly()
	{
		targetingOnly = true;
		return this;
	}

	public MobSpec checkDelay(int ticks)
	{
		checkDelay = ticks;
		return this;
	}

	public boolean steady()
	{
		return steady;
	}

	public boolean contactOnlyAttacker()
	{
		return contactOnly || guard == Guard.MELEE;
	}

	public boolean meleeInContactAttacker()
	{
		return meleeInContact;
	}

	public boolean targetingOnlyAttacker()
	{
		return targetingOnly;
	}

	public int checkDelay()
	{
		return checkDelay;
	}

	public boolean isAttack(int animation)
	{
		return attacks.containsKey(animation);
	}

	/** Ticks until the next attack after this animation, or -1 if it is not a pause animation. */
	public int quietTicks(int animation)
	{
		return quietFor.getOrDefault(animation, -1);
	}

	public Reaction reaction(int animation)
	{
		return reactions.get(animation);
	}

	public Map<Integer, Reaction> reactions()
	{
		return Collections.unmodifiableMap(reactions);
	}
}
