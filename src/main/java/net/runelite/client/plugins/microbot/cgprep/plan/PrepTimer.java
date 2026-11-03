package net.runelite.client.plugins.microbot.cgprep.plan;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.cgprep.data.CgIds;

/**
 * Seconds of prep left. Primary source: the in-game timer overlay text (GauntletOverlay.TIMER_TEXT).
 * Fallback: wall-clock since the run started, against the mode's known prep length.
 *
 * TODO(recon): confirm the timer widget's text format (assumed "m:ss") and that it counts down.
 */
public final class PrepTimer
{
	private static final Pattern MMSS = Pattern.compile("(\\d+):(\\d{2})");

	private long runStartMs = -1;
	private int prepSeconds;

	public void start(int prepSeconds)
	{
		this.prepSeconds = prepSeconds;
		this.runStartMs = System.currentTimeMillis();
	}

	public void clear()
	{
		runStartMs = -1;
	}

	public boolean running()
	{
		return runStartMs > 0;
	}

	/** Seconds left, or -1 if unknown. Script thread. */
	public int secondsLeft()
	{
		Integer fromWidget = Microbot.getClientThread().runOnClientThreadOptional(() ->
		{
			Widget w = Microbot.getClient().getWidget(CgIds.IF_OVERLAY_TIMER_TEXT);
			if (w == null || w.isHidden() || w.getText() == null)
			{
				return null;
			}
			Matcher m = MMSS.matcher(w.getText());
			return m.find() ? Integer.parseInt(m.group(1)) * 60 + Integer.parseInt(m.group(2)) : null;
		}).orElse(null);
		if (fromWidget != null)
		{
			return fromWidget;
		}
		if (runStartMs < 0)
		{
			return -1;
		}
		return Math.max(0, prepSeconds - (int) ((System.currentTimeMillis() - runStartMs) / 1000));
	}
}
