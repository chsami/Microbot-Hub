package net.runelite.client.plugins.microbot.actionreplay;

import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

import javax.inject.Inject;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

public class ActionReplayOverlay extends Overlay
{
	private static final int DOT_RADIUS = 6;
	private static final Color RECORD_COLOR = new Color(220, 40, 40);
	private static final Color PLAY_COLOR = new Color(40, 200, 80);

	private final ActionReplayPlugin plugin;

	@Inject
	public ActionReplayOverlay(ActionReplayPlugin plugin)
	{
		this.plugin = plugin;
		setPosition(OverlayPosition.TOP_RIGHT);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(OverlayPriority.HIGH);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		boolean recording = plugin.isRecording();
		boolean playing = plugin.isPlaying();
		if (!recording && !playing)
		{
			return null;
		}

		boolean blink = ((System.currentTimeMillis() / 500) % 2) == 0;
		Color color = recording ? RECORD_COLOR : PLAY_COLOR;
		String label = recording ? "AIO AIO REC" : "AIO AIO PLAY";

		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		g.setFont(FontManager.getRunescapeSmallFont());
		int labelWidth = g.getFontMetrics().stringWidth(label);
		int textBaseline = g.getFontMetrics().getAscent();
		int height = Math.max(DOT_RADIUS * 2 + 2, textBaseline + 2);
		int width = DOT_RADIUS * 2 + 6 + labelWidth;

		int dotY = (height - DOT_RADIUS * 2) / 2;
		if (blink || playing)
		{
			g.setColor(color);
			g.fillOval(0, dotY, DOT_RADIUS * 2, DOT_RADIUS * 2);
		}
		g.setColor(color.darker());
		g.setStroke(new BasicStroke(1f));
		g.drawOval(0, dotY, DOT_RADIUS * 2, DOT_RADIUS * 2);

		int textX = DOT_RADIUS * 2 + 6;
		int textY = (height + textBaseline) / 2 - 1;
		g.setColor(Color.BLACK);
		g.drawString(label, textX + 1, textY + 1);
		g.setColor(Color.WHITE);
		g.drawString(label, textX, textY);

		return new Dimension(width, height);
	}
}
