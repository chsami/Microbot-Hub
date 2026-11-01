package net.runelite.client.plugins.microbot.cgprep.antiban;

import java.util.function.Consumer;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerPlugin;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;

/**
 * Breaks only between runs (1.5.0, user). Microbot's break handlers (v1 and v2) start a break
 * whenever their timer runs out and nothing has hit us for 10 s, which in a run means mid-prep:
 * the prep timer keeps running while the bot is paused (or logged out). So the shared break-handler
 * lock is held for the whole time we're inside the Gauntlet and released in the lobby, and the
 * lobby loop waits there while a break is due ({@link #breakDue}) instead of entering again.
 */
public final class BreakGate
{
	private boolean locked;
	/** Set by {@link #release} (plugin stopping): no more locking until {@link #arm} (next start). */
	private volatile boolean stopped;

	/** Plugin starting: locking allowed again. */
	public void arm()
	{
		stopped = false;
	}

	/** Lock while inside the Gauntlet, unlock outside. Script thread, every loop. */
	public synchronized void sync(boolean inGauntlet, Consumer<String> log)
	{
		if (stopped && inGauntlet)
		{
			return; // stopping: never take the lock again (a fast-follow pass after shutdown)
		}
		if (inGauntlet == locked)
		{
			return;
		}
		BreakHandlerScript.setLockState(inGauntlet);
		locked = inGauntlet;
		log.accept(inGauntlet ? "break handler locked for the run" : "break handler unlocked (lobby)");
	}

	/** Plugin stopping: let go of the lock if we hold it. */
	public synchronized void release()
	{
		stopped = true;
		if (locked)
		{
			BreakHandlerScript.setLockState(false);
			locked = false;
		}
	}

	/** A break handler is on and wants (or is taking) a break now. */
	public static boolean breakDue()
	{
		try
		{
			if (Microbot.isPluginEnabled(BreakHandlerPlugin.class)
				&& (BreakHandlerScript.isBreakActive() || (BreakHandlerScript.breakIn != -1 && BreakHandlerScript.breakIn <= 0)))
			{
				return true;
			}
			return v2BreakDue();
		}
		catch (Throwable t)
		{
			return false; // a client without these classes: no break handling
		}
	}

	/** Break Handler v2, read by reflection (it moves between client versions): its state isn't WAITING_FOR_BREAK. */
	@SuppressWarnings("unchecked")
	private static boolean v2BreakDue()
	{
		try
		{
			String pkg = "net.runelite.client.plugins.microbot.breakhandler.breakhandlerv2.";
			Class<? extends Plugin> plugin = (Class<? extends Plugin>) Class.forName(pkg + "BreakHandlerV2Plugin");
			if (!Microbot.isPluginEnabled(plugin))
			{
				return false;
			}
			Object state = Class.forName(pkg + "BreakHandlerV2State").getMethod("getCurrentState").invoke(null);
			return state != null && !"WAITING_FOR_BREAK".equals(((Enum<?>) state).name());
		}
		catch (Throwable t)
		{
			return false;
		}
	}
}
