package net.runelite.client.plugins.microbot.cgprep.antiban;

import java.util.Random;
import net.runelite.client.plugins.microbot.util.Global;

/**
 * Spacing between consecutive clicks, the way a player menus. Script thread only.
 *
 * What players do (and what the 0.2.6 test got wrong: everything was ~3 ticks apart):
 *  - INVENTORY: item-on-item and wear/wield clicks need no game tick in between. "Use A" then
 *    "on B" is one quick motion, and four armour pieces go on in about a second.
 *  - MENU (singing bowl make menu): one selection per game tick. The caller must have seen the
 *    previous selection's result (the new item) before the next click; this only adds the
 *    short human gap after that.
 *  - WORLD: clicking an object or the ground is a context switch, so a longer reaction.
 *
 * The gap runs from the previous click, or from when its result showed up if the next click
 * had to wait for that (observed()). Nothing here sleeps on the client thread.
 */
public final class Pace
{
	public enum Kind
	{
		INVENTORY, MENU, WORLD
	}

	private final Random rng = new Random();
	private volatile Kind lastKind;
	private volatile long lastMs;
	private volatile boolean enabled = true;

	public void setEnabled(boolean enabled)
	{
		this.enabled = enabled;
	}

	/** Wait (if needed) so a click of this kind follows the previous click naturally. */
	public void before(Kind next)
	{
		if (next == Kind.INVENTORY)
		{
			ensureInventoryTab();
		}
		long since = System.currentTimeMillis() - lastMs;
		int want = gapMs(lastKind, next);
		long remaining = want - since;
		if (remaining > 0)
		{
			Global.sleep((int) remaining);
		}
	}

	/** Record that a click of this kind just happened. */
	public void after(Kind kind)
	{
		lastKind = kind;
		lastMs = System.currentTimeMillis();
	}

	/**
	 * The result of the previous click just showed up (new item, menu refreshed). For a click that
	 * had to wait for it, the human gap runs from this moment (seeing it), not from the click.
	 */
	public void observed()
	{
		lastMs = System.currentTimeMillis();
	}

	/** Forget the previous click (e.g. after walking or a pause): the next one gets a full reaction gap. */
	public void reset()
	{
		lastKind = null;
		lastMs = 0;
	}

	/**
	 * Inventory clicks need the inventory open. If another tab is up (the prayer tab after prep
	 * prayers, 0.4.7) press the inventory's F-key like a player, instead of letting Microbot switch
	 * it without any input. Falls back to Microbot's switch when no key is bound.
	 */
	public static void ensureInventoryTab()
	{
		net.runelite.client.plugins.microbot.globval.enums.InterfaceTab inv = net.runelite.client.plugins.microbot.globval.enums.InterfaceTab.INVENTORY;
		if (net.runelite.client.plugins.microbot.util.tabs.Rs2Tab.isCurrentTab(inv))
		{
			return;
		}
		int key = inv.getHotkey();
		if (key == -1)
		{
			net.runelite.client.plugins.microbot.util.tabs.Rs2Tab.switchTo(inv);
			return;
		}
		net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard.keyPress(key);
		Global.sleepUntil(() -> net.runelite.client.plugins.microbot.util.tabs.Rs2Tab.isCurrentTab(inv), 300);
	}

	int gapMs(Kind prev, Kind next)
	{
		if (!enabled)
		{
			return 0;
		}
		if (prev == Kind.INVENTORY && next == Kind.INVENTORY)
		{
			return gauss(135, 45, 60, 290);
		}
		if (prev == Kind.MENU && next == Kind.MENU)
		{
			// Bowl (user, 0.3.16: still too slow): one selection per tick, like a player. Usually
			// the very next tick, sometimes a tick and a bit later.
			return rng.nextDouble() < 0.72 ? gauss(140, 60, 40, 330) : gauss(720, 140, 480, 1050);
		}
		if (prev != null && prev != Kind.WORLD && next != Kind.WORLD)
		{
			return gauss(210, 70, 100, 420); // inventory <-> menu
		}
		return gauss(300, 90, 140, 600); // anything involving the game world, or a fresh start
	}

	private int gauss(double mean, double sd, int lo, int hi)
	{
		int v = (int) Math.round(mean + rng.nextGaussian() * sd);
		return Math.max(lo, Math.min(hi, v));
	}
}
