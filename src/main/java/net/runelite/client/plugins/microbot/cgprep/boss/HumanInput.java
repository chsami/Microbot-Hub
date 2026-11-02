package net.runelite.client.plugins.microbot.cgprep.boss;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.Random;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.input.InputLoop;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api.MouseMotion;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api.SystemCalls;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.DefaultNoiseProvider;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.DefaultOvershootManager;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.Flow;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.MouseMotionNature;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.SinusoidalDeviationProvider;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.util.FlowTemplates;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.util.Pair;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;

/**
 * Human-style fight input: the cursor travels to each target along Microbot's natural-mouse
 * curves (same flows, deviation and noise), lands on a point inside the target and clicks there.
 * Unlike the stock natural mouse (120-240 ms per move at "fast gamer" whatever the distance, plus
 * overshoots), the travel time follows Fitts' law ({@link InputTiming}): short hops inside the
 * side panel are quick, the trip to the 3D view takes longer, as with a real hand. Overshoots only
 * on long moves, sometimes, when Microbot's "simulate mistakes" antiban setting is on; moves get a
 * little slower over the fight with "simulate fatigue". Tabs are switched with the tab's F-key.
 */
public final class HumanInput
{
	private final Random rnd = new Random();
	private final MouseMotionNature nature = new MouseMotionNature();
	private final DefaultOvershootManager overshoots = new DefaultOvershootManager(rnd);
	private final Flow[] flows = {
		new Flow(FlowTemplates.variatingFlow()), new Flow(FlowTemplates.slowStartupFlow()), new Flow(FlowTemplates.slowStartup2Flow()),
		new Flow(FlowTemplates.adjustingFlow()), new Flow(FlowTemplates.jaggedFlow())};
	/** Grows a little over the fight with "simulate fatigue" on. */
	volatile double fatigue = 1.0;
	/** Extra slowness for an unhurried move (a drift between ticks). */
	private volatile double pace = 1.0;
	/** Set when a tab has no key bound (we fall back to switching it without input). */
	volatile String tabWarning;

	public HumanInput()
	{
		nature.setSystemCalls(new SystemCalls()
		{
			@Override
			public long currentTimeMillis()
			{
				return System.currentTimeMillis();
			}

			@Override
			public void sleep(long time)
			{
				if (time <= 0)
				{
					return;
				}
				try
				{
					Thread.sleep(time);
				}
				catch (InterruptedException e)
				{
					Thread.currentThread().interrupt();
				}
			}

			@Override
			public Dimension getScreenSize()
			{
				return Microbot.getClient().getCanvas().getSize();
			}

			@Override
			public void setMousePosition(int x, int y)
			{
				Microbot.getMouse().move(x, y);
			}
		});
		nature.setMouseInfo(() -> Microbot.getMouse().getMousePosition());
		nature.setDeviationProvider(new SinusoidalDeviationProvider(10.0));
		nature.setNoiseProvider(new DefaultNoiseProvider(2.0));
		nature.setSpeedManager(distance ->
		{
			double t = InputTiming.travelMean(distance) * Math.exp(rnd.nextGaussian() * 0.18) * fatigue * pace;
			return new Pair<>(flows[rnd.nextInt(flows.length)], (long) Math.max(25, t));
		});
		overshoots.setMinDistanceForOvershoots(150);
		overshoots.setMinOvershootMovementMs(50);
		overshoots.setOvershoots(0);
		nature.setOvershootManager(overshoots);
		nature.setEffectFadeSteps(8);
		nature.setMinSteps(6);
		nature.setTimeToStepsDivider(8.0);
		nature.setReactionTimeBaseMs(10); // settle on the target before pressing
		nature.setReactionTimeVariationMs(35);
	}

	/** Move along a natural path to (x, y). Call inside a gesture (see {@link #gesture}). */
	private void moveTo(int x, int y) throws InterruptedException
	{
		overshoots.setOvershoots(Rs2AntibanSettings.simulateMistakes && rnd.nextDouble() < 0.12 ? 1 : 0);
		new MouseMotion(nature, rnd, x, y).move();
	}

	private interface Body
	{
		void run(InputLoop.Emit emit) throws InterruptedException;
	}

	/**
	 * Run a move (+ click) as ONE gesture under Microbot's input lock, the same lock its own clicks
	 * take, so another thread's click (the prep loop while the prayer thread clicks, 0.4.7) can't
	 * start halfway through ours and drag the cursor away. False if it didn't run (a real mouse in
	 * use, or the lock wasn't free within its timeout).
	 */
	private boolean gesture(Body body) throws InterruptedException
	{
		InterruptedException[] ie = new InterruptedException[1];
		InputLoop.Result r = InputLoop.run(emit ->
		{
			try
			{
				body.run(emit);
			}
			catch (InterruptedException e)
			{
				ie[0] = e;
			}
		});
		if (ie[0] != null)
		{
			Thread.currentThread().interrupt();
			throw ie[0];
		}
		return r == InputLoop.Result.COMPLETED;
	}

	/** Travel to a point inside the target and click it there. */
	boolean click(QuickClicks.Target t) throws InterruptedException
	{
		if (t == null)
		{
			return false;
		}
		java.awt.Point p = t.fixed != null ? t.fixed : pick(t);
		if (p == null)
		{
			return false;
		}
		return gesture(emit ->
		{
			moveTo(p.x, p.y);
			// What VirtualMouse's quick click does once the cursor is there: the menu entry rides the
			// press, then press / short hold / release.
			if (!net.runelite.client.plugins.microbot.util.input.PointerState.isAt(p.x, p.y))
			{
				emit.move(p.x, p.y);
			}
			Microbot.targetMenu = t.entry;
			emit.press(p.x, p.y, 1);
			Thread.sleep(30 + rnd.nextInt(36));
			emit.release(p.x, p.y, 1);
			emit.click(p.x, p.y, 1);
			Microbot.getMouse().setLastClick(new net.runelite.api.Point(p.x, p.y));
		});
	}

	/** A point inside the target, biased to its centre like a person aiming. */
	private java.awt.Point pick(QuickClicks.Target t)
	{
		Rectangle r = t.rect;
		if (r == null && t.shape != null)
		{
			r = t.shape.getBounds();
		}
		if (r == null || r.width <= 0 || r.height <= 0)
		{
			return null;
		}
		for (int i = 0; i < 15; i++)
		{
			int x = (int) Math.round(r.getCenterX() + rnd.nextGaussian() * r.width / 6.0);
			int y = (int) Math.round(r.getCenterY() + rnd.nextGaussian() * r.height / 6.0);
			if (r.contains(x, y) && (t.shape == null || t.shape.contains(x, y)))
			{
				return new java.awt.Point(x, y);
			}
		}
		return new java.awt.Point((int) r.getCenterX(), (int) r.getCenterY());
	}

	/** Drift the cursor over a target without clicking (unhurried, a hand getting ready). */
	boolean hover(QuickClicks.Target t) throws InterruptedException
	{
		if (t == null)
		{
			return false;
		}
		java.awt.Point p = t.fixed != null ? t.fixed : pick(t);
		if (p == null)
		{
			return false;
		}
		pace = 1.3 + rnd.nextDouble() * 0.5;
		try
		{
			return gesture(emit -> moveTo(p.x, p.y));
		}
		finally
		{
			pace = 1.0;
		}
	}

	/** Open the inventory or prayer tab with its F-key; true once it shows (or already did). */
	boolean ensureTab(InputTiming.Tab tab)
	{
		InterfaceTab it = tab == InputTiming.Tab.PRAYER ? InterfaceTab.PRAYER : InterfaceTab.INVENTORY;
		if (Rs2Tab.isCurrentTab(it))
		{
			return true;
		}
		int key = it.getHotkey();
		if (key == -1)
		{
			tabWarning = "no key is bound to the " + it.getName() + " tab: switching it without input. Bind F-keys in the game's keybinding settings";
			Rs2Tab.switchTo(it);
		}
		else
		{
			Rs2Keyboard.keyPress(key);
		}
		long until = System.currentTimeMillis() + 150;
		while (System.currentTimeMillis() < until)
		{
			if (Rs2Tab.isCurrentTab(it))
			{
				return true;
			}
			try
			{
				Thread.sleep(10);
			}
			catch (InterruptedException e)
			{
				Thread.currentThread().interrupt();
				return false;
			}
		}
		return Rs2Tab.isCurrentTab(it);
	}

	/**
	 * Press a mouse button at (x0, y0), drag along a natural path to (x1, y1) and let go: the
	 * middle button turns the game camera (1.2.0). Unhurried, like a hand. False if it didn't run
	 * (a real mouse in use).
	 */
	public boolean drag(int x0, int y0, int x1, int y1, int button) throws InterruptedException
	{
		pace = 1.4 + rnd.nextDouble() * 0.6;
		try
		{
			return gesture(emit ->
			{
				moveTo(x0, y0);
				emit.press(x0, y0, button);
				Thread.sleep(35 + rnd.nextInt(45));
				moveTo(x1, y1);
				Thread.sleep(15 + rnd.nextInt(40));
				emit.release(x1, y1, button);
			});
		}
		finally
		{
			pace = 1.0;
		}
	}

	/**
	 * Move to (x, y) and roll the mouse wheel {@code notches} times ({@code rotation} +1 = towards
	 * you, -1 = away), a notch every 30-85 ms like a finger. False if it didn't run.
	 */
	public boolean scroll(int x, int y, int notches, int rotation) throws InterruptedException
	{
		return gesture(emit ->
		{
			moveTo(x, y);
			for (int i = 0; i < notches; i++)
			{
				emit.wheel(x, y, rotation, 1);
				Thread.sleep(30 + rnd.nextInt(55));
			}
		});
	}

	/**
	 * Your own mouse pointer is over the game view (1.5.4). The game has ONE cursor position that
	 * your mouse and the bot both move, so every bot move starts from wherever your pointer last
	 * was and looks like a jump. While yours is over the game, the bot only moves the cursor to
	 * click: no hovering ahead and no idle drift.
	 */
	public static boolean playerCursorOverGame()
	{
		try
		{
			java.awt.Canvas c = Microbot.getClient().getCanvas();
			java.awt.PointerInfo pi = java.awt.MouseInfo.getPointerInfo();
			if (c == null || pi == null || !c.isShowing())
			{
				return false;
			}
			java.awt.Point at = c.getLocationOnScreen();
			return new java.awt.Rectangle(at.x, at.y, c.getWidth(), c.getHeight()).contains(pi.getLocation());
		}
		catch (Exception e)
		{
			return false;
		}
	}

	/** Last OS pointer position seen and when it last changed (sampled each fight tick). */
	private static java.awt.Point lastPointer;
	private static long lastPointerMoveMs;
	/** How long after your last mouse move over the game the bot keeps to clicks only. */
	static final long PLAYER_MOVE_QUIET_MS = 4000;

	/**
	 * You moved your mouse over the game in the last few seconds (1.5.5). Only then does your mouse
	 * fight the bot for the cursor (a pointer parked still over the game sends nothing), so only
	 * then are the hover-ahead and idle drift skipped. Call once per tick: it samples the pointer.
	 */
	public static synchronized boolean playerMovingOverGame()
	{
		long now = System.currentTimeMillis();
		java.awt.Point p = null;
		try
		{
			java.awt.PointerInfo pi = java.awt.MouseInfo.getPointerInfo();
			p = pi == null ? null : pi.getLocation();
		}
		catch (Exception ignored)
		{
		}
		if (p != null && !p.equals(lastPointer))
		{
			if (lastPointer != null && playerCursorOverGame())
			{
				lastPointerMoveMs = now;
			}
			lastPointer = p;
		}
		return playerCursorOverGame() && now - lastPointerMoveMs < PLAYER_MOVE_QUIET_MS;
	}

	/** A small idle drift of the cursor (a hand resting on the mouse). */
	void drift() throws InterruptedException
	{
		java.awt.Point m = Microbot.getMouse().getMousePosition();
		if (m == null || m.x < 0 || m.y < 0)
		{
			return;
		}
		int dx = (int) Math.round(rnd.nextGaussian() * 9);
		int dy = (int) Math.round(rnd.nextGaussian() * 7);
		gesture(emit -> moveTo(Math.max(5, m.x + dx), Math.max(5, m.y + dy)));
	}
}
