package net.runelite.client.plugins.microbot.theatre.Bloat;

import net.runelite.api.NPC;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.theatre.RoomOverlay;
import net.runelite.client.plugins.microbot.theatre.TheatreConfig;
import net.runelite.client.ui.overlay.OverlayLayer;

import javax.inject.Inject;
import java.awt.*;

public class BloatOverlay extends RoomOverlay
{
	@Inject
	private Bloat bloat;

	@Inject
	protected BloatOverlay(TheatreConfig config)
	{
		super(config);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	public Dimension render(Graphics2D graphics)
	{
		if (bloat.isBloatActive())
		{
			if (config.bloatIndicator())
			{
				renderPoly(graphics, bloat.getBloatStateColor(), bloat.getBloatTilePoly(), 2);
			}

			if (config.bloatTickCounter())
			{
				NPC boss = bloat.getBloatNPC();

				int tick = bloat.getBloatTickCount();
				final String ticksCounted = String.valueOf(tick);
				Point canvasPoint = boss.getCanvasTextLocation(graphics, ticksCounted, 50);
				if ((bloat.getBloatState() > 1 && bloat.getBloatState() < 4) && config.BloatTickCountStyle() == TheatreConfig.BLOATTIMEDOWN.COUNTDOWN)
				{
					renderTextLocation(graphics, String.valueOf(33 - bloat.getBloatDownCount()), Color.WHITE, canvasPoint);
				}
				else
				{
					renderTextLocation(graphics, ticksCounted, Color.WHITE, canvasPoint);
				}
			}

			if (config.bloatHands())
			{
				for (WorldPoint point : bloat.getBloatHands().keySet())
				{
					drawTile(graphics, point, config.bloatHandsColor(), config.bloatHandsWidth(), 255, 10);
				}
			}
		}
		return null;
	}
}
