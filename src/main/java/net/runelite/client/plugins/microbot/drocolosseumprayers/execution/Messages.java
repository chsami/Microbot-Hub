package net.runelite.client.plugins.microbot.drocolosseumprayers.execution;

import com.google.gson.annotations.SerializedName;

/**
 * The shapes RuneVision reads, sent as JSON. You fill these in from plugins/. The field names are what the server
 * reads, so keep them as they are.
 *
 * Coordinates: x/y are world tiles. tx/ty are TEMPLATE tiles: inside an instance (the Inferno, the Fight Caves, the
 * Colosseum) the map's own coordinate, the same every run, wherever the instance was put. Outside an instance tx/ty
 * equal x/y. In RuneLite: WorldPoint.fromLocalInstance(client, localPoint).
 *
 * "Actor" fields (interacting, target, source, event actor) are the String "player" for you, an NPC's index as an
 * Integer, or null for nothing / another player.
 */
public final class Messages
{
	/** The actor value meaning "you". */
	public static final String PLAYER = "player";

	private Messages()
	{
	}

	/** An NPC you can see. */
	public static final class Npc
	{
		/** The NPC's index (unique while it is alive; commands name NPCs by it). */
		public int index;
        /** Session-local native identity; index reuse cannot inherit an earlier attack clock. */
        public String key;
		public int id;
		public String name;
		/** World tile of its south-west corner. */
		public int x;
		public int y;
		/** Template tile of its south-west corner. */
		public int tx;
		public int ty;
		/** Tiles across (1 for most). */
		public int size = 1;
		public boolean dead;
		/** Current orientation, 0-2047. */
		public int orientation;
		/** Current animation id, -1 for none. */
		public int anim = -1;
        /** Current native animation frame, local capture only. */
        public int animationFrame = -1;
		/** Its health bar: ratio out of scale (-1 when no bar is showing). */
		public int hpRatio = -1;
		public int hpScale = -1;
		/** Who it is attacking/facing: "player", an NPC index, or null. */
		public Object interacting;
		/** Combat level (some NPCs share a name). */
		public Integer level;
		/** Spot animations on it now, or null for none. */
		public int[] spot;
		/** Text above its head while it shows, colour tags removed, or null. */
		public String say;
	}

	/** An inventory item. */
	public static final class Item
	{
		/** 0-27. */
		public int slot;
		public int id;
		public int qty;
		/** Item name, colour tags removed. */
		public String name;
		/** Equipment label, or null for an item that can't be worn or whose stats aren't loaded. */
		public Gear gear;

		public Item(int slot, int id, int qty, String name)
		{
			this.slot = slot;
			this.id = id;
			this.qty = qty;
			this.name = name;
		}
	}

	/**
	 * What a wearable item does, from the game's item stats: so the engine can build each style's switch from
	 * whatever the player brought (a book or antler guard that serves several styles, an imbued or max cape
	 * over the Infernal cape for magic) instead of a fixed item list.
	 */
	public static final class Gear
	{
		/** Equipment slot, as in {@link Worn}. */
		public int eq;
		public boolean twoHanded;
		public int stab, slash, crush, magic, range, str, rstr, prayer;
		/** Magic damage, percent. */
		public float mdmg;
		/** Weapon attack speed in ticks, 0 when not a weapon. */
		public int speed;
	}

	/** A worn item. slot: HEAD 0, CAPE 1, AMULET 2, WEAPON 3, BODY 4, SHIELD 5, LEGS 7, GLOVES 9, BOOTS 10, RING 12, AMMO 13. */
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

	/** A ground item stack on a template tile. */
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

	/** A projectile in the air. */
	public static final class Projectile
	{
		public int id;
		/** What it is aimed at: "player", an NPC index, or null for a tile. */
		public Object target;
		/** Who fired it: "player", an NPC index, or null. */
		public Object source;
		/** Template tile it is aimed at. */
		public int tx;
		public int ty;
		/** Game ticks until it lands, rounded up: (endCycle - gameCycle + 29) / 30, at least 0. */
		public int ticksLeft;
	}

	/** A widget the server asked about (Colosseum), while it is on screen. */
	public static final class WidgetInfo
	{
		/** The packed id the server asked for (group << 16 | child). */
		public int id;
		/** Name / text with colour tags removed, or null. */
		public String name;
		public String text;
		/** Its menu options in order (empty slots as ""), or null. */
		public String[] actions;
		/** Its dynamic children's texts in order (empty as ""), or null. */
		public String[] kids;
	}

	/** The open dialogue (Colosseum). */
	public static final class Dialogue
	{
		/** A "Click here to continue" is showing. */
		@SerializedName("continue")
		public boolean canContinue;
		/** Who is speaking, or null. */
		public String name;
		/** The line, or an option menu's title, or null. */
		public String text;
		/** The option texts of an open menu, or null. */
		public String[] options;
	}
}
