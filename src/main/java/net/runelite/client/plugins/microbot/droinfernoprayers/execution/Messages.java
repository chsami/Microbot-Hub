package net.runelite.client.plugins.microbot.droinfernoprayers.execution;

import com.google.gson.annotations.SerializedName;

public final class Messages
{
	public static final String PLAYER = "player";

	private Messages()
	{
	}

	public static final class Npc
	{
		public int index;
		public int id;
		public String name;

		public int x;
		public int y;

		public int tx;
		public int ty;

		public int size = 1;
		public boolean dead;

		public int orientation;

		public int anim = -1;

		public int hpRatio = -1;
		public int hpScale = -1;

		public Object interacting;

		public Integer level;

		public int[] spot;

		public String say;
	}

	public static final class Item
	{
		public int slot;
		public int id;
		public int qty;

		public String name;

		public Gear gear;

		public Item(int slot, int id, int qty, String name)
		{
			this.slot = slot;
			this.id = id;
			this.qty = qty;
			this.name = name;
		}
	}

	public static final class Gear
	{
		public int eq;
		public boolean twoHanded;
		public int stab, slash, crush, magic, range, str, rstr, prayer;

		public float mdmg;

		public int speed;
	}

	public static final class Worn
	{
		public int slot;
		public int id;
		public String name;
		public Gear gear;

		public Worn(int slot, int id, String name)
		{
			this.slot = slot;
			this.id = id;
			this.name = name;
		}
	}

	public static final class GroundItem
	{
		public int id;
		public String name;
		public int qty;
		public int tx;
		public int ty;

		public GroundItem(int id, String name, int qty, int tx, int ty)
		{
			this.id = id;
			this.name = name;
			this.qty = qty;
			this.tx = tx;
			this.ty = ty;
		}
	}

	public static final class Projectile
	{
		public int id;

		public Object target;

		public Object source;

		public int tx;
		public int ty;

		public int ticksLeft;
	}

	public static final class WidgetInfo
	{
		public int id;

		public String name;
		public String text;

		public String[] actions;

		public String[] kids;
	}

	public static final class Dialogue
	{
		@SerializedName("continue")
		public boolean canContinue;

		public String name;

		public String text;

		public String[] options;
	}
}
