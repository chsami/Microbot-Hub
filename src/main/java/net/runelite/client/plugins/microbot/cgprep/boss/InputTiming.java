package net.runelite.client.plugins.microbot.cgprep.boss;

import java.util.Random;

/**
 * How long a human-style click takes, for both the in-game natural mouse (to decide whether a
 * click still fits in this tick) and the simulator (to sample when it lands).
 *
 * Mouse travel follows Fitts' law, time = A + B * log2(1 + distance / width), with a hand near a
 * fast gamer's (inventory slot to slot ~100 ms, side panel to the 3D view ~250 ms), unlike Microbot's
 * stock natural mouse, which takes 120-240 ms (fast gamer) for any distance. Then it settles
 * 10-45 ms and presses 30-65 ms. Clicks on prayers need the prayer tab open and item clicks the
 * inventory tab: switching is one key press of the tab's F-key (50-130 ms, as Rs2Keyboard does).
 */
public final class InputTiming
{
	public enum Tab
	{
		INVENTORY, PRAYER, OTHER
	}

	public enum Zone
	{
		SCENE, PANEL
	}

	public static final double FITTS_A = 25;
	public static final double FITTS_B = 60;
	public static final double TARGET_WIDTH = 30;
	/** Typical travel in px: inside the side panel, panel <-> 3D view, inside the 3D view. */
	public static final double D_PANEL = 70;
	public static final double D_CROSS = 380;
	public static final double D_SCENE = 110;
	/** Stop starting clicks whose estimated end is later than this into the tick. */
	public static final int BUDGET_MS = 540;

	private InputTiming()
	{
	}

	/** Which tab a click needs (null: none, it's in the 3D view). */
	public static Tab tabFor(Click c)
	{
		switch (c.type)
		{
			case PROTECT:
			case PROTECT_OFF:
			case OFFENSE_ON:
			case OFFENSE_OFF:
				return Tab.PRAYER;
			case WIELD:
			case EAT:
			case DRINK:
				return Tab.INVENTORY;
			default:
				return null;
		}
	}

	public static Zone zoneFor(Click c)
	{
		return tabFor(c) == null ? Zone.SCENE : Zone.PANEL;
	}

	public static double travelMean(double distance)
	{
		return FITTS_A + FITTS_B * (Math.log(1 + distance / TARGET_WIDTH) / Math.log(2));
	}

	public static double distance(Zone from, Zone to)
	{
		if (from != to)
		{
			return D_CROSS;
		}
		return from == Zone.PANEL ? D_PANEL : D_SCENE;
	}

	/** Expected ms for a click from where the cursor is (no tab switch). */
	public static double estimate(Zone from, Zone to)
	{
		return travelMean(distance(from, to)) + 28 + 48;
	}

	public static final double KEY_MS = 95;

	/** Sampled ms for the travel (the simulator; noisy around the Fitts mean, sometimes an overshoot). */
	public static double sampleTravel(Random r, double distance, double fatigue, boolean mistakes)
	{
		double t = travelMean(distance) * Math.exp(r.nextGaussian() * 0.18) * fatigue;
		if (mistakes && distance > 150 && r.nextDouble() < 0.12)
		{
			t += 70 + r.nextDouble() * 80; // overshoot and correct
		}
		return t;
	}

	public static double sampleSettle(Random r)
	{
		return 10 + r.nextDouble() * 35;
	}

	public static double samplePress(Random r)
	{
		return 30 + r.nextDouble() * 35;
	}

	public static double sampleKey(Random r)
	{
		return 50 + r.nextDouble() * 80 + 15; // press-release, then the tab is drawn
	}
}
