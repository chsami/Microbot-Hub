package net.runelite.client.plugins.microbot.cgprep.boss;

import java.util.List;
import java.util.Random;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.cgprep.fight.PrayerGuard;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;

/**
 * Prep-phase prayers clicked the way the boss fight clicks them (user, 0.4.7): open the prayer tab
 * with its F-key, move the cursor along a natural path onto each prayer icon and click it there;
 * once nothing needs praying against for a moment, put the inventory back (F-key again).
 * Runs on the prep prayer thread; every move + click is one gesture under Microbot's input lock,
 * so it never interleaves with the prep loop's own clicks.
 */
public final class PrepPrayerInput implements PrayerGuard.Clicker
{
	private final HumanInput in = new HumanInput();
	private final Random rnd = new Random();
	private volatile long lastClickMs;
	private volatile long lastBusyMs;
	private volatile long tabBackAfterMs = 1500;
	/** We opened the prayer tab (so it's ours to close again). */
	private volatile boolean opened;
	/** Something worth logging once (no F-key bound to a tab), read and cleared by the script. */
	public volatile String warning;

	@Override
	public int toggle(List<Rs2PrayerEnum> prayers, boolean urgent) throws InterruptedException
	{
		if (prayers.isEmpty())
		{
			return 0;
		}
		// Seeing it and reacting: quicker for a protection that's needed now.
		Thread.sleep(logNormal(urgent ? 55 : 110, urgent ? 0.4 : 0.5, urgent ? 15 : 30, urgent ? 160 : 320));
		boolean wasOpen = Rs2Tab.isCurrentTab(InterfaceTab.PRAYER);
		if (!in.ensureTab(InputTiming.Tab.PRAYER))
		{
			takeTabWarning();
			return 0;
		}
		takeTabWarning();
		if (!wasOpen)
		{
			opened = true;
			Thread.sleep(40 + rnd.nextInt(80)); // the panel just changed: find the icon
		}
		int n = 0;
		for (Rs2PrayerEnum p : prayers)
		{
			QuickClicks.Target t = QuickClicks.prayer(Microbot.getClient(), p);
			if (t == null || t.rect == null)
			{
				continue; // the prayer tab closed under us; the guard re-decides next tick
			}
			if (n > 0)
			{
				Thread.sleep(35 + rnd.nextInt(70));
			}
			if (in.click(t))
			{
				n++;
			}
		}
		lastClickMs = System.currentTimeMillis();
		tabBackAfterMs = 900 + rnd.nextInt(2700); // 1.5 to 6 ticks before going back to the bag
		return n;
	}

	@Override
	public boolean idle(boolean prayingNow) throws InterruptedException
	{
		long now = System.currentTimeMillis();
		if (prayingNow)
		{
			lastBusyMs = now;
			return false;
		}
		if (!opened || now - Math.max(lastClickMs, lastBusyMs) < tabBackAfterMs)
		{
			return false;
		}
		opened = false;
		if (!Rs2Tab.isCurrentTab(InterfaceTab.PRAYER))
		{
			return false; // something else already switched away
		}
		boolean ok = in.ensureTab(InputTiming.Tab.INVENTORY);
		takeTabWarning();
		return ok;
	}

	private void takeTabWarning()
	{
		if (in.tabWarning != null)
		{
			warning = in.tabWarning;
			in.tabWarning = null;
		}
	}

	private long logNormal(double median, double sigma, long lo, long hi)
	{
		long v = Math.round(median * Math.exp(rnd.nextGaussian() * sigma));
		return Math.max(lo, Math.min(hi, v));
	}
}
