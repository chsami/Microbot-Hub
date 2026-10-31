package net.runelite.client.plugins.microbot.cgprep.boss;

/**
 * What to do for the coming tick, in priority order: protection prayer, movement or attack, weapon,
 * food/potion, offensive prayer. Each field is the wanted state; the executor only clicks what
 * differs from what's on screen.
 */
public final class FightPlan
{
	/** Protection prayer to have on (null = none wanted). */
	public Style protect;
	/** Offensive prayer for the weapon in hand on/off. */
	public boolean offense;
	/** Weapon to hold: RANGED = bow, MAGIC = staff. */
	public Style weapon;
	public boolean eat;
	public boolean drink;
	/** Tile to step to (arena-relative), or -1 to stand and attack. */
	public int moveX = -1;
	public int moveY = -1;
	/** The move is an escape: staying put risks damage in the next 2 ticks (it goes before anything but prayer). */
	public boolean urgent;
	/** Click Attack on the boss (we're standing and not attacking yet). */
	public boolean attack;
	/** Planner's expected cost (for logs). */
	public double cost;
	public String why = "";

	public boolean moving()
	{
		return moveX >= 0;
	}

	@Override
	public String toString()
	{
		StringBuilder b = new StringBuilder();
		b.append("pray=").append(protect == null ? "-" : protect == Style.RANGED ? "MSL" : protect == Style.MAGIC ? "MAG" : "MEL");
		b.append(offense ? "+off" : "");
		b.append(" wpn=").append(weapon == Style.RANGED ? "bow" : weapon == Style.MAGIC ? "staff" : "-");
		if (moving())
		{
			b.append(" move=").append(moveX).append(',').append(moveY).append(urgent ? "!" : "");
		}
		else
		{
			b.append(attack ? " attack" : " stay");
		}
		b.append(eat ? " eat" : "").append(drink ? " drink" : "");
		b.append(String.format(" cost=%.1f", cost));
		if (!why.isEmpty())
		{
			b.append(" (").append(why).append(')');
		}
		return b.toString();
	}
}
