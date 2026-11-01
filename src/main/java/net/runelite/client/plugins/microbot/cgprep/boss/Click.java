package net.runelite.client.plugins.microbot.cgprep.boss;

/** One input the executor sends (in game: a prayer/inventory/scene click). */
public final class Click
{
	public enum Type
	{
		PROTECT, PROTECT_OFF, OFFENSE_ON, OFFENSE_OFF, WIELD, MOVE, ATTACK, EAT, DRINK
	}

	public final Type type;
	public final Style style;
	public final int x;
	public final int y;

	public Click(Type type, Style style, int x, int y)
	{
		this.type = type;
		this.style = style;
		this.x = x;
		this.y = y;
	}

	@Override
	public String toString()
	{
		switch (type)
		{
			case PROTECT:
			case WIELD:
			case OFFENSE_ON:
				return type + ":" + style;
			case MOVE:
				return "MOVE:" + x + "," + y;
			default:
				return type.toString();
		}
	}
}
