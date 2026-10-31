package net.runelite.client.plugins.microbot.cgprep.boss;

/**
 * Everything seen at the end of one game tick of the boss fight, arena-relative. Built in game by
 * {@code BossObserver} (client thread) and in the simulator by the world itself, so the model and the
 * brain run on exactly the same input in both places.
 */
public final class FightObs
{
	public static final int ANIM_ATTACK = 8419;
	public static final int ANIM_TORNADO = 8418;
	public static final int ANIM_TO_MAGIC = 8754;
	public static final int ANIM_TO_RANGED = 8755;
	/** Unconfirmed (no stomp in the recordings); the model treats any other boss animation as no attack. */
	public static final int ANIM_STOMP = 8420;

	public static final byte FLOOR_NONE = 0;
	public static final byte FLOOR_WARN = 1;
	public static final byte FLOOR_HIT = 2;

	public int tick;

	// ---- us
	public int px;
	public int py;
	public int hp;
	public int hpMax;
	public int prayer;
	public int prayerMax;
	public boolean protRanged;
	public boolean protMagic;
	public boolean protMelee;
	/** An offensive prayer is on (Mystic Vigour/Augury..., Deadeye/Rigour...). */
	public boolean offense;
	/** Which one: MAGIC (Mystic Vigour/Augury), RANGED (Deadeye/Rigour), null = none. */
	public Style offenseStyle;
	/** The tab open in the side panel (natural input): INVENTORY / PRAYER / OTHER. */
	public InputTiming.Tab tab = InputTiming.Tab.INVENTORY;
	/** Weapon in hand: RANGED = bow, MAGIC = staff, MELEE = anything else. */
	public Style weapon = Style.MELEE;
	/** Our bow/staff attack animation started this tick (= a swing at the boss). */
	public boolean swung;
	/** We're interacting with the boss (attack mode). */
	public boolean attacking;
	public int fish;
	public int doses;
	/** The "Your prayers have been disabled!" message arrived this tick. */
	public boolean prayersDisabled;

	// ---- boss
	public boolean bossPresent;
	public int bx;
	public int by;
	/** Style its overhead blocks (from its NPC id), or null if unknown. */
	public Style bossProtects;
	/** Animation the boss started this tick, -1 = none. */
	public int bossAnim = -1;
	public int bossHp = 1000;
	/** Boss projectiles first seen this tick. */
	public int projRanged;
	public int projMagic;
	public int projDisable;

	// ---- tornadoes (NPC index + tile)
	public int torN;
	public int[] torIdx = new int[8];
	public int[] torX = new int[8];
	public int[] torY = new int[8];

	// ---- floor, per tile (x + 12 * y)
	public final byte[] floor = new byte[Arena.TILES];

	public void addTornado(int idx, int x, int y)
	{
		if (torN == torIdx.length)
		{
			torIdx = java.util.Arrays.copyOf(torIdx, torN * 2);
			torX = java.util.Arrays.copyOf(torX, torN * 2);
			torY = java.util.Arrays.copyOf(torY, torN * 2);
		}
		torIdx[torN] = idx;
		torX[torN] = x;
		torY[torN] = y;
		torN++;
	}

	public boolean protects(Style s)
	{
		return s == Style.RANGED ? protRanged : s == Style.MAGIC ? protMagic : protMelee;
	}

	public Style activeProtection()
	{
		return protRanged ? Style.RANGED : protMagic ? Style.MAGIC : protMelee ? Style.MELEE : null;
	}

	// ------------------------------------------------------------------ log line round trip

	/** Compact one-line form for the decision log (FOBS lines), read back by {@link #decode}. */
	public String encode()
	{
		StringBuilder b = new StringBuilder(260);
		b.append(tick).append('|').append(px).append(',').append(py).append('|').append(hp).append('/').append(hpMax)
			.append('|').append(prayer).append('/').append(prayerMax).append('|');
		b.append(protRanged ? 'R' : '-').append(protMagic ? 'M' : '-').append(protMelee ? 'X' : '-').append(offense ? 'O' : '-')
			.append(swung ? 'S' : '-').append(attacking ? 'A' : '-').append(prayersDisabled ? 'D' : '-').append(bossPresent ? 'B' : '-');
		b.append('|').append(code(weapon)).append('|').append(fish).append('|').append(doses);
		b.append('|').append(bx).append(',').append(by).append('|').append(code(bossProtects)).append('|').append(bossAnim)
			.append('|').append(bossHp).append('|').append(projRanged).append(',').append(projMagic).append(',').append(projDisable).append('|');
		for (int i = 0; i < torN; i++)
		{
			b.append(i > 0 ? ";" : "").append(torIdx[i]).append(':').append(torX[i]).append(',').append(torY[i]);
		}
		b.append('|');
		// floor: run-length "<state><count>" pairs over the 144 tiles
		int i = 0;
		while (i < floor.length)
		{
			int j = i;
			while (j < floor.length && floor[j] == floor[i])
			{
				j++;
			}
			b.append((char) ('0' + floor[i])).append(j - i).append('.');
			i = j;
		}
		b.append('|').append(code(offenseStyle)).append('|').append(tab == null ? '-' : tab.name().charAt(0));
		return b.toString();
	}

	public static FightObs decode(String s)
	{
		String[] f = s.split("\\|", -1);
		FightObs o = new FightObs();
		o.tick = Integer.parseInt(f[0]);
		String[] p = f[1].split(",");
		o.px = Integer.parseInt(p[0]);
		o.py = Integer.parseInt(p[1]);
		String[] h = f[2].split("/");
		o.hp = Integer.parseInt(h[0]);
		o.hpMax = Integer.parseInt(h[1]);
		String[] q = f[3].split("/");
		o.prayer = Integer.parseInt(q[0]);
		o.prayerMax = Integer.parseInt(q[1]);
		String fl = f[4];
		o.protRanged = fl.charAt(0) == 'R';
		o.protMagic = fl.charAt(1) == 'M';
		o.protMelee = fl.charAt(2) == 'X';
		o.offense = fl.charAt(3) == 'O';
		o.swung = fl.charAt(4) == 'S';
		o.attacking = fl.charAt(5) == 'A';
		o.prayersDisabled = fl.charAt(6) == 'D';
		o.bossPresent = fl.charAt(7) == 'B';
		o.weapon = style(f[5]);
		o.fish = Integer.parseInt(f[6]);
		o.doses = Integer.parseInt(f[7]);
		String[] b = f[8].split(",");
		o.bx = Integer.parseInt(b[0]);
		o.by = Integer.parseInt(b[1]);
		o.bossProtects = style(f[9]);
		o.bossAnim = Integer.parseInt(f[10]);
		o.bossHp = Integer.parseInt(f[11]);
		String[] pr = f[12].split(",");
		o.projRanged = Integer.parseInt(pr[0]);
		o.projMagic = Integer.parseInt(pr[1]);
		o.projDisable = Integer.parseInt(pr[2]);
		if (!f[13].isEmpty())
		{
			for (String t : f[13].split(";"))
			{
				String[] a = t.split("[:,]");
				o.addTornado(Integer.parseInt(a[0]), Integer.parseInt(a[1]), Integer.parseInt(a[2]));
			}
		}
		int i = 0;
		for (String run : f[14].split("\\."))
		{
			if (run.isEmpty())
			{
				continue;
			}
			byte v = (byte) (run.charAt(0) - '0');
			int n = Integer.parseInt(run.substring(1));
			for (int k = 0; k < n && i < o.floor.length; k++)
			{
				o.floor[i++] = v;
			}
		}
		if (f.length > 16)
		{
			o.offenseStyle = style(f[15]);
			o.tab = f[16].equals("P") ? InputTiming.Tab.PRAYER : f[16].equals("I") ? InputTiming.Tab.INVENTORY : InputTiming.Tab.OTHER;
		}
		else if (o.offense)
		{
			o.offenseStyle = o.weapon; // 0.4.0/0.4.1 logs: assume it matched the weapon
		}
		return o;
	}

	private static char code(Style s)
	{
		return s == null ? '-' : s == Style.RANGED ? 'R' : s == Style.MAGIC ? 'M' : 'X';
	}

	private static Style style(String c)
	{
		return c.equals("R") ? Style.RANGED : c.equals("M") ? Style.MAGIC : c.equals("X") ? Style.MELEE : null;
	}
}
