package net.runelite.client.plugins.microbot.cgprep.actions;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

import java.awt.Rectangle;
import java.util.Random;
import net.runelite.api.Client;
import net.runelite.api.VarClientInt;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.cgprep.boss.HumanInput;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;

/**
 * The camera moved the way a player moves it (1.2.0, user: "don't just snap the camera"):
 *
 * TURNING: a middle-mouse drag across the game view, along a natural-mouse path. How many pixels
 * turn the camera how far isn't known up front (it depends on the client), so the first drag is
 * a moderate one and the yaw it actually produced sets the scale; each later drag is sized from
 * that and re-checked. Small leftovers are left alone (a tile only has to be on screen). If drags
 * don't turn the camera (middle-button camera off in the game settings), the arrow keys are held
 * instead (Microbot's Rs2Camera.setAngle), and only if that doesn't move it either (keys need the
 * window focused, 0.3.23) Microbot's eased yaw.
 *
 * ZOOM: the mouse wheel, a few notches at a time with short pauses, until the zoom stops changing.
 */
public final class HumanCamera
{
	private static final HumanCamera INSTANCE = new HumanCamera();

	public static HumanCamera get()
	{
		return INSTANCE;
	}

	/** Yaw units per full turn (Rs2Camera.getYaw / setYawInstant scale). */
	private static final int TURN = 2048;
	/** Drag pixels per yaw unit, signed, learned from the drags so far (NaN = not yet). */
	private double gain = Double.NaN;
	private int noResponse;
	private boolean dragsDontTurn;
	private final Random rnd = new Random();
	private final HumanInput in = new HumanInput();
	/** Set by the script: one-off notes for the decision log. */
	public volatile java.util.function.Consumer<String> log;

	private HumanCamera()
	{
	}

	/** True when the tile is (now) on screen: turns towards it first if it isn't. */
	public boolean bringOnScreen(LocalPoint lp) throws InterruptedException
	{
		if (lp == null)
		{
			return false;
		}
		if (Rs2Camera.isTileOnScreen(lp))
		{
			return true;
		}
		Integer to = angleTo(lp);
		if (to == null)
		{
			return false;
		}
		int deg = Math.floorMod(to - 90, 360);
		int yaw = (int) Math.round(deg / 0.17578125) % TURN;
		if (!turnTo(yaw, 140, deg))
		{
			return Rs2Camera.isTileOnScreen(lp);
		}
		return sleepUntil(() -> Rs2Camera.isTileOnScreen(lp), 600);
	}

	/**
	 * Turn the camera to {@code target} yaw (0-2047), within {@code tolerance}. {@code degrees} is
	 * the same target in Rs2Camera.setAngle's degrees (key fallback). False if it couldn't (a real
	 * mouse in use, or nothing moved it).
	 */
	public boolean turnTo(int target, int tolerance, int degrees) throws InterruptedException
	{
		for (int step = 0; step < 4 && !dragsDontTurn; step++)
		{
			int before = Rs2Camera.getYaw();
			int delta = shortest(target - before);
			if (Math.abs(delta) <= tolerance)
			{
				return true;
			}
			Rectangle vp = viewport();
			if (vp == null)
			{
				return false;
			}
			int maxDx = (int) (vp.width * 0.42);
			int dx = Double.isNaN(gain) ? (int) Math.signum(delta) * Math.min(maxDx, 130 + rnd.nextInt(40)) : (int) Math.round(delta * gain);
			dx = Math.max(-maxDx, Math.min(maxDx, dx));
			if (Math.abs(dx) < 15)
			{
				dx = dx < 0 ? -15 : 15;
			}
			int x0 = (int) Math.round(vp.x + vp.width / 2.0 - dx / 2.0 + rnd.nextGaussian() * vp.width * 0.06);
			int y0 = (int) Math.round(vp.y + vp.height * (0.48 + rnd.nextGaussian() * 0.08));
			x0 = Math.max(vp.x + 10, Math.min(vp.x + vp.width - 10, x0));
			y0 = Math.max(vp.y + 20, Math.min(vp.y + vp.height - 20, y0));
			int x1 = Math.max(vp.x + 5, Math.min(vp.x + vp.width - 5, x0 + dx));
			// Level drag (1.6.1): a few pixels up or down tilt the camera too, and over a session of
			// drags the pitch could drift until stations fell off screen.
			int y1 = y0;
			if (!in.drag(x0, y0, x1, y1, 2))
			{
				return false; // a real mouse in use: leave the camera to the player
			}
			settle();
			int moved = shortest(Rs2Camera.getYaw() - before);
			if (Math.abs(moved) < 4)
			{
				if (++noResponse >= 2)
				{
					dragsDontTurn = true;
					note("camera: middle-mouse drags don't turn the camera here (middle-button camera off in the game settings?), using the arrow keys");
				}
				continue;
			}
			noResponse = 0;
			double g = (double) (x1 - x0) / moved;
			gain = Double.isNaN(gain) ? g : gain * 0.5 + g * 0.5;
			Thread.sleep(40 + rnd.nextInt(90));
		}
		if (Math.abs(shortest(target - Rs2Camera.getYaw())) <= tolerance)
		{
			return true;
		}
		if (!dragsDontTurn)
		{
			return false; // drags work, 4 weren't enough: the caller walks closer instead
		}
		int before = Rs2Camera.getYaw();
		Rs2Camera.setAngle(degrees, 25); // holds the arrow keys until the angle is reached
		if (Math.abs(shortest(Rs2Camera.getYaw() - before)) < 4)
		{
			Rs2Camera.setYaw(target); // keys don't reach an unfocused client: Microbot's eased turn
		}
		return Math.abs(shortest(target - Rs2Camera.getYaw())) <= tolerance;
	}

	/**
	 * Zoom all the way out with the mouse wheel (at start, user 1.2.0). False when it couldn't run
	 * yet (your own mouse in use, e.g. the click that switched the plugin on; the game view not
	 * readable): the script tries again a moment later.
	 */
	public boolean zoomOutFully() throws InterruptedException
	{
		Rectangle vp = viewport();
		// The zoom can be negative (RuneLite's Camera plugin extends the limit: 1.2.2 went to -52),
		// so "unknown" is MIN_VALUE, not -1 (1.5.1: -52 read as unknown and the zoom retried for 25 s).
		if (vp == null || zoom() == Integer.MIN_VALUE || net.runelite.client.plugins.microbot.util.input.InputArbiter.isHuman())
		{
			return false;
		}
		int rotation = 1; // towards you: zooms out in the OSRS client; checked on the first burst
		boolean flipped = false;
		int start = zoom();
		long until = System.currentTimeMillis() + 6000; // never hold the bot up for long
		for (int burst = 0; burst < 10 && System.currentTimeMillis() < until; burst++)
		{
			int before = zoom();
			int x = (int) Math.round(vp.x + vp.width * (0.5 + rnd.nextGaussian() * 0.12));
			int y = (int) Math.round(vp.y + vp.height * (0.45 + rnd.nextGaussian() * 0.1));
			x = Math.max(vp.x + 10, Math.min(vp.x + vp.width - 10, x));
			y = Math.max(vp.y + 10, Math.min(vp.y + vp.height - 10, y));
			if (!in.scroll(x, y, 3 + rnd.nextInt(4), rotation))
			{
				return false;
			}
			sleepUntil(() -> zoom() != before, 250);
			Thread.sleep(90 + rnd.nextInt(160));
			int after = zoom();
			if (after > before && !flipped)
			{
				rotation = -rotation; // that zoomed in (bigger = closer): the other way, once
				flipped = true;
				continue;
			}
			if (after >= before)
			{
				break; // as far out as it goes
			}
		}
		note("camera: zoomed out with the mouse wheel (" + start + " -> " + zoom() + ")");
		return true;
	}

	/**
	 * Angle (degrees, 0 = east, counter-clockwise, as Rs2Camera's) from the player to a scene
	 * point, both in LOCAL coordinates (1.6.2). Microbot's Rs2Camera.angleToTile(LocalPoint) puts
	 * the local point against the player's WORLD tile, which gives a meaningless angle: every
	 * "turn to it" turned somewhere random, and stations / nodes stayed off screen (the pump,
	 * range and grym hangs).
	 */
	static Integer angleTo(LocalPoint lp)
	{
		return Microbot.getClientThread().runOnClientThreadOptional(() ->
		{
			net.runelite.api.Player me = Microbot.getClient().getLocalPlayer();
			LocalPoint at = me == null ? null : me.getLocalLocation();
			if (at == null)
			{
				return null;
			}
			int dx = lp.getX() - at.getX();
			int dy = lp.getY() - at.getY();
			if (dx == 0 && dy == 0)
			{
				return null;
			}
			int a = (int) Math.round(Math.toDegrees(Math.atan2(dy, dx)));
			return Math.floorMod(a, 360);
		}).orElse(null);
	}

	/** The zoom of the current viewport mode (VarClientInt; bigger = closer), Integer.MIN_VALUE if unknown. */
	static int zoom()
	{
		return Microbot.getClientThread().runOnClientThreadOptional(() ->
		{
			Client c = Microbot.getClient();
			return c.getVarcIntValue(c.isResized() ? VarClientInt.CAMERA_ZOOM_RESIZABLE_VIEWPORT : VarClientInt.CAMERA_ZOOM_FIXED_VIEWPORT);
		}).orElse(Integer.MIN_VALUE);
	}

	private static Rectangle viewport()
	{
		return Microbot.getClientThread().runOnClientThreadOptional(() ->
		{
			Client c = Microbot.getClient();
			int w = c.getViewportWidth();
			int h = c.getViewportHeight();
			return w <= 0 || h <= 0 ? null : new Rectangle(c.getViewportXOffset(), c.getViewportYOffset(), w, h);
		}).orElse(null);
	}

	/** Wait until the yaw stops changing (a drag ends where the hand stops; up to 0.6 s). */
	private static void settle()
	{
		int[] last = {Integer.MIN_VALUE};
		sleepUntil(() ->
		{
			int y = Rs2Camera.getYaw();
			boolean same = y == last[0];
			last[0] = y;
			return same;
		}, 600);
	}

	private static int shortest(int d)
	{
		int x = Math.floorMod(d, TURN);
		return x > TURN / 2 ? x - TURN : x;
	}

	private void note(String s)
	{
		java.util.function.Consumer<String> l = log;
		if (l != null)
		{
			l.accept(s);
		}
	}
}
