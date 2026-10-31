package net.runelite.client.plugins.microbot.cgprep.actions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.runelite.client.plugins.microbot.cgprep.data.CgIds;
import net.runelite.client.plugins.microbot.cgprep.data.CgMode;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

/**
 * The whole potion chain in one go (user, 0.3.33), driven by the plugin's GameTick; the clicks go
 * out on a small executor thread, never the client thread. Three phases, in order:
 *
 *  1. HERB: grym on a water vial ONCE. The game then puts grym into every water vial by itself, one
 *     per tick, until they run out or something interrupts it (user, 0.4.7). We watch the water
 *     vial count; while it keeps dropping we do nothing, if it stalls with work left we click again.
 *  2. CRUSH: pestle on the shards, one click per tick (crushing does NOT repeat by itself).
 *  3. DUST: dust on an unfinished potion ONCE, then the same watching as HERB (unfinished count).
 *
 * Planned from the bag when started. Stops when nothing's left to do, or when a phase keeps
 * stalling (a few re-clicks without any progress).
 */
public final class BrewChain
{
	private final ExecutorService exec = Executors.newSingleThreadExecutor(r ->
	{
		Thread t = new Thread(r, "cgprep-brew");
		t.setDaemon(true);
		return t;
	});

	private enum Kind
	{
		HERB, CRUSH, DUST
	}

	/** Ticks without progress after which an auto-repeating phase counts as interrupted. */
	static final int STALL_TICKS = 3;
	/** The first result after a click can take a tick longer (processed next tick, seen the one after). */
	static final int FIRST_TICKS = 4;
	/** Re-clicks without any progress before the chain gives up. */
	static final int MAX_DEAD_CLICKS = 3;

	private static final class Phase
	{
		final Kind kind;
		/** Items still to make (HERB / DUST) or crush clicks still to send (CRUSH). */
		int left;
		boolean sent;
		int lastEventTick;
		int lastSig = -1;
		boolean progressed;
		int deadClicks;

		Phase(Kind kind, int left)
		{
			this.kind = kind;
			this.left = left;
		}
	}

	private final List<Phase> phases = new ArrayList<>();
	private volatile boolean active;
	private volatile boolean sending;
	private volatile CgMode mode;
	/** Dust wanted once the crushing is done (CRUSH ends when it's there, or a few ticks later). */
	private int dustTarget;
	private int lastCrushTick = -10;
	private int clicks;
	private volatile long startedMs;
	/** What the chain did, for the log (read and cleared by the script). */
	public volatile String note;

	/**
	 * Plan the chain for the bag as it is: {@code brews} = potions still to brew (caps everything).
	 * Returns about how many game ticks it takes, one per item made or crushed (0 = nothing to do).
	 */
	public synchronized int plan(CgMode mode, int water, int grym, int unf, int dust, int shards, boolean pestle,
		int freeSlots, int brews)
	{
		this.mode = mode;
		phases.clear();
		int herb = Math.max(0, Math.min(Math.min(water, grym), brews - unf));
		if (herb > 0)
		{
			phases.add(new Phase(Kind.HERB, herb));
		}
		int unfAfter = unf + herb;
		// Every herb frees a slot (2 items -> 1); the dust stack needs one unless some is held.
		int slots = freeSlots + herb;
		int crush = 0;
		if (pestle && (dust > 0 || slots > 0))
		{
			int dustShort = Math.max(0, unfAfter * CgIds.DUST_PER_POTION - dust);
			crush = Math.min((dustShort + CgIds.DUST_PER_POTION - 1) / CgIds.DUST_PER_POTION, shards / CgIds.DUST_PER_POTION);
		}
		if (crush > 0)
		{
			phases.add(new Phase(Kind.CRUSH, crush));
		}
		dustTarget = dust + crush * CgIds.DUST_PER_POTION;
		int potions = Math.min(unfAfter, dustTarget / CgIds.DUST_PER_POTION);
		if (potions > 0)
		{
			phases.add(new Phase(Kind.DUST, potions));
		}
		return herb + crush + potions;
	}

	/** Start the planned phases from the next tick. */
	public synchronized void start(String why)
	{
		if (phases.isEmpty())
		{
			return;
		}
		active = true;
		clicks = 0;
		startedMs = System.currentTimeMillis();
		note = "brew chain: " + summary() + (why == null ? "" : ", " + why);
	}

	/** Running. A watchdog ends it after 30 s whatever happened (the main loop waits while it runs). */
	public boolean active()
	{
		if (active && System.currentTimeMillis() - startedMs > 30_000)
		{
			synchronized (this)
			{
				note = "brew chain: gave up after 30 s";
				stop();
			}
		}
		return active;
	}

	public synchronized void stop()
	{
		active = false;
		phases.clear();
	}

	/** Plugin thread (GameTick): watch the running phase, click when it needs one. */
	public void onTick(int tick)
	{
		if (!active || sending)
		{
			return;
		}
		CgMode m = mode;
		if (m == null)
		{
			stop();
			return;
		}
		int a;
		int b;
		synchronized (this)
		{
			Phase p = current(m, tick);
			if (p == null)
			{
				active = false;
				note = "brew chain: done (" + clicks + " click(s))";
				return;
			}
			if (p.kind == Kind.CRUSH)
			{
				if (p.left <= 0)
				{
					return; // all crushes sent: waiting a tick or two for the last dust to show
				}
				p.left--;
				lastCrushTick = tick;
				a = CgIds.PESTLE;
				b = m.shard;
			}
			else
			{
				int sig = signature(p.kind);
				if (p.sent)
				{
					if (sig != p.lastSig)
					{
						p.left -= Math.max(0, p.lastSig - sig);
						p.lastSig = sig;
						p.lastEventTick = tick;
						p.progressed = true;
						p.deadClicks = 0;
						return; // still repeating by itself
					}
					int wait = p.progressed ? STALL_TICKS : FIRST_TICKS;
					if (tick - p.lastEventTick < wait)
					{
						return;
					}
					// Interrupted (a click elsewhere, an attack) with work left: start it again.
					if (++p.deadClicks > MAX_DEAD_CLICKS)
					{
						note = "brew chain: stopped, " + p.kind.name().toLowerCase() + " isn't going (" + p.left + " left)";
						stop();
						return;
					}
					note = "brew chain: " + p.kind.name().toLowerCase() + " stopped with " + p.left + " left, clicking it again";
				}
				p.sent = true;
				p.lastSig = sig;
				p.lastEventTick = tick;
				p.progressed = false;
				a = p.kind == Kind.HERB ? CgIds.VIAL_WATER : m.dust;
				b = p.kind == Kind.HERB ? m.grym : CgIds.POTION_UNF;
			}
		}
		clicks++;
		sending = true;
		final int ia = a;
		final int ib = b;
		exec.execute(() ->
		{
			try
			{
				net.runelite.client.plugins.microbot.cgprep.antiban.Pace.ensureInventoryTab();
				Rs2Inventory.combine(ia, ib);
			}
			catch (Exception ignored)
			{
				// a missing item between the check and the click: the next tick re-checks
			}
			finally
			{
				sending = false;
			}
		});
	}

	/** The phase to work on now; finished phases (or ones whose items ran out) fall away. */
	private Phase current(CgMode m, int tick)
	{
		while (!phases.isEmpty())
		{
			Phase p = phases.get(0);
			boolean done;
			switch (p.kind)
			{
				case HERB:
					done = p.left <= 0 || !Rs2Inventory.contains(CgIds.VIAL_WATER) || !Rs2Inventory.contains(m.grym);
					break;
				case CRUSH:
					done = !Rs2Inventory.contains(CgIds.PESTLE)
						|| (p.left > 0 && Rs2Inventory.itemQuantity(m.shard) < CgIds.DUST_PER_POTION)
						|| (p.left <= 0 && (Rs2Inventory.itemQuantity(m.dust) >= dustTarget || tick - lastCrushTick >= 3));
					break;
				default:
					done = p.left <= 0 || !Rs2Inventory.contains(CgIds.POTION_UNF)
						|| Rs2Inventory.itemQuantity(m.dust) < CgIds.DUST_PER_POTION;
					break;
			}
			if (!done)
			{
				return p;
			}
			phases.remove(0);
		}
		return null;
	}

	private static int signature(Kind k)
	{
		return k == Kind.HERB ? Rs2Inventory.count(CgIds.VIAL_WATER) : Rs2Inventory.count(CgIds.POTION_UNF);
	}

	private String summary()
	{
		StringBuilder sb = new StringBuilder();
		for (Phase p : phases)
		{
			if (sb.length() > 0)
			{
				sb.append(", then ");
			}
			switch (p.kind)
			{
				case HERB:
					sb.append("grym into ").append(p.left).append(" vial(s) (one click, it repeats)");
					break;
				case CRUSH:
					sb.append(p.left).append(" crush click(s)");
					break;
				default:
					sb.append("dust into ").append(p.left).append(" potion(s) (one click, it repeats)");
					break;
			}
		}
		return sb.toString();
	}

	public void shutdown()
	{
		exec.shutdownNow();
	}
}
