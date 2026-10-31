package net.runelite.client.plugins.microbot.cgprep.antiban;

import java.util.function.Consumer;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

/**
 * CG Prep's bridge to Microbot's shared antiban (the Antiban panel in the client sidebar).
 *
 * Setup uses the standard template (applyCombatSetup: natural mouse, mistakes, fatigue, attention
 * span, action cooldowns) and then sets the built-in activity COMPLETING_THE_CORRUPTED_GAUNTLET
 * (category COMBAT_HIGH, intensity HIGH, so cooldowns stay short). If you've ticked "overwrite
 * script settings" in the Antiban panel, the template does nothing and your panel settings rule.
 *
 * The Gauntlet is timed and full of things that hit you, so the bot decides WHEN antiban may act:
 *  - a human-like reaction gap between consecutive actions (always, cheap);
 *  - Rs2Antiban.actionCooldown() only at safe moments (start room, not in combat, time to spare).
 *    It pauses all scripts until the Antiban plugin un-pauses them; a watchdog clears the pause
 *    if that never happens, so the run can't stall;
 *  - micro-breaks only outside the Gauntlet (between runs), never inside.
 */
public final class CgAntiban
{
	/** Longest an action cooldown we triggered may hold the global pause before we clear it. */
	private static final long COOLDOWN_WATCHDOG_MS = 15_000;
	/**
	 * Our own gate on top of the shared settings. The combat template sets actionCooldownChance
	 * to 1.0 (a cooldown after EVERY action); in a 7:30 timed prep that cost ~12 s in the 0.2.5
	 * test (7 cooldowns). So: offer one with this chance, at most MAX_PER_RUN times per run.
	 */
	private static final double OFFER_CHANCE = 0.2;
	private static final int MAX_PER_RUN = 2;
	private final java.util.Random rng = new java.util.Random();
	private volatile int cooldownsThisRun;

	private final Consumer<String> logger;
	private volatile boolean active;
	private volatile long cooldownStartedMs;

	public CgAntiban(Consumer<String> logger)
	{
		this.logger = logger;
	}

	public boolean isActive()
	{
		return active;
	}

	public void start()
	{
		Rs2Antiban.resetAntibanSettings();
		Rs2Antiban.antibanSetupTemplates.applyCombatSetup();
		Rs2Antiban.setActivity(Activity.COMPLETING_THE_CORRUPTED_GAUNTLET);
		active = true;
		logger.accept("antiban on: activity=" + Rs2Antiban.getActivity() + " intensity=" + Rs2Antiban.getActivityIntensity()
			+ " cooldownChance=" + Rs2AntibanSettings.actionCooldownChance + " naturalMouse=" + Rs2AntibanSettings.naturalMouse
			+ " panelOverrides=" + Rs2AntibanSettings.overwriteScriptSettings);
	}

	public void stop()
	{
		if (!active)
		{
			return;
		}
		releaseStuckCooldown(true);
		Rs2Antiban.resetAntibanSettings();
		active = false;
	}

	public void newRun()
	{
		cooldownsThisRun = 0;
	}

	/** A short human reaction gap after an action that changed something. Script thread only. */
	public void reactionGap()
	{
		if (!active)
		{
			return;
		}
		Global.sleepGaussianFatigued(260, 90);
	}

	/**
	 * Offer the shared antiban a chance to take an action cooldown. Call only when it's safe:
	 * the caller checks the room and the timer; this also refuses while in combat.
	 */
	public void maybeCooldown(String where)
	{
		if (!active || Rs2Player.isInCombat() || Microbot.pauseAllScripts.get()
			|| cooldownsThisRun >= MAX_PER_RUN || rng.nextDouble() >= OFFER_CHANCE)
		{
			return;
		}
		Rs2Antiban.actionCooldown();
		if (Rs2AntibanSettings.actionCooldownActive)
		{
			cooldownsThisRun++;
			cooldownStartedMs = System.currentTimeMillis();
			logger.accept("action cooldown after " + where + " (" + Rs2Antiban.getTIMEOUT() + " ticks)");
		}
	}

	/** Micro-break by chance: only call outside the Gauntlet. */
	public void maybeMicroBreak()
	{
		if (active && Rs2Antiban.takeMicroBreakByChance())
		{
			logger.accept("micro-break");
		}
	}

	/** Watchdog: if our cooldown is still holding the global pause long after it should, clear it. */
	public void releaseStuckCooldown(boolean force)
	{
		long started = cooldownStartedMs;
		if (started == 0)
		{
			return;
		}
		boolean stillPaused = Microbot.pauseAllScripts.get() && Rs2AntibanSettings.actionCooldownActive;
		if (!stillPaused)
		{
			cooldownStartedMs = 0;
			return;
		}
		if (force || System.currentTimeMillis() - started > COOLDOWN_WATCHDOG_MS)
		{
			Rs2AntibanSettings.actionCooldownActive = false;
			Microbot.pauseAllScripts.set(false);
			cooldownStartedMs = 0;
			logger.accept("cooldown watchdog: cleared a stuck global pause");
		}
	}

	/** True while an antiban pause (ours or a micro-break) is holding the scripts. */
	public boolean holding()
	{
		return Microbot.pauseAllScripts.get() || Rs2AntibanSettings.microBreakActive;
	}
}
